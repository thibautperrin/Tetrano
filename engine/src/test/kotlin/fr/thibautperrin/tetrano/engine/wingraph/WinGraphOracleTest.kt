package fr.thibautperrin.tetrano.engine.wingraph

import fr.thibautperrin.tetrano.engine.nn.Tactics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import kotlin.random.Random

class WinGraphOracleTest {
    private val book = File("../tools/wingraph/graph/tetrano_win.bin").inputStream().use { WinBook.load(it) }

    /** A game of the Unbeatable player against random replies, as rings in play order. */
    private fun game(rng: Random, deviateAt: Int = -1): ByteArray {
        val player = UnbeatablePlayer(book, UnbeatablePlayer.STRONG_RINGS, rng)
        val moves = ArrayList<Byte>()
        var first = 0L
        var second = 0L
        while (true) {
            val ply = moves.size
            val free = (first or second).inv()
            // From the deviation on, the first player plays at random too (the graph is left).
            val cell = if (ply % 2 == 0 && (deviateAt < 0 || ply < deviateAt)) {
                player.chooseRing(first, second)
            } else {
                var bits = free
                repeat(rng.nextInt(java.lang.Long.bitCount(free))) { bits = bits and (bits - 1) }
                java.lang.Long.numberOfTrailingZeros(bits)
            }
            moves += cell.toByte()
            if (ply % 2 == 0) first = first or (1L shl cell) else second = second or (1L shl cell)
            val mover = if (ply % 2 == 0) first else second
            if (Tactics.wonWith(mover, cell) || (first or second) == -1L) return moves.toByteArray()
        }
    }

    @Test
    fun positionsOfTheStrategyAreExact() {
        val rng = Random(1)
        val oracle = WinGraphOracle(listOf(book to UnbeatablePlayer.STRONG_RINGS))
        repeat(20) {
            val moves = game(rng)
            for (count in 1 until moves.size) {
                val r = oracle.lookup(moves, count)
                assertNotNull("ply $count", r)
                assertEquals(count % 2 == 0, r!!.first)
            }
        }
    }

    @Test
    fun aDeviationEndsTheProof() {
        val rng = Random(2)
        val oracle = WinGraphOracle(listOf(book to UnbeatablePlayer.STRONG_RINGS))
        var checked = 0
        repeat(20) {
            val moves = game(rng, deviateAt = 4)
            if (moves.size <= 6) return@repeat
            assertNotNull(oracle.lookup(moves, 4)) // before the deviation: still on the walk
            // After a random move instead of the strategy's (unless it happened to be equivalent).
            val after = oracle.lookup(moves, 5)
            if (after == null) checked++
            for (count in 6 until moves.size) if (after == null) assertNull(oracle.lookup(moves, count))
        }
        assert(checked > 10)
    }
}
