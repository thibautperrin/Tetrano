package fr.thibautperrin.tetrano.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min

/**
 * A playable board (with its tray) and a panel of information and controls:
 * portrait, the board on top and the panel below it; landscape, the board on
 * the left and the panel on the right. The board takes all the room the
 * panel leaves (at least [minPanelHeight] in portrait, about 40 % of the
 * width in landscape); the panel scrolls if it does not fit.
 */
@Composable
fun BoardWithPanel(
    board: BoardVisualState,
    onRingDropped: (ring: Int) -> Unit,
    modifier: Modifier = Modifier,
    minPanelHeight: Dp = 200.dp,
    panel: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            val boardHeight = min(maxHeight, maxWidth * 0.6f / LANDSCAPE_RATIO)
            Row(Modifier.fillMaxSize()) {
                BoardCanvas(
                    state = board,
                    orientation = BoardOrientation.LANDSCAPE,
                    onRingDropped = onRingDropped,
                    modifier = Modifier.size(width = boardHeight * LANDSCAPE_RATIO, height = boardHeight),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = panel,
                )
            }
        } else {
            val boardWidth = min(maxWidth, (maxHeight - minPanelHeight) * PORTRAIT_RATIO).coerceAtLeast(MIN_BOARD)
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    BoardCanvas(
                        state = board,
                        orientation = BoardOrientation.PORTRAIT,
                        onRingDropped = onRingDropped,
                        modifier = Modifier.size(width = boardWidth, height = boardWidth / PORTRAIT_RATIO),
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = panel,
                )
            }
        }
    }
}

/** Board width / height with the tray (a fifth row or column). */
private const val PORTRAIT_RATIO = 4f / 5f
private const val LANDSCAPE_RATIO = 5f / 4f
private val MIN_BOARD = 160.dp
