package fr.thibautperrin.tetrano.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Low-level board drawing math, ported from the legacy `Dessin` class and
 * its `StaticFields` coefficients unchanged (see docs/GLOSSARY.md) so the
 * board renders pixel-for-pixel like the original — everything here is
 * relative to `side` (the board's own edge length, "cote"), which lets it
 * scale to any size with a single number.
 */
internal object BoardDrawing {

    // StaticFields' drawing coefficients, unchanged.
    private const val THICKNESS_BORDER = 0.01262f
    private const val THICKNESS_SEPARATION = 0.009464f
    private const val THICKNESS_RINGS = 0.014f
    private const val DISTANCE_LINES = 0.236593f
    private const val SHORTEST_RADIUS = 0.01893f
    private const val DIFF_RADIUS = 0.02839f
    private const val POS0 = THICKNESS_BORDER + DISTANCE_LINES / 2f
    private const val DISTANCE_RING_CENTERS = DISTANCE_LINES + THICKNESS_SEPARATION

    /** The 4x4 grid border and its 6 interior separator lines. Ports `dessineGrille`. */
    fun DrawScope.drawGrid(side: Float, color: Color) {
        val borderThickness = side * THICKNESS_BORDER
        val lineThickness = side * THICKNESS_SEPARATION
        val lineDistance = side * DISTANCE_LINES

        drawLine(color, Offset(borderThickness / 2f, 0f), Offset(borderThickness / 2f, side), borderThickness)
        drawLine(color, Offset(side - borderThickness / 2f, 0f), Offset(side - borderThickness / 2f, side), borderThickness)
        drawLine(color, Offset(0f, borderThickness / 2f), Offset(side, borderThickness / 2f), borderThickness)
        drawLine(color, Offset(0f, side - borderThickness / 2f), Offset(side, side - borderThickness / 2f), borderThickness)

        for (i in 1..3) {
            val offset = borderThickness + i * lineDistance + lineThickness * (2 * i - 1) / 2f
            drawLine(color, Offset(offset, lineThickness / 2f), Offset(offset, side - lineThickness / 2f), lineThickness)
            drawLine(color, Offset(lineThickness / 2f, offset), Offset(side - lineThickness / 2f, offset), lineThickness)
        }
    }

    /**
     * A ring (or partial arc of one, for [angleDegrees] `< 360`) of size
     * [ringSize] (0..3) centered at ([centerX], [centerY]). Ports
     * `dessineArcs`/`dessineAnneau`.
     */
    fun DrawScope.drawRing(
        side: Float,
        color: Color,
        centerX: Float,
        centerY: Float,
        ringSize: Int,
        angleDegrees: Float = 360f,
    ) {
        val radius = side * SHORTEST_RADIUS + side * DIFF_RADIUS * ringSize
        val strokeWidth = side * THICKNESS_RINGS
        val topLeft = Offset(centerX - radius, centerY - radius)
        val boxSize = Size(radius * 2f, radius * 2f)
        if (angleDegrees >= 360f) {
            drawOval(color, topLeft = topLeft, size = boxSize, style = Stroke(strokeWidth))
        } else {
            drawArc(
                color = color,
                startAngle = 0f,
                sweepAngle = angleDegrees,
                useCenter = false,
                topLeft = topLeft,
                size = boxSize,
                style = Stroke(strokeWidth),
            )
        }
    }

    /** The center, in pixels, of grid cell ([cellX], [cellY]) (each 0..3). Ports the position half of `dessineAnneauPlace`. */
    fun cellCenter(side: Float, cellX: Int, cellY: Int): Offset {
        val distance = side * DISTANCE_RING_CENTERS
        val pos0 = side * POS0
        return Offset(pos0 + distance * cellX, pos0 + distance * cellY)
    }

    /** A ring by its number (0..63: `x + 4*y + 16*size`) at its resting position. Ports `dessineAnneauNumero`. */
    fun DrawScope.drawPlacedRing(side: Float, color: Color, ringNumber: Int) {
        val x = ringNumber % 4
        val y = (ringNumber / 4) % 4
        val ringSize = (ringNumber / 16) % 4
        val center = cellCenter(side, x, y)
        drawRing(side, color, center.x, center.y, ringSize)
    }

    /**
     * The center, in pixels, of the [ringSize]-th (0..3) tray ring: past
     * the grid's edge, along the cross axis, so it always sits in the
     * reserve strip regardless of [vertical]. Ports the position half of
     * `dessineAnneauASaisir`.
     */
    fun trayRingCenter(side: Float, ringSize: Int, vertical: Boolean): Offset {
        val distance = side * DISTANCE_RING_CENTERS
        val pos0 = side * POS0
        return if (vertical) {
            Offset(pos0 + distance * ringSize, pos0 + distance * 4)
        } else {
            Offset(pos0 + distance * 4, pos0 + distance * ringSize)
        }
    }

    /** The 4 tray rings a player can pick up, tinted [color] (or not drawn at all if [color] is null). Ports `dessineAnneauASaisir`. */
    fun DrawScope.drawTray(side: Float, color: Color?, vertical: Boolean, angleDegrees: Float = 360f) {
        if (color == null) return
        for (ringSize in 0 until 4) {
            val center = trayRingCenter(side, ringSize, vertical)
            drawRing(side, color, center.x, center.y, ringSize, angleDegrees)
        }
    }

    /**
     * A ring travelling in a straight line from the tray (size [ringSize])
     * to its destination [ringNumber], [progress] of the way there (0 at
     * the tray, 1 at rest). Ports `dessineTrajetAnneau`.
     */
    fun DrawScope.drawMovingRing(side: Float, color: Color, ringNumber: Int, vertical: Boolean, progress: Float) {
        val ringSize = (ringNumber / 16) % 4
        val from = trayRingCenter(side, ringSize, vertical)
        val to = cellCenter(side, ringNumber % 4, (ringNumber / 4) % 4)
        val x = from.x + (to.x - from.x) * progress
        val y = from.y + (to.y - from.y) * progress
        drawRing(side, color, x, y, ringSize)
    }
}
