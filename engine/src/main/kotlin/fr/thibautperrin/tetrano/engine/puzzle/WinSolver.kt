package fr.thibautperrin.tetrano.engine.puzzle

import fr.thibautperrin.tetrano.engine.nn.QubicGeometry.lowestCell
import fr.thibautperrin.tetrano.engine.nn.QubicGeometry.popCount
import fr.thibautperrin.tetrano.engine.nn.Tactics

/**
 * Exact "win within n moves" solver, for puzzles: can `p`, to move, complete
 * a line within `n` of its own moves whatever `o` does? Unlike [fr.thibautperrin.tetrano.engine.nn.Vcf],
 * it is complete: quiet moves (creating no threat) are tried too, the
 * opponent then answering anywhere.
 *
 * Quiet moves are pruned with the monotonicity of positional games (an
 * extra ring never hurts its owner, so never helps the opponent): after a
 * quiet move, `p` must be able to win within `n - 1` moves even if `o`
 * passed, otherwise no reply of `o` can be worse for `o` than passing.
 *
 * [maxNodes] bounds each top-level call ([winIn], [winningMoves],
 * [minimalWin]): past it, [OutOfBudget] is thrown. Results are memoized
 * until [clear]. Not thread-safe.
 */
class WinSolver(private val maxNodes: Long = 2_000_000L) {

    class OutOfBudget : RuntimeException("puzzle solver budget exceeded")

    private data class Key(val p: Long, val o: Long, val n: Int)

    private val memo = HashMap<Key, Boolean>()
    private var budget = 0L

    /** Nodes visited since the last [clear]. */
    var nodes: Long = 0L
        private set

    fun clear() {
        memo.clear()
        nodes = 0
    }

    /** True iff [p], to move, can force a completed line within [n] of its moves. */
    fun winIn(p: Long, o: Long, n: Int): Boolean {
        budget = maxNodes
        return search(p, o, n)
    }

    /** The smallest n in 1..[maxN] such that [winIn] holds, or 0 if none. */
    fun minimalWin(p: Long, o: Long, maxN: Int): Int {
        budget = maxNodes
        for (n in 1..maxN) if (search(p, o, n)) return n
        return 0
    }

    /** The moves of [p] (to move) that keep a win within [n] moves (this move included). */
    fun winningMoves(p: Long, o: Long, n: Int): Long {
        budget = maxNodes
        if (n <= 0) return 0L
        val immediate = Tactics.threats(p, o)
        if (n == 1) return immediate
        val toBlock = Tactics.threats(o, p)
        if (popCount(toBlock) >= 2) return immediate
        var candidates = if (toBlock != 0L) toBlock or immediate else (p or o).inv()
        var result = 0L
        while (candidates != 0L) {
            val cell = lowestCell(candidates)
            candidates = candidates and (candidates - 1)
            if (winsAfter(p or (1L shl cell), o, cell, n)) result = result or (1L shl cell)
        }
        return result
    }

    /**
     * After [p] played [cell] (with [n] moves allowed counting that one), the replies of [o]
     * that can matter: the forced block when [p] threatens once, else every free cell. Empty
     * when the move already won.
     */
    fun replies(p: Long, o: Long, cell: Int): Long {
        if (Tactics.wonWith(p, cell)) return 0L
        val threats = Tactics.threats(p, o)
        return if (popCount(threats) == 1) threats else (p or o).inv()
    }

    private fun search(p: Long, o: Long, n: Int): Boolean {
        if (n <= 0) return false
        nodes++
        if (Tactics.threats(p, o) != 0L) return true
        if (n == 1) return false
        val toBlock = Tactics.threats(o, p)
        if (popCount(toBlock) >= 2) return false
        val key = Key(p, o, n)
        memo[key]?.let { return it }
        if (--budget < 0) throw OutOfBudget()
        var candidates = if (toBlock != 0L) toBlock else (p or o).inv()
        var result = false
        while (candidates != 0L) {
            val cell = lowestCell(candidates)
            candidates = candidates and (candidates - 1)
            if (winsAfter(p or (1L shl cell), o, cell, n)) {
                result = true
                break
            }
        }
        memo[key] = result
        return result
    }

    /** [p] just played [cell]; [o] to move. Does [p] complete a line within [n] moves (the one played included)? */
    private fun winsAfter(p: Long, o: Long, cell: Int, n: Int): Boolean {
        if (Tactics.wonWith(p, cell)) return true
        if (n <= 1) return false
        if (Tactics.threats(o, p) != 0L) return false // o completes a line first
        val threats = Tactics.threats(p, o)
        val count = popCount(threats)
        if (count >= 2) return true // o blocks one, p completes the other
        if (count == 1) return search(p, o or threats, n - 1) // forced block
        // A quiet move: useless unless p wins within n - 1 even if o passes.
        if (!search(p, o, n - 1)) return false
        var replies = (p or o).inv()
        while (replies != 0L) {
            val reply = lowestCell(replies)
            replies = replies and (replies - 1)
            if (!search(p, o or (1L shl reply), n - 1)) return false
        }
        return true
    }
}
