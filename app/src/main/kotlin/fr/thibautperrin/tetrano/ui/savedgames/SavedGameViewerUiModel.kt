package fr.thibautperrin.tetrano.ui.savedgames

import fr.thibautperrin.tetrano.ui.components.BoardVisualState
import fr.thibautperrin.tetrano.ui.theme.TetranoRingColors

/** Everything [SavedGameViewerScreen] needs. Ports `WatchSavedGameActivity`'s `cursor` state. */
data class SavedGameViewerUiModel(
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val gameName: String = "",
    val board: BoardVisualState = BoardVisualState(
        rings = ByteArray(64),
        player1Color = TetranoRingColors.Red,
        player2Color = TetranoRingColors.Blue,
    ),
    val cursor: Int = 0,
    val totalMoves: Int = 0,
    val canGoPrevious: Boolean = false,
    val canGoNext: Boolean = false,
)

sealed interface SavedGameViewerAction {
    data object PreviousClicked : SavedGameViewerAction
    data object NextClicked : SavedGameViewerAction
}
