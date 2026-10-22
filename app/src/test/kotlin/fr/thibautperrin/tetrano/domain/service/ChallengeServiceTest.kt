package fr.thibautperrin.tetrano.domain.service

import fr.thibautperrin.tetrano.data.assets.ChallengeLevels
import fr.thibautperrin.tetrano.data.db.ChallengeProgressDao
import fr.thibautperrin.tetrano.data.db.ChallengeProgressEntity
import fr.thibautperrin.tetrano.domain.model.ChallengeHintResult
import fr.thibautperrin.tetrano.domain.model.ChallengeMoveResult
import fr.thibautperrin.tetrano.engine.BoardState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises [ChallengeService]'s orchestration (ports `Challenge`/
 * `MemoryVarsChallenge`/`ChallengeActivity`, see docs/PLAN.md P6) against
 * hand-built fixture positions, using [BoardState] itself (rather than
 * hand-derived signature bytes) to construct them and a fixed symmetry
 * (0 = identity) so ring numbers stay predictable.
 */
class ChallengeServiceTest {

    /** The AI runs inline: these tests check the orchestration, not the threading. */
    private fun newService(levels: ChallengeLevels, dao: ChallengeProgressDao) =
        ChallengeService(levels, dao, Dispatchers.Unconfined)

    private fun progress(level: Int = 1, symmetry: Int = 0) =
        ChallengeProgressEntity(level = level, symmetry = symmetry, normallyPlayed = null, playedByHints = null, nextTimeAdvert = false)

    private class FakeChallengeLevels(
        signatures: Map<Int, ByteArray>,
        private val forkOrders: Map<Int, Int>,
        override val levelCount: Int,
    ) : ChallengeLevels {
        private val signatures = signatures.toMap()
        override fun signature(level: Int): ByteArray = signatures.getValue(level)
        override fun forkOrder(level: Int): Int = forkOrders[level] ?: 0
        override fun isInvertPlayer(level: Int): Boolean = false
    }

    private class FakeChallengeProgressDao(initial: ChallengeProgressEntity?) : ChallengeProgressDao {
        private val state = MutableStateFlow(initial)
        override suspend fun get(): ChallengeProgressEntity? = state.value
        override suspend fun upsert(progress: ChallengeProgressEntity) {
            state.value = progress
        }
    }

    /** A fresh board with [moves] played (alternating, player 1 first), as a level-1 signature. */
    private fun signatureFrom(vararg moves: Int): ByteArray {
        val board = BoardState()
        var player = 1
        for (ring in moves) {
            board.play(ring, player)
            player = 3 - player
        }
        return board.signature(invertPlayers = false)
    }

    /** Player 1 owns rings 0, 1, 2 of line {0,1,2,3} (see BoardTables); 50/51 are unrelated player-2 filler. */
    private val userThreeInARow get() = signatureFrom(0, 50, 1, 51, 2)

    @Test
    fun winningMoveEndsTheLevelAndAdvances() = runTest {
        val levels = FakeChallengeLevels(mapOf(1 to userThreeInARow), mapOf(1 to 0), levelCount = 5)
        val dao = FakeChallengeProgressDao(progress())
        val service = newService(levels, dao)

        assertTrue(service.startCurrentLevel())
        assertEquals(1, service.snapshot().level)

        val result = service.playUserRing(3)
        assertEquals(ChallengeMoveResult.LevelPassed(1), result)
        assertEquals(2, dao.get()?.level)
        // The finished position stays readable for the end-of-level animation.
        val finished = service.snapshot()
        assertEquals(1, finished.level)
        assertEquals(1, finished.usedRings)
        assertTrue(finished.isOver)
        assertFalse(finished.canAbandon)
        assertArrayEquals(byteArrayOf(0, 1, 2, 3), service.blinkingRings().sortedArray())
    }

    @Test
    fun runningOutOfRingsFailsTheLevelWithoutAdvancing() = runTest {
        val emptySignature = signatureFrom() // no moves played: an empty board
        val levels = FakeChallengeLevels(mapOf(1 to emptySignature), mapOf(1 to 0), levelCount = 5) // authorizedRings = 2
        val dao = FakeChallengeProgressDao(progress())
        val service = newService(levels, dao)
        assertTrue(service.startCurrentLevel())

        val first = service.playUserRing(0)
        assertTrue(first is ChallengeMoveResult.ComputerReplied)
        assertEquals(1, service.snapshot().level) // still on the same level

        // The user's 2nd ring (an arbitrary still-free one) exhausts the
        // authorizedRings=2 budget without completing a line.
        val stillFree = (0 until 64).first { service.snapshot().rings[it] == 0.toByte() }
        val second = service.playUserRing(stillFree)
        assertTrue(second is ChallengeMoveResult.LevelFailed)
        assertEquals(false, (second as ChallengeMoveResult.LevelFailed).computerWon)
        assertNull(second.computerRing)
        assertEquals(1, dao.get()?.level) // retry the same level
        assertEquals(2, service.snapshot().usedRings)
        assertEquals(0, service.blinkingRings().size)
    }

