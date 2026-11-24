package fr.thibautperrin.tetrano.engine.nn

import fr.thibautperrin.tetrano.engine.wingraph.CanonicalForm
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Exact value of a position for the player to move: [Outcome.WIN], [Outcome.DRAW] or
 * [Outcome.LOSS] (perfect play by both sides), or [Outcome.UNKNOWN] when the evaluation
 * budget runs out first. Two proof searches ([ProofSearch], df-pn without network): "does the
 * player to move force a win?", then "does the opponent?"; two disproofs make a proven draw.
 *
 * The budget counts positions evaluated, never time: on a given instance state the answer is
 * the same on any device. Each search keeps its table across calls, so solving the positions
 * of one game from the last to the first reuses the earlier proofs. Not thread-safe: one
 * instance per thread.
 *
 * @param log2Entries table size of each of the two searches (2^20 entries ≈ 29 MB each).
 */
class ExactSolver(log2Entries: Int = 20, vcfBudget: Int = 5_000) {

    enum class Outcome { WIN, DRAW, LOSS, UNKNOWN }

    /**
     * [outcome] for the player to move; [move]: a cell keeping that outcome (a winning move,
     * a move holding the draw), -1 when lost or unknown or not found within the budget.
     */
    class Answer(val outcome: Outcome, val move: Int, val evals: Long)

    /** Proves wins of the first player (attacker = first player). */
    private val first = ProofSearch(attackerFirst = true, log2Entries, vcfBudget)

    /** Proves wins of the second player. */
    private val second = ProofSearch(attackerFirst = false, log2Entries, vcfBudget)
    private val form = CanonicalForm()

    /** Positions evaluated since creation, by both searches. */
    val evals: Long get() = first.evals + second.evals

    /** Set from another thread to make the running [solve] return [Outcome.UNKNOWN] soon. */
    var abort: Boolean
        get() = first.abort || second.abort
        set(value) {
            first.abort = value
            second.abort = value
        }

    /**
     * Solves the position where the player to move owns [mine] and the opponent [theirs] (no
     * line completed yet, board not full) within about [maxEvals] evaluations. With
     * [opponentFirst], asks first whether the opponent wins (when that is the likelier answer).
     * With [findMove] false, [Answer.move] is always -1 (cheaper).
     */
    fun solve(mine: Long, theirs: Long, maxEvals: Long, opponentFirst: Boolean = false, findMove: Boolean = true): Answer {
        val start = evals
        val immediate = Tactics.threats(mine, theirs)
        if (immediate != 0L) return Answer(Outcome.WIN, QubicGeometry.lowestCell(immediate), 0)
        val stmFirst = QubicGeometry.popCount(mine) == QubicGeometry.popCount(theirs)
        var win = ProofSearch.UNKNOWN // "the player to move wins"
        var loss = ProofSearch.UNKNOWN // "the opponent wins"
        fun askWin(budget: Long) {
            if (budget > 0 && win == ProofSearch.UNKNOWN) win = proveWin(mine, theirs, stmFirst, budget)
        }
        fun askLoss(budget: Long) {
            if (budget > 0 && loss == ProofSearch.UNKNOWN) loss = proveWin(theirs, mine, !stmFirst, budget)
        }
        fun left() = maxEvals - (evals - start)
        if (opponentFirst) {
            askLoss(maxEvals / 2)
            if (loss != ProofSearch.PROVED) askWin(left())
            if (win == ProofSearch.DISPROVED) askLoss(left())
        } else {
            askWin(maxEvals / 2)
            if (win != ProofSearch.PROVED) askLoss(left())
            if (loss == ProofSearch.DISPROVED) askWin(left())
        }
        val outcome = when {
            win == ProofSearch.PROVED -> Outcome.WIN
            loss == ProofSearch.PROVED -> Outcome.LOSS
            win == ProofSearch.DISPROVED && loss == ProofSearch.DISPROVED -> Outcome.DRAW
            else -> Outcome.UNKNOWN
        }
        val move = if (!findMove) -1 else when (outcome) {
            Outcome.WIN -> winningMove(mine, theirs, stmFirst, maxEvals)
            Outcome.DRAW -> holdingMove(mine, theirs, stmFirst, maxEvals)
            else -> -1
        }
        return Answer(outcome, move, evals - start)
    }

