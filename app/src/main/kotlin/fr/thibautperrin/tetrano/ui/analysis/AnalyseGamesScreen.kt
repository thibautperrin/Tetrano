package fr.thibautperrin.tetrano.ui.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.thibautperrin.tetrano.R
import fr.thibautperrin.tetrano.ui.components.LegacyTopBar
import fr.thibautperrin.tetrano.ui.theme.TetranoPalette

/** Wires [AnalyseGamesViewModel] to the stateless [AnalyseGamesScreen]. */
@Composable
fun AnalyseGamesRoute(
    onOpenGame: (name: String) -> Unit,
    onOpenEmptyBoard: () -> Unit,
    viewModel: AnalyseGamesViewModel = hiltViewModel(),
) {
    val uiModel by viewModel.uiState.collectAsState()
    AnalyseGamesScreen(uiModel, viewModel::onAction, onOpenGame, onOpenEmptyBoard)
}

/**
 * The games to analyse: the empty board first (to explore any position), then the saved games,
 * newest first, each with its number of rings and its result; a game's menu renames or deletes it.
 */
@Composable
fun AnalyseGamesScreen(
    uiModel: AnalyseGamesUiModel,
    onAction: (AnalyseGamesAction) -> Unit,
    onOpenGame: (name: String) -> Unit,
    onOpenEmptyBoard: () -> Unit,
) {
    Scaffold(
        containerColor = PageColor,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { LegacyTopBar(title = stringResource(R.string.Analyse_Games)) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "empty") { EmptyBoardCard(onOpenEmptyBoard) }
                if (!uiModel.isLoading && uiModel.games.isEmpty()) {
                    item(key = "none") {
                        Text(
                            stringResource(R.string.analysis_no_games),
                            color = HintColor,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
                items(uiModel.games, key = { it.name }) { game ->
                    GameCard(game, onClick = { onOpenGame(game.name) }, onAction = onAction)
                }
            }
        }
    }

    uiModel.renameTarget?.let { RenameDialog(uiModel.renameDraft, uiModel.renameError, onAction) }
    if (uiModel.deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { onAction(AnalyseGamesAction.DeleteDismissed) },
            text = { Text(stringResource(R.string.Sentence_Confirm_Delete_Game)) },
            confirmButton = {
                TextButton(onClick = { onAction(AnalyseGamesAction.DeleteConfirmed) }) { Text(stringResource(R.string.Delete)) }
            },
            dismissButton = {
                TextButton(onClick = { onAction(AnalyseGamesAction.DeleteDismissed) }) { Text(stringResource(R.string.Cancel)) }
            },
        )
    }
}

@Composable
private fun EmptyBoardCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = AccentColor, contentColor = TetranoPalette.OnAccent),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(20.dp)) {
            Icon(Icons.Default.GridOn, contentDescription = null, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(stringResource(R.string.analysis_empty_board), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.analysis_empty_board_hint), fontSize = 13.sp, color = TetranoPalette.OnAccent.copy(alpha = 0.75f))
            }
        }
    }
}

@Composable
private fun GameCard(game: SavedGameItem, onClick: () -> Unit, onAction: (AnalyseGamesAction) -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = TetranoPalette.Panel),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    game.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = BodyColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.analysis_rings_count, game.moves), fontSize = 13.sp, color = HintColor)
                    Text("  ·  ", fontSize = 13.sp, color = HintColor)
                    when (game.result) {
                        GameResult.WON -> {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(Color(game.winnerColor)))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.analysis_result_wins), fontSize = 13.sp, color = HintColor)
                        }
                        GameResult.DRAW -> Text(stringResource(R.string.Draw_Short), fontSize = 13.sp, color = HintColor)
                        GameResult.UNFINISHED -> Text(stringResource(R.string.analysis_result_unfinished), fontSize = 13.sp, color = HintColor)
                    }
                }
            }
            GameMenu(game.name, onAction)
        }
    }
}

@Composable
private fun GameMenu(name: String, onAction: (AnalyseGamesAction) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = null, tint = HintColor) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.Rename)) },
                onClick = {
                    expanded = false
                    onAction(AnalyseGamesAction.RenameClicked(name))
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.Delete)) },
                onClick = {
                    expanded = false
                    onAction(AnalyseGamesAction.DeleteClicked(name))
                },
            )
        }
    }
}

/** The legacy rename dialog: title `Rename_A_Saved_Game`, message `Enter_A_Name`, pre-filled field. */
@Composable
private fun RenameDialog(draft: String, error: Boolean, onAction: (AnalyseGamesAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAction(AnalyseGamesAction.RenameDismissed) },
        title = { Text(stringResource(R.string.Rename_A_Saved_Game)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.Enter_A_Name))
                OutlinedTextField(
                    value = draft,
                    onValueChange = { onAction(AnalyseGamesAction.RenameDraftChanged(it)) },
                    isError = error,
                    supportingText = if (error) {
                        { Text(stringResource(R.string.name_already_used)) }
                    } else {
                        null
                    },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAction(AnalyseGamesAction.RenameConfirmed) },
                enabled = draft.isNotBlank(),
            ) { Text(stringResource(R.string.Confirm)) }
        },
        dismissButton = {
            TextButton(onClick = { onAction(AnalyseGamesAction.RenameDismissed) }) { Text(stringResource(R.string.Cancel)) }
        },
    )
}

private val PageColor = TetranoPalette.Page
private val BodyColor = TetranoPalette.Text
private val HintColor = TetranoPalette.TextSecondary
private val AccentColor = TetranoPalette.Accent
