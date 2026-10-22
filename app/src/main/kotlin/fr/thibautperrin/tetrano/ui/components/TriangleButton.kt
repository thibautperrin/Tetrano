package fr.thibautperrin.tetrano.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import fr.thibautperrin.tetrano.ui.theme.TetranoPalette

/**
 * A black "previous" / "next" arrow-head button. Ports `ButtonNextPrevious`
 * (the 6-point path, and its width/height ratio of 1.2) — used next to the
 * AI level slider and by the saved-game viewer.
 */
@Composable
fun TriangleButton(
    next: Boolean,
    onClick: () -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = TetranoPalette.Accent,
) {
    val xs = if (next) NEXT_X else PREVIOUS_X
    Canvas(
        modifier = modifier
            .aspectRatio(RATIO_WIDTH_ON_HEIGHT)
            .alpha(if (enabled) 1f else 0.3f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
    ) {
        val path = Path().apply {
            moveTo(xs.last() * size.width, YS.last() * size.height)
            for (i in xs.indices) lineTo(xs[i] * size.width, YS[i] * size.height)
            close()
        }
        drawPath(path, color)
    }
}

private const val RATIO_WIDTH_ON_HEIGHT = 1.2f
private val YS = floatArrayOf(0.9f, 0.9f, 0.5f, 0.1f, 0.1f, 0.5f)
private val NEXT_X = floatArrayOf(0.1f, 0.5f, 0.9f, 0.5f, 0.1f, 0.35f)
private val PREVIOUS_X = floatArrayOf(0.9f, 0.5f, 0.1f, 0.5f, 0.9f, 0.65f)
