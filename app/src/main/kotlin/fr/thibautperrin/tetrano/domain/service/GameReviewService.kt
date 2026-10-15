package fr.thibautperrin.tetrano.domain.service

import fr.thibautperrin.tetrano.data.assets.NeuralNets
import fr.thibautperrin.tetrano.di.DefaultDispatcher
import fr.thibautperrin.tetrano.engine.nn.GameReview
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Analyses a game with the level-101 network: see [GameReview]. Each
 * [session] evaluates the positions of a game quickly, then judges its
 * moves, possibly again later with deeper evaluations of some positions.
 */
class GameReviewService @Inject constructor(
    @param:DefaultDispatcher private val dispatcher: CoroutineDispatcher,
    private val neuralNets: NeuralNets,
) {
    fun session(): Session = Session()

    /** One game's review. Calls are serialized ([GameReview] is not thread-safe). */
    inner class Session {
        private val mutex = Mutex()
        private var review: GameReview? = null

        private fun review(): GameReview =
            review ?: GameReview(neuralNets.net, SIMULATIONS_PER_POSITION).also { review = it }

        /**
         * Evaluates every position of [moves] in order, emitting each as soon as it is done;
         * cancelling the collector stops after the position being searched.
         */
        fun evaluateAll(moves: ByteArray): Flow<Pair<Int, GameReview.Evaluation>> = flow {
            for (i in 0..moves.size) {
                emit(i to mutex.withLock { review().evaluate(moves, i) })
            }
        }.flowOn(dispatcher)

        /** The verdict on move [ply] of [moves], given the evaluations of the positions around it. */
        suspend fun judge(
            moves: ByteArray,
            ply: Int,
            before: GameReview.Evaluation,
            after: GameReview.Evaluation,
        ): GameReview.Judgement = withContext(dispatcher) {
            mutex.withLock { review().judge(moves, ply, before, after) }
        }
    }

    private companion object {
        /** ~0.2-0.4 s per position on a phone. */
        const val SIMULATIONS_PER_POSITION = 64
    }
}
