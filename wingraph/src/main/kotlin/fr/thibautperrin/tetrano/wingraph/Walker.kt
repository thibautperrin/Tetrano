package fr.thibautperrin.tetrano.wingraph

import fr.thibautperrin.tetrano.engine.nn.QubicNet
import fr.thibautperrin.tetrano.engine.nn.Tactics
import fr.thibautperrin.tetrano.engine.wingraph.WinRules
import kotlin.math.exp

/** The network seen from the side to move of a canonical position; one per thread. */
class NetEval(private val net: QubicNet) {
    private val workspace = net.newWorkspace()
    private val logits = FloatArray(64)
    private val wdl = FloatArray(3)

    /** Win probability of the side to move. */
    fun win(a: Long, d: Long): Float {
        run(a, d)
        return wdl[0]
    }

    /** Fills [out] with the policy over [moves] (softmax, 0 elsewhere); returns the side to move's win probability. */
    fun policy(a: Long, d: Long, moves: Long, out: FloatArray): Float {
        run(a, d)
        var maxLogit = Float.NEGATIVE_INFINITY
        Board.cells(moves) { if (logits[it] > maxLogit) maxLogit = logits[it] }
        var sum = 0f
        out.fill(0f)
        Board.cells(moves) {
            out[it] = exp(logits[it] - maxLogit)
            sum += out[it]
        }
        if (sum > 0f) for (i in 0 until 64) out[i] /= sum
        return wdl[0]
    }

    private fun run(a: Long, d: Long) {
        if (Board.attackerToMove(a, d)) net.evaluate(a, d, workspace, logits, wdl)
        else net.evaluate(d, a, workspace, logits, wdl)
    }
}

/**
 * Walks the strategy "[WinRules] + book" from canonical attacker-to-move
 * positions against **every** defender reply (up to symmetry), to the end of
 * each game. It is at once:
 * - the verifier: a walk succeeds iff the strategy wins everywhere below;
 * - the extractor: where no rule applies, [chooser] (if any) picks a proven
 *   move, which is added to the book through [record].
 *
 * Every book entry must be a move proven to win (invariant kept by every
 * writer of the book), so a walk with a [chooser] only fails when the
 * chooser runs out of budget.
 *
 * Sizes are numbers of book nodes (positions decided by the book): exact and
 * distinct within one walk ([visited] is per walk), approximate across
 * [covered] subtrees (sizes recorded by earlier walks, shared nodes counted
 * once per subtree).
 */
class Walker(
    private val book: WinRules.Book,
    /** Stores a chosen move; returns the move now in the book (another walk may have been first). */
    private val record: (a: Long, d: Long, move: Int) -> Int,
    /** Book positions whose whole subtree an earlier walk completed (value: its size). */
    private val covered: PositionMap?,
    private val chooser: MoveChooser?,
) {
    fun interface MoveChooser {
        /** A proven winning move of canonical (a, d), or -1 (out of budget, aborted). */
        fun choose(a: Long, d: Long): Int
    }

    private val visited = PositionMap(16)
    private val vcf = WinRules.newVcf()
    private val children = Array(65) { Children() }

    /** When > 0, a walk fails as soon as it has met this many book nodes. */
    var sizeLimit: Int = 0

    /** Called for each book node met for the first time in the walk. */
    var onBookNode: ((a: Long, d: Long, move: Int) -> Unit)? = null

    /** Why the last walk failed, or null. */
    var failure: String? = null
        private set

    var positions: Long = 0L
        private set
    var maxRings: Int = 0
        private set
    private var bookNodes = 0

    /** Forgets the previous walks (not the book). */
    fun reset() {
        visited.clear()
        positions = 0
        maxRings = 0
        bookNodes = 0
        failure = null
    }

    /** Book nodes met since [reset]. */
    val bookNodesMet: Int get() = bookNodes

    /**
     * Walks canonical attacker-to-move (a, d); returns the size of its
     * subtree (book nodes, 0 if the rules alone win), or -1 on failure
     * ([failure] says why).
     */
    fun walk(a: Long, d: Long): Int {
        val seen = visited.get(a, d)
        if (seen >= 0) return 0
        if (covered != null) {
            val size = covered.get(a, d)
            if (size >= 0) {
                visited.put(a, d, size)
                return size
            }
        }
        positions++
        val decision = WinRules.decide(a, d, book, vcf)
        val m: Int
        val isBookNode: Boolean
        if (decision == WinRules.NONE) {
            val chosen = chooser?.choose(a, d) ?: -1
            if (chosen < 0) return fail("no move", a, d)
            m = record(a, d, chosen)
            isBookNode = true
        } else {
            m = WinRules.move(decision)
            isBookNode = WinRules.rule(decision) == WinRules.BOOK
        }
        if (isBookNode) {
            bookNodes++
            onBookNode?.invoke(a, d, m)
            if (sizeLimit in 1..bookNodes) return fail("size limit", a, d)
        }
        val na = a or (1L shl m)
        var size = if (isBookNode) 1 else 0
        if (!Tactics.wonWith(na, m)) {
            if (Tactics.threats(d, na) != 0L) return fail("defender wins", na, d)
            val ta = Tactics.threats(na, d)
            if (java.lang.Long.bitCount(ta) < 2) {
                val replies = if (ta != 0L) ta else (na or d).inv()
                if (replies == 0L) return fail("draw", na, d)
                val rings = java.lang.Long.bitCount(na or d) + 1
                if (rings > maxRings) maxRings = rings
                val kids = children[rings - 1].generate(na, d, replies)
                for (i in 0 until kids.count) {
                    val s = walk(kids.a[i], kids.d[i])
                    if (s < 0) return -1
                    size += s
                }
            }
        }
        visited.put(a, d, size)
        if (isBookNode) covered?.put(a, d, size)
        return size
    }

    private fun fail(reason: String, a: Long, d: Long): Int {
        if (failure == null) failure = "$reason at ${hex(a)}/${hex(d)}"
        return -1
    }
}

