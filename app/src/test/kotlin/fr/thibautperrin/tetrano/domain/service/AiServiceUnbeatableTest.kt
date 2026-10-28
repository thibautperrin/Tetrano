package fr.thibautperrin.tetrano.domain.service

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import fr.thibautperrin.tetrano.data.assets.NeuralNets
import fr.thibautperrin.tetrano.data.assets.WinGraphs
import fr.thibautperrin.tetrano.data.prefs.TetranoPreferencesDataSource
import fr.thibautperrin.tetrano.engine.nn.Tactics
import fr.thibautperrin.tetrano.engine.wingraph.UnbeatablePlayer
import fr.thibautperrin.tetrano.engine.wingraph.WinBook
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** Levels 102 and 103 with the graphs shipped in res/raw, against a random user. */
class AiServiceUnbeatableTest {

    private fun TestScope.service() = AiService(
        StandardTestDispatcher(testScheduler),
        object : NeuralNets {
            override val net get() = error("level 101 is not used in these tests")
        },
        TetranoPreferencesDataSource(
            PreferenceDataStoreFactory.create(scope = backgroundScope) { File.createTempFile("prefs", ".preferences_pb") },
        ),
        object : WinGraphs {
            override val strong by lazy { load("tetrano_win_strong.bin") }
            override val weak by lazy { load("tetrano_win_weak.bin") }
        },
    )

    private fun load(name: String): WinBook = File("src/main/res/raw/$name").inputStream().use { WinBook.load(it) }

    private suspend fun computerAlwaysWins(ai: AiService, level: Int, firstRings: Long) {
        val rng = Random(level)
        repeat(GAMES) {
            val moves = ArrayList<Byte>()
            var computer = 0L
            var user = 0L
            while (true) {
                val ring = ai.chooseRing(moves.toByteArray(), level)
                if (moves.isEmpty()) assertTrue(firstRings and (1L shl ring) != 0L)
                assertEquals(0L, (computer or user) and (1L shl ring))
                computer = computer or (1L shl ring)
                moves += ring.toByte()
                if (Tactics.wonWith(computer, ring)) break
                val free = (computer or user).inv()
                assertTrue("draw after $moves", free != 0L)
                // A user who blocks most threats, else plays at random.
                val threats = Tactics.threats(computer, user)
                var bits = if (threats != 0L && rng.nextInt(8) != 0) threats else free
                repeat(rng.nextInt(java.lang.Long.bitCount(bits))) { bits = bits and (bits - 1) }
                val reply = java.lang.Long.numberOfTrailingZeros(bits)
                user = user or (1L shl reply)
                moves += reply.toByte()
                assertTrue("the user won after $moves", !Tactics.wonWith(user, reply))
            }
        }
    }

    @Test
    fun level102WinsEveryGame() = runTest { computerAlwaysWins(service(), AiService.UNBEATABLE_STRONG_LEVEL, UnbeatablePlayer.STRONG_RINGS) }

    @Test
    fun level103WinsEveryGame() = runTest { computerAlwaysWins(service(), AiService.UNBEATABLE_WEAK_LEVEL, UnbeatablePlayer.WEAK_RINGS) }

    @Test
    fun strongAndWeakRingsSplitTheBoard() {
        assertEquals(16, java.lang.Long.bitCount(UnbeatablePlayer.STRONG_RINGS))
        assertEquals(48, java.lang.Long.bitCount(UnbeatablePlayer.WEAK_RINGS))
    }

    private companion object {
        const val GAMES = 300
    }
}
