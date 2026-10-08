package fr.thibautperrin.tetrano.ui.savedgames

import fr.thibautperrin.tetrano.ui.components.BoardVisualState
import fr.thibautperrin.tetrano.ui.theme.TetranoRingColors

/** Everything [SavedGameViewerScreen] needs. Ports `WatchSavedGameActivity`'s `cursor` state. */
data class SavedGameViewerUiModel(
    /** True until the save is read: the resume menu is hidden meanwhile. */
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val gameName: String = "",
    /** False for a game that was never saved (reviewed from the end of a game): nothing to resume. */
    val canResume: Boolean = true,
    val board: BoardVisualState = BoardVisualState(
        rings = ByteArray(64),
        player1Color = TetranoRingColors.Red,
        player2Color = TetranoRingColors.Blue,
    ),
    /** Number of rings displayed (`cursor`), 0..[totalMoves]; a resumed game keeps exactly these. */
    val cursor: Int = 0,
    val totalMoves: Int = 0,
    /** The level-101 analysis, once requested. */
    val review: ReviewUiModel? = null,
)

/**
 * The analysis of the displayed position. Players are the board's: 1 is drawn in
 * [BoardVisualState.player1Color], 2 in [BoardVisualState.player2Color].
 */
data class ReviewUiModel(
    /** 0..1 while the analysis runs, null once it is complete. */
    val progress: Float? = 0f,
    /** Outlook of the displayed position, null until that position is analysed. */
    val outlook: Outlook? = null,
    /** Verdict on the move that led to the displayed position (null at the start or before analysis). */
    val lastMove: MoveVerdict? = null,
    /** The board player (1 or 2) who played that move. */
    val lastMovePlayer: Int = 1,
    val hasPreviousMistake: Boolean = false,
    val hasNextMistake: Boolean = false,
)

/** Probabilities that each board player wins, or that the game is drawn (sum 1). */
data class Outlook(val player1Wins: Float, val draw: Float, val player2Wins: Float)

/** Mirrors [fr.thibautperrin.tetrano.engine.nn.GameReview.Verdict]. */
enum class MoveVerdict {
    BEST, GOOD, INACCURACY, MISTAKE, BLUNDER, MISSED_WIN, ALLOWED_WIN;

    /** Worth stopping at when jumping from mistake to mistake. */
    val isMistake: Boolean get() = this == MISTAKE || this == BLUNDER || this == MISSED_WIN || this == ALLOWED_WIN
}

sealed interface SavedGameViewerAction {
    data object PreviousClicked : SavedGameViewerAction
    data object NextClicked : SavedGameViewerAction
    data object AnalyseClicked : SavedGameViewerAction
    data object PreviousMistakeClicked : SavedGameViewerAction
    data object NextMistakeClicked : SavedGameViewerAction
}
