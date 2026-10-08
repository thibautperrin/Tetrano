package fr.thibautperrin.tetrano.domain.service

import fr.thibautperrin.tetrano.data.assets.NeuralNets
import fr.thibautperrin.tetrano.di.DefaultDispatcher
import fr.thibautperrin.tetrano.engine.BoardState
import fr.thibautperrin.tetrano.engine.ai.Ai
import fr.thibautperrin.tetrano.engine.ai.AiProgress
import fr.thibautperrin.tetrano.engine.ai.AiSearchPhase
import fr.thibautperrin.tetrano.engine.nn.NeuralPlayer
import kotlinx.coroutines.CoroutineDispatcher
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
 * trained network plus a tree search), which plays either side.
 */
@Singleton
class AiService @Inject constructor(
    @param:DefaultDispatcher private val dispatcher: CoroutineDispatcher,
    private val neuralNets: NeuralNets,
) {
    private val neuralLock = Any()
    private var neuralPlayer: NeuralPlayer? = null

    /**
     * Chooses a ring for the next player to move.
     *
     * @param movesPlayed rings played so far, in order, alternating
     *   starting with player 1.
     * @param level AI difficulty, 0..[NEURAL_LEVEL].
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
        if (level >= NEURAL_LEVEL) {
            // NeuralPlayer is not thread-safe; only one computer move is ever searched at a time anyway.
            synchronized(neuralLock) {
                val player = neuralPlayer ?: NeuralPlayer(neuralNets.net).also { neuralPlayer = it }
                return@withContext player.chooseRing(movesPlayed, NEURAL_THINKING_MILLIS, progress)
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

    companion object {
        /** The neural-network level, one above the legacy maximum of 100. */
        const val NEURAL_LEVEL: Int = 101

        /** Thinking time per move at [NEURAL_LEVEL]. */
        const val NEURAL_THINKING_MILLIS: Long = 2_000
    }
}
