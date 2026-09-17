package fr.thibautperrin.tetrano.domain.model

/** A snapshot of [fr.thibautperrin.tetrano.domain.service.ChallengeService], ready for the UI to render. */
data class ChallengeState(
    val level: Int,
    val rings: ByteArray,
    val usedRings: Int,
    val authorizedRings: Int,
    /** Ports `updateTextAndButton`'s visibility rule for the "give up" button. */
    val canAbandon: Boolean,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChallengeState) return false
        return level == other.level && rings.contentEquals(other.rings) &&
            usedRings == other.usedRings && authorizedRings == other.authorizedRings &&
            canAbandon == other.canAbandon
    }

    override fun hashCode(): Int {
        var result = level
        result = 31 * result + rings.contentHashCode()
        result = 31 * result + usedRings
        result = 31 * result + authorizedRings
        result = 31 * result + canAbandon.hashCode()
        return result
    }
}

/** Outcome of [fr.thibautperrin.tetrano.domain.service.ChallengeService.playUserRing]. Ports `ChallengeActivity.postRingPlayedByUser`. */
sealed interface ChallengeMoveResult {
    data object InvalidMove : ChallengeMoveResult
    data class ComputerReplied(val computerRing: Int) : ChallengeMoveResult
    data class LevelPassed(val level: Int) : ChallengeMoveResult
    data class LevelFailed(val level: Int, val computerWon: Boolean) : ChallengeMoveResult
}

/** Outcome of [fr.thibautperrin.tetrano.domain.service.ChallengeService.useStarHint]. Ports `ChallengeActivity.useAStar`/`StarUsedBean`. */
sealed interface ChallengeHintResult {
    data object NoStarsAvailable : ChallengeHintResult

    /**
     * The hint immediately completes the level: [ringForUser] should be
     * shown being played, then the level-passed flow followed. Ports
     * `isFinishedJustAfterStar`.
     */
    data class LevelWon(val ringForUser: Int, val level: Int) : ChallengeHintResult

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
