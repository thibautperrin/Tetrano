package fr.thibautperrin.tetrano.engine.nn

import fr.thibautperrin.tetrano.engine.ai.AiProgress
import fr.thibautperrin.tetrano.engine.ai.AiSearchPhase

/** Bitboards of a game given as its rings in play order (the first ring is the first player's). */
data class Position(val mine: Long, val theirs: Long) {
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
 * The level-101 player: [GumbelMcts] guided by [QubicNet], searching a
 * fixed number of simulations per move (the "variations tested", a user
 * setting). Plays either side. Deterministic except for its first moves,
 * sampled (Gumbel noise) among the best ones so that openings vary from
 * game to game. Not thread-safe.
 */
class NeuralPlayer(
    net: QubicNet,
    /** Moves played with Gumbel noise at the start of a game, so the openings vary. */
    private val variedOpeningPlies: Int = 4,
) {
    private val mcts = GumbelMcts(net, GumbelMcts.Config(gumbelNoise = false))

    /** The full search result for the player to move after [movesPlayed], with [simulations] simulations. */
    fun search(movesPlayed: ByteArray, simulations: Int, progress: AiProgress? = null): GumbelMcts.Result {
        val position = Position.after(movesPlayed)
        return mcts.search(
            position.mine,
            position.theirs,
            simulations.coerceAtLeast(1),
            noise = movesPlayed.size < variedOpeningPlies,
        ) { done, total ->
            progress?.onProgress(AiSearchPhase.NEURAL_SEARCH, done, total)
            progress?.isCancelled() != true
        }
    }

    /** The ring to play after [movesPlayed]. */
    fun chooseRing(movesPlayed: ByteArray, simulations: Int, progress: AiProgress? = null): Int =
        search(movesPlayed, simulations, progress).move
}
