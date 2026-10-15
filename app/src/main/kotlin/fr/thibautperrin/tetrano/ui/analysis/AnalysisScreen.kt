package fr.thibautperrin.tetrano.ui.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.LastPage
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.thibautperrin.tetrano.R
import fr.thibautperrin.tetrano.domain.service.LiveAnalysisService
import fr.thibautperrin.tetrano.ui.components.BoardWithPanel
import fr.thibautperrin.tetrano.ui.components.LegacyTopBar
import java.text.NumberFormat
import kotlin.math.roundToInt

/**
 * Wires [AnalysisViewModel] to the stateless [AnalysisScreen]. Both resume
 * callbacks receive the number of rings currently displayed (legacy
 * `ringsPlayedUntilCursor`), not the whole save.
 */
@Composable
fun AnalysisRoute(
    onContinueTwoPlayers: (savedGameName: String, ringCount: Int) -> Unit,
    onContinueAgainstComputer: (savedGameName: String, ringCount: Int) -> Unit,
    viewModel: AnalysisViewModel = hiltViewModel(),
) {
    val uiModel by viewModel.uiState.collectAsState()
    AnalysisScreen(
        uiModel = uiModel,
        onAction = viewModel::onAction,
        onContinueAgainstComputer = { onContinueAgainstComputer(uiModel.gameName, uiModel.cursor) },
        onContinueTwoPlayers = { onContinueTwoPlayers(uiModel.gameName, uiModel.cursor) },
    )
}

/**
 * The analysis of a game, or of the empty board: the board (moves are played by dragging a
 * ring from the tray, for either side) with the network's recommended move as a translucent
 * ring; below (or beside) it, the outlook bar and the search's progress, the verdict on the
 * last move of the game with jumps between mistakes, the move navigation, and the variation
 * on display, if any.
 */
@Composable
fun AnalysisScreen(
    uiModel: AnalysisUiModel,
    onAction: (AnalysisAction) -> Unit,
    onContinueAgainstComputer: () -> Unit,
    onContinueTwoPlayers: () -> Unit,
) {
    Scaffold(
        containerColor = PageColor,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            LegacyTopBar(
                title = uiModel.gameName.ifEmpty {
                    stringResource(if (uiModel.freePlay) R.string.analysis_empty_board else R.string.analysis_title)
                },
                actions = {
                    if (uiModel.canResume) {
                        ResumeMenu(onContinueAgainstComputer = onContinueAgainstComputer, onContinueTwoPlayers = onContinueTwoPlayers)
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
        if (uiModel.isLoading) return@Scaffold
        BoardWithPanel(
            board = uiModel.board,
            onRingDropped = { onAction(AnalysisAction.RingDropped(it)) },
            modifier = Modifier.padding(padding),
            minPanelHeight = 230.dp,
        ) {
            uiModel.variation?.let { VariationBanner(it, onAction) }
            LivePanel(uiModel, onAction)
            uiModel.review?.let { ReviewPanel(it, uiModel, onAction) }
            Navigation(uiModel, onAction)
            if (uiModel.lineLength == 0 || (uiModel.variation == null && uiModel.cursor == uiModel.lineLength && !uiModel.gameOver)) {
                Text(stringResource(R.string.analysis_play_hint), fontSize = 13.sp, color = HintColor)
            }
        }
    }
}

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

/** The outlook bar, the search's progress or proven result, and the analysis depth. */
@Composable
private fun LivePanel(uiModel: AnalysisUiModel, onAction: (AnalysisAction) -> Unit) {
    val live = uiModel.live
    val format = NumberFormat.getIntegerInstance()
    if (live != null) OutlookBar(live.outlook, uiModel.board.player1Color, uiModel.board.player2Color)
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (live?.running == true) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = AccentColor)
            Spacer(Modifier.width(8.dp))
        }
        val status = when {
            uiModel.gameOver -> stringResource(R.string.analysis_game_over)
            live == null -> stringResource(R.string.Analysing)
            live.provenWinner != null -> provenText(live.provenWinner)
            else -> stringResource(R.string.analysis_variations, format.format(live.simulations))
        }
        val winner = live?.provenWinner?.takeIf { it > 0 && !uiModel.gameOver }
        if (winner != null) {
            val color = if (winner == 1) uiModel.board.player1Color else uiModel.board.player2Color
            Box(Modifier.size(12.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
        }
        Text(status, fontSize = 14.sp, color = BodyColor, modifier = Modifier.weight(1f))
        DepthChip(uiModel.depth, onAction)
    }
    if (live != null && live.bestRing >= 0 && !uiModel.gameOver) {
        val color = if (live.playerToMove == 1) uiModel.board.player1Color else uiModel.board.player2Color
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(color.copy(alpha = 0.45f)))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.analysis_best_move), fontSize = 13.sp, color = HintColor)
        }
    }
}

