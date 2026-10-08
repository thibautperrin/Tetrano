package fr.thibautperrin.tetrano.ui.savedgames

import androidx.compose.foundation.background
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
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
import kotlin.math.roundToInt

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
                title = uiModel.gameName.ifEmpty {
                    stringResource(if (uiModel.canResume) R.string.Saved_Games else R.string.Review_Game)
                },
                actions = {
                    if (!uiModel.isLoading && !uiModel.notFound) {
                        ResumeMenu(
                            canResume = uiModel.canResume,
                            canAnalyse = uiModel.review == null,
                            onAnalyse = { onAction(SavedGameViewerAction.AnalyseClicked) },
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

/** `menu_watch_saved_game.xml`, in its `orderInCategory` order, after the level-101 analysis. */
@Composable
private fun ResumeMenu(
    canResume: Boolean,
    canAnalyse: Boolean,
    onAnalyse: () -> Unit,
    onContinueAgainstComputer: () -> Unit,
    onContinueTwoPlayers: () -> Unit,
) {
    if (!canResume && !canAnalyse) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Default.MoreVert, contentDescription = null)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        if (canAnalyse) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.Analyse_Game)) },
                onClick = {
                    expanded = false
                    onAnalyse()
                },
            )
        }
        if (!canResume) return@DropdownMenu
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
            val panelHeight = if (uiModel.review != null) ReviewPanelHeight else 0.dp
            val side = min(maxWidth, maxHeight - buttonHeight - panelHeight).coerceAtLeast(0.dp)
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
                uiModel.review?.let { review ->
                    ReviewPanel(review, uiModel, onAction, Modifier.fillMaxWidth().height(ReviewPanelHeight))
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
                    uiModel.review?.let { review ->
                        ReviewPanel(
                            review,
                            uiModel,
                            onAction,
                            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(ReviewPanelHeight),
                        )
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

/** Height of [ReviewPanel]. */
private val ReviewPanelHeight = 112.dp

/**
 * The level-101 analysis of the displayed position: a progress bar while it runs; then the
 * win / draw / win bar in the players' colors, the verdict on the last move (a better move is
 * shown on the board as a translucent ring), and buttons jumping between mistakes.
 */
@Composable
private fun ReviewPanel(
    review: ReviewUiModel,
    uiModel: SavedGameViewerUiModel,
    onAction: (SavedGameViewerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val progress = review.progress
        if (progress != null) {
            Text(
                "${stringResource(R.string.Analysing)} ${(progress * 100).toInt()} %",
                color = DefaultTextColor,
            )
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            return@Column
        }
        review.outlook?.let { OutlookBar(it, uiModel.board.player1Color, uiModel.board.player2Color) }
        review.lastMove?.let { verdict ->
            val color = if (review.lastMovePlayer == 1) uiModel.board.player1Color else uiModel.board.player2Color
            Text(
                text = "${stringResource(R.string.Ring)} ${uiModel.cursor} : ${stringResource(verdict.label())}",
                color = if (verdict.isMistake) MistakeTextColor else DefaultTextColor,
                fontWeight = if (verdict.isMistake) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .drawBehind { drawRect(color, size = Size(6.dp.toPx(), size.height)) }
                    .padding(start = 10.dp),
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(
                onClick = { onAction(SavedGameViewerAction.PreviousMistakeClicked) },
                enabled = review.hasPreviousMistake,
            ) { Text("\u25C0 ${stringResource(R.string.Previous_Mistake)}") }
            TextButton(
                onClick = { onAction(SavedGameViewerAction.NextMistakeClicked) },
                enabled = review.hasNextMistake,
            ) { Text("${stringResource(R.string.Next_Mistake)} \u25B6") }
        }
    }
}

/** Three segments proportional to the outlook, labelled with rounded percentages. */
@Composable
private fun OutlookBar(outlook: Outlook, player1Color: Color, player2Color: Color) {
    val drawLabel = stringResource(R.string.Draw_Short)
    Row(modifier = Modifier.fillMaxWidth().height(22.dp)) {
        val segments = listOf(
            Triple(outlook.player1Wins, player1Color, ""),
            Triple(outlook.draw, DrawColor, "$drawLabel "),
            Triple(outlook.player2Wins, player2Color, ""),
        )
        for ((share, color, label) in segments) {
            if (share < 0.005f) continue
            Box(
                modifier = Modifier.weight(share).fillMaxHeight().background(color),
                contentAlignment = Alignment.Center,
            ) {
                if (share >= 0.12f) {
                    Text("$label${(share * 100).roundToInt()} %", color = Color.White, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}

private fun MoveVerdict.label(): Int = when (this) {
    MoveVerdict.BEST -> R.string.Verdict_Best
    MoveVerdict.GOOD -> R.string.Verdict_Good
    MoveVerdict.INACCURACY -> R.string.Verdict_Inaccuracy
    MoveVerdict.MISTAKE -> R.string.Verdict_Mistake
    MoveVerdict.BLUNDER -> R.string.Verdict_Blunder
    MoveVerdict.MISSED_WIN -> R.string.Verdict_Missed_Win
    MoveVerdict.ALLOWED_WIN -> R.string.Verdict_Allowed_Win
}

private val MistakeTextColor = Color(0xFFB00020)
private val DrawColor = Color(0xFF9E9E9E)

/** `ButtonNextPrevious`' `ratioWidthOnHeight`. */
private const val BUTTON_RATIO = 1.2f

/** A plain legacy `TextView`'s color (`textDescriptionAnneau`): Material light's `textColorSecondary`. */
private val DefaultTextColor = Color(0x8A000000)
