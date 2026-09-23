package fr.thibautperrin.tetrano.ui.playmenu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.thibautperrin.tetrano.R
import fr.thibautperrin.tetrano.ui.components.IconTextButton

/** Wires [PlayMenuViewModel] to the stateless [PlayMenuScreen]. */
@Composable
fun PlayMenuRoute(
    onNavigateToTwoPlayersSetup: () -> Unit,
    onNavigateToComputerSetup: () -> Unit,
    onNavigateToSavedGames: () -> Unit,
    onNavigateToChallenge: () -> Unit,
    viewModel: PlayMenuViewModel = hiltViewModel(),
) {
    val uiModel by viewModel.uiState.collectAsState()
    PlayMenuScreen(
        uiModel = uiModel,
        onAction = { action ->
            when (action) {
                PlayMenuAction.TwoPlayersClicked -> onNavigateToTwoPlayersSetup()
                PlayMenuAction.OnePlayerClicked -> onNavigateToComputerSetup()
                PlayMenuAction.SavedGamesClicked -> onNavigateToSavedGames()
                PlayMenuAction.ChallengeClicked -> onNavigateToChallenge()
                PlayMenuAction.OnlineClicked -> Unit
            }
        },
    )
}

/**
 * Ports `JouerActivity` (see docs/GLOSSARY.md, docs/PLAN.md P8) — minus the
 * Bluetooth button (removed, see docs/PLAN.md §10); "Online" is kept but
 * disabled.
 */
@Composable
fun PlayMenuScreen(uiModel: PlayMenuUiModel, onAction: (PlayMenuAction) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        IconTextButton(
            icon = rememberVectorPainter(Icons.Default.Groups),
            text = stringResource(R.string.Two_Players),
            onClick = { onAction(PlayMenuAction.TwoPlayersClicked) },
        )
        IconTextButton(
            icon = rememberVectorPainter(Icons.Default.SmartToy),
            text = stringResource(R.string.One_Player),
            onClick = { onAction(PlayMenuAction.OnePlayerClicked) },
        )
        IconTextButton(
            icon = rememberVectorPainter(Icons.Default.Save),
            text = stringResource(R.string.Saved_Games),
            onClick = { onAction(PlayMenuAction.SavedGamesClicked) },
        )
        IconTextButton(
            icon = rememberVectorPainter(Icons.Default.EmojiEvents),
            text = stringResource(R.string.Challenge),
            onClick = { onAction(PlayMenuAction.ChallengeClicked) },
        )
        IconTextButton(
            icon = rememberVectorPainter(Icons.Default.Wifi),
            text = stringResource(R.string.online_coming_soon),
            onClick = { onAction(PlayMenuAction.OnlineClicked) },
            modifier = Modifier.alpha(if (uiModel.onlineAvailable) 1f else 0.4f),
        )
    }
}
