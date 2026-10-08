package fr.thibautperrin.tetrano.engine.nn

/**
 * Post-game analysis: for every position of a game, the network's
 * win/draw/loss estimate refined by a short search, the best move, and a
 * verdict on the move actually played.
 *
 * Verdicts compare the expected score (win 1, draw ½, loss 0) the mover
 * could get (the search value before the move) with what the move left
 * (minus the search value of the next position, for the opponent).
 * Two verdicts are exact rather than estimated, from the tactics solver:
 * [Verdict.MISSED_WIN] (a forced win existed and was not kept) and
 * [Verdict.ALLOWED_WIN] (the move gave the opponent a forced win).
 */
class GameReview(net: QubicNet, private val simulations: Int = 96) {

    enum class Verdict { BEST, GOOD, INACCURACY, MISTAKE, BLUNDER, MISSED_WIN, ALLOWED_WIN }

    /** Analysis of one position (before move [ply]) and of the move played there, if any. */
    class PositionReview(
        val ply: Int,
        /** Win, draw, loss probabilities for the player to move (exact one-hot when proven). */
        val wdl: FloatArray,
        /** Search value for the player to move, in [-1, 1]. */
        val value: Float,
        val status: GumbelMcts.Status,
        val bestRing: Int,
        /** The ring played in this position, or -1 for the final position. */
        val playedRing: Int,
        /** Expected score lost by the played move (0..1), or 0 for the final position. */
        val expectedScoreLoss: Float,
        val verdict: Verdict?,
    )

    private val mcts = GumbelMcts(net, GumbelMcts.Config(gumbelNoise = false))

    /**
     * Reviews [moves] (rings in play order). [onPosition] receives each
     * position's search as soon as it is done (in order) and may return
     * false to stop; the verdicts are only final in the returned list.
     */
    fun review(
        moves: ByteArray,
        onPosition: ((index: Int, total: Int) -> Boolean)? = null,
    ): List<PositionReview> {
        val count = moves.size
        val results = ArrayList<GumbelMcts.Result?>(count + 1)
        for (i in 0..count) {
            val position = Position.after(moves, i)
            val finished = i > 0 && Tactics.wonWith(position.theirs, moves[i - 1].toInt())
            val full = (position.mine or position.theirs) == -1L
            results += if (finished || full) null else mcts.search(position.mine, position.theirs, simulations)
            if (onPosition != null && !onPosition(i, count + 1)) break
        }
        return List(results.size) { i ->
            val r = results[i]
            val lastFinished = r == null && i > 0
            val wdl: FloatArray
            val value: Float
            val status: GumbelMcts.Status
            if (r == null) {
                // Game over: the player to move has lost (line completed) or drawn (full board).
                val lost = i > 0 && Tactics.wonWith(Position.after(moves, i).theirs, moves[i - 1].toInt())
                status = if (lost) GumbelMcts.Status.LOSS else GumbelMcts.Status.DRAW
                value = GumbelMcts.exactValue(status)
                wdl = if (lost) floatArrayOf(0f, 0f, 1f) else floatArrayOf(0f, 1f, 0f)
            } else {
                status = r.status
                value = r.value
                wdl = r.netWdl
            }
            val played = if (i < count) moves[i].toInt() else -1
            var loss = 0f
            var verdict: Verdict? = null
            if (r != null && played >= 0 && i + 1 < results.size) {
                val next = results[i + 1]
                val nextStatus = next?.status ?: if (Tactics.wonWith(Position.after(moves, i + 1).theirs, played)) {
                    GumbelMcts.Status.LOSS
                } else {
                    GumbelMcts.Status.DRAW
                }
                val nextValue = next?.value ?: GumbelMcts.exactValue(nextStatus)
                val before = (value + 1f) / 2f
                val after = (1f - nextValue) / 2f
                loss = (before - after).coerceAtLeast(0f)
                verdict = when {
                    r.status == GumbelMcts.Status.WIN && nextStatus != GumbelMcts.Status.LOSS -> Verdict.MISSED_WIN
                    r.status != GumbelMcts.Status.LOSS && nextStatus == GumbelMcts.Status.WIN -> Verdict.ALLOWED_WIN
                    played == r.move -> Verdict.BEST
                    loss >= BLUNDER -> Verdict.BLUNDER
                    loss >= MISTAKE -> Verdict.MISTAKE
                    loss >= INACCURACY -> Verdict.INACCURACY
                    else -> Verdict.GOOD
                }
            }
            if (lastFinished) verdict = null
            PositionReview(i, wdl, value, status, r?.move ?: -1, played, loss, verdict)
        }
    }

    companion object {
        const val INACCURACY = 0.08f
        const val MISTAKE = 0.18f
        const val BLUNDER = 0.35f
    }
}
