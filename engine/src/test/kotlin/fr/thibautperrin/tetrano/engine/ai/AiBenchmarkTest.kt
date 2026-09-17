package fr.thibautperrin.tetrano.engine.ai

import fr.thibautperrin.tetrano.engine.BoardState
import org.junit.Test
import kotlin.random.Random

/**
 * Not a correctness test: logs how long [Ai.playLevel] at the hardest
 * difficulty (100) takes on a batch of reference positions, so a future
 * change to the search (an optimization, or an accidental regression) has
 * something concrete to compare against. See docs/PLAN.md P3.4.
 *
 * Reference numbers (JBR 21, this machine, single run): search the commit
 * introducing this file for the logged output.
 */
class AiBenchmarkTest {

    @Test
    fun logPlayLevel100Timings() {
        val positions = referencePositions()
        val timingsMs = LongArray(positions.size)
        for ((index, moves) in positions.withIndex()) {
            val board = BoardState()
            var player = 1
            for (ring in moves) {
                board.play(ring, player)
                player = 3 - player
            }
            val ai = Ai(board)
            val start = System.nanoTime()
            ai.playLevel(player, level = 100)
            timingsMs[index] = (System.nanoTime() - start) / 1_000_000
        }
        println(
            "playLevel(level=100) over ${positions.size} reference positions: " +
                "min=${timingsMs.min()}ms max=${timingsMs.max()}ms avg=${timingsMs.average().toLong()}ms " +
                "all=${timingsMs.toList()}",
        )
    }

    /** 20 positions of varying length, generated deterministically so the log is reproducible. */
    private fun referencePositions(): List<IntArray> {
        val rng = Random(42L)
        return List(20) { i ->
            val board = BoardState()
            val moveCount = 4 + i * 2 // 4, 6, 8, ..., 42
            val moves = ArrayList<Int>(moveCount)
            var player = 1
            for (m in 0 until moveCount) {
                val free = (0 until 64).filter { board.rings[it] == 0.toByte() }
                if (free.isEmpty() || board.lineSets[4].length() > 0 || board.lineSets[8].length() > 0) break
                val ring = free[rng.nextInt(free.size)]
                board.play(ring, player)
                moves.add(ring)
                player = 3 - player
            }
            moves.toIntArray()
        }
    }
}
