package fr.thibautperrin.tetrano.domain.service

import fr.thibautperrin.tetrano.domain.model.GameMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameSessionServiceTest {

    // AiService's own withContext hop must land back on the test's virtual
    // scheduler, or advanceUntilIdle() below can't see (or wait for) it —
    // Dispatchers.Default would run it on a real thread, invisible to
    // virtual time control.
    private fun TestScope.factory() = GameSessionService.Factory(AiService(UnconfinedTestDispatcher(testScheduler)))

    @Test
    fun twoPlayersAlternatesTurnsAndUndoes() = runTest {
        val session = factory().create(scope = this, mode = GameMode.TWO_PLAYERS)
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

        assertTrue(session.undo())
        assertEquals(1, session.state.value.playedCount)
        assertEquals(2, session.state.value.nextPlayer)
        assertTrue(session.state.value.rings[1].toInt() == 0)
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
        assertEquals(1, session.state.value.nextPlayer)

        assertTrue(session.play(0))
        advanceUntilIdle()

        // The computer (player 2) should have replied by now, handing the
        // turn back to the user.
        assertEquals(1, session.state.value.nextPlayer)
        assertEquals(2, session.state.value.playedCount)
        assertFalse(session.state.value.isBusy)
        assertNull(session.state.value.aiProgress)
    }

    @Test
    fun vsComputerStartingAsPlayerTwoMovesFirst() = runTest {
        val session = factory().create(
            scope = this,
            mode = GameMode.VS_COMPUTER,
            aiLevel = 0,
            startingPlayer = 2,
        )
        advanceUntilIdle()

        // The computer was asked to start; it should have played without
        // any call to play() from the human side.
        assertEquals(1, session.state.value.playedCount)
        assertEquals(1, session.state.value.nextPlayer)
    }

    @Test
    fun vsComputerUndoTakesBackBothMovesWhenComputerAlreadyReplied() = runTest {
        val session = factory().create(scope = this, mode = GameMode.VS_COMPUTER, aiLevel = 0)
        session.play(0)
        advanceUntilIdle()
        assertEquals(2, session.state.value.playedCount)

        assertTrue(session.undo())
        assertEquals(0, session.state.value.playedCount)
        assertEquals(1, session.state.value.nextPlayer)
    }

    @Test
    fun vsComputerUndoOfSoleOpeningMoveByComputerIsRejected() = runTest {
        val session = factory().create(
            scope = this,
            mode = GameMode.VS_COMPUTER,
            aiLevel = 0,
            startingPlayer = 2,
        )
        advanceUntilIdle()
        assertEquals(1, session.state.value.playedCount)

        // Only the computer's own opening move exists: there is no human
        // move to return to, so undo() is a no-op (ports the legacy quirk).
        assertFalse(session.undo())
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
}
