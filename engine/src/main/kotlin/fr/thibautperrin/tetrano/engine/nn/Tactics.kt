package fr.thibautperrin.tetrano.engine.nn

import fr.thibautperrin.tetrano.engine.nn.QubicGeometry.lineMasks
import fr.thibautperrin.tetrano.engine.nn.QubicGeometry.lowestCell
import fr.thibautperrin.tetrano.engine.nn.QubicGeometry.popCount

/**
 * Exact tactics on bitboards. `p` is the side being examined, `o` its
 * opponent. A "threat" is a line holding 3 of p's rings and none of o's:
 * its free cell wins on p's next move.
 */
object Tactics {

    /** Cells where [p] completes a line on its next move. */
    fun threats(p: Long, o: Long): Long {
        var cells = 0L
        for (line in lineMasks) {
            if (line and o == 0L && popCount(line and p) == 3) cells = cells or (line and p.inv())
        }
        return cells
    }

    /** Threat cells [p] gains by playing [cell] (only lines through [cell] can change). */
    fun newThreats(p: Long, o: Long, cell: Int): Long {
        val played = p or (1L shl cell)
        var cells = 0L
        for (i in 0 until QubicGeometry.cellDegree(cell)) {
            val line = lineMasks[QubicGeometry.cellLine(cell, i)]
            if (line and o == 0L && popCount(line and played) == 3) cells = cells or (line and played.inv())
        }
        return cells
    }

    /** Free cells that would give [p] a threat: on a line with 2 of p's rings and none of o's. */
    fun threatMoves(p: Long, o: Long): Long {
        var cells = 0L
        for (line in lineMasks) {
            if (line and o == 0L && popCount(line and p) == 2) cells = cells or (line and p.inv())
        }
        return cells
    }

    /** True iff [p] has 4 in a line. */
    fun hasWon(p: Long): Boolean {
        for (line in lineMasks) if (line and p == line) return true
        return false
    }

    /** True iff [p] completed a line by playing [cell]. */
    fun wonWith(p: Long, cell: Int): Boolean {
        for (i in 0 until QubicGeometry.cellDegree(cell)) {
            val line = lineMasks[QubicGeometry.cellLine(cell, i)]
            if (line and p == line) return true
        }
        return false
    }
}

/**
 * Victory by Continuous Forcing: can `p`, to move, win with a sequence of
 * threats — each forcing `o` to block on the single cell that stops it —
 * ending on a double threat? Sound (a found win is a real win) but not
 * complete. `o`'s forced blocks may create threats of its own, which `p`
 * must then block while keeping up the pressure. Ported from
 * `tools/qubic-solver/src/Vcf.java`.
 *
 * Results are cached in a table keyed on the exact (p, o) pair; a search cut
 * short by its node budget is never cached as a failure. Not thread-safe:
 * one instance per thread.
 */
class Vcf(log2TableSize: Int = 16) {
    private val keyP = LongArray(1 shl log2TableSize)
    private val keyO = LongArray(1 shl log2TableSize)

    /** 0 = empty slot, 1 = no VCF, 2 + cell = VCF starting with that cell. */
    private val value = ByteArray(1 shl log2TableSize)
    private val mask = (1 shl log2TableSize) - 1
    private var budget = 0

    /** Total nodes visited by this instance, for statistics. */
    var nodes: Long = 0L
        private set

    /**
     * The first move of a VCF win for [p] (to move) against [o], [NONE] if
     * there is none, or [UNKNOWN] if [maxNodes] ran out before an answer.
     * An immediate win counts as a VCF of length 1.
     */
    fun solve(p: Long, o: Long, maxNodes: Int): Int {
        budget = maxNodes
        return search(p, o)
    }

    private fun slot(p: Long, o: Long): Int {
        var h = p * -0x61c8864680b583ebL xor java.lang.Long.rotateLeft(o * -0x3d4d51c2d82b14b1L, 31)
        h = h xor (h ushr 29)
        return h.toInt() and mask
    }

