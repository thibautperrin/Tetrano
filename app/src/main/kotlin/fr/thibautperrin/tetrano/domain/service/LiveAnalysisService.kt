package fr.thibautperrin.tetrano.domain.service

import fr.thibautperrin.tetrano.data.assets.NeuralNets
import fr.thibautperrin.tetrano.di.DefaultDispatcher
import fr.thibautperrin.tetrano.engine.nn.GameReview
import fr.thibautperrin.tetrano.engine.nn.GumbelMcts
import fr.thibautperrin.tetrano.engine.nn.Position
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import javax.inject.Inject

/**
 * The analysis screen's search: the level-101 network keeps searching the
 * position on display, the same tree growing step by step, and reports its
 * refined estimate and recommended move after each step, until
 * `maxSimulations` variations have been tested.
 */
class LiveAnalysisService @Inject constructor(
    @param:DefaultDispatcher private val dispatcher: CoroutineDispatcher,
    private val neuralNets: NeuralNets,
) {
    /** What the search says so far about the position after [moves]. */
    class Snapshot(
        /** Win, draw, loss estimate for the player to move, consistent with [value]. */
        val wdl: FloatArray,
        val evaluation: GameReview.Evaluation,
        /** Variations tested so far (simulations through the root). */
        val simulations: Int,
        /** Whether the search has stopped (budget reached, or the position is proven or over). */
        val finished: Boolean,
    )

    /**
     * Searches the position after [moves] up to [maxSimulations] simulations; a cold flow
     * running on the default dispatcher, stopped by cancelling its collector.
     */
    fun analyse(moves: ByteArray, maxSimulations: Int): Flow<Snapshot> = flow {
        GameReview.finalEvaluation(moves, moves.size)?.let {
            emit(Snapshot(it.wdl, it, 0, finished = true))
            return@flow
        }
        val mcts = GumbelMcts(neuralNets.net, GumbelMcts.Config(gumbelNoise = false, movesLeftWeight = 0f))
        val position = Position.after(moves)
        val root = mcts.newRoot(position.mine, position.theirs)
        val context = currentCoroutineContext()
        var target = FIRST_STEP
        while (true) {
            val before = root.simulations
            // Cancelling the collector stops the search at the next simulation.
            val result = mcts.search(root, (target - before).coerceAtLeast(1)) { _, _ -> context.isActive }
            val coroutineActive = context.isActive
            val done = root.simulations
            val finished = result.status != GumbelMcts.Status.UNKNOWN || done >= maxSimulations ||
                done == before || !coroutineActive
            val evaluation = GameReview.Evaluation(result.netWdl, result.value, result.status, result.move, done)
            emit(Snapshot(searchWdl(result), evaluation, done, finished))
            if (finished) return@flow
            target = minOf(maxSimulations, done + done.coerceIn(FIRST_STEP, MAX_STEP))
        }
    }.flowOn(dispatcher)

    /**
     * A win/draw/loss split whose difference is the search value: the network's draw share
     * is kept, and win and loss move apart (exact one-hot when proven).
     */
    private fun searchWdl(result: GumbelMcts.Result): FloatArray {
        if (result.status != GumbelMcts.Status.UNKNOWN) return result.netWdl
        val v = result.value
        var draw = result.netWdl[1].coerceIn(0f, 1f)
        var win = (1f - draw + v) / 2f
        var loss = (1f - draw - v) / 2f
        if (win < 0f) {
            win = 0f
            loss = -v
            draw = 1f + v
        } else if (loss < 0f) {
            loss = 0f
            win = v
            draw = 1f - v
        }
        return floatArrayOf(win, draw, loss)
    }

    companion object {
        private const val FIRST_STEP = 64

        /** Never more than this many simulations between two reports. */
        private const val MAX_STEP = 1_000

        /** The choices of the analysis depth (variations to test). */
        val DEPTH_CHOICES = listOf(1_000, 2_000, 5_000, 10_000, 20_000)
    }
}
