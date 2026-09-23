package fr.thibautperrin.tetrano.ui.rules

import fr.thibautperrin.tetrano.ui.components.BoardVisualState

/**
 * The 6 static boards of `ExamplesActivity` (`grilleView1`..`grilleView6`),
 * in the legacy order — see [WinningExamplesViewModel] for their rings.
 */
data class WinningExamplesUiModel(val examples: List<BoardVisualState> = emptyList())
