package fr.thibautperrin.tetrano.domain.service

import fr.thibautperrin.tetrano.data.assets.NeuralNets
import fr.thibautperrin.tetrano.data.assets.WinGraphs
import fr.thibautperrin.tetrano.data.prefs.TetranoPreferencesDataSource
import fr.thibautperrin.tetrano.di.DefaultDispatcher
import fr.thibautperrin.tetrano.engine.BoardState
import fr.thibautperrin.tetrano.engine.ai.Ai
import fr.thibautperrin.tetrano.engine.ai.AiProgress
import fr.thibautperrin.tetrano.engine.ai.AiSearchPhase
import fr.thibautperrin.tetrano.engine.nn.NeuralPlayer
import fr.thibautperrin.tetrano.engine.wingraph.UnbeatablePlayer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs [Ai] searches off the main thread, on a private [BoardState]/[Ai]
 * pair built fresh for each call — mirroring the legacy `ThreadIA`'s own
 * private `Partie`, never shared with the caller's game state. Ports
 * `ThreadIA`/`HandlerIntelligence` (see docs/GLOSSARY.md, docs/PLAN.md
 * P6/§6.3).
 *
 * Level [NEURAL_LEVEL] is not the legacy AI: it is [NeuralPlayer] (the
 * trained network plus a tree search), which plays either side, testing as
 * many variations per move as [TetranoPreferencesDataSource.neuralSimulations] says.
 *
 * Levels [UNBEATABLE_STRONG_LEVEL] and [UNBEATABLE_WEAK_LEVEL] are [UnbeatablePlayer] with a
 * proven winning graph ([WinGraphs]): the computer begins, on a strong or on a weak place, and
 * wins whatever the user plays. They only exist when the computer begins: as second player, or in
 * a position the graph does not know (a resumed game the user began), they play as level
 * [NEURAL_LEVEL].
 */
@Singleton
class AiService @Inject constructor(
    @param:DefaultDispatcher private val dispatcher: CoroutineDispatcher,
    private val neuralNets: NeuralNets,
    private val preferences: TetranoPreferencesDataSource,
    private val winGraphs: WinGraphs,
) {
    private val neuralLock = Any()
    private var neuralPlayer: NeuralPlayer? = null
    private val unbeatableLock = Any()
    private val unbeatablePlayers = HashMap<Int, UnbeatablePlayer>()

    /**
     * Chooses a ring for the next player to move.
     *
     * @param movesPlayed rings played so far, in order, alternating
     *   starting with player 1.
     * @param level AI difficulty, 0..[MAX_LEVEL].
     * @param onProgress reports search progress at AI levels high enough
     *   for a single move to take a noticeable time. Called on the search's
     *   own thread (the [DefaultDispatcher]), not the caller's.
     *
     * Cancelling the calling coroutine stops the search at its next
     * checkpoint (see [AiProgress.isCancelled]) instead of leaving it to
     * run to completion in the background.
     */
    suspend fun chooseRing(
        movesPlayed: ByteArray,
        level: Int,
        onProgress: ((phase: AiSearchPhase, current: Int, total: Int) -> Unit)? = null,
    ): Int = withContext(dispatcher) {
        // Captured here, where `isActive` resolves to this withContext
        // block's own CoroutineScope receiver (a plain, non-suspend
        // property read) — isCancelled() itself can't be a suspend
        // function, since Ai calls it from deep inside a synchronous
        // recursive search.
        val scope = this
        val progress = object : AiProgress {
            override fun onProgress(phase: AiSearchPhase, current: Int, total: Int) {
                onProgress?.invoke(phase, current, total)
            }

            override fun isCancelled(): Boolean = !scope.isActive
        }
        if (isUnbeatable(level) && movesPlayed.size % 2 == 0) {
            val ring = unbeatableRing(movesPlayed, level)
            if (ring >= 0) return@withContext ring
        }
        if (level >= NEURAL_LEVEL) {
            val simulations = preferences.neuralSimulations.first()
            // NeuralPlayer is not thread-safe; only one computer move is ever searched at a time anyway.
            synchronized(neuralLock) {
                val player = neuralPlayer ?: NeuralPlayer(neuralNets.net).also { neuralPlayer = it }
                return@withContext player.chooseRing(movesPlayed, simulations, progress)
            }
        }
        val board = BoardState()
        var player = 1
        for (ring in movesPlayed) {
            board.play(ring.toInt(), player)
            player = 3 - player
        }
        Ai(board).playLevel(player, level, progress)
    }

    /** The Unbeatable player's ring (it moves first: [movesPlayed] has an even size), or -1. */
    private fun unbeatableRing(movesPlayed: ByteArray, level: Int): Int {
        var attacker = 0L
        var defender = 0L
        movesPlayed.forEachIndexed { i, ring ->
            if (i % 2 == 0) attacker = attacker or (1L shl ring.toInt()) else defender = defender or (1L shl ring.toInt())
        }
        // UnbeatablePlayer is not thread-safe.
        synchronized(unbeatableLock) {
            val player = unbeatablePlayers.getOrPut(level) {
                if (level == UNBEATABLE_STRONG_LEVEL) {
                    UnbeatablePlayer(winGraphs.strong, UnbeatablePlayer.STRONG_RINGS)
                } else {
                    UnbeatablePlayer(winGraphs.weak, UnbeatablePlayer.WEAK_RINGS)
                }
            }
            return player.chooseRing(attacker, defender)
        }
    }

    companion object {
        /** The neural-network level, one above the legacy maximum of 100. */
        const val NEURAL_LEVEL: Int = 101

        /** Unbeatable, first ring on a strong place (computer begins only). */
        const val UNBEATABLE_STRONG_LEVEL: Int = 102

        /** Unbeatable, first ring on a weak place (computer begins only). */
        const val UNBEATABLE_WEAK_LEVEL: Int = 103

        const val MAX_LEVEL: Int = UNBEATABLE_WEAK_LEVEL

        fun isUnbeatable(level: Int): Boolean = level >= UNBEATABLE_STRONG_LEVEL
    }
}
