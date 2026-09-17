package fr.thibautperrin.tetrano.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.random.Random

/**
 * A generative stand-in for the legacy `fond_inverse.png` (a black
 * background scattered with tilted 4x4 grids and concentric colored
 * rings — see docs/PLAN.md P7/P9, `seed_assets/README.md`): drawn once
 * from a fixed seed, so the layout is stable across recompositions and
 * process restarts, and scales to any size with zero extra assets.
 *
 * Draws behind [content]; nothing here allocates per frame — the scene
 * (grid and ring placements, as size-independent fractions) is computed
 * once and cached with [remember].
 */
@Composable
fun TetranoBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scene = remember { generateScene() }
    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(color = Color.Black)
            for (grid in scene.grids) drawGrid(grid, size.width, size.height)
            for (ring in scene.rings) drawScatteredRing(ring, size.width, size.height)
        }
        content()
    }
}

private class Scene(val grids: List<ScatteredGrid>, val rings: List<ScatteredRing>)

private class ScatteredGrid(
    val centerXFraction: Float,
    val centerYFraction: Float,
    val sizeFraction: Float,
    val rotationDegrees: Float,
    val color: Color,
)

private class ScatteredRing(
    val centerXFraction: Float,
    val centerYFraction: Float,
    val radiusFraction: Float,
    val color: Color,
)

private val PALETTE = listOf(
    Color(0xFF29B6D8), // cyan
    Color(0xFFE0E040), // yellow
    Color(0xFF66D26A), // green
    Color(0xFF5C7CFA), // blue
    Color(0xFFE066D2), // magenta
    Color(0xFFE0E0E0), // near-white
)

private fun generateScene(seed: Long = 20260917L): Scene {
    val random = Random(seed)
    val grids = List(GRID_COUNT) {
        ScatteredGrid(
            centerXFraction = random.nextFloat(),
            centerYFraction = random.nextFloat(),
            sizeFraction = 0.16f + random.nextFloat() * 0.14f,
            rotationDegrees = random.nextFloat() * 40f - 20f,
            color = PALETTE[random.nextInt(PALETTE.size)].copy(alpha = 0.55f),
        )
    }
    val rings = List(RING_COUNT) {
        ScatteredRing(
            centerXFraction = random.nextFloat(),
            centerYFraction = random.nextFloat(),
            radiusFraction = 0.015f + random.nextFloat() * 0.045f,
            color = PALETTE[random.nextInt(PALETTE.size)].copy(alpha = 0.7f),
        )
    }
    return Scene(grids, rings)
}

private fun DrawScope.drawGrid(grid: ScatteredGrid, canvasWidth: Float, canvasHeight: Float) {
    val side = grid.sizeFraction * minOf(canvasWidth, canvasHeight)
    val centerX = grid.centerXFraction * canvasWidth
    val centerY = grid.centerYFraction * canvasHeight
    val strokeWidth = side * 0.018f
    rotate(degrees = grid.rotationDegrees, pivot = Offset(centerX, centerY)) {
        val left = centerX - side / 2f
        val top = centerY - side / 2f
        // Outer square.
        drawRect(
            color = grid.color,
            topLeft = Offset(left, top),
            size = androidx.compose.ui.geometry.Size(side, side),
            style = Stroke(width = strokeWidth),
        )
        // 3 interior lines each way (a 4x4 grid).
        for (i in 1..3) {
            val x = left + side * i / 4f
            drawLine(grid.color, Offset(x, top), Offset(x, top + side), strokeWidth)
            val y = top + side * i / 4f
            drawLine(grid.color, Offset(left, y), Offset(left + side, y), strokeWidth)
        }
    }
}

private fun DrawScope.drawScatteredRing(ring: ScatteredRing, canvasWidth: Float, canvasHeight: Float) {
    val radius = ring.radiusFraction * minOf(canvasWidth, canvasHeight)
    val center = Offset(ring.centerXFraction * canvasWidth, ring.centerYFraction * canvasHeight)
    drawCircle(
        color = ring.color,
        radius = radius,
        center = center,
        style = Stroke(width = radius * 0.35f),
    )
}

private const val GRID_COUNT = 9
private const val RING_COUNT = 22
