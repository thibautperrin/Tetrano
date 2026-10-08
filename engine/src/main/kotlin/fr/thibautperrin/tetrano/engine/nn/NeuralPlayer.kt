package fr.thibautperrin.tetrano.engine.nn

import fr.thibautperrin.tetrano.engine.ai.AiProgress
import fr.thibautperrin.tetrano.engine.ai.AiSearchPhase

/** Bitboards of a game given as its rings in play order (the first ring is the first player's). */
class Position(val mine: Long, val theirs: Long) {
    companion object {
        /** The position after [moves], from the point of view of the player to move. */
        fun after(moves: ByteArray, count: Int = moves.size): Position {
            var first = 0L
            var second = 0L
            for (i in 0 until count) {
                if (i % 2 == 0) first = first or (1L shl moves[i].toInt()) else second = second or (1L shl moves[i].toInt())
            }
            return if (count % 2 == 0) Position(first, second) else Position(second, first)
        }
    }
}

/**
 * The level-101 player: [GumbelMcts] guided by [QubicNet], deterministic,
 * with a thinking-time budget. Plays either side.
 *
 * The simulation count is derived from the budget and a running estimate
 * of the time one simulation takes on this device, so sequential halving
 * gets to finish its phases; a hard deadline at twice the budget is a
 * safety net. Not thread-safe.
 */
class NeuralPlayer(
    net: QubicNet,
    private val maxSimulations: Int = 3000,
    private val minSimulations: Int = 32,
) {
    private val mcts = GumbelMcts(net, GumbelMcts.Config(gumbelNoise = false))
    private var nanosPerSimulation = 0.0

    init {
        // Calibrate on a quiet early position.
        val start = System.nanoTime()
        val r = mcts.search(1L, 1L shl 63, 24)
        nanosPerSimulation = (System.nanoTime() - start).toDouble() / maxOf(1, r.simulations)
    }

    /** The full search result for the player to move after [movesPlayed]. */
    fun search(movesPlayed: ByteArray, timeBudgetMillis: Long, progress: AiProgress? = null): GumbelMcts.Result {
        val position = Position.after(movesPlayed)
        val budgetNanos = timeBudgetMillis * 1_000_000
        val simulations = (budgetNanos / nanosPerSimulation).toInt().coerceIn(minSimulations, maxSimulations)
        val start = System.nanoTime()
        val result = mcts.search(
            position.mine,
            position.theirs,
            simulations,
            deadlineNanos = start + 2 * budgetNanos,
        ) { done, total ->
            progress?.onProgress(AiSearchPhase.NEURAL_SEARCH, done, total)
            progress?.isCancelled() != true
        }
        if (result.simulations >= 16) {
            val measured = (System.nanoTime() - start).toDouble() / result.simulations
            nanosPerSimulation = 0.7 * nanosPerSimulation + 0.3 * measured
        }
        return result
    }

    /** The ring to play after [movesPlayed]. */
    fun chooseRing(movesPlayed: ByteArray, timeBudgetMillis: Long, progress: AiProgress? = null): Int =
        search(movesPlayed, timeBudgetMillis, progress).move
}
