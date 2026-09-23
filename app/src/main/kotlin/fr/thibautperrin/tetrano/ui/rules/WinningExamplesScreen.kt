package fr.thibautperrin.tetrano.ui.rules

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.hilt.navigation.compose.hiltViewModel
import fr.thibautperrin.tetrano.R
import fr.thibautperrin.tetrano.ui.components.BoardCanvas
import fr.thibautperrin.tetrano.ui.components.BoardOrientation
import fr.thibautperrin.tetrano.ui.components.LegacyTopBar
import fr.thibautperrin.tetrano.ui.theme.LegacyColors

/** Wires [WinningExamplesViewModel] to the stateless [WinningExamplesScreen]. */
@Composable
fun WinningExamplesRoute(viewModel: WinningExamplesViewModel = hiltViewModel()) {
    val uiModel by viewModel.uiState.collectAsState()
    WinningExamplesScreen(uiModel = uiModel)
}

/**
 * Ports `ExamplesActivity` (see docs/GLOSSARY.md, docs/PLAN.md P8) and its
 * `activity_examples` layouts, under the dark action bar
 * (`Winning_Situation_Example`): 6 unlabeled square `GrilleView`s — black
 * grid, blue rings, no empty slots, no background — side by side with a
 * 1dp margin around them: 2 columns (1–3 | 4–6) in portrait, 3 columns
 * (1–2 | 3–4 | 5–6) in landscape. Boards are sized to fit the window
 * without scrolling; only when that would make them smaller than
 * [MIN_BOARD_SIDE] do they keep that size and the page scrolls.
 */
@Composable
fun WinningExamplesScreen(uiModel: WinningExamplesUiModel) {
    Scaffold(
        containerColor = LegacyColors.Page,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { LegacyTopBar(title = stringResource(R.string.Winning_Situation_Example)) },
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(1.dp),
        ) {
            val columns = if (maxWidth > maxHeight) 3 else 2
            val rows = (uiModel.examples.size + columns - 1) / columns
            val widthSide = maxWidth / columns
            val fittingSide = if (rows == 0) widthSide else min(widthSide, maxHeight / rows)
            val side = if (fittingSide >= MIN_BOARD_SIDE) fittingSide else min(widthSide, MIN_BOARD_SIDE)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row {
                    uiModel.examples.chunked(rows.coerceAtLeast(1)).forEach { column ->
                        Column {
                            column.forEach { board ->
                                // GrilleView.onDraw(): Dessin.dessineGrille(noir) + blue rings only.
                                BoardCanvas(
                                    state = board,
                                    orientation = BoardOrientation.SQUARE,
                                    onRingDropped = {},
                                    emptyRingColor = Color.Transparent,
                                    backgroundColor = Color.Transparent,
                                    modifier = Modifier.size(side),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Below this, a board is too small to read: scroll instead. */
private val MIN_BOARD_SIDE = 110.dp
