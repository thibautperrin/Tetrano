package fr.thibautperrin.tetrano.ui.savedgames

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.thibautperrin.tetrano.R
import fr.thibautperrin.tetrano.ui.components.BoardCanvas
import fr.thibautperrin.tetrano.ui.components.BoardOrientation

/** Wires [SavedGameViewerViewModel] to the stateless [SavedGameViewerScreen]. */
@Composable
fun SavedGameViewerRoute(viewModel: SavedGameViewerViewModel = hiltViewModel()) {
    val uiModel by viewModel.uiState.collectAsState()
    SavedGameViewerScreen(uiModel = uiModel, onAction = viewModel::onAction)
}

/**
 * Ports `WatchSavedGameActivity` (see docs/GLOSSARY.md, docs/PLAN.md P8).
 * The board is rendered non-interactively (`trayPlayer = 0`, no
 * `onRingDropped` handling) — this screen only replays a finished
 * position, it never plays a move. `ButtonNextPrevious`'s hand-drawn
 * triangle glyph isn't reproduced (ui/components is out of this commit's
 * scope to extend); plain Material arrow icons serve the same purpose.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedGameViewerScreen(uiModel: SavedGameViewerUiModel, onAction: (SavedGameViewerAction) -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(uiModel.gameName) }) },
    ) { padding ->
        if (uiModel.notFound) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.Sentence_No_Saved_Game))
            }
            return@Scaffold
        }
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
                onRingDropped = {},
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onAction(SavedGameViewerAction.PreviousClicked) }, enabled = uiModel.canGoPrevious) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.previous))
                }
                // WatchSavedGameActivity.actualiseTextView(): "%s %d / %d".
                Text("${stringResource(R.string.Ring)} ${uiModel.cursor} / ${uiModel.totalMoves}")
                IconButton(onClick = { onAction(SavedGameViewerAction.NextClicked) }, enabled = uiModel.canGoNext) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.Next))
                }
            }
        }
    }
}
