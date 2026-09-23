package fr.thibautperrin.tetrano.ui.rules

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.thibautperrin.tetrano.ui.components.BoardVisualState
import fr.thibautperrin.tetrano.ui.theme.TetranoRingColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Backs [WinningExamplesScreen] — replaces `ExamplesActivity` (see
 * docs/GLOSSARY.md, docs/PLAN.md P8). The 6 boards are the exact ring
 * groups docs/PLAN.md P8's table lists; each is built once here as a
 * static, non-interactive [BoardVisualState] (`trayPlayer = 0`, same
 * convention as
 * [fr.thibautperrin.tetrano.ui.savedgames.SavedGameViewerScreen]) with
 * only that example's 4 rings set to player 1's color — no drag, no
 * second color needed.
 */
@HiltViewModel
class WinningExamplesViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(WinningExamplesUiModel(examples = buildExamples()))
    val uiState: StateFlow<WinningExamplesUiModel> = _uiState.asStateFlow()

    private fun buildExamples(): List<WinningExampleUiModel> =
        EXAMPLES.map { (kind, rings) -> WinningExampleUiModel(kind, boardOf(rings)) }

    private fun boardOf(winningRings: IntArray): BoardVisualState {
        val rings = ByteArray(64)
        for (ring in winningRings) rings[ring] = 1
        return BoardVisualState(
            rings = rings,
            player1Color = TetranoRingColors.Red,
            player2Color = TetranoRingColors.Blue,
        )
    }

    private companion object {
        // docs/PLAN.md P8's table, ring numbers as-is.
        val EXAMPLES = listOf(
            WinningExampleKind.DIAGONAL_CONSTANT_SIZE to intArrayOf(48, 53, 58, 63),
            WinningExampleKind.ROW_CONSTANT_SIZE to intArrayOf(40, 41, 42, 43),
            WinningExampleKind.CONCENTRIC to intArrayOf(9, 25, 41, 57),
            WinningExampleKind.COLUMN_INCREASING_SIZE to intArrayOf(2, 22, 42, 62),
            WinningExampleKind.DIAGONAL_INCREASING_SIZE to intArrayOf(12, 25, 38, 51),
            WinningExampleKind.COLUMN_CONSTANT_SIZE to intArrayOf(1, 5, 9, 13),
        )
    }
}