@Composable
private fun provenText(winner: Int): String = when (winner) {
    0 -> stringResource(R.string.analysis_forced_draw)
    else -> stringResource(R.string.analysis_forced_win)
}

/** A chip showing the analysis depth, opening the list of choices. */
@Composable
private fun DepthChip(depth: Int, onAction: (AnalysisAction) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val format = NumberFormat.getIntegerInstance()
    Box {
        AssistChip(
            onClick = { expanded = true },
            label = { Text(stringResource(R.string.analysis_depth, format.format(depth)), fontSize = 12.sp) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (choice in LiveAnalysisService.DEPTH_CHOICES) {
                DropdownMenuItem(
                    text = {
                        Text(
                            format.format(choice),
                            fontWeight = if (choice == depth) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        expanded = false
                        onAction(AnalysisAction.DepthSelected(choice))
                    },
                )
            }
        }
    }
}

/** The review of the game's moves: progress while it runs, then the last move's verdict and the jumps between mistakes. */
@Composable
private fun ReviewPanel(review: ReviewUiModel, uiModel: AnalysisUiModel, onAction: (AnalysisAction) -> Unit) {
    val progress = review.progress
    if (progress != null) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.analysis_reviewing, (progress * 100).roundToInt()), fontSize = 13.sp, color = HintColor)
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = AccentColor)
        }
    }
    review.lastMove?.let { verdict ->
        val color = if (review.lastMovePlayer == 1) uiModel.board.player1Color else uiModel.board.player2Color
        Text(
            text = "${stringResource(R.string.Ring)} ${uiModel.cursor} : ${stringResource(verdict.label())}",
            color = if (verdict.isMistake) MistakeTextColor else BodyColor,
            fontWeight = if (verdict.isMistake) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier
                .drawBehind { drawRect(color, size = Size(6.dp.toPx(), size.height)) }
                .padding(start = 12.dp),
        )
    }
    if (review.hasPreviousMistake || review.hasNextMistake) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(
                onClick = { onAction(AnalysisAction.PreviousMistakeClicked) },
                enabled = review.hasPreviousMistake,
            ) { Text("◀ ${stringResource(R.string.Previous_Mistake)}") }
            TextButton(
                onClick = { onAction(AnalysisAction.NextMistakeClicked) },
                enabled = review.hasNextMistake,
            ) { Text("${stringResource(R.string.Next_Mistake)} ▶") }
        }
    }
}

/** First / previous / "Ring n / N" / next / last. */
@Composable
private fun Navigation(uiModel: AnalysisUiModel, onAction: (AnalysisAction) -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            IconButton(onClick = { onAction(AnalysisAction.FirstClicked) }, enabled = uiModel.cursor > 0) {
                Icon(Icons.Default.FirstPage, contentDescription = stringResource(R.string.analysis_first))
            }
            IconButton(onClick = { onAction(AnalysisAction.PreviousClicked) }, enabled = uiModel.cursor > 0) {
                Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = stringResource(R.string.previous))
            }
            Text(
                "${stringResource(R.string.Ring)} ${uiModel.cursor} / ${uiModel.lineLength}",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
                color = BodyColor,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onAction(AnalysisAction.NextClicked) }, enabled = uiModel.cursor < uiModel.lineLength) {
                Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = stringResource(R.string.Next))
            }
            IconButton(onClick = { onAction(AnalysisAction.LastClicked) }, enabled = uiModel.cursor < uiModel.lineLength) {
                Icon(Icons.Default.LastPage, contentDescription = stringResource(R.string.analysis_last))
            }
        }
    }
}

@Composable
private fun VariationBanner(variation: VariationUiModel, onAction: (AnalysisAction) -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = VariationColor, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)) {
            Text(
                stringResource(R.string.analysis_variation_from, variation.fromMove),
                fontSize = 14.sp,
                color = BodyColor,
                modifier = Modifier.weight(1f),
            )
            FilledTonalButton(onClick = { onAction(AnalysisAction.BackToGameClicked) }) {
                Text(stringResource(R.string.analysis_back_to_game), fontSize = 13.sp)
            }
        }
    }
}

/** Three segments proportional to the outlook, labelled with rounded percentages. */
@Composable
private fun OutlookBar(outlook: Outlook, player1Color: Color, player2Color: Color) {
    val drawLabel = stringResource(R.string.Draw_Short)
    Row(modifier = Modifier.fillMaxWidth().height(24.dp).clip(RoundedCornerShape(12.dp))) {
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

private val PageColor = Color(0xFFF2F3F7)
private val BodyColor = Color(0xFF2B2B2B)
private val HintColor = Color(0xFF6B7280)
private val AccentColor = Color(0xFF1F2A44)
private val VariationColor = Color(0xFFFFF3C4)
private val MistakeTextColor = Color(0xFFB00020)
private val DrawColor = Color(0xFF9E9E9E)
