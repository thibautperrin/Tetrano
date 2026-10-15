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
 *
 * The evaluation of each position ([Evaluation]) and the verdict on a move
 * ([judge]) are separate, so that a deeper evaluation of a position (the
 * analysis screen keeps searching the position on display) can refine the
 * verdicts of the moves leading to and from it.
 */
class GameReview(net: QubicNet, private val simulations: Int = 96) {

    enum class Verdict { BEST, GOOD, INACCURACY, MISTAKE, BLUNDER, MISSED_WIN, ALLOWED_WIN }

    /** What a search (or the end of the game) says about one position, for the player to move. */
    class Evaluation(
        /** Win, draw, loss probabilities for the player to move (exact one-hot when proven). */
        val wdl: FloatArray,
        /** Search value for the player to move, in [-1, 1]. */
        val value: Float,
        val status: GumbelMcts.Status,
        /** The best move, or -1 when the game is over. */
        val bestRing: Int,
        /** Simulations behind this evaluation (0 for a finished game or a proven position). */
        val simulations: Int,
    ) {
        companion object {
            fun of(result: GumbelMcts.Result) =
                Evaluation(result.netWdl, result.value, result.status, result.move, result.simulations)
        }
    }

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

    /** A verdict on a move and the expected score it lost (0..1). */
    class Judgement(val expectedScoreLoss: Float, val verdict: Verdict)

    private val mcts = GumbelMcts(net, GumbelMcts.Config(gumbelNoise = false, movesLeftWeight = 0f))

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
        val evaluations = ArrayList<Evaluation>(count + 1)
        for (i in 0..count) {
            evaluations += evaluate(moves, i)
            if (onPosition != null && !onPosition(i, count + 1)) break
        }
        return List(evaluations.size) { i ->
            val e = evaluations[i]
            val played = if (i < count) moves[i].toInt() else -1
            val judgement = if (played >= 0 && i + 1 < evaluations.size) judge(moves, i, e, evaluations[i + 1]) else null
            PositionReview(
                i, e.wdl, e.value, e.status, e.bestRing, played,
                judgement?.expectedScoreLoss ?: 0f, judgement?.verdict,
            )
        }
    }

    /** The position after the first [count] of [moves], searched with this review's simulations. */
    fun evaluate(moves: ByteArray, count: Int): Evaluation {
        finalEvaluation(moves, count)?.let { return it }
        val position = Position.after(moves, count)
        return Evaluation.of(mcts.search(position.mine, position.theirs, simulations))
    }

    /**
     * Verdict on move [ply] of [moves], from the evaluations of the positions before ([before])
     * and after it ([after]).
     */
    fun judge(moves: ByteArray, ply: Int, before: Evaluation, after: Evaluation): Judgement {
        val played = moves[ply].toInt()
        val beforeScore = (before.value + 1f) / 2f
        val afterScore = (1f - after.value) / 2f
        val loss = (beforeScore - afterScore).coerceAtLeast(0f)
        val verdict = when {
            before.status == GumbelMcts.Status.WIN && after.status != GumbelMcts.Status.LOSS -> Verdict.MISSED_WIN
            before.status != GumbelMcts.Status.LOSS && after.status == GumbelMcts.Status.WIN &&
                hasSafeMove(Position.after(moves, ply)) -> Verdict.ALLOWED_WIN
            played == before.bestRing -> Verdict.BEST
            loss >= BLUNDER -> Verdict.BLUNDER
            loss >= MISTAKE -> Verdict.MISTAKE
            loss >= INACCURACY -> Verdict.INACCURACY
            else -> Verdict.GOOD
        }
        return Judgement(loss, verdict)
    }

    private val vcf = Vcf(16)
    private val safeMoves = HashMap<Position, Boolean>()

    /**
     * True iff the player to move has a move after which the opponent has no immediate win
     * and no VCF. Otherwise every move concedes a forced win: the position was already lost
     * (the mistake came earlier) and the move played is not to blame.
     */
    private fun hasSafeMove(position: Position): Boolean = safeMoves.getOrPut(position) {
        val mine = position.mine
        val theirs = position.theirs
        val block = Tactics.threats(theirs, mine)
        var candidates = if (block != 0L) block else (mine or theirs).inv()
        while (candidates != 0L) {
            val cell = java.lang.Long.numberOfTrailingZeros(candidates)
            candidates = candidates and (candidates - 1)
            val after = mine or (1L shl cell)
            if (Tactics.wonWith(after, cell)) return@getOrPut true
            if (vcf.solve(theirs, after, SAFE_MOVE_VCF_BUDGET) == Vcf.NONE) return@getOrPut true
        }
        false
    }

    companion object {
        private const val SAFE_MOVE_VCF_BUDGET = 20_000
        const val INACCURACY = 0.08f
        const val MISTAKE = 0.18f
        const val BLUNDER = 0.35f

        /**
         * The exact evaluation of the position after the first [count] of [moves] if the game
         * is over there (the last move completed a line, or the board is full), else null.
         */
        fun finalEvaluation(moves: ByteArray, count: Int): Evaluation? {
            val position = Position.after(moves, count)
            val lost = count > 0 && Tactics.wonWith(position.theirs, moves[count - 1].toInt())
            val full = (position.mine or position.theirs) == -1L
            if (!lost && !full) return null
            // Game over: the player to move has lost (line completed) or drawn (full board).
            val status = if (lost) GumbelMcts.Status.LOSS else GumbelMcts.Status.DRAW
            val wdl = if (lost) floatArrayOf(0f, 0f, 1f) else floatArrayOf(0f, 1f, 0f)
            return Evaluation(wdl, GumbelMcts.exactValue(status), status, -1, 0)
        }
    }
}
