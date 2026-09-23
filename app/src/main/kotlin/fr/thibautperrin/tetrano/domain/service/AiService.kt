package fr.thibautperrin.tetrano.domain.service

import fr.thibautperrin.tetrano.di.DefaultDispatcher
import fr.thibautperrin.tetrano.engine.BoardState
import fr.thibautperrin.tetrano.engine.ai.Ai
import fr.thibautperrin.tetrano.engine.ai.AiProgress
import fr.thibautperrin.tetrano.engine.ai.AiSearchPhase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Runs [Ai] searches off the main thread, on a private [BoardState]/[Ai]
 * pair built fresh for each call — mirroring the legacy `ThreadIA`'s own
 * private `Partie`, never shared with the caller's game state. Ports
 * `ThreadIA`/`HandlerIntelligence` (see docs/GLOSSARY.md, docs/PLAN.md
 * P6/§6.3).
 */
class AiService @Inject constructor(
    @param:DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) {
    /**
     * Chooses a ring for the next player to move.
     *
     * @param movesPlayed rings played so far, in order, alternating
     *   starting with player 1.
     * @param level AI difficulty, 0..100.
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
        val board = BoardState()
        var player = 1
        for (ring in movesPlayed) {
            board.play(ring.toInt(), player)
            player = 3 - player
        }
        val ai = Ai(board)
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
        ai.playLevel(player, level, progress)
    }
}
