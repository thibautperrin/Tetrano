package fr.thibautperrin.tetrano.ui.savedgames

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.times
import androidx.hilt.navigation.compose.hiltViewModel
import fr.thibautperrin.tetrano.R
import fr.thibautperrin.tetrano.ui.components.BoardCanvas
import fr.thibautperrin.tetrano.ui.components.BoardOrientation
import fr.thibautperrin.tetrano.ui.components.FitText
import fr.thibautperrin.tetrano.ui.components.LegacyTopBar
import fr.thibautperrin.tetrano.ui.components.TriangleButton
import fr.thibautperrin.tetrano.ui.theme.LegacyColors
import fr.thibautperrin.tetrano.ui.theme.LocalLegacyDimens

/**
 * Wires [SavedGameViewerViewModel] to the stateless [SavedGameViewerScreen].
 * Both resume callbacks receive the number of rings currently displayed
 * (legacy `ringsPlayedUntilCursor`), not the whole save.
 */
@Composable
fun SavedGameViewerRoute(
    onContinueTwoPlayers: (savedGameName: String, ringCount: Int) -> Unit,
    onContinueAgainstComputer: (savedGameName: String, ringCount: Int) -> Unit,
    viewModel: SavedGameViewerViewModel = hiltViewModel(),
) {
    val uiModel by viewModel.uiState.collectAsState()
    SavedGameViewerScreen(
        uiModel = uiModel,
        onAction = viewModel::onAction,
        onContinueAgainstComputer = { onContinueAgainstComputer(uiModel.gameName, uiModel.cursor) },
        onContinueTwoPlayers = { onContinueTwoPlayers(uiModel.gameName, uiModel.cursor) },
    )
}

/**
 * Ports `WatchSavedGameActivity` (see docs/GLOSSARY.md, docs/PLAN.md P8)
 * and its `activity_watch_saved_game` layouts: dark action bar titled with
 * the game's name and the `menu_watch_saved_game` overflow menu; a square
 * board (`setFormeCarre(true)`) with the `ButtonNextPrevious` arrows
 * (`Grosse_marge` wide) and the "Ring n / N" text (`Taille_titre_3`).
 * Portrait: board on top, arrows below it at both edges, text between them.
 * Landscape: board on the left; on the right, `Grosse_marge` from the top,
 * the text centered with the two arrows right above its ends. The board is
 * sized so that everything fits, whatever the window.
 */
@Composable
fun SavedGameViewerScreen(
    uiModel: SavedGameViewerUiModel,
    onAction: (SavedGameViewerAction) -> Unit,
    onContinueAgainstComputer: () -> Unit,
    onContinueTwoPlayers: () -> Unit,
) {
    Scaffold(
        containerColor = LegacyColors.Page,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            LegacyTopBar(
                title = uiModel.gameName.ifEmpty { stringResource(R.string.Saved_Games) },
                actions = {
                    if (!uiModel.isLoading && !uiModel.notFound) {
                        ResumeMenu(
                            onContinueAgainstComputer = onContinueAgainstComputer,
                            onContinueTwoPlayers = onContinueTwoPlayers,
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (uiModel.notFound) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.Sentence_No_Saved_Game), color = Color.Black)
            }
            return@Scaffold
        }
        ViewerContent(uiModel = uiModel, onAction = onAction, modifier = Modifier.fillMaxSize().padding(padding))
    }
}

/** `menu_watch_saved_game.xml`, in its `orderInCategory` order. */
@Composable
private fun ResumeMenu(onContinueAgainstComputer: () -> Unit, onContinueTwoPlayers: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Default.MoreVert, contentDescription = null)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.Continue_Against_Computer)) },
            onClick = {
                expanded = false
                onContinueAgainstComputer()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.Continue_In_Two_Players_Mode)) },
            onClick = {
                expanded = false
                onContinueTwoPlayers()
            },
        )
    }
}

