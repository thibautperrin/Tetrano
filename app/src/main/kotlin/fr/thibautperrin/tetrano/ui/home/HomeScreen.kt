package fr.thibautperrin.tetrano.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.thibautperrin.tetrano.R
import fr.thibautperrin.tetrano.ui.components.IconTextButton
import fr.thibautperrin.tetrano.ui.components.StarBadge

/** Wires [HomeViewModel] to the stateless [HomeScreen]. */
@Composable
fun HomeRoute(
    onNavigateToPlayMenu: () -> Unit,
    onNavigateToRules: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiModel by viewModel.uiState.collectAsState()
    HomeScreen(
        uiModel = uiModel,
        onAction = { action ->
            when (action) {
                HomeAction.PlayClicked -> onNavigateToPlayMenu()
                HomeAction.RulesClicked -> onNavigateToRules()
                HomeAction.SettingsClicked -> onNavigateToSettings()
            }
        },
    )
}

/** Ports `MainActivity`'s menu (see docs/GLOSSARY.md, docs/PLAN.md P8). */
@Composable
fun HomeScreen(uiModel: HomeUiModel, onAction: (HomeAction) -> Unit) {
    var showAbout by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        StarBadge(
            count = uiModel.numberStars,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
        )
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IconTextButton(
                icon = rememberVectorPainter(Icons.Default.PlayArrow),
                text = stringResource(R.string.Play),
                onClick = { onAction(HomeAction.PlayClicked) },
            )
            IconTextButton(
                icon = rememberVectorPainter(Icons.Default.MenuBook),
                text = stringResource(R.string.Rules_Of_Game),
                onClick = { onAction(HomeAction.RulesClicked) },
            )
            IconTextButton(
                icon = rememberVectorPainter(Icons.Default.Settings),
                text = stringResource(R.string.action_settings),
                onClick = { onAction(HomeAction.SettingsClicked) },
            )
            IconTextButton(
                icon = rememberVectorPainter(Icons.Default.Info),
                text = stringResource(R.string.About),
                onClick = { showAbout = true },
            )
        }
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text(stringResource(R.string.About)) },
            text = { Text(stringResource(R.string.Long_Sentence_About)) },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) { Text(stringResource(R.string.OK)) }
            },
        )
    }
}
