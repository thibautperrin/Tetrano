package fr.thibautperrin.tetrano.ui.savedgames

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.thibautperrin.tetrano.R
import fr.thibautperrin.tetrano.domain.model.SavedGameSummary
import fr.thibautperrin.tetrano.ui.components.BoardCanvas
import fr.thibautperrin.tetrano.ui.components.BoardOrientation
import fr.thibautperrin.tetrano.ui.components.BoardVisualState

/** Wires [SavedGamesViewModel] to the stateless [SavedGamesScreen], and handles its 3 navigation actions. */
@Composable
fun SavedGamesRoute(
    onWatch: (String) -> Unit,
    onContinueTwoPlayers: (String) -> Unit,
    onContinueAgainstComputer: (String) -> Unit,
    viewModel: SavedGamesViewModel = hiltViewModel(),
) {
    val uiModel by viewModel.uiState.collectAsState()
    SavedGamesScreen(
        uiModel = uiModel,
        onAction = { action ->
            when (action) {
                is SavedGamesAction.WatchClicked -> onWatch(action.name)
                is SavedGamesAction.ContinueTwoPlayersClicked -> onContinueTwoPlayers(action.name)
                is SavedGamesAction.ContinueAgainstComputerClicked -> onContinueAgainstComputer(action.name)
                else -> viewModel.onAction(action)
            }
        },
    )
}

/** Ports `ListePartiesEnregistreesActivity` (see docs/GLOSSARY.md, docs/PLAN.md P8). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedGamesScreen(uiModel: SavedGamesUiModel, onAction: (SavedGamesAction) -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.Saved_Games)) }) },
    ) { padding ->
        if (!uiModel.isLoading && uiModel.games.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.Sentence_No_Saved_Game))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(uiModel.games, key = { it.name }) { game ->
                    SavedGameRow(game = game, onAction = onAction)
                }
            }
        }
    }

    uiModel.renameTarget?.let {
        RenameDialog(draft = uiModel.renameDraft, error = uiModel.renameError, onAction = onAction)
    }

    if (uiModel.deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { onAction(SavedGamesAction.DeleteDismissed) },
            text = { Text(stringResource(R.string.Sentence_Confirm_Delete_Game)) },
            confirmButton = {
                TextButton(onClick = { onAction(SavedGamesAction.DeleteConfirmed) }) { Text(stringResource(R.string.Confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { onAction(SavedGamesAction.DeleteDismissed) }) { Text(stringResource(R.string.Cancel)) }
            },
        )
    }
}

@Composable
private fun SavedGameRow(game: SavedGameSummary, onAction: (SavedGamesAction) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BoardCanvas(
                    state = BoardVisualState(
                        rings = game.matrix(),
                        player1Color = Color(game.userColor),
                        player2Color = Color(game.adversaryColor),
                    ),
                    orientation = BoardOrientation.PORTRAIT,
                    onRingDropped = {},
                    modifier = Modifier.width(64.dp),
                )
                Column(modifier = Modifier.padding(start = 4.dp)) {
                    Text(game.name)
                    Row {
                        IconButton(onClick = { onAction(SavedGamesAction.WatchClicked(game.name)) }) {
                            Icon(Icons.Default.Visibility, contentDescription = stringResource(R.string.Watch))
                        }
                        IconButton(onClick = { onAction(SavedGamesAction.RenameClicked(game.name)) }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.Rename))
                        }
                        IconButton(onClick = { onAction(SavedGamesAction.DeleteClicked(game.name)) }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.Delete))
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onAction(SavedGamesAction.ContinueTwoPlayersClicked(game.name)) }) {
                    Text(stringResource(R.string.Continue_In_Two_Players_Mode))
                }
                TextButton(onClick = { onAction(SavedGamesAction.ContinueAgainstComputerClicked(game.name)) }) {
                    Text(stringResource(R.string.Continue_Against_Computer))
                }
            }
        }
    }
}

@Composable
private fun RenameDialog(draft: String, error: Boolean, onAction: (SavedGamesAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAction(SavedGamesAction.RenameDismissed) },
        title = { Text(stringResource(R.string.Rename_A_Saved_Game)) },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { onAction(SavedGamesAction.RenameDraftChanged(it)) },
                label = { Text(stringResource(R.string.Enter_A_Name)) },
                isError = error,
                supportingText = if (error) {
                    { Text(stringResource(R.string.name_already_used)) }
                } else {
                    null
                },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onAction(SavedGamesAction.RenameConfirmed) }) { Text(stringResource(R.string.Confirm)) }
        },
        dismissButton = {
            TextButton(onClick = { onAction(SavedGamesAction.RenameDismissed) }) { Text(stringResource(R.string.Cancel)) }
        },
    )
}
