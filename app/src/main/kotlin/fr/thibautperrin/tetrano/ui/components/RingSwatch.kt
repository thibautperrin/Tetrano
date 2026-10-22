package fr.thibautperrin.tetrano.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.thibautperrin.tetrano.ui.theme.LegacyColors

/** A board ring: a thick circle in [color]. */
@Composable
fun RingPreview(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.22f
        drawCircle(
            color = color,
            radius = (size.minDimension - stroke) / 2f,
            center = Offset(size.width / 2f, size.height / 2f),
            style = Stroke(width = stroke),
        )
    }
}

/**
 * A player's ring color as it looks in play: a ring on a small square of
 * the board's light background. Every ring color is readable there, where
 * some (black, blue) would vanish on the black pages.
 */
@Composable
fun RingSwatch(color: Color, modifier: Modifier = Modifier, size: Dp = 32.dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .background(LegacyColors.BoardBackground, RoundedCornerShape(size / 6)),
    ) {
        RingPreview(color, Modifier.padding(size / 8).size(size))
    }
}
