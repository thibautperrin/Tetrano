package fr.thibautperrin.tetrano.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import fr.thibautperrin.tetrano.ui.theme.LegacyColors

/**
 * A play-menu button. Ports `BoutonsJouer` (see docs/GLOSSARY.md) exactly:
 * a 2:1 translucent yellow panel, the 2:1 artwork ([icon], the vectorized
 * `bouton_*` PNGs) stretched over it, and an optional black label whose
 * width is always 60% of the button's (`setTextSizeForWidth(height * 1.2)`)
 * with its baseline at 35% of the height — so the label scales with the
 * button and can never overflow it.
 *
 * @param enabled false greys the button out and makes it inert.
 */
@Composable
fun IconTextButton(
    icon: Painter,
    text: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = text,
) {
    val textMeasurer = rememberTextMeasurer()
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2f)
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
    ) {
        drawRect(LegacyColors.PlayModeButton)
        with(icon) { draw(size) }
        if (text != null) {
            val probe = textMeasurer.measure(text, TextStyle(fontSize = PROBE_SIZE, color = Color.Black))
            val targetWidth = size.height * 1.2f
            val scale = if (probe.size.width > 0) targetWidth / probe.size.width else 1f
            val layout = textMeasurer.measure(
                text,
                TextStyle(fontSize = PROBE_SIZE * scale, color = Color.Black),
            )
            val baseline = size.height * 0.35f
            drawText(
                layout,
                topLeft = Offset((size.width - layout.size.width) / 2f, baseline - layout.firstBaseline),
            )
        }
    }
}

private val PROBE_SIZE = 70.sp
