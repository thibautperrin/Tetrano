package fr.thibautperrin.tetrano.domain.service

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import fr.thibautperrin.tetrano.data.prefs.TetranoPreferencesDataSource
import fr.thibautperrin.tetrano.domain.model.GameMode
import fr.thibautperrin.tetrano.domain.model.StarAward
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Exercises [StarService] against a real DataStore backed by a temp file
 * (no Android Context needed — DataStore's core API is plain Kotlin/JVM).
 */
class StarServiceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun newStarService(): StarService {
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(tempFolder.newFolder(), "settings.preferences_pb") },
        )
        return StarService(TetranoPreferencesDataSource(dataStore))
    }

    @Test
    fun startsWithThreeStars() = runTest {
        assertEquals(3, newStarService().numberStars.first())
    }

    @Test
    fun useStarSpendsOneAndFailsAtZero() = runTest {
        val service = newStarService()
        repeat(3) { assertTrue(service.useStar()) }
        assertEquals(0, service.numberStars.first())
        assertFalse(service.useStar())
    }

    @Test
    fun awardsStarAtGameCountMilestone() = runTest {
        val service = newStarService()
        repeat(4) { service.recordGameFinished(GameMode.TWO_PLAYERS) }
        val starsBefore = service.numberStars.first()

        val awards = service.recordGameFinished(GameMode.TWO_PLAYERS) // 5th game: a milestone
        assertTrue(awards.any { it is StarAward.GamesPlayed && it.count == 5 })
        assertEquals(starsBefore + 1, service.numberStars.first())
    }

    @Test
    fun doesNotAwardBetweenMilestones() = runTest {
        val service = newStarService()
        val awards = service.recordGameFinished(GameMode.TWO_PLAYERS) // 1st game: not a milestone by count...
        // ...but IS the first time this mode was ever played, so it's
        // allowed to carry an AllModesTried check (which won't fire yet).
        assertTrue(awards.none { it is StarAward.GamesPlayed })
    }

    @Test
    fun awardsWinMilestoneOnlyWhenWon() = runTest {
        val service = newStarService()
        repeat(4) { service.recordGameFinished(GameMode.VS_COMPUTER, won = false) }
        val noWinAward = service.recordGameFinished(GameMode.VS_COMPUTER, won = false)
        assertTrue(noWinAward.none { it is StarAward.GamesWonAgainstComputer })

        repeat(4) { service.recordGameFinished(GameMode.VS_COMPUTER, won = true) }
        val winAward = service.recordGameFinished(GameMode.VS_COMPUTER, won = true) // 5th win
        assertTrue(winAward.any { it is StarAward.GamesWonAgainstComputer && it.count == 5 })
    }

    @Test
    fun allModesTriedAwardedOnceWhenLastModeIsFirstPlayed() = runTest {
        val service = newStarService()
        val afterTwoPlayers = service.recordGameFinished(GameMode.TWO_PLAYERS)
        assertTrue(afterTwoPlayers.none { it is StarAward.AllModesTried })

        val afterChallenge = service.recordChallengeAttempted()
        assertEquals(null, afterChallenge) // vs-computer still untried

        val afterVsComputer = service.recordGameFinished(GameMode.VS_COMPUTER)
        assertTrue(afterVsComputer.any { it is StarAward.AllModesTried })

        // Never awarded a second time, even after replaying every mode again.
        service.recordGameFinished(GameMode.TWO_PLAYERS)
        val secondChallengeAttempt = service.recordChallengeAttempted()
        assertEquals(null, secondChallengeAttempt)
        val afterVsComputerAgain = service.recordGameFinished(GameMode.VS_COMPUTER)
        assertTrue(afterVsComputerAgain.none { it is StarAward.AllModesTried })
    }

    @Test
    fun bluetoothAndOnlineAreNoOps() = runTest {
        val service = newStarService()
        assertEquals(emptyList<StarAward>(), service.recordGameFinished(GameMode.BLUETOOTH))
        assertEquals(emptyList<StarAward>(), service.recordGameFinished(GameMode.ONLINE))
    }
}
