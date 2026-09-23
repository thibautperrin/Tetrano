package fr.thibautperrin.tetrano.ui.savedgames

import fr.thibautperrin.tetrano.ui.components.BoardVisualState
import fr.thibautperrin.tetrano.ui.theme.TetranoRingColors

/** Everything [SavedGameViewerScreen] needs. Ports `WatchSavedGameActivity`'s `cursor` state. */
data class SavedGameViewerUiModel(
    /** True until the save is read: the resume menu is hidden meanwhile. */
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val gameName: String = "",
    val board: BoardVisualState = BoardVisualState(
        rings = ByteArray(64),
        player1Color = TetranoRingColors.Red,
        player2Color = TetranoRingColors.Blue,
    ),
    /** Number of rings displayed (`cursor`), 0..[totalMoves]; a resumed game keeps exactly these. */
    val cursor: Int = 0,
    val totalMoves: Int = 0,
)

sealed interface SavedGameViewerAction {
    data object PreviousClicked : SavedGameViewerAction
    data object NextClicked : SavedGameViewerAction
}