    /** Whether [attacker] (to move iff [attackerFirst] says so by the ring counts) forces a win. */
    private fun proveWin(attacker: Long, defender: Long, attackerFirst: Boolean, budget: Long): Int {
        form.of(attacker, defender)
        return search(attackerFirst).prove(form.attacker, form.defender, budget)
    }

    private fun search(attackerFirst: Boolean) = if (attackerFirst) first else second

    /** The table's verdict on "[attacker] wins" in this position, without searching. */
    private fun known(attacker: Long, defender: Long, attackerFirst: Boolean): Int {
        form.of(attacker, defender)
        return search(attackerFirst).status(form.attacker, form.defender)
    }

    /** The legal moves worth playing for the side to move [p] (no immediate win): a forced block, else every free cell. */
    private fun candidates(p: Long, o: Long): Long {
        val block = Tactics.threats(o, p)
        return if (block != 0L) block else (p or o).inv()
    }

    /** A move after which the opponent is proven lost; the side to move is proven to win. */
    private fun winningMove(mine: Long, theirs: Long, stmFirst: Boolean, maxEvals: Long): Int {
        val vcfMove = vcf.solve(mine, theirs, 20_000)
        if (vcfMove >= 0) return vcfMove
        val moves = candidates(mine, theirs)
        // After our move, "we win" is asked with us as the attacker, the opponent to move.
        forEach(moves) { c -> if (known(mine or (1L shl c), theirs, stmFirst) == ProofSearch.PROVED) return c }
        val budget = (maxEvals / 8).coerceIn(1_000, 100_000)
        forEach(moves) { c ->
            val after = mine or (1L shl c)
            form.of(after, theirs)
            if (search(stmFirst).prove(form.attacker, form.defender, budget) == ProofSearch.PROVED) return c
        }
        return -1
    }

    /** A move after which the opponent is proven not to win (the position is a proven draw). */
    private fun holdingMove(mine: Long, theirs: Long, stmFirst: Boolean, maxEvals: Long): Int {
        val moves = candidates(mine, theirs)
        // After our move, "the opponent wins" has the opponent as the attacker, to move.
        forEach(moves) { c -> if (known(theirs, mine or (1L shl c), !stmFirst) == ProofSearch.DISPROVED) return c }
        val budget = (maxEvals / 8).coerceIn(1_000, 100_000)
        forEach(moves) { c ->
            val after = mine or (1L shl c)
            if (Tactics.wonWith(after, c)) return c
            form.of(theirs, after)
            if (search(!stmFirst).prove(form.attacker, form.defender, budget) == ProofSearch.DISPROVED) return c
        }
        return -1
    }

    private val vcf = Vcf(16)

    private inline fun forEach(bits: Long, action: (Int) -> Unit) {
        var rest = bits
        while (rest != 0L) {
            action(QubicGeometry.lowestCell(rest))
            rest = rest and (rest - 1)
        }
    }
}

/**
 * Depth-first proof-number search (df-pn with the 1+epsilon trick): does the attacker force a
 * win? The attacker is the first player if [attackerFirst], else the second; positions are
 * canonical ([CanonicalForm]) (attacker, defender) bitboards. Exact: a proof or disproof is a
 * real one (leaves closed by immediate wins, double threats and sound VCFs of either side);
 * only the move ordering is heuristic. Generalizes `:wingraph`'s `DfPn` (first player only),
 * which it matches move for move when [attackerFirst].
 *
 * The table keeps its entries across calls (4-way buckets, the cheapest entry replaced). Not
 * thread-safe.
 */
