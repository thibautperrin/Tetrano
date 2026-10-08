package fr.thibautperrin.tetrano.domain.model

import fr.thibautperrin.tetrano.engine.ai.AiSearchPhase

/**
 * A snapshot of a [fr.thibautperrin.tetrano.domain.service.GameSessionService]
 * game, ready for the UI to render. Rendering the actual board (positions,
 * animations) is a Compose concern (docs/PLAN.md P7); this only carries the
 * facts.
 */
data class GameSessionState(
    /** `rings[ring]` is 0 (free), 1 or 2 (occupied by that player), for all 64 rings. */
    val rings: ByteArray,
    val nextPlayer: Int,
    val playedCount: Int,
    /** True while the computer searches for its reply (the legacy `ThreadIA.thinking`). */
    val isAiThinking: Boolean,
    val isFinished: Boolean,
    val isDraw: Boolean,
    /** 1 or 2, or null if the game isn't won. */
    val winner: Int?,
    /** The most recently played ring, or null if no move has been played yet. */
    val lastRing: Int?,
    /** The winning line if the game is won, otherwise just [lastRing], or null before any move. */
    val blinkingRings: ByteArray?,
    /**
     * Non-null once the computer's search has reported progress (only at AI
     * levels slow enough for that — `HandlerIntelligence`'s
     * `LABEL_INFO_FORK`/`LABEL_INFO_BLOCK_FORK` messages).
     */
    val aiProgress: AiProgressState?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GameSessionState) return false
        return rings.contentEquals(other.rings) &&
            nextPlayer == other.nextPlayer &&
            playedCount == other.playedCount &&
            isAiThinking == other.isAiThinking &&
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
        result = 31 * result + isAiThinking.hashCode()
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

/** How far the computer's current search has got: [current] out of [total] steps of [phase]. */
data class AiProgressState(
    val phase: AiSearchPhase,
    val current: Int,
    val total: Int,
) {
    /**
     * The legacy tray arc (`HandlerIntelligence.handleMessage`): 0..180° while
     * searching for a fork, 180..360° while looking for one to block; the
     * level-101 neural search fills the whole circle.
     */
    val trayAngleDegrees: Float
        get() {
            val fraction = if (total > 0) (current.toFloat() / total).coerceIn(0f, 1f) else 0f
            return when (phase) {
                AiSearchPhase.SEARCHING_FORK -> fraction * 180f
                AiSearchPhase.BLOCKING_FORK -> 180f + fraction * 180f
                AiSearchPhase.NEURAL_SEARCH -> fraction * 360f
            }
        }
}

/** A move taken back by [fr.thibautperrin.tetrano.domain.service.GameSessionService.undo]: [ring], and the [player] who had played it. */
data class UndoneMove(val ring: Int, val player: Int)

/** One-off happenings of a [fr.thibautperrin.tetrano.domain.service.GameSessionService] game, beyond its [GameSessionState]. */
sealed interface GameSessionEvent {
    /** The computer has just played [ring] (`GestionJeuContreOrdinateur.onOrdinateurAJoue`); the new position is already in the state. */
    data class ComputerPlayed(val ring: Int) : GameSessionEvent

    /** The engine or the AI failed (`HandlerIntelligence`'s `LABEL_EXCEPTION_THROWN`): the UI shows `Internal_Error`. */
    data object InternalError : GameSessionEvent
}
