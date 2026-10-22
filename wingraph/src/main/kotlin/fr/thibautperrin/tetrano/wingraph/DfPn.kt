package fr.thibautperrin.tetrano.wingraph

import fr.thibautperrin.tetrano.engine.nn.Tactics
import fr.thibautperrin.tetrano.engine.nn.Vcf
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Depth-first proof-number search (df-pn with the 1+epsilon trick), proving
 * that the attacker wins a canonical position; ported from
 * `tools/qubic-solver/src/Solver.java`. Exact: a proof or disproof is a real
 * one (attacker-to-move leaves are closed by a sound VCF, defender-to-move
 * ones by the defender's VCF), only the move ordering is heuristic. No
 * network: it is the fast worker closing the middle game under the top tree
 * ([TopTree]).
 *
 * The table keeps its entries across calls (4-way buckets, the cheapest
 * entry replaced). Not thread-safe: one instance per thread.
 */
class DfPn(log2Entries: Int = 22, private val vcfBudget: Int = 5_000, private val nullMovePn: Int = 10) {
    private val keyA = LongArray(1 shl log2Entries)
    private val keyD = LongArray(1 shl log2Entries)
    private val tpn = IntArray(1 shl log2Entries)
    private val tdn = IntArray(1 shl log2Entries)
    private val twork = IntArray(1 shl log2Entries)
    private val bucketMask = (1 shl (log2Entries - 2)) - 1
    private val vcf = Vcf(18)

    /** Per depth (rings on the board): children and their current numbers. */
    private val children = Array(65) { Children() }
    private val childPn = Array(65) { IntArray(64) }
    private val childDn = Array(65) { IntArray(64) }

    /** Positions evaluated since creation. */
    var evals: Long = 0L
        private set
    private var limit = 0L

    /** Set from another thread to make the running [solve] return [UNKNOWN] at once. */
    @Volatile
    var abort: Boolean = false

    /**
     * Searches canonical position (a, d) for at most [maxEvals] evaluations:
     * [PROVED] (the attacker wins), [DISPROVED] (it does not) or [UNKNOWN].
     */
    fun solve(a: Long, d: Long, maxEvals: Long): Int {
        limit = evals + maxEvals
        var e = find(a, d)
        if (e < 0) e = evaluate(a, d)
        if (tpn[e] != 0 && tdn[e] != 0) mid(a, d, INF, INF)
        return status(a, d)
    }

    /** What the table knows of (a, d): [PROVED], [DISPROVED] or [UNKNOWN]. */
    fun status(a: Long, d: Long): Int {
        val e = find(a, d)
        return when {
            e < 0 -> UNKNOWN
            tpn[e] == 0 -> PROVED
            tdn[e] == 0 -> DISPROVED
            else -> UNKNOWN
        }
    }

    /** The table's proof number of (a, d), 1 if unknown. */
    fun proofNumber(a: Long, d: Long): Int {
        val e = find(a, d)
        return if (e < 0) 1 else tpn[e]
    }

    /** Work spent on (a, d) by its last search (a proof size estimate), or -1 if unknown. */
    fun work(a: Long, d: Long): Int {
        val e = find(a, d)
        return if (e < 0) -1 else twork[e]
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
            // Solved entries are worth a bit more than unsolved ones with the same work.
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

    /** Static evaluation of a canonical position into the table; returns its entry. */
    private fun evaluate(a: Long, d: Long): Int {
        evals++
        var pn = 1
        var dn = 1
        if (Board.attackerToMove(a, d)) {
            if (Tactics.threats(a, d) != 0L) {
                pn = 0; dn = INF
            } else {
                val td = Tactics.threats(d, a)
                if (java.lang.Long.bitCount(td) >= 2 || (a or d) == Board.FULL) {
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
            } else if ((a or d) == Board.FULL) {
                pn = INF; dn = 0
            } else if (ta == 0L) {
                if (vcf.solve(d, a, vcfBudget) >= 0) {
                    // The defender wins by continuous threats.
                    pn = INF; dn = 0
                } else {
                    // Free choice for the defender. If the attacker has no VCF even when the
                    // defender passes, its last move threatened nothing: expect much to prove.
                    pn = if (nullMovePn > 0 && vcf.solve(a, d, vcfBudget) < 0) nullMovePn else 2
                }
            }
        }
        store(a, d, pn, dn, 0)
        return find(a, d)
    }

    private fun mid(a: Long, d: Long, thpn: Int, thdn: Int) {
        val startEvals = evals
        val or = Board.attackerToMove(a, d)
        val depth = java.lang.Long.bitCount(a or d)
        val kids = children[depth].generate(a, d, Board.candidateMoves(a, d))
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
            if (n == 0) { // full board: a draw
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

        const val UNKNOWN: Int = 0
        const val PROVED: Int = 1
        const val DISPROVED: Int = 2
    }
}
