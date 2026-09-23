package fr.thibautperrin.tetrano.ui.challenge

import fr.thibautperrin.tetrano.ui.components.BoardVisualState
import fr.thibautperrin.tetrano.ui.theme.TetranoRingColors

/**
 * Everything [ChallengeScreen] needs, one level up from [BoardVisualState].
 * Unlike [fr.thibautperrin.tetrano.ui.game.GameUiModel], colors are fixed
 * (the legacy `ChallengeActivity` always drew the user in
 * [fr.thibautperrin.tetrano.data.prefs.TetranoPreferencesDataSource]'s
 * user/adversary colors, refreshed from Settings — [ChallengeViewModel]
 * reads them once at start, same as the legacy `refreshSettings`).
 */
data class ChallengeUiModel(
    val isLoading: Boolean = true,
    val board: BoardVisualState = BoardVisualState(
        rings = ByteArray(64),
        player1Color = TetranoRingColors.Red,
        player2Color = TetranoRingColors.Blue,
    ),
    val level: Int = 1,
    val usedRings: Int = 0,
    val authorizedRings: Int = 0,
    /** Ports `updateTextAndButton`'s visibility rule for the "give up" button. */
    val canAbandon: Boolean = false,
    val numberStars: Int = 0,
    /** 1 or 2, or null when no explanations dialog is shown. Ports `displayExplanations`'s 2-page flow. */
    val showExplanationsPage: Int? = null,
    val showRingsInfoDialog: Boolean = false,
    val showNoStarsMessage: Boolean = false,
    val showConfirmUseStar: Boolean = false,
    val endDialog: ChallengeEndDialog? = null,
    /** Ports the "all 4 main modes tried" star, surfaced the same way [fr.thibautperrin.tetrano.ui.game.GameUiModel.starAwards] does. */
    val showAllModesTriedAward: Boolean = false,
)

/** Ports `ChallengeActivity.displayDialogEnd`'s `situation` byte. */
enum class ChallengeEndSituation { USER_WON, COMPUTER_WON, RAN_OUT_OF_RINGS, ABANDONED }

data class ChallengeEndDialog(val situation: ChallengeEndSituation, val level: Int)

sealed interface ChallengeAction {
    data class RingDropped(val ring: Int) : ChallengeAction
    data object AbandonClicked : ChallengeAction
    data object RingsCounterClicked : ChallengeAction
    data object RingsInfoDismissed : ChallengeAction
    data object ExplanationsMenuClicked : ChallengeAction
    data object ExplanationsNextClicked : ChallengeAction
    data object ExplanationsDismissed : ChallengeAction
    data object UseStarMenuClicked : ChallengeAction
    data object NoStarsAcknowledged : ChallengeAction
    data object UseStarConfirmed : ChallengeAction
    data object UseStarDismissed : ChallengeAction
    data object EndDialogContinueClicked : ChallengeAction
    data object EndDialogQuitClicked : ChallengeAction
    data object AllModesTriedAwardAcknowledged : ChallengeAction
}

/** One-shot navigation out of [ChallengeScreen]. Ports `endDueToNoMoreLevels`/the "Quit" dialog button. */
sealed interface ChallengeExit {
    data object Quit : ChallengeExit
    data object AllLevelsPassed : ChallengeExit
}
