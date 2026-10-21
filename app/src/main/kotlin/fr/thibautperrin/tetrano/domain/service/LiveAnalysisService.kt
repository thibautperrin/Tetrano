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
        /** Every legal move, as the search sees it so far. */
        val hints: List<MoveHint> = emptyList(),
    )

    /**
     * A legal move: [rank] 1 for the recommended move, then by the search's preference (moves
     * equivalent by a symmetry of the position share their rank and values), null if the search
     * did not look at it; [share]: how much the search recommends it (its improved policy),
     * relative to the recommended move (0..1); [expectedScore] of the player to move after it (win 1, draw ½, loss 0), if known.
     */
    class MoveHint(val ring: Int, val rank: Int?, val share: Float, val expectedScore: Float?)

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
            emit(Snapshot(searchWdl(result), evaluation, done, finished, hints(result)))
            if (finished) return@flow
            target = minOf(maxSimulations, done + done.coerceIn(FIRST_STEP, MAX_STEP))
        }
    }.flowOn(dispatcher)

    /** [MoveHint]s of [result]: its classes of equivalent moves ranked, the chosen one first. */
    private fun hints(result: GumbelMcts.Result): List<MoveHint> {
        if (result.move < 0) return emptyList()
        if (result.status != GumbelMcts.Status.UNKNOWN && result.visits.all { it == 0 }) {
            // Proven before any search (a win by tactics, a forced reply): only the move found.
            val score = (GumbelMcts.exactValue(result.status) + 1f) / 2f
            return listOf(MoveHint(result.move, 1, 1f, score))
        }
        val classes = (0 until 64).filter { result.moveClass[it] >= 0 }.groupBy { result.moveClass[it] }
        val chosen = result.moveClass[result.move]
        val ranked = classes.keys.filter { it == chosen || result.visits[classes.getValue(it).first()] > 0 }
            .sortedWith(
                compareByDescending<Int> { it == chosen }
                    .thenByDescending { result.visits[classes.getValue(it).first()] }
                    .thenByDescending { result.moveValues[classes.getValue(it).first()] },
            )
        val rank = ranked.withIndex().associate { (i, c) -> c to i + 1 }
        // A class's policy is spread over its cells: compare whole classes.
        val mass = classes.mapValues { (_, cells) -> cells.sumOf { result.policy[it].toDouble() }.toFloat() }
        val maxMass = mass.values.max().coerceAtLeast(1e-6f)
        return classes.flatMap { (c, cells) ->
            val q = result.moveValues[cells.first()]
            val known = rank[c] != null && !q.isNaN()
            val share = if (c == chosen) 1f else (mass.getValue(c) / maxMass).coerceIn(0f, 1f)
            cells.map { MoveHint(it, rank[c], share, if (known) (q + 1f) / 2f else null) }
        }
    }

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
