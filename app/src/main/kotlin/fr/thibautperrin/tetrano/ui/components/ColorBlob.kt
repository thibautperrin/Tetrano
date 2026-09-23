package fr.thibautperrin.tetrano.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import fr.thibautperrin.tetrano.ui.theme.TetranoRingColors

/**
 * An irregular ink-blot shape filled with [color] — the settings screen's
 * color swatch. Ports `TacheView` (see docs/GLOSSARY.md): a hand-drawn
 * 29-point outline, normalized to a unit square, kept unchanged so it
 * still vectorizes the exact legacy artwork instead of a plain circle or
 * square (see docs/PLAN.md P9 — this replaces a raster PNG with zero
 * asset, one of the vectorization targets).
 */
@Composable
fun ColorBlob(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.aspectRatio(1f)) {
        val side = minOf(size.width, size.height)
        val path = Path().apply {
            moveTo(BLOB_POINTS_X.last() * side, BLOB_POINTS_Y.last() * side)
            for (i in BLOB_POINTS_X.indices) {
                lineTo(BLOB_POINTS_X[i] * side, BLOB_POINTS_Y[i] * side)
            }
            close()
        }
        drawPath(path, color)
    }
}

/**
 * The 8 selectable colors as one full-width row of equally sized square
 * blobs — the legacy `button_tache_style` (`layout_weight = 1`), so the row
 * always fits the screen width whatever its size.
 */
@Composable
fun ColorBlobRow(onColorSelected: (Color) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth()) {
        for (option in TetranoRingColors.selectable) {
            ColorBlob(
                color = option,
                modifier = Modifier
                    .weight(1f)
                    .clickable(role = Role.Button) { onColorSelected(option) },
            )
        }
    }
}

// Legacy TacheView.remplirTabPoints(), unchanged: fractions of the blob's
// bounding square.
private val BLOB_POINTS_X = floatArrayOf(
    0.255859375f, 0.28125f, 0.30859375f, 0.40234375f, 0.60546875f, 0.66796875f, 0.853515625f,
    0.89453125f, 0.927734375f, 0.93359375f, 0.93359375f, 0.8515625f, 0.8359375f, 0.83984375f,
    0.935546875f, 0.927734375f, 0.875f, 0.693359375f, 0.50390625f, 0.19140625f, 0.14453125f,
    0.140625f, 0.068359375f, 0.080078125f, 0.140625f, 0.146484375f, 0.064453125f, 0.0703125f,
    0.2421875f,
)
private val BLOB_POINTS_Y = floatArrayOf(
    0.0625f, 0.060546875f, 0.083984375f, 0.08203125f, 0.138671875f, 0.13671875f, 0.205078125f,
    0.23828125f, 0.251953125f, 0.279296875f, 0.3671875f, 0.474609375f, 0.544921875f, 0.5859375f,
    0.64453125f, 0.693359375f, 0.78125f, 0.884765625f, 0.88671875f, 0.7734375f, 0.716796875f,
    0.61328125f, 0.5703125f, 0.48046875f, 0.42578125f, 0.373046875f, 0.330078125f, 0.24609375f,
    0.091796875f,
)