class ProofSearch(
    private val attackerFirst: Boolean,
    log2Entries: Int = 22,
    private val vcfBudget: Int = 5_000,
    private val nullMovePn: Int = 10,
) {
    private val keyA = LongArray(1 shl log2Entries)
    private val keyD = LongArray(1 shl log2Entries)
    private val tpn = IntArray(1 shl log2Entries)
    private val tdn = IntArray(1 shl log2Entries)
    private val twork = IntArray(1 shl log2Entries)
    private val bucketMask = (1 shl (log2Entries - 2)) - 1
    private val vcf = Vcf(18)
    private val children = Array(66) { CanonicalChildren() }
    private val childPn = Array(66) { IntArray(64) }
    private val childDn = Array(66) { IntArray(64) }

    var evals: Long = 0L
        private set
    private var limit = 0L

    @Volatile
    var abort: Boolean = false

    private fun attackerToMove(a: Long, d: Long): Boolean =
        java.lang.Long.bitCount(a) + (if (attackerFirst) 0 else 1) == java.lang.Long.bitCount(d)

    /** Searches canonical position (a, d) for at most [maxEvals] evaluations: [PROVED], [DISPROVED] or [UNKNOWN]. */
    fun prove(a: Long, d: Long, maxEvals: Long): Int {
        limit = evals + maxEvals
        var e = find(a, d)
        if (e < 0) e = evaluate(a, d)
        if (tpn[e] != 0 && tdn[e] != 0) mid(a, d, INF, INF)
        return status(a, d)
    }

    /** What the table knows of canonical (a, d). */
    fun status(a: Long, d: Long): Int {
        val e = find(a, d)
        return when {
            e < 0 -> UNKNOWN
            tpn[e] == 0 -> PROVED
            tdn[e] == 0 -> DISPROVED
            else -> UNKNOWN
        }
    }

    private fun bucket(a: Long, d: Long): Int {
        var h = a * -0x61c8864680b583ebL xor java.lang.Long.rotateLeft(d * -0x3d4d51c2d82b14b1L, 27)
        h = h xor (h ushr 31)
        return (h.toInt() and bucketMask) shl 2
    }

    private fun find(a: Long, d: Long): Int {
        val b = bucket(a, d)
        for (i in b until b + 4) {
            if (keyA[i] == a && keyD[i] == d && (tpn[i] or tdn[i]) != 0) return i
        }
        return -1
    }

    private fun store(a: Long, d: Long, pn: Int, dn: Int, work: Int) {
        val b = bucket(a, d)
        var victim = -1
        var victimWork = Int.MAX_VALUE
        for (i in b until b + 4) {
            if ((tpn[i] or tdn[i]) == 0 || (keyA[i] == a && keyD[i] == d)) {
                victim = i
                break
            }
            val w = twork[i] + if (tpn[i] == 0 || tdn[i] == 0) 2 else 0
            if (w < victimWork) {
                victimWork = w
                victim = i
            }
        }
        keyA[victim] = a
        keyD[victim] = d
        tpn[victim] = pn
        tdn[victim] = dn
        twork[victim] = work
    }

    private fun evaluate(a: Long, d: Long): Int {
        evals++
        var pn = 1
        var dn = 1
        if (attackerToMove(a, d)) {
            if (Tactics.threats(a, d) != 0L) {
                pn = 0; dn = INF
            } else {
                val td = Tactics.threats(d, a)
                if (java.lang.Long.bitCount(td) >= 2 || (a or d) == FULL) {
                    pn = INF; dn = 0
                } else if (vcf.solve(a, d, vcfBudget) >= 0) {
                    pn = 0; dn = INF
                }
            }
        } else {
            val ta = Tactics.threats(a, d)
            if (Tactics.threats(d, a) != 0L) {
                pn = INF; dn = 0
            } else if (java.lang.Long.bitCount(ta) >= 2) {
                pn = 0; dn = INF
            } else if ((a or d) == FULL) {
                pn = INF; dn = 0
            } else if (ta == 0L) {
                if (vcf.solve(d, a, vcfBudget) >= 0) {
                    pn = INF; dn = 0
                } else {
                    pn = if (nullMovePn > 0 && vcf.solve(a, d, vcfBudget) < 0) nullMovePn else 2
                }
            }
        }
        store(a, d, pn, dn, 0)
        return find(a, d)
    }

    private fun candidateMoves(a: Long, d: Long): Long =
        if (attackerToMove(a, d)) {
            val td = Tactics.threats(d, a)
            if (td != 0L) td else (a or d).inv()
        } else {
            val ta = Tactics.threats(a, d)
            if (ta != 0L) ta else (a or d).inv()
        }

    private fun mid(a: Long, d: Long, thpn: Int, thdn: Int) {
        val startEvals = evals
        val or = attackerToMove(a, d)
        val depth = java.lang.Long.bitCount(a or d)
        val kids = children[depth].generate(a, d, or, candidateMoves(a, d))
        val n = kids.count
        val cpn = childPn[depth]
        val cdn = childDn[depth]
        var pn: Int
        var dn: Int
        while (true) {
            for (i in 0 until n) {
                var e = find(kids.a[i], kids.d[i])
                if (e < 0) e = evaluate(kids.a[i], kids.d[i])
                cpn[i] = tpn[e]
                cdn[i] = tdn[e]
            }
            var best = -1
            var second = INF
            if (or) {
                var sumDn = 0L
                var minPn = INF
                for (i in 0 until n) {
                    sumDn += cdn[i]
                    if (cpn[i] < minPn) {
                        second = minPn
                        minPn = cpn[i]
                        best = i
                    } else if (cpn[i] < second) {
                        second = cpn[i]
                    }
                }
                pn = minPn
                dn = cap(sumDn)
            } else {
                var sumPn = 0L
                var minDn = INF
                for (i in 0 until n) {
                    sumPn += cpn[i]
                    if (cdn[i] < minDn) {
                        second = minDn
                        minDn = cdn[i]
                        best = i
                    } else if (cdn[i] < second) {
                        second = cdn[i]
                    }
                }
                pn = cap(sumPn)
                dn = minDn
            }
            if (n == 0) {
                pn = INF
                dn = 0
            }
            if (pn >= thpn || dn >= thdn || evals >= limit || abort) break
            val childThPn: Int
            val childThDn: Int
            if (or) {
                childThPn = min(thpn, max(second + 1, ceil(second * (1 + EPS)).toInt()))
                childThDn = cap(thdn.toLong() - dn + cdn[best])
            } else {
                childThDn = min(thdn, max(second + 1, ceil(second * (1 + EPS)).toInt()))
                childThPn = cap(thpn.toLong() - pn + cpn[best])
            }
            mid(kids.a[best], kids.d[best], childThPn, childThDn)
        }
        store(a, d, pn, dn, min(evals - startEvals + 1, Int.MAX_VALUE.toLong()).toInt())
    }

    private fun cap(v: Long): Int = if (v >= INF) INF else v.toInt()

    companion object {
        const val INF: Int = 100_000_000
        private const val EPS = 0.25
        private const val FULL: Long = -1L

        const val UNKNOWN: Int = 0
        const val PROVED: Int = 1
        const val DISPROVED: Int = 2
    }
}

/** The distinct canonical children of a canonical (attacker, defender) position for a set of moves. Not thread-safe. */
internal class CanonicalChildren {
    val a = LongArray(64)
    val d = LongArray(64)
    var count = 0
        private set
    private val form = CanonicalForm()

    fun generate(parentA: Long, parentD: Long, attackerMoves: Boolean, moves: Long): CanonicalChildren {
        count = 0
        var rest = moves
        while (rest != 0L) {
            val bit = rest and -rest
            rest = rest xor bit
            form.of(if (attackerMoves) parentA or bit else parentA, if (attackerMoves) parentD else parentD or bit)
            val ca = form.attacker
            val cd = form.defender
            var dup = false
            for (i in 0 until count) {
                if (a[i] == ca && d[i] == cd) {
                    dup = true
                    break
                }
            }
            if (!dup) {
                a[count] = ca
                d[count] = cd
                count++
            }
        }
        return this
    }
}