@Composable
private fun ViewerContent(
    uiModel: SavedGameViewerUiModel,
    onAction: (SavedGameViewerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalLegacyDimens.current
    // WatchSavedGameActivity.actualiseTextView(): "%s %d / %d".
    val ring = stringResource(R.string.Ring)
    val ringText = "$ring ${uiModel.cursor} / ${uiModel.totalMoves}"
    // Measured with the widest cursor, so the layout doesn't move while stepping.
    val widestText = "$ring ${uiModel.totalMoves} / ${uiModel.totalMoves}"
    val textStyle = TextStyle(fontSize = dimens.title3)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val (textWidth, textHeight) = remember(widestText, textStyle, density) {
        val size = measurer.measure(widestText, textStyle, maxLines = 1, softWrap = false).size
        with(density) { size.width.toDp() to size.height.toDp() }
    }

    BoxWithConstraints(modifier = modifier) {
        val landscape = maxWidth > maxHeight
        if (!landscape) {
            val buttonWidth = dimens.largeMargin.coerceAtMost(maxWidth / 4)
            val buttonHeight = buttonWidth / BUTTON_RATIO
            val side = min(maxWidth, maxHeight - buttonHeight).coerceAtLeast(0.dp)
            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Board(uiModel, Modifier.size(side))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Arrow(next = false, onAction = onAction, width = buttonWidth)
                    FitText(
                        text = ringText,
                        maxFontSize = dimens.title3,
                        color = DefaultTextColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    Arrow(next = true, onAction = onAction, width = buttonWidth)
                }
            }
        } else {
            val paneMargin = dimens.tinyMargin
            val paneMinWidth = max(textWidth, 2 * dimens.largeMargin) + 2 * paneMargin
            val side = min(maxHeight, maxWidth - paneMinWidth).coerceAtLeast(0.dp)
            val paneWidth = maxWidth - side - 2 * paneMargin
            // Arrows above the text and, to keep the text itself centered,
            // as much blank space below it: shrink the arrows (then the top
            // margin) rather than overflow a short window.
            val buttonWidth = dimens.largeMargin
                .coerceAtMost((maxHeight - textHeight) / 2 * BUTTON_RATIO)
                .coerceAtMost(paneWidth / 2)
                .coerceAtLeast(0.dp)
            val buttonHeight = buttonWidth / BUTTON_RATIO
            val topMargin = dimens.largeMargin
                .coerceAtMost(maxHeight - textHeight - 2 * buttonHeight)
                .coerceAtLeast(0.dp)
            val rowWidth = max(textWidth, 2 * buttonWidth).coerceAtMost(paneWidth).coerceAtLeast(0.dp)
            Row(modifier = Modifier.fillMaxSize()) {
                Board(uiModel, Modifier.size(side))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = paneMargin, end = paneMargin, top = topMargin),
                ) {
                    Column(modifier = Modifier.align(Alignment.Center).width(rowWidth)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Arrow(next = false, onAction = onAction, width = buttonWidth)
                            Arrow(next = true, onAction = onAction, width = buttonWidth)
                        }
                        FitText(
                            text = ringText,
                            maxFontSize = dimens.title3,
                            color = DefaultTextColor,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(buttonHeight))
                    }
                }
            }
        }
    }
}

@Composable
private fun Board(uiModel: SavedGameViewerUiModel, modifier: Modifier) {
    BoardCanvas(
        state = uiModel.board,
        orientation = BoardOrientation.SQUARE,
        onRingDropped = {},
        modifier = modifier,
    )
}

/** `ButtonNextPrevious`: never disabled — a tap past either end does nothing, as in the legacy app. */
@Composable
private fun Arrow(next: Boolean, onAction: (SavedGameViewerAction) -> Unit, width: Dp) {
    TriangleButton(
        next = next,
        onClick = { onAction(if (next) SavedGameViewerAction.NextClicked else SavedGameViewerAction.PreviousClicked) },
        contentDescription = stringResource(if (next) R.string.Next else R.string.previous),
        modifier = Modifier.width(width),
    )
}

/** `ButtonNextPrevious`' `ratioWidthOnHeight`. */
private const val BUTTON_RATIO = 1.2f

/** A plain legacy `TextView`'s color (`textDescriptionAnneau`): Material light's `textColorSecondary`. */
private val DefaultTextColor = Color(0x8A000000)