internal fun hex(bits: Long): String = java.lang.Long.toHexString(bits)

/**
 * The extractor's choice of a book move where no rule applies, favouring
 * small graphs: first a move after which every defender reply is already
 * won by the rules or the book (no new node), else the df-pn-proven move
 * with the fewest replies left to settle, then the least proof work.
 * Candidates: the moves df-pn proved, then the network's best moves.
 */
class SmallGraphChooser(
    private val dfpn: DfPn,
    private val net: NetEval?,
    private val book: WinRules.Book,
    private val jobEvals: Long,
    private val maxEvals: Long,
    private val stop: () -> Boolean,
) : Walker.MoveChooser {
    private val moves = Children()
    private val replies = Children()
    private val policy = FloatArray(64)
    private val vcf = WinRules.newVcf()

    var solves: Long = 0L
        private set

    override fun choose(a: Long, d: Long): Int {
        var budget = jobEvals
        while (!stop()) {
            val r = pick(a, d)
            if (r >= 0) return r
            if (budget > maxEvals) return -1
            solves++
            val status = dfpn.solve(a, d, budget)
            if (status == DfPn.DISPROVED) {
                throw IllegalStateException("extraction reached a position the attacker does not win: ${hex(a)}/${hex(d)}")
            }
            if (status == DfPn.PROVED && pick(a, d) < 0) proveSomeChild(a, d, budget)
            budget *= 4
        }
        return -1
    }

    /**
     * The table knows (a, d) is won but lost the winning child (replaced since): searching (a, d)
     * again would stop at once, so its children are searched one by one, most promising first.
     */
    private fun proveSomeChild(a: Long, d: Long, budget: Long) {
        val kids = moves.generate(a, d, Board.candidateMoves(a, d))
        val ca = kids.a.copyOf(kids.count)
        val cd = kids.d.copyOf(kids.count)
        for (i in ca.indices.sortedBy { dfpn.proofNumber(ca[it], cd[it]) }) {
            if (stop()) return
            if (dfpn.solve(ca[i], cd[i], budget) == DfPn.PROVED) return
        }
    }

    private fun pick(a: Long, d: Long): Int {
        val kids = moves.generate(a, d, Board.candidateMoves(a, d))
        // Candidates: proven by df-pn (ordered by proof work), then up to NET_CANDIDATES by policy.
        val order = IntArray(kids.count)
        val key = DoubleArray(kids.count)
        var n = 0
        for (i in 0 until kids.count) {
            if (dfpn.status(kids.a[i], kids.d[i]) == DfPn.PROVED) {
                order[n] = i
                key[n] = dfpn.work(kids.a[i], kids.d[i]).toDouble()
                n++
            }
        }
        val provedCount = n
        if (net != null) {
            net.policy(a, d, Board.candidateMoves(a, d), policy)
            val prior = DoubleArray(kids.count) { i ->
                var p = 0.0
                Board.cells(kids.moveClass[i]) { p += policy[it] }
                p
            }
            val rest = (0 until kids.count).filter { i -> (0 until provedCount).none { order[it] == i } }
                .sortedByDescending { prior[it] }.take(NET_CANDIDATES)
            for (i in rest) {
                order[n] = i
                key[n] = Double.MAX_VALUE
                n++
            }
        }
        val sorted = (0 until n).sortedBy { key[it] }
        var best = -1
        var bestCost = Int.MAX_VALUE
        for (j in sorted) {
            val i = order[j]
            val proved = j < provedCount
            val cost = openReplies(kids.a[i], kids.d[i], if (proved) bestCost else 1)
            if (cost == 0) return kids.move[i]
            if (proved && cost < bestCost) {
                bestCost = cost
                best = kids.move[i]
            }
        }
        return best
    }

    /**
     * Defender replies of canonical defender-to-move (a, d) not already won
     * by the rules or the book, counted up to [limit] (then [limit] is
     * returned); 0 means the move is proven without any new node.
     */
    private fun openReplies(a: Long, d: Long, limit: Int): Int {
        if (Tactics.threats(d, a) != 0L) return limit
        val ta = Tactics.threats(a, d)
        if (java.lang.Long.bitCount(ta) >= 2) return 0
        val free = (a or d).inv()
        if (free == 0L) return limit
        val kids = replies.generate(a, d, if (ta != 0L) ta else free)
        var open = 0
        for (i in 0 until kids.count) {
            val ca = kids.a[i]
            val cd = kids.d[i]
            val won = Tactics.threats(ca, cd) != 0L ||
                (Tactics.threats(cd, ca) == 0L && (book.move(ca, cd) >= 0 || WinRules.vcfMove(ca, cd, vcf) >= 0))
            if (!won && ++open >= limit) return limit
        }
        return open
    }

    companion object {
        const val NET_CANDIDATES = 6
    }
}
