package fr.thibautperrin.tetrano.ui.challenge

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.thibautperrin.tetrano.R
import fr.thibautperrin.tetrano.ui.components.BoardCanvas
import fr.thibautperrin.tetrano.ui.components.BoardOrientation

/** Wires [ChallengeViewModel] to the stateless [ChallengeScreen]. */
@Composable
fun ChallengeRoute(
    onNavigateToSettings: () -> Unit,
    onExit: () -> Unit,
    viewModel: ChallengeViewModel = hiltViewModel(),
) {
    val uiModel by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val allLevelsPassedMessage = stringResource(R.string.All_Levels_Passed)
    LaunchedEffect(Unit) {
        viewModel.exit.collect { reason ->
            if (reason is ChallengeExit.AllLevelsPassed) {
                Toast.makeText(context, allLevelsPassedMessage, Toast.LENGTH_LONG).show()
            }
            onExit()
        }
    }
    ChallengeScreen(uiModel = uiModel, onAction = viewModel::onAction, onNavigateToSettings = onNavigateToSettings)
}

/** Ports `ChallengeActivity` (see docs/GLOSSARY.md, docs/PLAN.md P8). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChallengeScreen(
    uiModel: ChallengeUiModel,
    onAction: (ChallengeAction) -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                // ChallengeActivity.updateTitle(): "%s      %s %d".
                title = { Text("${stringResource(R.string.Challenge)}      ${stringResource(R.string.Level)} ${uiModel.level}") },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_settings)) },
                            onClick = {
                                menuExpanded = false
                                onNavigateToSettings()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.Explanations)) },
                            onClick = {
                                menuExpanded = false
                                onAction(ChallengeAction.ExplanationsMenuClicked)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.Use_Star)) },
                            onClick = {
                                menuExpanded = false
                                onAction(ChallengeAction.UseStarMenuClicked)
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BoardCanvas(
                state = uiModel.board,
                orientation = BoardOrientation.PORTRAIT,
                onRingDropped = { onAction(ChallengeAction.RingDropped(it)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // updateTextAndButton()'s "%d / %d", tappable like onClickNbRings.
                Text(
                    text = "${uiModel.usedRings} / ${uiModel.authorizedRings}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.clickable { onAction(ChallengeAction.RingsCounterClicked) },
                )
                if (uiModel.canAbandon) {
                    Button(onClick = { onAction(ChallengeAction.AbandonClicked) }) {
                        Text(stringResource(R.string.Abandon_Challenge))
                    }
                }
            }
        }
    }

    if (uiModel.showRingsInfoDialog) {
        AlertDialog(
            onDismissRequest = { onAction(ChallengeAction.RingsInfoDismissed) },
            text = {
                Text(
                    stringResource(R.string.Explanation_Rings_Number) + "\n\n" +
                        stringResource(R.string.In_This_Level) + "\n\t" +
                        stringResource(R.string.Number_Of_Rings_Allowed) + " ${uiModel.authorizedRings}" + "\n\t" +
                        stringResource(R.string.Rings_used) + " ${uiModel.usedRings}",
                )
            },
            confirmButton = {
                TextButton(onClick = { onAction(ChallengeAction.RingsInfoDismissed) }) { Text(stringResource(R.string.OK)) }
            },
        )
    }

    when (uiModel.showExplanationsPage) {
        1 -> AlertDialog(
            onDismissRequest = {},
            title = { Text("${stringResource(R.string.Explanations)} 1/2") },
            text = { Text(stringResource(R.string.Explanations_Challenge1)) },
            confirmButton = {
                TextButton(onClick = { onAction(ChallengeAction.ExplanationsNextClicked) }) { Text(stringResource(R.string.Next)) }
            },
        )
        2 -> AlertDialog(
            onDismissRequest = { onAction(ChallengeAction.ExplanationsDismissed) },
            title = { Text("${stringResource(R.string.Explanations)} 2/2") },
            text = { Text(stringResource(R.string.Explanations_Challenge2)) },
            confirmButton = {
                TextButton(onClick = { onAction(ChallengeAction.ExplanationsDismissed) }) { Text(stringResource(R.string.Play)) }
            },
        )
    }

    if (uiModel.showNoStarsMessage) {
        AlertDialog(
            onDismissRequest = { onAction(ChallengeAction.NoStarsAcknowledged) },
            text = { Text(stringResource(R.string.No_More_Stars)) },
            confirmButton = {
                TextButton(onClick = { onAction(ChallengeAction.NoStarsAcknowledged) }) { Text(stringResource(R.string.OK)) }
            },
        )
    }

    if (uiModel.showConfirmUseStar) {
        AlertDialog(
            onDismissRequest = { onAction(ChallengeAction.UseStarDismissed) },
            title = { Text("${uiModel.numberStars} ★") },
            text = { Text(stringResource(R.string.Sentence_Confirm_Use_Star)) },
            confirmButton = {
                TextButton(onClick = { onAction(ChallengeAction.UseStarConfirmed) }) { Text(stringResource(R.string.OK)) }
            },
            dismissButton = {
                TextButton(onClick = { onAction(ChallengeAction.UseStarDismissed) }) { Text(stringResource(R.string.Cancel)) }
            },
        )
    }

    uiModel.endDialog?.let { dialog ->
        val (title, message, positiveLabel) = endDialogTexts(dialog)
        AlertDialog(
            onDismissRequest = { onAction(ChallengeAction.EndDialogQuitClicked) },
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { onAction(ChallengeAction.EndDialogContinueClicked) }) { Text(positiveLabel) }
            },
            dismissButton = {
                TextButton(onClick = { onAction(ChallengeAction.EndDialogQuitClicked) }) { Text(stringResource(R.string.Quit)) }
            },
        )
    }

    if (uiModel.showAllModesTriedAward) {
        AlertDialog(
            onDismissRequest = { onAction(ChallengeAction.AllModesTriedAwardAcknowledged) },
            title = { Text(stringResource(R.string.Explanation_Star)) },
            text = { Text(stringResource(R.string.Star_Won_All_Games)) },
            confirmButton = {
                TextButton(onClick = { onAction(ChallengeAction.AllModesTriedAwardAcknowledged) }) { Text(stringResource(R.string.OK)) }
            },
        )
    }
}

// displayDialogEnd(level, situation), ported.
@Composable
private fun endDialogTexts(dialog: ChallengeEndDialog): Triple<String, String, String> = when (dialog.situation) {
    ChallengeEndSituation.USER_WON -> Triple(
        stringResource(R.string.Sentence_End_Win),
        "${stringResource(R.string.You_Passed_Level)} ${dialog.level}",
        stringResource(R.string.Go_To_Next_Level),
    )
    ChallengeEndSituation.COMPUTER_WON -> Triple(
        stringResource(R.string.Sentence_End_Lost),
        "${stringResource(R.string.You_Lost_At_Level)} ${dialog.level}\n${stringResource(R.string.Computer_Won_Before)}",
        stringResource(R.string.Try_Again),
    )
    ChallengeEndSituation.RAN_OUT_OF_RINGS -> Triple(
        stringResource(R.string.Sentence_End_Lost),
        "${stringResource(R.string.You_Lost_At_Level)} ${dialog.level}\n${stringResource(R.string.Sentence_Number_Rings)}",
        stringResource(R.string.Try_Again),
    )
    ChallengeEndSituation.ABANDONED -> Triple(
        stringResource(R.string.Abandoned),
        "${stringResource(R.string.You_Lost_At_Level)} ${dialog.level}",
        stringResource(R.string.Try_Again),
    )
}
