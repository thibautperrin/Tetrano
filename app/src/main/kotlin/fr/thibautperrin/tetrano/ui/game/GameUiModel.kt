package fr.thibautperrin.tetrano.ui.game

import fr.thibautperrin.tetrano.domain.model.StarAward
import fr.thibautperrin.tetrano.ui.components.BoardVisualState
import fr.thibautperrin.tetrano.ui.theme.TetranoRingColors

/**
 * Everything [GameScreen] needs, one level up from [BoardVisualState]:
 * names are raw (possibly blank — the screen resolves a localized
 * fallback, exactly like [fr.thibautperrin.tetrano.ui.setup.TwoPlayersSetupScreen]'s
 * hints) so this stays free of Android string resources.
 */
data class GameUiModel(
    val isLoading: Boolean = true,
    val board: BoardVisualState = BoardVisualState(
        rings = ByteArray(64),
        player1Color = TetranoRingColors.Red,
        player2Color = TetranoRingColors.Blue,
    ),
    val player1Name: String = "",
    val player2Name: String = "",
    val nextPlayer: Int = 1,
    val playedCount: Int = 0,
    val isBusy: Boolean = false,
    val isFinished: Boolean = false,
    val isDraw: Boolean = false,
    val winner: Int? = null,
    val canUndo: Boolean = false,
    val canBlink: Boolean = false,
    val showSaveDialog: Boolean = false,
    val saveNameDraft: String = "",
    val lastSavedName: String? = null,
    val starAwards: List<StarAward> = emptyList(),
)

sealed interface GameAction {
    data class RingDropped(val ring: Int) : GameAction
    data object RestartClicked : GameAction
    data object UndoClicked : GameAction
    data object BlinkClicked : GameAction
    data object SaveClicked : GameAction
    data class SaveNameChanged(val name: String) : GameAction
    data object SaveConfirmed : GameAction
    data object SaveDismissed : GameAction
    data object StarAwardsDismissed : GameAction
    data object SavedNameAcknowledged : GameAction
}