    private fun search(p: Long, o: Long): Int {
        nodes++
        val win = Tactics.threats(p, o)
        if (win != 0L) return lowestCell(win)
        val toBlock = Tactics.threats(o, p)
        if (popCount(toBlock) >= 2) return NONE
        val h = slot(p, o)
        if (value[h].toInt() != 0 && keyP[h] == p && keyO[h] == o) {
            return if (value[h].toInt() == 1) NONE else value[h] - 2
        }
        if (--budget < 0) return UNKNOWN

        var result = NONE
        if (toBlock != 0L) {
            // Forced to block; the block must itself threaten to keep the initiative.
            val cell = lowestCell(toBlock)
            val created = Tactics.newThreats(p, o, cell)
            if (created != 0L) {
                if (popCount(created) >= 2) {
                    result = cell
                } else {
                    val r = search(p or (1L shl cell), o or created)
                    if (r == UNKNOWN) return UNKNOWN
                    if (r >= 0) result = cell
                }
            }
        } else {
            val candidates = Tactics.threatMoves(p, o)
            var rest = candidates
            while (rest != 0L) {
                val cell = lowestCell(rest)
                rest = rest and (rest - 1)
                if (popCount(Tactics.newThreats(p, o, cell)) >= 2) {
                    result = cell
                    break
                }
            }
            if (result == NONE) {
                var unknown = false
                rest = candidates
                while (rest != 0L) {
                    val cell = lowestCell(rest)
                    rest = rest and (rest - 1)
                    val created = Tactics.newThreats(p, o, cell)
                    val r = search(p or (1L shl cell), o or created)
                    if (r == UNKNOWN) {
                        unknown = true
                    } else if (r >= 0) {
                        result = cell
                        break
                    }
                }
                if (result == NONE && unknown) return UNKNOWN
            }
        }
        keyP[h] = p
        keyO[h] = o
        value[h] = (if (result == NONE) 1 else result + 2).toByte()
        return result
    }

    // ---- shortest VCF (iterative deepening) ----

    /** Number of moves of [p] in the last VCF found by [shortest] (the winning move included). */
    var shortestLength: Int = 0
        private set

    private val depthKeyP = LongArray(1 shl log2TableSize)
    private val depthKeyO = LongArray(1 shl log2TableSize)

    /** Deepest limit (in [p]'s moves) proven to hold no VCF for that (p, o); 0 = nothing known. */
    private val depthFailed = ByteArray(1 shl log2TableSize)

    /**
     * Like [solve], but the first move of a *shortest* VCF: the fewest moves of [p] until its
     * line is complete, by iterative deepening. [NONE] if there is no VCF, [UNKNOWN] if
     * [maxNodes] ran out before any VCF was found; if it runs out during the deepening, the
     * first VCF found is returned instead and [shortestLength] is 0. [maxNodes] bounds each of
     * the two searches.
     */
    fun shortest(p: Long, o: Long, maxNodes: Int): Int {
        val any = solve(p, o, maxNodes)
        if (any < 0) return any
        depthFailed.fill(0)
        budget = maxNodes // shared by all the iterations
        for (limit in 1..MAX_VCF_MOVES) {
            val r = searchWithin(p, o, limit)
            if (r == UNKNOWN) break
            if (r >= 0) {
                shortestLength = limit
                return r
            }
        }
        // Out of budget before the shortest was found: fall back on the VCF already known.
        shortestLength = 0
        return any
    }

    /** A first move winning within [limit] moves of [p] by continuous threats, [NONE] or [UNKNOWN]. */
    private fun searchWithin(p: Long, o: Long, limit: Int): Int {
        nodes++
        val win = Tactics.threats(p, o)
        if (win != 0L) return lowestCell(win)
        if (limit < 2) return NONE
        val toBlock = Tactics.threats(o, p)
        if (popCount(toBlock) >= 2) return NONE
        val h = slot(p, o)
        if (depthKeyP[h] == p && depthKeyO[h] == o && depthFailed[h] >= limit) return NONE
        if (--budget < 0) return UNKNOWN

        var unknown = false
        var candidates = if (toBlock != 0L) toBlock else Tactics.threatMoves(p, o)
        // A double threat wins in 2 moves: look for one before going deeper.
        var rest = candidates
        while (rest != 0L) {
            val cell = lowestCell(rest)
            rest = rest and (rest - 1)
            if (popCount(Tactics.newThreats(p, o, cell)) >= 2) return cell
        }
        if (limit > 2) {
            while (candidates != 0L) {
                val cell = lowestCell(candidates)
                candidates = candidates and (candidates - 1)
                val created = Tactics.newThreats(p, o, cell)
                if (created == 0L) continue
                val r = searchWithin(p or (1L shl cell), o or created, limit - 1)
                if (r == UNKNOWN) unknown = true else if (r >= 0) return cell
            }
        }
        if (unknown) return UNKNOWN
        depthKeyP[h] = p
        depthKeyO[h] = o
        depthFailed[h] = limit.toByte()
        return NONE
    }

    companion object {
        const val NONE: Int = -1
        const val UNKNOWN: Int = -2

        /** A VCF is at most 32 moves long (the board has 64 cells). */
        private const val MAX_VCF_MOVES = 32
    }
}
