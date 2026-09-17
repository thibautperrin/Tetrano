package fr.thibautperrin.tetrano.domain.service

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import fr.thibautperrin.tetrano.data.assets.ChallengeLevels
import fr.thibautperrin.tetrano.data.db.ChallengeProgressDao
import fr.thibautperrin.tetrano.data.db.ChallengeProgressEntity
import fr.thibautperrin.tetrano.data.prefs.TetranoPreferencesDataSource
import fr.thibautperrin.tetrano.domain.model.ChallengeHintResult
import fr.thibautperrin.tetrano.domain.model.ChallengeMoveResult
import fr.thibautperrin.tetrano.engine.BoardState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Exercises [ChallengeService]'s orchestration (ports `Challenge`/
 * `MemoryVarsChallenge`/`ChallengeActivity`, see docs/PLAN.md P6) against
 * hand-built fixture positions, using [BoardState] itself (rather than
 * hand-derived signature bytes) to construct them and a fixed symmetry
 * (0 = identity) so ring numbers stay predictable.
 */
class ChallengeServiceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun newStarService(): StarService {
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(tempFolder.newFolder(), "settings.preferences_pb") },
        )
        return StarService(TetranoPreferencesDataSource(dataStore))
    }

    private class FakeChallengeLevels(
        private val signatures: Map<Int, ByteArray>,
        private val forkOrders: Map<Int, Int>,
        override val levelCount: Int,
    ) : ChallengeLevels {
        override fun signature(level: Int): ByteArray = signatures.getValue(level)
        override fun forkOrder(level: Int): Int = forkOrders[level] ?: 0
        override fun isInvertPlayer(level: Int): Boolean = false
    }

    private class FakeChallengeProgressDao(initial: ChallengeProgressEntity?) : ChallengeProgressDao {
        private val state = MutableStateFlow(initial)
        override fun observe(): Flow<ChallengeProgressEntity?> = state
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

    @Test
    fun winningMoveEndsTheLevelAndAdvances() = runTest {
        // Player 1 owns rings 0, 1, 2 of line {0,1,2,3} (see BoardTables);
        // ring 50/51 are unrelated filler moves for player 2.
        val signature = signatureFrom(0, 50, 1, 51, 2)
        val levels = FakeChallengeLevels(mapOf(1 to signature), mapOf(1 to 0), levelCount = 5)
        val dao = FakeChallengeProgressDao(ChallengeProgressEntity(level = 1, symmetry = 0, normallyPlayed = null, playedByStars = null, nextTimeAdvert = false))
        val service = ChallengeService(levels, dao, newStarService())

        assertTrue(service.startCurrentLevel())
        assertEquals(1, service.snapshot().level)

        val result = service.playUserRing(3)
        assertEquals(ChallengeMoveResult.LevelPassed(1), result)
        assertEquals(2, dao.get()?.level)
    }

    @Test
    fun runningOutOfRingsFailsTheLevelWithoutAdvancing() = runTest {
        val emptySignature = signatureFrom() // no moves played: an empty board
        val levels = FakeChallengeLevels(mapOf(1 to emptySignature), mapOf(1 to 0), levelCount = 5) // authorizedRings = 2
        val dao = FakeChallengeProgressDao(ChallengeProgressEntity(level = 1, symmetry = 0, normallyPlayed = null, playedByStars = null, nextTimeAdvert = false))
        val service = ChallengeService(levels, dao, newStarService())
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
        assertEquals(1, dao.get()?.level) // retry the same level
    }

    @Test
    fun starHintWinsImmediatelyWhenAThreeInARowAlreadyExists() = runTest {
        // Player 1 already owns 3 of the 4 rings of line {0,1,2,3} before
        // the attempt even starts (a position the built-in level data
        // could plausibly encode) — the very first star hint should just
        // complete it.
        val signature = signatureFrom(0, 50, 1, 51, 2)
        val levels = FakeChallengeLevels(mapOf(1 to signature), mapOf(1 to 0), levelCount = 5)
        val dao = FakeChallengeProgressDao(ChallengeProgressEntity(level = 1, symmetry = 0, normallyPlayed = null, playedByStars = null, nextTimeAdvert = false))
        val service = ChallengeService(levels, dao, newStarService())
        assertTrue(service.startCurrentLevel())

        val hint = service.useStarHint()
        assertTrue(hint is ChallengeHintResult.LevelWon)
        assertEquals(3, (hint as ChallengeHintResult.LevelWon).ringForUser)
        assertEquals(2, dao.get()?.level)
    }

    @Test
    fun noStarsAvailableAfterSpendingAllThree() = runTest {
        val signature = signatureFrom(0, 50, 1, 51, 2)
        val levels = FakeChallengeLevels(mapOf(1 to signature), mapOf(1 to 0), levelCount = 5)
        val dao = FakeChallengeProgressDao(ChallengeProgressEntity(level = 1, symmetry = 0, normallyPlayed = null, playedByStars = null, nextTimeAdvert = false))
        val stars = newStarService()
        repeat(3) { stars.useStar() }
        val service = ChallengeService(levels, dao, stars)
        assertTrue(service.startCurrentLevel())

        assertEquals(ChallengeHintResult.NoStarsAvailable, service.useStarHint())
    }

    @Test
    fun allLevelsPassedOnceLevelExceedsCount() = runTest {
        val levels = FakeChallengeLevels(emptyMap(), emptyMap(), levelCount = 1)
        val dao = FakeChallengeProgressDao(ChallengeProgressEntity(level = 2, symmetry = 0, normallyPlayed = null, playedByStars = null, nextTimeAdvert = false))
        val service = ChallengeService(levels, dao, newStarService())

        assertEquals(false, service.startCurrentLevel())
        assertTrue(service.isAllLevelsPassed)
    }
}
