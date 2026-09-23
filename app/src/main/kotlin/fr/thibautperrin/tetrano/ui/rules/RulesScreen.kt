package fr.thibautperrin.tetrano.ui.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.thibautperrin.tetrano.R

/** Wires [RulesViewModel] to the stateless [RulesScreen]. */
@Composable
fun RulesRoute(
    onNavigateToWinningExamples: () -> Unit,
    viewModel: RulesViewModel = hiltViewModel(),
) {
    val uiModel by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.navigateToWinningExamples.collect { onNavigateToWinningExamples() }
    }
    RulesScreen(uiModel = uiModel, onAction = viewModel::onAction)
}

/**
 * Ports `NoticeFormelleActivity` (see docs/GLOSSARY.md, docs/PLAN.md P8):
 * the legacy rules text (`Long_Sentence_Rules`, ported verbatim from
 * seed_assets/strings/values/strings.xml) plus a button reaching
 * [WinningExamplesScreen].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(uiModel: RulesUiModel, onAction: (RulesAction) -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.Rules_Of_Game)) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(stringResource(R.string.Long_Sentence_Rules))
            Button(onClick = { onAction(RulesAction.WinningExamplesClicked) }) {
                Text(stringResource(R.string.Winning_Situation_Example))
            }
        }
    }
}
