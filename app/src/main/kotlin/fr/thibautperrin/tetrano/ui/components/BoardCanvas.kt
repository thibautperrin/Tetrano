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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
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
    val currentOnRingDropped by rememberUpdatedState(onRingDropped)
    val dragAllowed = showTray && state.trayPlayer != 0 && state.dragEnabled

    val ratio = when (orientation) {
        BoardOrientation.PORTRAIT -> 4f / 5f
        BoardOrientation.LANDSCAPE -> 5f / 4f
        BoardOrientation.SQUARE -> 1f
    }

    Canvas(
        modifier = modifier
            .aspectRatio(ratio)
            .pointerInput(dragAllowed, orientation) {
                draggedRingSize = -1
                dragPosition = null
                if (!dragAllowed) return@pointerInput
                awaitEachGesture {
                    val side = if (vertical) size.width.toFloat() else size.height.toFloat()
                    fun pickUpIfOverTray(position: Offset) {
                        val crossAxis = if (vertical) position.y else position.x
                        if (draggedRingSize == -1 && crossAxis > side) {
                            val mainAxis = if (vertical) position.x else position.y
                            draggedRingSize = (mainAxis * 4 / side).toInt().coerceIn(0, 3)
                        }
                        if (draggedRingSize >= 0) dragPosition = position
                    }

                    val down = awaitFirstDown(requireUnconsumed = false)
                    pickUpIfOverTray(down.position)
                    var release = down.position
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        release = change.position
                        if (!change.pressed) break
                        pickUpIfOverTray(change.position)
                        if (draggedRingSize >= 0 && change.positionChange() != Offset.Zero) change.consume()
                    }

                    val pickedUpSize = draggedRingSize
                    draggedRingSize = -1
                    dragPosition = null
                    val crossAxis = if (vertical) release.y else release.x
                    if (pickedUpSize >= 0 && crossAxis <= side) {
                        val cellX = (release.x * 4 / side).toInt().coerceIn(0, 3)
                        val cellY = (release.y * 4 / side).toInt().coerceIn(0, 3)
                        currentOnRingDropped(cellX + 4 * cellY + 16 * pickedUpSize)
                    }
                }
            },
    ) {
        val side = if (vertical) size.width else size.height
        drawRect(backgroundColor)
        with(BoardDrawing) {
            drawGrid(side, gridColor)

            for (ring in 0 until 64) {
                if (ring in state.hiddenRings) continue
                // A flying ring's destination stays an empty slot until it lands.
                val player = if (state.movingRing?.ringNumber == ring) 0 else state.rings[ring].toInt()
                drawPlacedRing(side, colorFor(player, state, emptyRingColor), ring)
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
        }
    }
}

private fun colorFor(player: Int, state: BoardVisualState, empty: Color): Color = when (player) {
    1 -> state.player1Color
    2 -> state.player2Color
    else -> empty
}
