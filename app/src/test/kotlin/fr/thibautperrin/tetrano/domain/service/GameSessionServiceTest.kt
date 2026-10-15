package fr.thibautperrin.tetrano.domain.service

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import fr.thibautperrin.tetrano.data.assets.NeuralNets
import fr.thibautperrin.tetrano.data.prefs.TetranoPreferencesDataSource
import fr.thibautperrin.tetrano.domain.model.AiProgressState
import fr.thibautperrin.tetrano.domain.model.GameMode
import fr.thibautperrin.tetrano.domain.model.GameSessionEvent
import fr.thibautperrin.tetrano.domain.model.UndoneMove
import fr.thibautperrin.tetrano.engine.ai.AiSearchPhase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class GameSessionServiceTest {

    // AiService's own withContext hop must land back on the test's virtual
    // scheduler, or advanceUntilIdle() below can't see (or wait for) it —
    // Dispatchers.Default would run it on a real thread, invisible to
    // virtual time control. A StandardTestDispatcher keeps the search
    // pending until the scheduler runs it, so a test can act "while the
    // computer thinks".
    private fun TestScope.factory() = GameSessionService.Factory(
        AiService(
            StandardTestDispatcher(testScheduler),
            object : NeuralNets {
                override val net get() = error("level 101 is not used in these tests")
            },
            // Only read at level 101.
            TetranoPreferencesDataSource(
                PreferenceDataStoreFactory.create(scope = backgroundScope) { File.createTempFile("prefs", ".preferences_pb") },
            ),
        ),
    )

    private fun TestScope.collectEvents(session: GameSessionService): List<GameSessionEvent> {
        val events = mutableListOf<GameSessionEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { session.events.toList(events) }
        return events
    }

    @Test
    fun twoPlayersAlternatesTurnsAndUndoes() = runTest {
        val session = factory().create(scope = this, mode = GameMode.TWO_PLAYERS)
        session.start()
        assertEquals(1, session.state.value.nextPlayer)

        assertTrue(session.play(0))
        assertEquals(2, session.state.value.nextPlayer)
        assertEquals(1, session.state.value.rings[0].toInt())

        assertTrue(session.play(1))
        assertEquals(1, session.state.value.nextPlayer)

        // Playing an occupied ring, or one out of range, fails without side effects.
        assertFalse(session.play(0))
        assertFalse(session.play(64))
        assertFalse(session.play(-1))
        assertEquals(2, session.state.value.playedCount)

        assertEquals(listOf(UndoneMove(ring = 1, player = 2)), session.undo())
        assertEquals(1, session.state.value.playedCount)
        assertEquals(2, session.state.value.nextPlayer)
        assertEquals(0, session.state.value.rings[1].toInt())
    }

    @Test
    fun undoWithNothingPlayedDoesNothing() = runTest {
        val session = factory().create(scope = this, mode = GameMode.TWO_PLAYERS)
        assertEquals(emptyList<UndoneMove>(), session.undo())
    }

    @Test
    fun restartChangingBeginnerFlipsStartingPlayer() = runTest {
        val session = factory().create(scope = this, mode = GameMode.TWO_PLAYERS)
        session.play(0)
        assertEquals(1, session.startingPlayer())

        session.restart(changingBeginner = true)
        assertEquals(0, session.state.value.playedCount)
        assertEquals(2, session.state.value.nextPlayer)
    }

    @Test
    fun vsComputerRepliesAutomaticallyAfterUserMove() = runTest {
        val session = factory().create(scope = this, mode = GameMode.VS_COMPUTER, aiLevel = 0)
        val events = collectEvents(session)
        session.start()
        assertEquals(1, session.state.value.nextPlayer)

        assertTrue(session.play(0))
        assertTrue(session.state.value.isAiThinking)
        // The user can't play for the computer meanwhile.
        assertFalse(session.play(1))
        advanceUntilIdle()

        // The computer (player 2) should have replied by now, handing the
        // turn back to the user, and said which ring it played.
        val state = session.state.value
        assertEquals(1, state.nextPlayer)
        assertEquals(2, state.playedCount)
        assertFalse(state.isAiThinking)
        assertNull(state.aiProgress)
        assertEquals(listOf<GameSessionEvent>(GameSessionEvent.ComputerPlayed(state.lastRing!!)), events)
        assertEquals(2, state.rings[state.lastRing!!].toInt())
    }

    @Test
    fun vsComputerStartingAsPlayerTwoMovesFirstOnceStarted() = runTest {
        val session = factory().create(
            scope = this,
            mode = GameMode.VS_COMPUTER,
            aiLevel = 0,
            startingPlayer = 2,
        )
        advanceUntilIdle()
        // Nothing happens before start().
        assertEquals(0, session.state.value.playedCount)
        assertFalse(session.state.value.isAiThinking)

        session.start()
        assertTrue(session.state.value.isAiThinking)
        advanceUntilIdle()

        // The computer was asked to start; it should have played without
        // any call to play() from the human side.
        assertEquals(1, session.state.value.playedCount)
        assertEquals(1, session.state.value.nextPlayer)
    }

    @Test
    fun vsComputerUndoTakesBackBothMovesWhenComputerAlreadyReplied() = runTest {
        val session = factory().create(scope = this, mode = GameMode.VS_COMPUTER, aiLevel = 0)
        session.start()
        session.play(0)
        advanceUntilIdle()
        assertEquals(2, session.state.value.playedCount)
        val computerRing = session.state.value.lastRing!!

        // Most recent first: the computer's reply, then the user's move.
        assertEquals(listOf(UndoneMove(computerRing, 2), UndoneMove(0, 1)), session.undo())
        assertEquals(0, session.state.value.playedCount)
        assertEquals(1, session.state.value.nextPlayer)
    }

    @Test
    fun vsComputerUndoWhileThinkingCancelsTheSearch() = runTest {
        val session = factory().create(scope = this, mode = GameMode.VS_COMPUTER, aiLevel = 0)
        val events = collectEvents(session)
        session.start()
        session.play(5)
        assertTrue(session.state.value.isAiThinking)

        // Only the user's move is taken back; the pending answer is dropped.
        assertEquals(listOf(UndoneMove(5, 1)), session.undo())
        assertFalse(session.state.value.isAiThinking)
        advanceUntilIdle()
        assertEquals(0, session.state.value.playedCount)
        assertEquals(1, session.state.value.nextPlayer)
        assertEquals(emptyList<GameSessionEvent>(), events)
    }

    @Test
    fun vsComputerRestartWhileThinkingStartsAFreshSearch() = runTest {
        val session = factory().create(scope = this, mode = GameMode.VS_COMPUTER, aiLevel = 0)
        val events = collectEvents(session)
        session.start()
        session.play(5)
        assertTrue(session.state.value.isAiThinking)

        // The user started: the computer starts the next game, at once.
        session.restart(changingBeginner = true)
        assertTrue(session.state.value.isAiThinking)
        advanceUntilIdle()

        assertEquals(1, session.state.value.playedCount)
        assertEquals(2, session.startingPlayer())
        assertEquals(1, events.size)
    }

    @Test
    fun vsComputerUndoOfSoleOpeningMoveByComputerIsRejected() = runTest {
        val session = factory().create(
            scope = this,
            mode = GameMode.VS_COMPUTER,
            aiLevel = 0,
            startingPlayer = 2,
        )
        session.start()
        advanceUntilIdle()
        assertEquals(1, session.state.value.playedCount)

        // Only the computer's own opening move exists: there is no human
        // move to return to, so undo() is a no-op (ports the legacy quirk).
        assertEquals(emptyList<UndoneMove>(), session.undo())
        assertEquals(1, session.state.value.playedCount)
    }

    @Test
    fun resumesFromInitialMoves() = runTest {
        val session = factory().create(
            scope = this,
            mode = GameMode.TWO_PLAYERS,
            initialMoves = byteArrayOf(10, 20, 13),
            startingPlayer = 1,
        )
        assertEquals(3, session.state.value.playedCount)
        assertEquals(2, session.state.value.nextPlayer)
        assertEquals(listOf<Byte>(10, 20, 13), session.playedRings().toList())
    }

    @Test
    fun aiProgressMapsToTheLegacyTrayArc() {
        assertEquals(0f, AiProgressState(AiSearchPhase.SEARCHING_FORK, 0, 10).trayAngleDegrees, 0f)
        assertEquals(90f, AiProgressState(AiSearchPhase.SEARCHING_FORK, 5, 10).trayAngleDegrees, 0f)
        assertEquals(180f, AiProgressState(AiSearchPhase.BLOCKING_FORK, 0, 10).trayAngleDegrees, 0f)
        assertEquals(360f, AiProgressState(AiSearchPhase.BLOCKING_FORK, 10, 10).trayAngleDegrees, 0f)
        assertEquals(0f, AiProgressState(AiSearchPhase.SEARCHING_FORK, 3, 0).trayAngleDegrees, 0f)
    }

    @Test
    fun resumeStopsAtTheFirstInvalidMove() = runTest {
        val session = factory().create(
            scope = this,
            mode = GameMode.TWO_PLAYERS,
            initialMoves = byteArrayOf(10, 20, 10, 13),
        )
        assertEquals(listOf<Byte>(10, 20), session.playedRings().toList())
    }
}
