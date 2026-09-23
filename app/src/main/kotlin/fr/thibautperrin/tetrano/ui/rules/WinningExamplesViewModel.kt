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
 * docs/GLOSSARY.md, docs/PLAN.md P8). Each of its 6 `anneaux` groups
 * becomes a static, non-interactive [BoardVisualState] (`trayPlayer = 0`)
 * with only those 4 rings set, as player 1, in `GrilleView`'s blue
 * (`R.color.bleu`).
 */
@HiltViewModel
class WinningExamplesViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(WinningExamplesUiModel(examples = EXAMPLES.map(::boardOf)))
    val uiState: StateFlow<WinningExamplesUiModel> = _uiState.asStateFlow()

    private companion object {
        // ExamplesActivity's anneaux[0..5], ring = x + 4y + 16*size.
        val EXAMPLES = listOf(
            intArrayOf(48, 53, 58, 63), // diagonal, constant size 3
            intArrayOf(40, 41, 42, 43), // row y = 2, constant size 2
            intArrayOf(9, 25, 41, 57), // concentric: same square, sizes 0..3
            intArrayOf(2, 22, 42, 62), // column x = 2, increasing size
            intArrayOf(12, 25, 38, 51), // anti-diagonal, increasing size
            intArrayOf(1, 5, 9, 13), // column x = 1, constant size 0
        )

        fun boardOf(winningRings: IntArray): BoardVisualState {
            val rings = ByteArray(64)
            for (ring in winningRings) rings[ring] = 1
            return BoardVisualState(
                rings = rings,
                player1Color = TetranoRingColors.Blue,
                player2Color = TetranoRingColors.Blue,
            )
        }
    }
}
