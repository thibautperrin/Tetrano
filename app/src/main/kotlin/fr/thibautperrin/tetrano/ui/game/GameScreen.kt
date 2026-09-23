package fr.thibautperrin.tetrano.ui.game

import android.content.res.Configuration
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.thibautperrin.tetrano.R
import fr.thibautperrin.tetrano.domain.model.GameMode
import fr.thibautperrin.tetrano.domain.model.StarAward
import fr.thibautperrin.tetrano.ui.components.BoardCanvas
import fr.thibautperrin.tetrano.ui.components.BoardOrientation

/** Wires [GameViewModel] (created for [mode] via its assisted [GameViewModel.Factory]) to the stateless [GameScreen]. */
@Composable
fun GameRoute(
    mode: GameMode,
    savedGameName: String?,
    onNavigateToSettings: () -> Unit,
    onExit: () -> Unit,
    viewModel: GameViewModel = hiltViewModel(
        creationCallback = { factory: GameViewModel.Factory -> factory.create(mode, savedGameName) },
    ),
) {
    val uiModel by viewModel.uiState.collectAsState()
    GameScreen(
        mode = mode,
        uiModel = uiModel,
        onAction = viewModel::onAction,
        onNavigateToSettings = onNavigateToSettings,
        onExit = onExit,
    )
}

/** Ports `JeuActivity` (see docs/GLOSSARY.md, docs/PLAN.md P8). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    mode: GameMode,
    uiModel: GameUiModel,
    onAction: (GameAction) -> Unit,
    onNavigateToSettings: () -> Unit,
    onExit: () -> Unit,
) {
    val orientation = if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        BoardOrientation.LANDSCAPE
    } else {
        BoardOrientation.PORTRAIT
    }
    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(gameTitle(mode, uiModel)) },
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
                            text = { Text(stringResource(R.string.Save_The_Game)) },
                            onClick = {
                                menuExpanded = false
                                onAction(GameAction.SaveClicked)
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
                orientation = orientation,
                onRingDropped = { onAction(GameAction.RingDropped(it)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { onAction(GameAction.UndoClicked) },
                    enabled = uiModel.canUndo,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.undo)) }
                Button(
                    onClick = { onAction(GameAction.RestartClicked) },
                    enabled = !uiModel.isBusy,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.restart)) }
                Button(
                    onClick = { onAction(GameAction.BlinkClicked) },
                    enabled = uiModel.canBlink,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.Mot_Clignoter)) }
            }
        }
    }

    if (uiModel.showSaveDialog) {
        AlertDialog(
            onDismissRequest = { onAction(GameAction.SaveDismissed) },
            title = { Text(stringResource(R.string.Save_The_Game)) },
            text = {
                OutlinedTextField(
                    value = uiModel.saveNameDraft,
                    onValueChange = { onAction(GameAction.SaveNameChanged(it)) },
                    label = { Text(stringResource(R.string.Enter_A_Name)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { onAction(GameAction.SaveConfirmed) }) {
                    Text(stringResource(R.string.Save_The_Game))
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(GameAction.SaveDismissed) }) {
                    Text(stringResource(R.string.Cancel))
                }
            },
        )
    }

    uiModel.lastSavedName?.let {
        AlertDialog(
            onDismissRequest = { onAction(GameAction.SavedNameAcknowledged) },
            title = { Text(stringResource(R.string.Settings_Saved)) },
            text = { Text(it) },
            confirmButton = {
                TextButton(onClick = { onAction(GameAction.SavedNameAcknowledged) }) {
                    Text(stringResource(R.string.OK))
                }
            },
        )
    }

    if (uiModel.starAwards.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { onAction(GameAction.StarAwardsDismissed) },
            title = { Text(stringResource(R.string.Explanation_Star)) },
            text = { Text(uiModel.starAwards.map { starAwardMessage(it) }.joinToString("\n\n")) },
            confirmButton = {
                TextButton(onClick = { onAction(GameAction.StarAwardsDismissed) }) {
                    Text(stringResource(R.string.OK))
                }
            },
        )
    }
}

// GestionJeu2Joueurs.mettreLeBonTitre / GestionJeuContreOrdinateur.mettreLeBonTitre, ported.
@Composable
private fun gameTitle(mode: GameMode, uiModel: GameUiModel): String {
    val player1Name = uiModel.player1Name.ifBlank { stringResource(R.string.Joueur1) }
    val player2Name = uiModel.player2Name.ifBlank {
        if (mode == GameMode.VS_COMPUTER) stringResource(R.string.Computer) else stringResource(R.string.Joueur2)
    }
    if (uiModel.isFinished) {
        return when {
            uiModel.isDraw || uiModel.winner == null -> stringResource(R.string.Sentence_End_Null)
            mode == GameMode.VS_COMPUTER -> {
                if (uiModel.winner == 1) stringResource(R.string.Sentence_End_Win) else stringResource(R.string.Sentence_End_Lost)
            }
            else -> {
                val winnerName = if (uiModel.winner == 1) player1Name else player2Name
                "${stringResource(R.string.Winner)}: $winnerName"
            }
        }
    }
    return when (mode) {
        GameMode.VS_COMPUTER -> when {
            uiModel.playedCount == 0 && uiModel.nextPlayer == 1 -> stringResource(R.string.Sentence_Begin_Start)
            uiModel.nextPlayer == 1 -> "$player1Name ${stringResource(R.string.Your_Turn)}"
            else -> stringResource(R.string.Sentence_Computer_Work)
        }
        else -> {
            val name = if (uiModel.nextPlayer == 1) player1Name else player2Name
            if (uiModel.playedCount == 0) "$name${stringResource(R.string.You_Can_Start)}" else "$name, ${stringResource(R.string.Your_Turn)}"
        }
    }
}

@Composable
private fun starAwardMessage(award: StarAward): String = when (award) {
    is StarAward.GamesPlayed -> when (award.mode) {
        GameMode.TWO_PLAYERS -> stringResource(R.string.Star_Won_Two_Players, award.count)
        GameMode.VS_COMPUTER -> stringResource(R.string.Star_Won_Against_Computer, award.count)
        else -> stringResource(R.string.Star_Won_Two_Players, award.count)
    }
    is StarAward.GamesWonAgainstComputer -> stringResource(R.string.Star_Won_Win_Against_Computer, award.count)
    StarAward.AllModesTried -> stringResource(R.string.Star_Won_All_Games)
}
