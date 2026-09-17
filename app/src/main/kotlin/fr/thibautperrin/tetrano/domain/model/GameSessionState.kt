package fr.thibautperrin.tetrano.domain.model

import fr.thibautperrin.tetrano.engine.ai.AiSearchPhase

/**
 * A snapshot of a [fr.thibautperrin.tetrano.domain.service.GameSessionService]
 * game, ready for the UI to render — no engine types leak past this
 * boundary. Rendering the actual board (positions, animations) is a
 * Compose concern (docs/PLAN.md P7); this only carries the facts.
 */
data class GameSessionState(
    /** `rings[ring]` is 0 (free), 1 or 2 (occupied by that player), for all 64 rings. */
    val rings: ByteArray,
    val nextPlayer: Int,
    val playedCount: Int,
    /** True while the AI is thinking, or a caller-imposed animation lock (see `setBusy`) is held. */
    val isBusy: Boolean,
    val isFinished: Boolean,
    val isDraw: Boolean,
    /** 1 or 2, or null if the game isn't won. */
    val winner: Int?,
    /** The most recently played ring, or null if no move has been played yet. */
    val lastRing: Int?,
    /** The winning line if the game is won, otherwise just [lastRing], or null before any move. */
    val blinkingRings: ByteArray?,
    /** Non-null while the AI is searching at a level where that's slow enough to show progress. */
    val aiProgress: AiProgressState?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GameSessionState) return false
        return rings.contentEquals(other.rings) &&
            nextPlayer == other.nextPlayer &&
            playedCount == other.playedCount &&
            isBusy == other.isBusy &&
            isFinished == other.isFinished &&
            isDraw == other.isDraw &&
            winner == other.winner &&
            lastRing == other.lastRing &&
            blinkingRings.contentEqualsOrBothNull(other.blinkingRings) &&
            aiProgress == other.aiProgress
    }

    override fun hashCode(): Int {
        var result = rings.contentHashCode()
        result = 31 * result + nextPlayer
        result = 31 * result + playedCount
        result = 31 * result + isBusy.hashCode()
        result = 31 * result + isFinished.hashCode()
        result = 31 * result + isDraw.hashCode()
        result = 31 * result + (winner ?: 0)
        result = 31 * result + (lastRing ?: -1)
        result = 31 * result + (blinkingRings?.contentHashCode() ?: 0)
        result = 31 * result + (aiProgress?.hashCode() ?: 0)
        return result
    }

    private fun ByteArray?.contentEqualsOrBothNull(other: ByteArray?): Boolean =
        if (this == null || other == null) this == null && other == null else contentEquals(other)
}

data class AiProgressState(
    val phase: AiSearchPhase,
    val current: Int,
    val total: Int,
)
