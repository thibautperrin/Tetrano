package fr.thibautperrin.tetrano.ui.puzzles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.thibautperrin.tetrano.R
import fr.thibautperrin.tetrano.ui.components.BoardWithPanel
import fr.thibautperrin.tetrano.ui.components.LegacyTopBar
import fr.thibautperrin.tetrano.ui.theme.TetranoPalette

/** Wires [PuzzleViewModel] to the stateless [PuzzleScreen]. */
@Composable
fun PuzzleRoute(viewModel: PuzzleViewModel = hiltViewModel()) {
    val uiModel by viewModel.uiState.collectAsState()
    PuzzleScreen(uiModel, viewModel::onAction)
}

/**
 * A puzzle: the board, the player's rating in the bar, and below the board the goal ("win in
 * N moves"), then the outcome with the rating change, Retry and Next. The rules are explained
 * on the first visit and behind the info button.
 */
@Composable
fun PuzzleScreen(uiModel: PuzzleUiModel, onAction: (PuzzleAction) -> Unit) {
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var helpShownOnce by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(uiModel.isLoading, uiModel.attempts) {
        if (!uiModel.isLoading && uiModel.attempts == 0 && !helpShownOnce) {
            helpShownOnce = true
            showHelp = true
        }
    }
    Scaffold(
        containerColor = PageColor,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            LegacyTopBar(
                title = stringResource(R.string.Puzzles),
                actions = {
                    if (!uiModel.isLoading) {
                        Text(
                            stringResource(R.string.puzzle_rating, uiModel.rating),
                            color = TetranoPalette.Text,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    IconButton(onClick = { showHelp = true }) {
                        Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.Explanations))
                    }
                },
            )
        },
    ) { padding ->
        if (uiModel.isLoading) return@Scaffold
        BoardWithPanel(
            board = uiModel.board,
            onRingDropped = { onAction(PuzzleAction.RingDropped(it)) },
            modifier = Modifier.padding(padding),
            minPanelHeight = 190.dp,
        ) {
            Goal(uiModel)
            when (uiModel.phase) {
                PuzzlePhase.SOLVED -> Outcome(uiModel, success = true, onAction)
                PuzzlePhase.FAILED -> Outcome(uiModel, success = false, onAction)
                else -> if (uiModel.progressing) {
                    Text(stringResource(R.string.puzzle_correct), color = SuccessColor, fontWeight = FontWeight.SemiBold)
                }
            }
            Text(
                stringResource(R.string.puzzle_stats, uiModel.solved, uiModel.attempts),
                fontSize = 13.sp,
                color = HintColor,
            )
        }
    }
    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text(stringResource(R.string.Puzzles)) },
            text = { Text(stringResource(R.string.puzzle_explanations)) },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text(stringResource(R.string.OK)) } },
        )
    }
}

@Composable
private fun Goal(uiModel: PuzzleUiModel) {
    Surface(shape = RoundedCornerShape(16.dp), color = TetranoPalette.Panel, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(uiModel.board.player1Color))
            Spacer(Modifier.width(10.dp))
            Text(
                pluralStringResource(R.plurals.puzzle_find_win, uiModel.movesToWin, uiModel.movesToWin),
                style = MaterialTheme.typography.titleMedium,
                color = BodyColor,
            )
        }
    }
}

@Composable
private fun Outcome(uiModel: PuzzleUiModel, success: Boolean, onAction: (PuzzleAction) -> Unit) {
    Text(
        stringResource(if (success) R.string.puzzle_solved else R.string.puzzle_failed),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = if (success) SuccessColor else FailureColor,
    )
    if (!success) Text(stringResource(R.string.puzzle_solution_shown), fontSize = 14.sp, color = BodyColor)
    val delta = uiModel.ratingDelta
    if (delta != null) {
        val sign = if (delta >= 0) "+" else "−"
        Text(
            stringResource(R.string.puzzle_rating_change, uiModel.rating, "$sign${kotlin.math.abs(delta)}"),
            fontSize = 14.sp,
            color = if (delta >= 0) SuccessColor else FailureColor,
        )
    }
    uiModel.puzzleRating?.let { Text(stringResource(R.string.puzzle_difficulty, it), fontSize = 13.sp, color = HintColor) }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        if (!success) {
            OutlinedButton(onClick = { onAction(PuzzleAction.RetryClicked) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.puzzle_retry))
            }
        }
        Button(
            onClick = { onAction(PuzzleAction.NextClicked) },
            colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
            modifier = Modifier.weight(1f),
        ) { Text(stringResource(R.string.puzzle_next)) }
    }
    if (!success) Text(stringResource(R.string.puzzle_retry_note), fontSize = 12.sp, color = HintColor)
}

private val PageColor = TetranoPalette.Page
private val BodyColor = TetranoPalette.Text
private val HintColor = TetranoPalette.TextSecondary
private val AccentColor = TetranoPalette.Accent
private val SuccessColor = TetranoPalette.Success
private val FailureColor = TetranoPalette.Failure
