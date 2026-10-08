package fr.thibautperrin.tetrano.domain.service

import fr.thibautperrin.tetrano.data.assets.NeuralNets
import fr.thibautperrin.tetrano.di.DefaultDispatcher
import fr.thibautperrin.tetrano.engine.nn.GameReview
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Analyses a finished (or partial) game with the level-101 network: see
 * [GameReview]. Runs on the default dispatcher; cancelling the caller stops
 * it after the position being searched.
 */
class GameReviewService @Inject constructor(
    @param:DefaultDispatcher private val dispatcher: CoroutineDispatcher,
    private val neuralNets: NeuralNets,
) {
    /**
     * @param moves rings in play order.
     * @param onProgress (positions done, total positions), on the search thread.
     */
    suspend fun review(
        moves: ByteArray,
        onProgress: (done: Int, total: Int) -> Unit,
    ): List<GameReview.PositionReview> = withContext(dispatcher) {
        val scope = this
        GameReview(neuralNets.net, SIMULATIONS_PER_POSITION).review(moves) { index, total ->
            onProgress(index + 1, total)
            scope.isActive
        }
    }

    private companion object {
        /** ~0.2-0.4 s per position on a phone. */
        const val SIMULATIONS_PER_POSITION = 64
    }
}