    @Test
    fun computerWinningReplyFailsTheLevel() = runTest {
        // Player 2 (the computer) owns rings 0, 1, 2 of line {0,1,2,3}; the
        // user plays the unrelated ring 60 and the computer completes the line.
        val signature = signatureFrom(50, 0, 51, 1, 52, 2)
        val levels = FakeChallengeLevels(mapOf(1 to signature), mapOf(1 to 0), levelCount = 5)
        val dao = FakeChallengeProgressDao(progress())
        val service = newService(levels, dao)
        assertTrue(service.startCurrentLevel())

        val result = service.playUserRing(60)
        assertEquals(ChallengeMoveResult.LevelFailed(1, computerRing = 3), result)
        assertTrue((result as ChallengeMoveResult.LevelFailed).computerWon)
        assertEquals(1, dao.get()?.level)
        assertNull(dao.get()?.normallyPlayed)
        assertEquals(2.toByte(), service.snapshot().rings[3])
        assertArrayEquals(byteArrayOf(0, 1, 2, 3), service.blinkingRings().sortedArray())
    }

    @Test
    fun finishedAttemptRefusesMovesHintsAndGivingUp() = runTest {
        val levels = FakeChallengeLevels(mapOf(1 to userThreeInARow, 2 to signatureFrom()), mapOf(1 to 0, 2 to 0), levelCount = 5)
        val dao = FakeChallengeProgressDao(progress())
        val service = newService(levels, dao)
        assertTrue(service.startCurrentLevel())
        service.playUserRing(3)

        assertEquals(ChallengeMoveResult.InvalidMove, service.playUserRing(40))
        assertNull(service.abandon())
        assertEquals(ChallengeHintResult.LevelOver, service.useHint())
        assertEquals(2, dao.get()?.level)

        // Reloading starts the next attempt.
        assertTrue(service.startCurrentLevel())
        assertFalse(service.snapshot().isOver)
        assertEquals(2, service.snapshot().level)
    }

    @Test
    fun givingUpKeepsTheLevelAndSymmetry() = runTest {
        val levels = FakeChallengeLevels(mapOf(1 to signatureFrom()), mapOf(1 to 1), levelCount = 5) // authorizedRings = 3
        val dao = FakeChallengeProgressDao(progress(symmetry = 5))
        val service = newService(levels, dao)
        assertTrue(service.startCurrentLevel())
        assertFalse(service.snapshot().canAbandon) // nothing played yet

        assertTrue(service.playUserRing(0) is ChallengeMoveResult.ComputerReplied)
        assertTrue(service.snapshot().canAbandon)
        assertEquals(ChallengeMoveResult.LevelFailed(1), service.abandon())
        assertEquals(1, dao.get()?.level)
        assertEquals(5, dao.get()?.symmetry)
        assertNull(dao.get()?.normallyPlayed)
        assertFalse(service.snapshot().canAbandon)
    }

    @Test
    fun hintWinsImmediatelyWhenAThreeInARowAlreadyExists() = runTest {
        // Player 1 already owns 3 of the 4 rings of line {0,1,2,3} before
        // the attempt even starts (a position the built-in level data
        // could plausibly encode) — the very first hint should just
        // complete it.
        val levels = FakeChallengeLevels(mapOf(1 to userThreeInARow), mapOf(1 to 0), levelCount = 5)
        val dao = FakeChallengeProgressDao(progress())
        val service = newService(levels, dao)
        assertTrue(service.startCurrentLevel())

        val hint = service.useHint()
        assertTrue(hint is ChallengeHintResult.LevelWon)
        assertEquals(3, (hint as ChallengeHintResult.LevelWon).ringForUser)
        assertEquals(1, hint.level) // the completed level, not the next one
        assertEquals(2, dao.get()?.level)
        assertTrue(service.snapshot().isOver)
    }

    @Test
    fun hintUndoesTheAttemptsNormalMovesFirst() = runTest {
        val levels = FakeChallengeLevels(mapOf(1 to userThreeInARow), mapOf(1 to 1), levelCount = 5) // authorizedRings = 3
        val dao = FakeChallengeProgressDao(progress())
        val service = newService(levels, dao)
        assertTrue(service.startCurrentLevel())
        val initial = service.snapshot().rings

        // A move that doesn't win (the computer then blocks ring 3).
        assertTrue(service.playUserRing(40) is ChallengeMoveResult.ComputerReplied)

        val hint = service.useHint()
        // Undone back to the level's position, where ring 3 wins again.
        assertTrue(hint is ChallengeHintResult.LevelWon)
        assertEquals(3, (hint as ChallengeHintResult.LevelWon).ringForUser)
        val finalRings = service.snapshot().rings
        assertEquals(0.toByte(), finalRings[40])
        assertEquals(1.toByte(), finalRings[3])
        assertEquals(initial.count { it != 0.toByte() } + 1, finalRings.count { it != 0.toByte() })
    }

    @Test
    fun hintsAreFreeAndUnlimited() = runTest {
        // The legacy app gave 3 stars to start with, one per hint: a fourth
        // hint in a row must work just as well.
        val levels = FakeChallengeLevels(mapOf(1 to userThreeInARow), mapOf(1 to 0), levelCount = 5)
        val dao = FakeChallengeProgressDao(progress())
        val service = newService(levels, dao)
        repeat(4) {
            dao.upsert(progress())
            assertTrue(service.startCurrentLevel())
            assertTrue(service.useHint() is ChallengeHintResult.LevelWon)
        }
    }

    @Test
    fun allLevelsPassedOnceLevelExceedsCount() = runTest {
        val levels = FakeChallengeLevels(emptyMap(), emptyMap(), levelCount = 1)
        val dao = FakeChallengeProgressDao(progress(level = 2))
        val service = newService(levels, dao)

        assertEquals(false, service.startCurrentLevel())
        assertTrue(service.isAllLevelsPassed)
    }
}
