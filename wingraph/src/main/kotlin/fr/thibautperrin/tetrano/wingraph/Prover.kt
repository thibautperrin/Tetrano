package fr.thibautperrin.tetrano.wingraph

import fr.thibautperrin.tetrano.engine.nn.QubicNet
import fr.thibautperrin.tetrano.engine.nn.Tactics
import fr.thibautperrin.tetrano.engine.wingraph.WinRules

/**
 * Phase 1: proves that the first move on cell 0 wins, growing the [TopTree]
 * with [threads] workers. Each job takes the most-proving free leaf:
 * 1. bounded [DfPn] on it ([RunConfig.jobEvals] evaluations);
 * 2. proved: its strategy is walked at once ([Walker] + [SmallGraphChooser],
 *    with the worker's df-pn table still warm) and recorded in the book;
 *    disproved: settled;
 * 3. neither: expanded with the network.
 */
class Prover(
    private val run: Run,
    private val net: QubicNet,
) {
    private val tree get() = run.tree!!
    private val lock = run.lock

    @Volatile
    private var stopping = false

    fun requestStop() {
        stopping = true
        workers.forEach { it.dfpn.abort = true }
    }

    private val workers = List(run.config.threads) { Worker(it) }

    /** Runs until the root is solved or a stop is requested; true iff the root is proved. */
    fun prove(): Boolean {
        val threads = workers.map { w -> Thread({ w.loop() }, "prover-${w.id}").also { it.priority = Thread.MIN_PRIORITY } }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        return tree.root.state == TNode.PROVED
    }

    private inner class Worker(val id: Int) {
        val dfpn = DfPn(run.config.ttLog2, nullMovePn = 10)
        val eval = NetEval(net)
        val chooser = SmallGraphChooser(dfpn, eval, run.bookView, run.config.jobEvals, run.config.maxChooseEvals) { stopping }
        val walker = Walker(run.bookView, { a, d, m -> run.book.putIfAbsent(a, d, m) }, run.covered, chooser)
        val kids = Children()
        val policy = FloatArray(64)
        val ruleVcf = WinRules.newVcf()

        fun loop() {
            while (!stopping) {
                val leaf = synchronized(lock) {
                    if (tree.root.solved) null else tree.select()
                }
                if (leaf == null) {
                    if (synchronized(lock) { tree.root.solved }) return
                    Thread.sleep(200) // every open leaf is held by another worker
                    continue
                }
                try {
                    job(leaf)
                } catch (e: Throwable) {
                    synchronized(lock) { tree.release(leaf) }
                    run.log("worker $id: ${e.javaClass.simpleName}: ${e.message} on ${hex(leaf.a)}/${hex(leaf.d)}")
                    if (e is IllegalStateException || e is Error) {
                        run.fatal = e
                        run.requestStop()
                    }
                }
            }
        }

        private fun job(leaf: TNode) {
            val r = dfpn.solve(leaf.a, leaf.d, run.config.jobEvals)
            run.stats.jobs.incrementAndGet()
            if (stopping) {
                synchronized(lock) { tree.release(leaf) }
                return
            }
            when (r) {
                DfPn.PROVED -> {
                    val size = extract(leaf)
                    if (size < 0) {
                        synchronized(lock) { tree.release(leaf) }
                        return
                    }
                    run.stats.provedLeaves.incrementAndGet()
                    synchronized(lock) { tree.settle(leaf, true, size) }
                }
                DfPn.DISPROVED -> {
                    run.stats.disprovedLeaves.incrementAndGet()
                    synchronized(lock) { tree.settle(leaf, false, 0) }
                }
                else -> expand(leaf)
            }
        }

        /** Walks the strategy below a proved leaf; returns its size, or -1 when stopping. */
        private fun extract(leaf: TNode): Int {
            walker.reset()
            var size = 0
            if (leaf.isOr) {
                size = walker.walk(leaf.a, leaf.d)
            } else {
                val ta = Tactics.threats(leaf.a, leaf.d)
                if (java.lang.Long.bitCount(ta) < 2) {
                    val k = kids.generate(leaf.a, leaf.d, Board.candidateMoves(leaf.a, leaf.d))
                    val ca = k.a.copyOf(k.count)
                    val cd = k.d.copyOf(k.count)
                    for (i in ca.indices) {
                        val s = walker.walk(ca[i], cd[i])
                        if (s < 0) {
                            size = -1
                            break
                        }
                        size += s
                    }
                }
            }
            if (size < 0) {
                if (stopping) return -1
                // Proven by df-pn but not extracted within the budget: the assembly will retry.
                run.log("extraction failed below ${hex(leaf.a)}/${hex(leaf.d)}: ${walker.failure}")
                run.stats.extractionFailures.incrementAndGet()
                return FAILED_SIZE
            }
            run.stats.walkedPositions.addAndGet(walker.positions)
            return size
        }

        private fun expand(leaf: TNode) {
            if (leaf.isOr) {
                val moves = Board.candidateMoves(leaf.a, leaf.d)
                val k = kids.generate(leaf.a, leaf.d, moves)
                eval.policy(leaf.a, leaf.d, moves, policy)
                val priors = FloatArray(k.count) { i ->
                    var p = 0f
                    Board.cells(k.moveClass[i]) { p += policy[it] }
                    p
                }
                val order = (0 until k.count).sortedByDescending { priors[it] }
                // First children: the best moves up to INITIAL_MASS of the policy, at least 1, at most INITIAL_MAX.
                var mass = 0f
                var first = 0
                while (first < order.size && first < INITIAL_MAX && (first == 0 || mass < INITIAL_MASS)) {
                    mass += priors[order[first]]
                    first++
                }
                val moveList = order.map { k.move[it] }
                synchronized(lock) {
                    val children = (0 until first).map { tree.newAndChild(leaf, moveList[it], priors[order[it]]) }
                    val pending = order.drop(first)
                    tree.expand(
                        leaf, children,
                        IntArray(pending.size) { k.move[pending[it]] },
                        FloatArray(pending.size) { priors[pending[it]] },
                    )
                }
            } else {
                val k = kids.generate(leaf.a, leaf.d, Board.candidateMoves(leaf.a, leaf.d))
                val children = ArrayList<TNode>(k.count)
                val settled = ArrayList<Pair<TNode, Int>>() // child, state
                for (i in 0 until k.count) {
                    val child = TNode(k.a[i], k.d[i], leaf, k.move[i])
                    val known = run.solved.get(child.a, child.d)
                    when {
                        known == TNode.PROVED || known == TNode.DISPROVED -> settled += child to known
                        Tactics.threats(child.a, child.d) != 0L -> settled += child to TNode.PROVED
                        java.lang.Long.bitCount(Tactics.threats(child.d, child.a)) >= 2 -> settled += child to TNode.DISPROVED
                        Tactics.threats(child.d, child.a) == 0L && WinRules.vcfMove(child.a, child.d, ruleVcf) >= 0 ->
                            settled += child to TNode.PROVED
                        else -> {
                            val p = eval.win(child.a, child.d)
                            synchronized(lock) { tree.initOr(child, p) }
                        }
                    }
                    children += child
                }
                synchronized(lock) {
                    for ((child, state) in settled) {
                        if (state == TNode.PROVED) {
                            val size = run.covered.get(child.a, child.d)
                            tree.prove(child, if (size >= 0) size else 0)
                        } else {
                            tree.disprove(child)
                        }
                    }
                    tree.expand(leaf, children, null, null)
                }
            }
            run.stats.expansions.incrementAndGet()
        }
    }

    companion object {
        /** Size recorded for a proved leaf whose extraction failed (fixed by the assembly). */
        const val FAILED_SIZE = 1_000_000
        const val INITIAL_MASS = 0.8f
        const val INITIAL_MAX = 4
    }
}
