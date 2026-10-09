package fr.thibautperrin.tetrano.engine.nn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortestVcfTest {

    /** The game lost against level 101 on a phone (see GameReviewTest): computer first, VCF from ply 10. */
    private val phoneGame = byteArrayOf(
        12, 21, 3, 6, 48, 42, 63, 53, 37, 26, 18, 33, 23, 43, 29, 46, 36, 24,
        38, 39, 25, 51, 13, 49, 15, 14, 7, 11, 22, 52, 19, 28, 31, 47, 27,
    )

    @Test
    fun reportsShortestVcfAlongThePhoneGame() {
        for (ply in 10 until phoneGame.size step 2) {
            val position = Position.after(phoneGame, ply)
            val vcf = Vcf()
            val move = vcf.shortest(position.mine, position.theirs, 2_000_000)
            val playedMovesLeft = (phoneGame.size - ply + 1) / 2
            assertTrue(move >= 0)
            assertTrue(vcf.shortestLength <= playedMovesLeft)
        }
    }

    @Test
    fun searchPlaysTheShortestForcedWin() {
        // At ply 10 of the phone game the computer had a 7-move VCF but took 13 moves to win.
        val net = QubicNet.load(checkNotNull(javaClass.getResourceAsStream("/nn/fixture_net.bin")))
        val mcts = GumbelMcts(net, GumbelMcts.Config(gumbelNoise = false))
        val start = Position.after(phoneGame, 10)
        var attacker = start.mine
        var defender = start.theirs
        var attackerMoves = 0
        while (true) {
            val move = mcts.search(attacker, defender, 64).move
            attacker = attacker or (1L shl move)
            attackerMoves++
            if (Tactics.wonWith(attacker, move)) break
            // the defender blocks a threat (always forced here), else plays anywhere
            val threats = Tactics.threats(attacker, defender)
            val block = if (threats != 0L) threats else (attacker or defender).inv()
            defender = defender or (1L shl java.lang.Long.numberOfTrailingZeros(block))
            assertTrue("no win after $attackerMoves moves", attackerMoves < 20)
        }
        assertEquals(7, attackerMoves)
    }

    @Test
    fun doubleThreatIsTwoMoves() {
        fun bits(vararg cells: Int): Long = cells.fold(0L) { acc, c -> acc or (1L shl c) }
        val vcf = Vcf()
        assertEquals(0, vcf.shortest(bits(1, 2, 4, 8), bits(63, 62, 21, 37), 10_000))
        assertEquals(2, vcf.shortestLength)
        assertEquals(3, vcf.shortest(bits(0, 1, 2, 40), bits(60, 61, 62), 10_000))
        assertEquals(1, vcf.shortestLength)
    }
}
