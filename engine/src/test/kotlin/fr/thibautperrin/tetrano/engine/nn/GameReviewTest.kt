package fr.thibautperrin.tetrano.engine.nn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameReviewTest {

    private val net = QubicNet.load(checkNotNull(javaClass.getResourceAsStream("/nn/fixture_net.bin")))

    @Test
    fun flagsExactMistakes() {
        // 1st: 0, 1, 2 (threatens 3); 2nd: 63, 62, then 61 instead of blocking 3 (threatens 60);
        // 1st then plays 40 instead of winning at 3; 2nd wins at 60.
        val moves = byteArrayOf(0, 63, 1, 62, 2, 61, 40, 60)
        val reviews = GameReview(net, simulations = 32).review(moves)
        assertEquals(moves.size + 1, reviews.size)
        assertEquals(GameReview.Verdict.ALLOWED_WIN, reviews[5].verdict)
        assertEquals(GameReview.Verdict.MISSED_WIN, reviews[6].verdict)
        assertEquals(3, reviews[6].bestRing)
        assertEquals(GumbelMcts.Status.WIN, reviews[7].status)
        // final position: the player to move has lost
        assertEquals(GumbelMcts.Status.LOSS, reviews[8].status)
        assertNull(reviews[8].verdict)
        assertEquals(1f, reviews[8].wdl[2])
    }

    @Test
    fun forcedWinIsNotBlamedOnEveryMoveOfItsSequence() {
        // A game lost against level 101 on a phone: from ply 10 the computer wins by a long
        // sequence of threats, each block being forced. None of those blocks may be blamed for
        // allowing the forced win, and the winner never "misses" it while carrying it out.
        val moves = byteArrayOf(
            12, 21, 3, 6, 48, 42, 63, 53, 37, 26, 18, 33, 23, 43, 29, 46, 36, 24,
            38, 39, 25, 51, 13, 49, 15, 14, 7, 11, 22, 52, 19, 28, 31, 47, 27,
        )
        val reviews = GameReview(net, simulations = 32).review(moves)
        val verdicts = reviews.dropLast(1).map { it.verdict }
        assertEquals(0, verdicts.count { it == GameReview.Verdict.MISSED_WIN })
        assertTrue(verdicts.count { it == GameReview.Verdict.ALLOWED_WIN } <= 1)
        // From ply 10 the computer has a VCF: it plays it out, the user's blocks are all forced.
        for (ply in 10 until moves.size) {
            val expected = if (ply % 2 == 0) GumbelMcts.Status.WIN else GumbelMcts.Status.LOSS
            assertEquals("ply $ply", expected, reviews[ply].status)
            // The computer's moves keep the win but not always the shortest one (BEST = shortest).
            val allowed = if (ply % 2 == 0) setOf(GameReview.Verdict.BEST, GameReview.Verdict.GOOD) else setOf(GameReview.Verdict.BEST)
            assertTrue("ply $ply: ${verdicts[ply]}", verdicts[ply] in allowed)
        }
    }

    @Test
    fun positionAfterMovesIsFromTheMoverPointOfView() {
        val moves = byteArrayOf(0, 63, 1)
        val p = Position.after(moves)
        assertEquals(1L shl 63, p.mine)
        assertEquals((1L shl 0) or (1L shl 1), p.theirs)
    }
}
