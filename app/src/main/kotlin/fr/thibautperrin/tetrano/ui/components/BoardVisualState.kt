package fr.thibautperrin.tetrano.ui.components

import androidx.compose.ui.graphics.Color

/** Which edge of the board the 4-ring tray sits along. Ports `orientationVerticale`. */
enum class BoardOrientation { PORTRAIT, LANDSCAPE }

/**
 * A ring animating in a straight line from the tray to its resting cell
 * (an opponent's move, or the outcome of a drag). Ports the
 * `anneauDeplacementAdversaire`/`fractionDeplacementAdversaire` fields of
 * the legacy `ThreadSurfaceGraphView`.
 */
data class MovingRing(val ringNumber: Int, val player: Int, val progress: Float)

/**
 * Everything [BoardCanvas] needs to render one frame — no engine or
 * domain types leak into this UI-facing model. Animation (blinking,
 * [MovingRing]'s progress, [trayAngleDegrees] during an AI search) is
 * driven by the caller (a ViewModel/screen, see docs/PLAN.md P8) updating
 * this state over time; [BoardCanvas] itself never animates on its own.
 */
data class BoardVisualState(
    /** `rings[ring]` is 0 (free), 1 or 2 (occupied by that player), for all 64 rings. */
    val rings: ByteArray,
    val player1Color: Color,
    val player2Color: Color,
    /** Rings not drawn this frame — the "off" half of a blink cycle. */
    val hiddenRings: Set<Int> = emptySet(),
    /** 0 = no tray shown and no drag accepted; 1 or 2 = that player may drag from the tray. */
    val trayPlayer: Int = 0,
    /** 0..360: how much of each tray ring is drawn — an AI-search progress indicator. 360 = whole ring. */
    val trayAngleDegrees: Float = 360f,
    val movingRing: MovingRing? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BoardVisualState) return false
        return rings.contentEquals(other.rings) &&
            player1Color == other.player1Color &&
            player2Color == other.player2Color &&
            hiddenRings == other.hiddenRings &&
            trayPlayer == other.trayPlayer &&
            trayAngleDegrees == other.trayAngleDegrees &&
            movingRing == other.movingRing
    }

    override fun hashCode(): Int {
        var result = rings.contentHashCode()
        result = 31 * result + player1Color.hashCode()
        result = 31 * result + player2Color.hashCode()
        result = 31 * result + hiddenRings.hashCode()
        result = 31 * result + trayPlayer
        result = 31 * result + trayAngleDegrees.hashCode()
        result = 31 * result + (movingRing?.hashCode() ?: 0)
        return result
    }
}
