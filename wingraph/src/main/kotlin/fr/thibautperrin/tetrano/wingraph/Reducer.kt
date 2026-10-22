package fr.thibautperrin.tetrano.wingraph

import fr.thibautperrin.tetrano.engine.nn.QubicNet
import fr.thibautperrin.tetrano.engine.wingraph.WinRules
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Phase 3: fewer graph nodes, by local improvement. A pass takes the nodes
 * of the best graph from the largest subtree to the smallest; at each one it
 * tries the network's other good moves: a trial walks the strategy with that
 * move (the extractor filling gaps into a scratch book) and stops as soon as
 * it meets as many nodes as the current move needs. A strictly smaller,
 * complete trial replaces the move (and its new nodes join the book).
 *
 * Every trial that is kept is a complete proof, so the book stays made of
 * winning moves only; the pass ends with an assembly, and the graph is kept
 * if it is smaller than the best one. Passes repeat until one improves
 * nothing. Resumable: the pass number and the nodes already tried are saved.
 */
class Reducer(private val run: Run, private val net: QubicNet) {
    @Volatile
    private var stopping = false
    private val dfpns = ArrayList<DfPn>()
    private var assembler: Assembler? = null

    fun requestStop() {
        stopping = true
        synchronized(dfpns) { dfpns.forEach { it.abort = true } }
        assembler?.requestStop()
    }

    /** True when a pass improved nothing (the phase is over); false when stopped. */
    fun reduce(): Boolean {
        while (!stopping) {
            val pass = run.stateLong("reduce_pass").toInt() + 1
            val nodes = orderedNodes()
            val skip = run.stateLong("reduce_done").toInt()
            run.log("réduction : passe $pass, ${nodes.size} nœuds à essayer (${skip} déjà faits)")
            val queue = ConcurrentLinkedQueue(nodes.drop(skip))
            val done = AtomicInteger(skip)
            val improvedBefore = run.stateLong("reduce_improved_in_pass")
            val improved = AtomicInteger(0)
            val threads = (0 until run.config.threads).map { id ->
                Thread({ work(queue, done, improved) }, "reducer-$id").also { it.priority = Thread.MIN_PRIORITY; it.start() }
            }
            threads.forEach { it.join() }
            run.setState("reduce_done", done.get())
            run.setState("reduce_improved_in_pass", improvedBefore + improved.get())
            if (stopping) return false
            val candidate = File(run.dir, "graph-candidate.bin")
            val asm = Assembler(run, net).also { assembler = it }
            val size = asm.assemble(candidate)
            assembler = null
            if (size < 0) return false
            val best = run.stateLong("graph_best_size")
            if (size < best) {
                candidate.copyTo(File(run.dir, "graph-best.bin"), overwrite = true)
                run.setState("graph_best_size", size)
                run.log("réduction : passe $pass, graphe ramené de $best à $size nœuds")
            } else {
                run.log("réduction : passe $pass, graphe de $size nœuds (meilleur : $best)")
            }
            candidate.delete()
            val anyImprovement = improvedBefore + improved.get() > 0
            run.setState("reduce_pass", pass)
            run.setState("reduce_done", 0)
            run.setState("reduce_improved_in_pass", 0)
            run.checkpoint()
            if (!anyImprovement) return true
        }
        return false
    }

    /** The nodes of the current strategy (book walked from the empty board), largest subtree first. */
    private fun orderedNodes(): List<LongArray> {
        // The walk records each book node's subtree size in its "covered" map.
        val sizeMap = PositionMap(16)
        run.config.start.walkAll(Walker(run.bookView, { _, _, m -> m }, sizeMap, null))
        val nodes = ArrayList<LongArray>()
        sizeMap.forEach { a, d, size -> if (size >= 2) nodes += longArrayOf(a, d, size.toLong()) }
        nodes.sortWith(compareByDescending<LongArray> { it[2] }.thenBy { it[0] }.thenBy { it[1] })
        return nodes
    }

    private fun work(queue: ConcurrentLinkedQueue<LongArray>, done: AtomicInteger, improved: AtomicInteger) {
        val dfpn = DfPn(run.config.ttLog2).also { synchronized(dfpns) { dfpns += it } }
        val eval = NetEval(net)
        val kids = Children()
        val policy = FloatArray(64)
        while (!stopping) {
            val node = queue.poll() ?: return
            val a = node[0]
            val d = node[1]
            val current = run.book.get(a, d)
            if (current < 0) {
                done.incrementAndGet()
                continue
            }
            // The current move's size, measured now (other threads may have shrunk it).
            val measure = Walker(run.bookView, { _, _, m -> m }, null, null)
            val s0 = measure.walk(a, d).let { if (it < 0) Int.MAX_VALUE else measure.bookNodesMet }
            if (s0 <= 1) {
                done.incrementAndGet()
                continue
            }
            val moves = Board.candidateMoves(a, d)
            val k = kids.generate(a, d, moves)
            eval.policy(a, d, moves, policy)
            val currentClass = (0 until k.count).firstOrNull { (k.moveClass[it] ushr current) and 1L != 0L } ?: -1
            val order = (0 until k.count).filter { it != currentClass }.sortedByDescending { i ->
                var p = 0f
                Board.cells(k.moveClass[i]) { p += policy[it] }
                p
            }.take(SmallGraphChooser.NET_CANDIDATES)
            var bestSize = s0
            var bestMove = -1
            var bestScratch: PositionMap? = null
            for (i in order) {
                if (stopping) break
                val move = k.move[i]
                val scratch = PositionMap(10)
                val view = WinRules.Book { x, y ->
                    if (x == a && y == d) move else scratch.get(x, y).let { if (it >= 0) it else run.book.get(x, y) }
                }
                val chooser = SmallGraphChooser(dfpn, eval, view, run.config.jobEvals, run.config.reduceEvals) { stopping }
                val trial = Walker(view, { x, y, m -> scratch.putIfAbsent(x, y, m) }, null, chooser)
                trial.sizeLimit = bestSize
                run.stats.reduceTried.incrementAndGet()
                if (trial.walk(a, d) >= 0 && trial.bookNodesMet < bestSize) {
                    bestSize = trial.bookNodesMet
                    bestMove = move
                    bestScratch = scratch
                }
            }
            if (stopping) return
            if (bestMove >= 0) {
                bestScratch!!.forEach { x, y, m -> run.book.putIfAbsent(x, y, m) }
                run.book.put(a, d, bestMove)
                improved.incrementAndGet()
                run.stats.reduceImproved.incrementAndGet()
            }
            done.incrementAndGet()
        }
    }
}
