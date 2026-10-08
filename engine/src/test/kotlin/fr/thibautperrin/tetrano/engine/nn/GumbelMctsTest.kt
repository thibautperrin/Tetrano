package fr.thibautperrin.tetrano.engine.nn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GumbelMctsTest {

    private fun bits(vararg cells: Int): Long = cells.fold(0L) { acc, c -> acc or (1L shl c) }

    private val net = QubicNet.load(checkNotNull(javaClass.getResourceAsStream("/nn/fixture_net.bin")))

    @Test
    fun completesOwnLine() {
        val r = GumbelMcts(net).search(bits(0, 1, 2, 40), bits(60, 61, 62), 32)
        assertEquals(3, r.move)
        assertEquals(GumbelMcts.Status.WIN, r.status)
    }

    @Test
    fun blocksOpponentLine() {
        val r = GumbelMcts(net).search(bits(0, 21), bits(60, 61, 62), 32)
        assertEquals(63, r.move)
    }

    @Test
    fun playsVcfWin() {
        val r = GumbelMcts(net).search(bits(1, 2, 4, 8), bits(63, 62, 21, 37), 32)
        assertEquals(GumbelMcts.Status.WIN, r.status)
        assertEquals(0, r.move)
    }

    @Test
    fun playsLegalMovesAndPolicyIsADistribution() {
        val rng = Random(5)
        for (net in listOf(net, null)) {
            val mcts = GumbelMcts(net, random = rng)
            var mine = 0L
            var theirs = 0L
            for (ply in 0 until 64) {
                val r = mcts.search(mine, theirs, 24)
                if (r.move < 0) break
                assertEquals(0L, ((mine or theirs) ushr r.move) and 1L)
                if (r.status == GumbelMcts.Status.UNKNOWN) {
                    assertEquals(1f, r.policy.sum(), 1e-3f)
                    for (c in 0 until 64) if (((mine or theirs) ushr c) and 1L != 0L) assertEquals(0f, r.policy[c])
                }
                val after = mine or (1L shl r.move)
                if (Tactics.wonWith(after, r.move) || (after or theirs) == -1L) break
                mine = theirs
                theirs = after
            }
        }
    }

    @Test
    fun deterministicWithoutNoise() {
        val config = GumbelMcts.Config(gumbelNoise = false)
        val a = GumbelMcts(net, config).search(bits(0, 21), bits(42), 40)
        val b = GumbelMcts(net, config).search(bits(0, 21), bits(42), 40)
        assertEquals(a.move, b.move)
        assertTrue(a.simulations in 1..40)
    }
}
