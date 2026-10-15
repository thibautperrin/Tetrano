package fr.thibautperrin.tetrano.ui.game

import fr.thibautperrin.tetrano.domain.model.StarAward
import fr.thibautperrin.tetrano.ui.components.BoardVisualState
import fr.thibautperrin.tetrano.ui.theme.TetranoRingColors

/**
 * Everything [GameScreen] needs, one level up from [BoardVisualState]:
 * names are raw (possibly blank — the screen resolves a localized
 * fallback) so this stays free of Android string resources.
 */
data class GameUiModel(
    /** True until the game is set up: the title stays blank meanwhile. */
    val isLoading: Boolean = true,
    val board: BoardVisualState = BoardVisualState(
        rings = ByteArray(64),
        player1Color = TetranoRingColors.Red,
        player2Color = TetranoRingColors.Blue,
    ),
    val player1Name: String = "",
    val player2Name: String = "",
    val title: GameTitleState = GameTitleState(),
    /** Star dialogs still to show, one at a time (`ApplicationTetrano.displayMessageStars`, once per award). */
    val starDialogs: List<StarDialog> = emptyList(),
    /** Show the `Internal_Error` toast once, then send [GameAction.InternalErrorShown]. */
    val showInternalError: Boolean = false,
    /** The screen leaves as soon as [starDialogs] is empty (`GestionJeu.sortir()` → `finish()`). */
    val exitRequested: Boolean = false,
    /** Set by [GameAction.ReviewClicked]: the finished game to open in the review screen, once. */
    val reviewRequest: ReviewRequest? = null,
)

/**
 * What the dynamic action-bar title (`mettreLeBonTitre`) is computed from.
 * Kept apart from the board because the legacy code updated the title at
 * its own moments (e.g. the end-of-game title only once the computer's
 * winning ring has landed).
 */
data class GameTitleState(
    val nextPlayer: Int = 1,
    val playedCount: Int = 0,
    val isFinished: Boolean = false,
    val isDraw: Boolean = false,
    val winner: Int? = null,
)

/** One star dialog: [award]'s message, titled with the [starCount] reached once it was granted. */
data class StarDialog(val award: StarAward, val starCount: Int)

/**
 * A game to review: [rings] in play order, comma-separated; [userBegins] and the colors follow
 * the saved-game convention (player 1 is the user, drawn in [userColor]).
 */
data class ReviewRequest(val rings: String, val userBegins: Boolean, val userColor: Int, val adversaryColor: Int)

sealed interface GameAction {
    data class RingDropped(val ring: Int) : GameAction
    data object PlayAgainClicked : GameAction
    data object BlinkClicked : GameAction

    /** Replaces Blink once the game is over: opens the level-101 review of this game. */
    data object ReviewClicked : GameAction
    data object ReviewHandled : GameAction
    data object CancelClicked : GameAction
    data object StarDialogDismissed : GameAction
    data object InternalErrorShown : GameAction
    data object BackPressed : GameAction
}
