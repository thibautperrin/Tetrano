package fr.thibautperrin.tetrano.ui.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.thibautperrin.tetrano.R
import fr.thibautperrin.tetrano.ui.components.BoardCanvas
import fr.thibautperrin.tetrano.ui.components.BoardOrientation

/** Wires [WinningExamplesViewModel] to the stateless [WinningExamplesScreen]. */
@Composable
fun WinningExamplesRoute(viewModel: WinningExamplesViewModel = hiltViewModel()) {
    val uiModel by viewModel.uiState.collectAsState()
    WinningExamplesScreen(uiModel = uiModel)
}

/**
 * Ports `ExamplesActivity` (see docs/GLOSSARY.md, docs/PLAN.md P8): 6
 * static, non-interactive boards, one per winning-line geometry (see
 * [WinningExampleKind]'s KDoc for how each was inferred), scrolled in a
 * single column.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WinningExamplesScreen(uiModel: WinningExamplesUiModel) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.Winning_Situation_Example)) }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            items(uiModel.examples) { example ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(example.kind.labelRes()))
                    BoardCanvas(
                        state = example.board,
                        orientation = BoardOrientation.PORTRAIT,
                        onRingDropped = {},
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private fun WinningExampleKind.labelRes(): Int = when (this) {
    WinningExampleKind.DIAGONAL_CONSTANT_SIZE -> R.string.winning_example_diagonal_constant_size
    WinningExampleKind.ROW_CONSTANT_SIZE -> R.string.winning_example_row_constant_size
    WinningExampleKind.CONCENTRIC -> R.string.winning_example_concentric
    WinningExampleKind.COLUMN_INCREASING_SIZE -> R.string.winning_example_column_increasing_size
    WinningExampleKind.DIAGONAL_INCREASING_SIZE -> R.string.winning_example_diagonal_increasing_size
    WinningExampleKind.COLUMN_CONSTANT_SIZE -> R.string.winning_example_column_constant_size
}
