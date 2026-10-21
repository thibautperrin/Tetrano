package fr.thibautperrin.tetrano.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import fr.thibautperrin.tetrano.ui.theme.LegacyColors

/**
 * The Tetrano board: background, the 4x4 grid, all 64 ring slots (empty
 * ones in light grey, exactly like the original), and — unless [orientation]
 * is [BoardOrientation.SQUARE] — the 4-ring tray a player drags from to
 * play a move. Ports `SurfaceGraphView`/`ThreadSurfaceGraphView`/`Dessin`
 * as a single stateless Compose `Canvas` (see docs/PLAN.md P7) — no
 * `SurfaceView`, no background thread: one redraw per state change.
 *
 * The board keeps its legacy aspect ratio (4:5 portrait, 5:4 landscape, 1:1
 * square) and shrinks to fit whatever space its parent gives it.
 *
 * With [BoardVisualState.hints] (the analysis), every free slot is drawn as a
 * dashed ring, as visible as the search recommends it, the recommended ones
 * labelled in small with their rank and percentage; a finger pressed on the
 * grid shows the label of the ring under it enlarged, following the finger.
 *
 * @param onRingDropped called with a ring number (0..63) when a ring picked
 *   up from the tray is released over the grid. Mirrors `onTouchEvent`:
 *   the ring is picked up as soon as the finger is (or moves) over the
 *   tray, releasing it back over the tray cancels, and a drop is reported
 *   even if the cell turns out to be occupied (that check is the caller's
 *   job — see `userTriedPlaying` in docs/GLOSSARY.md).
 */
@Composable
fun BoardCanvas(
    state: BoardVisualState,
    orientation: BoardOrientation,
    onRingDropped: (ringNumber: Int) -> Unit,
    modifier: Modifier = Modifier,
    gridColor: Color = Color.Black,
    emptyRingColor: Color = LegacyColors.EmptyRing,
    backgroundColor: Color = LegacyColors.BoardBackground,
) {
    val vertical = orientation != BoardOrientation.LANDSCAPE
    val showTray = orientation != BoardOrientation.SQUARE
    var draggedRingSize by remember { mutableIntStateOf(-1) }
    var dragPosition by remember { mutableStateOf<Offset?>(null) }
    var inspectPosition by remember { mutableStateOf<Offset?>(null) }
    val currentOnRingDropped by rememberUpdatedState(onRingDropped)
    val dragAllowed = showTray && state.trayPlayer != 0 && state.dragEnabled
    val hasHints = state.hints.isNotEmpty()
    val textMeasurer = rememberTextMeasurer()

    val ratio = when (orientation) {
        BoardOrientation.PORTRAIT -> 4f / 5f
        BoardOrientation.LANDSCAPE -> 5f / 4f
        BoardOrientation.SQUARE -> 1f
    }

    Canvas(
        modifier = modifier
            .aspectRatio(ratio)
            .pointerInput(dragAllowed, hasHints, orientation) {
                draggedRingSize = -1
                dragPosition = null
                inspectPosition = null
                if (!dragAllowed && !hasHints) return@pointerInput
                awaitEachGesture {
                    val side = if (vertical) size.width.toFloat() else size.height.toFloat()
                    fun overTray(position: Offset) = (if (vertical) position.y else position.x) > side
                    fun pickUpIfOverTray(position: Offset) {
                        if (!dragAllowed) return
                        if (draggedRingSize == -1 && overTray(position)) {
                            val mainAxis = if (vertical) position.x else position.y
                            draggedRingSize = (mainAxis * 4 / side).toInt().coerceIn(0, 3)
                        }
                        if (draggedRingSize >= 0) dragPosition = position
                    }

                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Pressed on the grid with hints: the finger inspects the rings, it plays nothing.
                    val inspecting = hasHints && !overTray(down.position)
                    if (inspecting) inspectPosition = down.position else pickUpIfOverTray(down.position)
                    var release = down.position
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        release = change.position
                        if (!change.pressed) break
                        if (inspecting) {
                            inspectPosition = change.position
                            change.consume()
                        } else {
                            pickUpIfOverTray(change.position)
                            if (draggedRingSize >= 0 && change.positionChange() != Offset.Zero) change.consume()
                        }
                    }

                    inspectPosition = null
                    val pickedUpSize = draggedRingSize
                    draggedRingSize = -1
                    dragPosition = null
                    if (pickedUpSize >= 0 && !overTray(release)) {
                        val cellX = (release.x * 4 / side).toInt().coerceIn(0, 3)
                        val cellY = (release.y * 4 / side).toInt().coerceIn(0, 3)
                        currentOnRingDropped(cellX + 4 * cellY + 16 * pickedUpSize)
                    }
                }
            },
    ) {
        val side = if (vertical) size.width else size.height
        drawRect(backgroundColor)
        val hinted = state.hints.filter { state.rings[it.ringNumber].toInt() == 0 }.associateBy { it.ringNumber }
        with(BoardDrawing) {
            drawGrid(side, gridColor)

            for (ring in 0 until 64) {
                if (ring in state.hiddenRings || ring in hinted) continue
                // A flying ring's destination stays an empty slot until it lands.
                val player = if (state.movingRing?.ringNumber == ring) 0 else state.rings[ring].toInt()
                drawPlacedRing(side, colorFor(player, state, emptyRingColor), ring)
            }

            state.ghostRing?.let { ghost ->
                if (state.rings[ghost.ringNumber].toInt() == 0) {
                    drawPlacedRing(side, colorFor(ghost.player, state, emptyRingColor).copy(alpha = GHOST_ALPHA), ghost.ringNumber)
                }
            }

            val inspected = inspectPosition?.let { ringAt(side, it) }?.let { hinted[it] }
            for (hint in hinted.values) {
                drawHint(side, hint, colorFor(hint.player, state, emptyRingColor), emptyRingColor, highlighted = hint == inspected)
            }
            for (hint in hinted.values) {
                if (hint.rank != null && hint != inspected && (hint.rank == 1 || hint.strength >= LABEL_MIN_STRENGTH)) {
                    drawHintLabel(textMeasurer, side, hint)
                }
            }

            if (showTray) {
                drawTray(side, colorFor(state.trayPlayer, state, emptyRingColor), vertical, state.trayAngleDegrees)
            }

            state.movingRing?.let { moving ->
                drawMovingRing(side, colorFor(moving.player, state, emptyRingColor), moving.ringNumber, vertical, moving.progress)
            }

            draggedRingSize.takeIf { it >= 0 && dragAllowed }?.let { ringSize ->
                dragPosition?.let { position ->
                    drawRing(side, colorFor(state.trayPlayer, state, emptyRingColor), position.x, position.y, ringSize)
                }
            }

            if (inspected != null) drawHintBubble(textMeasurer, side, inspected, inspectPosition!!)
        }
    }
}

