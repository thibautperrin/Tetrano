package fr.thibautperrin.tetrano.domain.service

import fr.thibautperrin.tetrano.data.assets.NeuralNets
import fr.thibautperrin.tetrano.data.assets.WinGraphs
import fr.thibautperrin.tetrano.di.DefaultDispatcher
import fr.thibautperrin.tetrano.engine.nn.GameReview
import fr.thibautperrin.tetrano.engine.wingraph.UnbeatablePlayer
import fr.thibautperrin.tetrano.engine.wingraph.WinGraphOracle
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
 *
 * Every position is first solved exactly within a small budget, and the positions on a proven
 * winning graph's walk are exact for free ([WinGraphOracle]); the others get a search whose
 * length follows its hesitation.
 */
class GameReviewService @Inject constructor(
    @param:DefaultDispatcher private val dispatcher: CoroutineDispatcher,
    private val neuralNets: NeuralNets,
    private val winGraphs: WinGraphs,
) {
    fun session(): Session = Session()

    /** One game's review. Calls are serialized ([GameReview] is not thread-safe). */
    inner class Session {
        private val mutex = Mutex()
        private var review: GameReview? = null

        private fun review(): GameReview = review ?: GameReview(
            neuralNets.net,
            simulations = MAX_SIMULATIONS_PER_POSITION,
            adaptive = true,
            solveBudget = SOLVE_BUDGET,
            oracle = WinGraphOracle(
                listOf(winGraphs.strong to UnbeatablePlayer.STRONG_RINGS, winGraphs.weak to UnbeatablePlayer.WEAK_RINGS),
            ),
        ).also { review = it }

        /**
         * Evaluates every position of [moves], from the last to the first (the proofs found late
         * in the game help the earlier positions), emitting each as soon as it is done;
         * cancelling the collector stops after the position being searched.
         */
        fun evaluateAll(moves: ByteArray): Flow<Pair<Int, GameReview.Evaluation>> = flow {
            for (i in moves.size downTo 0) {
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
        /** 64 simulations, doubled while the search hesitates, up to this. */
        const val MAX_SIMULATIONS_PER_POSITION = 256

        /** Exact solver evaluations per position (~0.2 s on a PC, a few times more on a phone at worst). */
        const val SOLVE_BUDGET = 20_000L
    }
}
