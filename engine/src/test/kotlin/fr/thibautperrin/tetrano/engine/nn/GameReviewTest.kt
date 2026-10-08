package fr.thibautperrin.tetrano.engine.nn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun positionAfterMovesIsFromTheMoverPointOfView() {
        val moves = byteArrayOf(0, 63, 1)
        val p = Position.after(moves)
        assertEquals(1L shl 63, p.mine)
        assertEquals((1L shl 0) or (1L shl 1), p.theirs)
    }
}
