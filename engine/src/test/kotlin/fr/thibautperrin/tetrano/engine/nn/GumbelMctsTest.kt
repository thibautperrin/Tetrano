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
        // The same move up to a symmetry of the position (0, 21, 42 are on a main diagonal).
        assertEquals(a.moveClass[a.move], b.moveClass[b.move])
        assertTrue(a.simulations in 1..40)
    }

    @Test
    fun equivalentMovesAreOneMove() {
        val r = GumbelMcts(net, GumbelMcts.Config(gumbelNoise = false)).search(0L, 0L, 64)
        // The empty board has two classes of moves: the 16 cells on 7 lines and the 48 on 4.
        assertEquals(setOf(0, 1), r.moveClass.toSet())
        for (c in 0 until 64) {
            val same = (0 until 64).filter { r.moveClass[it] == r.moveClass[c] }
            for (d in same) {
                assertEquals(r.policy[c], r.policy[d], 1e-6f)
                assertEquals(r.moveValues[c], r.moveValues[d], 1e-6f)
                assertEquals(r.visits[c], r.visits[d])
            }
        }
        assertEquals(1f, r.policy.sum(), 1e-3f)
        assertEquals(7, QubicGeometry.cellDegree(r.move))
    }

    @Test
    fun playsEveryCellOfTheBestClass() {
        val mcts = GumbelMcts(net, GumbelMcts.Config(gumbelNoise = false), Random(3))
        val played = (0 until 200).map { mcts.search(0L, 0L, 16).move }.toSet()
        assertTrue(played.size > 8)
        assertTrue(played.all { QubicGeometry.cellDegree(it) == 7 })
    }

    @Test
    fun adaptiveSearchStopsOnceSettled() {
        val position = bits(0, 21) to bits(42, 5)
        val never = GumbelMcts(net, GumbelMcts.Config(gumbelNoise = false, stableRounds = 99, decidedValue = 2f))
            .searchAdaptive(position.first, position.second, 512)
        assertEquals(512, never.simulations)
        val always = GumbelMcts(net, GumbelMcts.Config(gumbelNoise = false, stableRounds = 1))
            .searchAdaptive(position.first, position.second, 512)
        assertEquals(64, always.simulations)
        val rounds = ArrayList<Int>()
        GumbelMcts(net, GumbelMcts.Config(gumbelNoise = false)).searchAdaptive(
            position.first, position.second, 2048,
            stopRule = { rounds += it.simulations; false },
        )
        assertEquals(listOf(64, 128, 256, 512, 1024), rounds.take(5))
        // A forced move needs no search.
        val forced = GumbelMcts(net).searchAdaptive(bits(0, 21), bits(60, 61, 62), 512)
        assertEquals(63, forced.move)
        assertTrue(forced.simulations <= 1)
    }
}
