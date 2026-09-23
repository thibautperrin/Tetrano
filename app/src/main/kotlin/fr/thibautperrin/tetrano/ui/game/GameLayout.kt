package fr.thibautperrin.tetrano.ui.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import fr.thibautperrin.tetrano.R
import fr.thibautperrin.tetrano.ui.components.BoardCanvas
import fr.thibautperrin.tetrano.ui.components.BoardOrientation
import fr.thibautperrin.tetrano.ui.components.BoardVisualState
import fr.thibautperrin.tetrano.ui.components.LegacyButton
import fr.thibautperrin.tetrano.ui.theme.LegacyColors
import fr.thibautperrin.tetrano.ui.theme.LocalLegacyDimens

/** `LegacyButton`'s own padding around its label. */
private val ButtonHorizontalPadding = 8.dp
private val ButtonVerticalPadding = 6.dp
private val ButtonMinHeight = 48.dp

/** Below this, the board would be too small to play on: the content scrolls instead. */
private val MinBoardSide = 120.dp

private data class GameButtonSpec(val label: String, val background: Color, val onClick: () -> Unit)

/**
 * `activity_jeu.xml`: the board and the three buttons (Play again, Blink,
 * Cancel — equal weights, no gaps, blue bold italic text). Portrait: the
 * portrait board centered at the top, the buttons as a full-width row at
 * the bottom, as tall as their tallest label. Landscape (`layout-land`):
 * the landscape board at the top-left, the buttons stacked on the right in
 * a column as wide as their widest label. Either way the board takes all
 * the room the buttons leave, and the content only scrolls if that would
 * make the board smaller than [MinBoardSide].
 */
@Composable
internal fun GameBoardAndButtons(
    board: BoardVisualState,
    landscape: Boolean,
    onAction: (GameAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalLegacyDimens.current
    val buttons = listOf(
        GameButtonSpec(stringResource(R.string.Play_Again), LegacyColors.ButtonYellow) { onAction(GameAction.PlayAgainClicked) },
        GameButtonSpec(stringResource(R.string.Mot_Clignoter), LegacyColors.ButtonGreen) { onAction(GameAction.BlinkClicked) },
        GameButtonSpec(stringResource(R.string.Cancel), LegacyColors.ButtonCyan) { onAction(GameAction.CancelClicked) },
    )
    val onRingDropped: (Int) -> Unit = { onAction(GameAction.RingDropped(it)) }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = TextStyle(fontSize = dimens.bottomButtons, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)

    BoxWithConstraints(modifier.fillMaxSize()) {
        if (landscape) {
            // wrap_content width, with Tres_petite_marge on each side of the label.
            val sidePadding = max(dimens.tinyMargin, ButtonHorizontalPadding)
            val widestLabel = buttons.maxOf { button ->
                with(density) { textMeasurer.measure(button.label, labelStyle, maxLines = 1).size.width.toDp() }
            }
            val columnWidth = min(widestLabel + sidePadding * 2 + 1.dp, maxWidth * MAX_BUTTON_COLUMN_FRACTION)
            val boardHeight = min(maxHeight, (maxWidth - columnWidth) / LANDSCAPE_RATIO)
            val scrolls = boardHeight < MinBoardSide
            val rowHeight = if (scrolls) MinBoardSide else maxHeight
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (scrolls) Modifier.verticalScroll(rememberScrollState()) else Modifier.fillMaxHeight()),
            ) {
                Box(Modifier.weight(1f).height(rowHeight), contentAlignment = Alignment.TopStart) {
                    val side = if (scrolls) MinBoardSide else boardHeight
                    BoardCanvas(
                        state = board,
                        orientation = BoardOrientation.LANDSCAPE,
                        onRingDropped = onRingDropped,
                        modifier = Modifier.size(width = side * LANDSCAPE_RATIO, height = side),
                    )
                }
                Column(Modifier.width(columnWidth).height(rowHeight)) {
                    buttons.forEach { GameButton(it, Modifier.weight(1f).fillMaxWidth()) }
                }
            }
        } else {
            val labelWidth = with(density) { (maxWidth / buttons.size - ButtonHorizontalPadding * 2).roundToPx() }
            val tallestLabel = buttons.maxOf { button ->
                val layout = textMeasurer.measure(
                    button.label,
                    labelStyle,
                    maxLines = 2,
                    constraints = Constraints(maxWidth = labelWidth.coerceAtLeast(1)),
                )
                with(density) { layout.size.height.toDp() }
            }
            val rowHeight = max(ButtonMinHeight, tallestLabel + ButtonVerticalPadding * 2)
            val boardWidth = min(maxWidth, (maxHeight - rowHeight) * PORTRAIT_RATIO)
            val buttonsRow: @Composable (Modifier) -> Unit = { rowModifier ->
                Row(rowModifier.fillMaxWidth().height(rowHeight)) {
                    buttons.forEach { GameButton(it, Modifier.weight(1f).fillMaxHeight()) }
                }
            }
            if (boardWidth < MinBoardSide) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        BoardCanvas(
                            state = board,
                            orientation = BoardOrientation.PORTRAIT,
                            onRingDropped = onRingDropped,
                            modifier = Modifier.size(width = MinBoardSide, height = MinBoardSide / PORTRAIT_RATIO),
                        )
                    }
                    buttonsRow(Modifier)
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        BoardCanvas(
                            state = board,
                            orientation = BoardOrientation.PORTRAIT,
                            onRingDropped = onRingDropped,
                            modifier = Modifier.size(width = boardWidth, height = boardWidth / PORTRAIT_RATIO),
                        )
                    }
                    buttonsRow(Modifier)
                }
            }
        }
    }
}

@Composable
private fun GameButton(spec: GameButtonSpec, modifier: Modifier) {
    LegacyButton(
        text = spec.label,
        onClick = spec.onClick,
        modifier = modifier,
        background = spec.background,
        textColor = LegacyColors.TextBlue,
        fontSize = LocalLegacyDimens.current.bottomButtons,
    )
}

/** Board width / height (`SurfaceGraphView.doMeasure`: the tray adds a fifth row or column). */
private const val PORTRAIT_RATIO = 4f / 5f
private const val LANDSCAPE_RATIO = 5f / 4f

/** The buttons' column never takes more than this share of the width, however long a translation. */
private const val MAX_BUTTON_COLUMN_FRACTION = 0.4f
