package fr.thibautperrin.tetrano.ui.playmenu

/**
 * Static for now — nothing here is user- or persistence-dependent, unlike
 * [fr.thibautperrin.tetrano.ui.home.HomeUiModel]'s star count. Kept as a
 * proper UiModel/ViewModel pair anyway, matching the convention every
 * other screen follows (see docs/PLAN.md P8).
 */
data class PlayMenuUiModel(val onlineAvailable: Boolean = false)

sealed interface PlayMenuAction {
    data object TwoPlayersClicked : PlayMenuAction
    data object OnePlayerClicked : PlayMenuAction
    data object SavedGamesClicked : PlayMenuAction
    data object ChallengeClicked : PlayMenuAction

    /** No-op today — online play is reported but not implemented (see docs/PLAN.md §10). */
    data object OnlineClicked : PlayMenuAction
}