/**
 * A hinted free slot: the empty-slot ring, then a dashed ring in a pale [color] whose opacity
 * follows the hint's strength (almost invisible when the search did not look at the move).
 */
private fun DrawScope.drawHint(side: Float, hint: RingHint, color: Color, empty: Color, highlighted: Boolean) {
    with(BoardDrawing) { drawPlacedRing(side, empty, hint.ringNumber) }
    val alpha = when {
        highlighted -> 1f
        hint.rank == null -> UNSEARCHED_HINT_ALPHA
        else -> MIN_HINT_ALPHA + (MAX_HINT_ALPHA - MIN_HINT_ALPHA) * hint.strength.coerceIn(0f, 1f)
    }
    val pale = lerp(color, Color.White, if (highlighted) 0.15f else PALE_FRACTION).copy(alpha = alpha)
    val ringSize = (hint.ringNumber / 16) % 4
    val center = BoardDrawing.cellCenter(side, hint.ringNumber % 4, (hint.ringNumber / 4) % 4)
    val radius = BoardDrawing.ringRadius(side, ringSize)
    val dash = side * 0.018f
    drawCircle(
        color = pale,
        radius = radius,
        center = center,
        style = Stroke(
            width = BoardDrawing.ringStroke(side) * (if (highlighted) 1.3f else 1f),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash * 0.7f)),
        ),
    )
}

private fun hintText(hint: RingHint): String = "${hint.rank}·${hint.percent ?: "?"}%"

/** The small "rank·percent" label of a searched hint, on its circle. */
private fun DrawScope.drawHintLabel(textMeasurer: TextMeasurer, side: Float, hint: RingHint) {
    val style = TextStyle(fontSize = (side * LABEL_SIZE).toSp(), color = LABEL_COLOR, fontWeight = FontWeight.Medium)
    val layout = textMeasurer.measure(hintText(hint), style)
    val anchor = BoardDrawing.labelAnchor(side, hint.ringNumber)
    val topLeft = Offset(anchor.x - layout.size.width / 2f, anchor.y - layout.size.height / 2f)
    val pad = side * 0.004f
    drawRoundRect(
        color = LABEL_BACKGROUND,
        topLeft = topLeft - Offset(pad, 0f),
        size = Size(layout.size.width + 2 * pad, layout.size.height.toFloat()),
        cornerRadius = CornerRadius(layout.size.height / 2f),
    )
    drawText(layout, topLeft = topLeft)
}

/** The enlarged label of the ring under the finger, above the finger. */
private fun DrawScope.drawHintBubble(textMeasurer: TextMeasurer, side: Float, hint: RingHint, finger: Offset) {
    val text = if (hint.rank == null) "—" else "#${hint.rank}   ${hint.percent ?: "?"} %"
    val style = TextStyle(fontSize = (side * BUBBLE_TEXT_SIZE).toSp(), color = Color.White, fontWeight = FontWeight.Bold)
    val layout = textMeasurer.measure(text, style)
    val pad = side * 0.02f
    val width = layout.size.width + 2 * pad
    val height = layout.size.height + 2 * pad
    val x = (finger.x - width / 2f).coerceIn(0f, size.width - width)
    val y = (finger.y - side * 0.12f - height).coerceAtLeast(0f)
    drawRoundRect(BUBBLE_COLOR, topLeft = Offset(x, y), size = Size(width, height), cornerRadius = CornerRadius(height / 3f))
    drawText(layout, topLeft = Offset(x + pad, y + pad))
}

/** Opacity of a [GhostRing]. */
private const val GHOST_ALPHA = 0.45f

/** Opacity of hints: not searched, then from the least to the most searched move. */
private const val UNSEARCHED_HINT_ALPHA = 0.07f
private const val MIN_HINT_ALPHA = 0.25f
private const val MAX_HINT_ALPHA = 0.8f

/** How much of the player's color is replaced by white in a hint. */
private const val PALE_FRACTION = 0.35f

/** Hints recommended less than this (relative to the best) get no small label. */
private const val LABEL_MIN_STRENGTH = 0.1f

/** Text sizes, as fractions of the board's side. */
private const val LABEL_SIZE = 0.021f
private const val BUBBLE_TEXT_SIZE = 0.06f
private val LABEL_COLOR = Color(0xFF333333)
private val LABEL_BACKGROUND = Color(0xCCFFFFFF)
private val BUBBLE_COLOR = Color(0xE61F2A44)

private fun colorFor(player: Int, state: BoardVisualState, empty: Color): Color = when (player) {
    1 -> state.player1Color
    2 -> state.player2Color
    else -> empty
}
