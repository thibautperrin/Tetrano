package fr.thibautperrin.tetrano.domain.model

/** A snapshot of [fr.thibautperrin.tetrano.domain.service.ChallengeService], ready for the UI to render. */
data class ChallengeState(
    val level: Int,
    val rings: ByteArray,
    val usedRings: Int,
    val authorizedRings: Int,
    /**
     * Ports `updateTextAndButton`'s visibility rule for the "give up"
     * button (at least one ring of the user's on the board and budget left),
     * plus: never once the attempt is over (the legacy button stayed
     * clickable while the computer's winning ring flew in).
     */
    val canAbandon: Boolean,
    /** The attempt has ended (won, lost or abandoned); only a reload starts the next one. */
    val isOver: Boolean,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChallengeState) return false
        return level == other.level && rings.contentEquals(other.rings) &&
            usedRings == other.usedRings && authorizedRings == other.authorizedRings &&
            canAbandon == other.canAbandon && isOver == other.isOver
    }

    override fun hashCode(): Int {
        var result = level
        result = 31 * result + rings.contentHashCode()
        result = 31 * result + usedRings
        result = 31 * result + authorizedRings
        result = 31 * result + canAbandon.hashCode()
        result = 31 * result + isOver.hashCode()
        return result
    }
}

/**
 * Outcome of [fr.thibautperrin.tetrano.domain.service.ChallengeService.playUserRing]
 * (and, for [LevelFailed], of `abandon`). Ports `ChallengeActivity.postRingPlayedByUser`.
 *
 * `allModesTriedAward` is true when finishing this attempt also earned the
 * "every main mode tried" star ([StarAward.AllModesTried]).
 */
sealed interface ChallengeMoveResult {
    /** The ring is occupied, out of range, or the attempt is already over (legacy `userTriedPlaying` false). */
    data object InvalidMove : ChallengeMoveResult

    data class ComputerReplied(val computerRing: Int) : ChallengeMoveResult

    /** The user's own ring completed a line. Legacy `SITUATION_USER_WINS`. */
    data class LevelPassed(val level: Int, val allModesTriedAward: Boolean = false) : ChallengeMoveResult

    /**
     * The attempt is lost. [computerRing] is the computer's reply that ended
     * it (legacy `SITUATION_COMPUTER_WINS`), or null when the user's ring
     * exhausted the budget (`SITUATION_RINGS_NUMBER_PASSED`) or the user gave
     * up (`SITUATION_ABANDONED`).
     */
    data class LevelFailed(
        val level: Int,
        val computerRing: Int? = null,
        val allModesTriedAward: Boolean = false,
    ) : ChallengeMoveResult {
        val computerWon: Boolean get() = computerRing != null
    }
}

/** Outcome of [fr.thibautperrin.tetrano.domain.service.ChallengeService.useStarHint]. Ports `ChallengeActivity.useAStar`/`StarUsedBean`. */
sealed interface ChallengeHintResult {
    data object NoStarsAvailable : ChallengeHintResult

    /** The attempt is already over: no star was spent. */
    data object LevelOver : ChallengeHintResult

    /**
     * The hint immediately completes the level: [ringForUser] should be
     * shown being played, then the level-passed flow followed. Ports
     * `isFinishedJustAfterStar`. [level] is the completed level (the legacy
     * dialog showed the next one).
     */
    data class LevelWon(
        val ringForUser: Int,
        val level: Int,
        val allModesTriedAward: Boolean = false,
    ) : ChallengeHintResult

    /**
     * The hint plays a forced pair: [ringForUser] then [ringForComputer].
     * [boardBeforeHint] is the board right after undoing this attempt's
     * normal moves (to redraw from, before animating the hint pair onto
     * it), or null if there was nothing to undo. Ports `needRestart`
     * /`getMatrix12`.
     */
    data class Hint(
        val ringForUser: Int,
        val ringForComputer: Int,
        val boardBeforeHint: ByteArray?,
    ) : ChallengeHintResult {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Hint) return false
            return ringForUser == other.ringForUser && ringForComputer == other.ringForComputer &&
                boardBeforeHint.contentEqualsOrBothNull(other.boardBeforeHint)
        }

        override fun hashCode(): Int {
            var result = ringForUser
            result = 31 * result + ringForComputer
            result = 31 * result + (boardBeforeHint?.contentHashCode() ?: 0)
            return result
        }
    }
}

private fun ByteArray?.contentEqualsOrBothNull(other: ByteArray?): Boolean =
    if (this == null || other == null) this == null && other == null else contentEquals(other)
