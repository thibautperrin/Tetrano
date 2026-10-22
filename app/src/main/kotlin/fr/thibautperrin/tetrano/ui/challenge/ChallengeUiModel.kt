package fr.thibautperrin.tetrano.ui.challenge

import fr.thibautperrin.tetrano.ui.components.BoardVisualState
import fr.thibautperrin.tetrano.ui.theme.TetranoRingColors

/**
 * Everything [ChallengeScreen] needs, one level up from [BoardVisualState].
 * The user always plays [BoardVisualState.player1Color] and the computer
 * [BoardVisualState.player2Color]: the user/adversary colors of
 * [fr.thibautperrin.tetrano.data.prefs.TetranoPreferencesDataSource], which
 * [ChallengeViewModel] keeps observing so a change made in Settings applies
 * on return (legacy `onActivityResult` → `refreshSettings`).
 */
data class ChallengeUiModel(
    val board: BoardVisualState = BoardVisualState(
        rings = ByteArray(64),
        player1Color = TetranoRingColors.Red,
        player2Color = TetranoRingColors.Blue,
    ),
    val level: Int = 1,
    val usedRings: Int = 0,
    val authorizedRings: Int = 0,
    /** Ports `updateTextAndButton`'s visibility rule for the "give up" button (hidden, space kept, when false). */
    val canAbandon: Boolean = false,
    /** 1 or 2, or null when no explanations dialog is shown. Ports `displayExplanations`'s 2-page flow. */
    val showExplanationsPage: Int? = null,
    val showRingsInfoDialog: Boolean = false,
    val endDialog: ChallengeEndDialog? = null,
    /** Show the `Internal_Error` toast once, then send [ChallengeAction.InternalErrorShown]. */
    val showInternalError: Boolean = false,
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
    data object HintMenuClicked : ChallengeAction
    data object EndDialogContinueClicked : ChallengeAction
    data object EndDialogQuitClicked : ChallengeAction
    data object InternalErrorShown : ChallengeAction
}

/** One-shot navigation out of [ChallengeScreen]. Ports `endDueToNoMoreLevels`/the "Quit" dialog button. */
sealed interface ChallengeExit {
    data object Quit : ChallengeExit
    data object AllLevelsPassed : ChallengeExit
}
