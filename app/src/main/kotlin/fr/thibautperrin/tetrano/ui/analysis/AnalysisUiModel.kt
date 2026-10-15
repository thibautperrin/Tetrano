package fr.thibautperrin.tetrano.ui.analysis

import fr.thibautperrin.tetrano.ui.components.BoardVisualState
import fr.thibautperrin.tetrano.ui.theme.TetranoRingColors

/** Everything [AnalysisScreen] needs. Board players: 1 is drawn in player1Color, 2 in player2Color. */
data class AnalysisUiModel(
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    /** The saved game's name; blank for a game that was not saved or the empty board. */
    val gameName: String = "",
    /** True for the empty board: the moves played form the game itself, there are no variations. */
    val freePlay: Boolean = false,
    /** False for a game that was never saved: nothing to resume. */
    val canResume: Boolean = false,
    /** The position on display, with a translucent ring on the recommended move and the tray of the player to move. */
    val board: BoardVisualState = BoardVisualState(
        rings = ByteArray(64),
        player1Color = TetranoRingColors.Red,
        player2Color = TetranoRingColors.Blue,
    ),
    /** Moves displayed, 0..[lineLength], of the line on display (the game, or a variation of it). */
    val cursor: Int = 0,
    val lineLength: Int = 0,
    /** Set while a variation is on display. */
    val variation: VariationUiModel? = null,
    /** The level-101 search of the position on display. */
    val live: LiveUiModel? = null,
    /** Variations the search tests before it stops (the analysis depth setting). */
    val depth: Int = 5_000,
    /** The review of the game's moves; null for the empty board. */
    val review: ReviewUiModel? = null,
    /** The game is over in the position on display. */
    val gameOver: Boolean = false,
)

/** A line of play that leaves the game: [fromMove] is the 1-based number of its first move. */
data class VariationUiModel(val fromMove: Int)

/** The live search of the position on display. */
data class LiveUiModel(
    val outlook: Outlook,
    /** Variations tested so far. */
    val simulations: Int,
    val running: Boolean,
    /** The ring recommended to the player to move, or -1. */
    val bestRing: Int,
    /** The board player (1 or 2) to move. */
    val playerToMove: Int,
    /** Set when the search proved the outcome: the board player (1 or 2) who wins by force, or 0 for a draw. */
    val provenWinner: Int? = null,
)

/**
 * The review of the game's moves, for the position on display (on the game's line only).
 * Players are the board's.
 */
data class ReviewUiModel(
    /** 0..1 while the positions are being evaluated, null once done. */
    val progress: Float? = 0f,
    /** Verdict on the move that led to the position on display (null at the start, in a variation or before analysis). */
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

sealed interface AnalysisAction {
    data object FirstClicked : AnalysisAction
    data object PreviousClicked : AnalysisAction
    data object NextClicked : AnalysisAction
    data object LastClicked : AnalysisAction
    data object PreviousMistakeClicked : AnalysisAction
    data object NextMistakeClicked : AnalysisAction

    /** A ring dropped on the board: plays it for the player to move (a variation, unless it is the game's move). */
    data class RingDropped(val ring: Int) : AnalysisAction

    /** Leaves the variation, back to the game where it branched off. */
    data object BackToGameClicked : AnalysisAction
    data class DepthSelected(val simulations: Int) : AnalysisAction
}
