package fr.thibautperrin.tetrano.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput

/**
 * The Tetrano board: the 4x4 grid, placed rings, and (when [state]'s
 * `trayPlayer` is non-zero) the 4-ring tray a player drags from to play a
 * move. Ports `SurfaceGraphView`/`ThreadSurfaceGraphView`/`Dessin` as a
 * single stateless Compose `Canvas` (see docs/PLAN.md P7) — no
 * `SurfaceView`, no background thread: one redraw per state change.
 *
 * @param orientation which edge the tray sits along; the board's own
 *   aspect ratio follows it (4:5 in portrait, 5:4 in landscape) exactly
 *   like the legacy layout.
 * @param onRingDropped called with a ring number (0..63) when a drag from
 *   the tray ends over a valid grid cell. Not called for a drag that ends
 *   back over the tray (cancelling the pick-up) — mirrors
 *   `onTouchEvent`'s exact behavior, including that a drop is reported
 *   here even if the cell turns out to be occupied (that check, and
 *   whether to actually play it, is the caller's job — see
 *   `userTriedPlaying` in docs/GLOSSARY.md).
 */
@Composable
fun BoardCanvas(
    state: BoardVisualState,
    orientation: BoardOrientation,
    onRingDropped: (ringNumber: Int) -> Unit,
    modifier: Modifier = Modifier,
    gridColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val vertical = orientation == BoardOrientation.PORTRAIT
    var draggedRingSize by remember { mutableStateOf(-1) }
    var dragPosition by remember { mutableStateOf<Offset?>(null) }

    val aspectModifier = if (vertical) Modifier.aspectRatio(4f / 5f) else Modifier.aspectRatio(5f / 4f)

    Canvas(
        modifier = modifier
            .then(aspectModifier)
            .pointerInput(state.trayPlayer, orientation) {
                if (state.trayPlayer == 0) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        val side = if (vertical) size.width.toFloat() else size.height.toFloat()
                        val crossAxis = if (vertical) offset.y else offset.x
                        if (crossAxis > side) {
                            val mainAxis = if (vertical) offset.x else offset.y
                            draggedRingSize = (mainAxis * 4 / side).toInt().coerceIn(0, 3)
                            dragPosition = offset
                        }
                    },
                    onDrag = { change, _ ->
                        if (draggedRingSize >= 0) dragPosition = change.position
                    },
                    onDragEnd = {
                        val pickedUpSize = draggedRingSize
                        val releasePosition = dragPosition
                        draggedRingSize = -1
                        dragPosition = null
                        if (pickedUpSize >= 0 && releasePosition != null) {
                            val side = if (vertical) this.size.width.toFloat() else this.size.height.toFloat()
                            val crossAxis = if (vertical) releasePosition.y else releasePosition.x
                            if (crossAxis <= side) {
                                val cellX = (releasePosition.x * 4 / side).toInt().coerceIn(0, 3)
                                val cellY = (releasePosition.y * 4 / side).toInt().coerceIn(0, 3)
                                onRingDropped(cellX + 4 * cellY + 16 * pickedUpSize)
                            }
                        }
                    },
                    onDragCancel = {
                        draggedRingSize = -1
                        dragPosition = null
                    },
                )
            },
    ) {
        val side = if (vertical) size.width else size.height
        with(BoardDrawing) {
            drawGrid(side, gridColor)

            for (ring in 0 until 64) {
                if (ring in state.hiddenRings || state.movingRing?.ringNumber == ring) continue
                val player = state.rings[ring].toInt()
                if (player == 0) continue
                drawPlacedRing(side, colorFor(player, state), ring)
            }

            state.movingRing?.let { moving ->
                drawMovingRing(side, colorFor(moving.player, state), moving.ringNumber, vertical, moving.progress)
            }

            val trayColor = when (state.trayPlayer) {
                1 -> state.player1Color
                2 -> state.player2Color
                else -> null
            }
            drawTray(side, trayColor, vertical, state.trayAngleDegrees)

            draggedRingSize.takeIf { it >= 0 }?.let { ringSize ->
                dragPosition?.let { position ->
                    drawRing(side, colorFor(state.trayPlayer, state), position.x, position.y, ringSize)
                }
            }
        }
    }
}

private fun colorFor(player: Int, state: BoardVisualState): Color =
    if (player == 1) state.player1Color else state.player2Color
