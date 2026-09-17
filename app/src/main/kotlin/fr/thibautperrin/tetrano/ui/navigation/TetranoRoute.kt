package fr.thibautperrin.tetrano.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Every screen in the app, as type-safe Navigation Compose destinations.
 * One activity ([fr.thibautperrin.tetrano.MainActivity]) hosts all of
 * them — see docs/PLAN.md P7/P8 for which legacy Activity each replaces.
 *
 * Real screens land in P8; for now [TetranoNavHost] renders a placeholder
 * for every destination except [Home].
 */
sealed interface TetranoRoute {
    @Serializable
    data object Home : TetranoRoute

    @Serializable
    data object PlayMenu : TetranoRoute

    @Serializable
    data object TwoPlayersSetup : TetranoRoute

    /** [savedGameName] is set when resuming a save; null starts a fresh game. */
    @Serializable
    data class ComputerSetup(val savedGameName: String? = null) : TetranoRoute

    /** [savedGameName] is set when resuming a save; null starts a fresh game. */
    @Serializable
    data class TwoPlayersGame(val savedGameName: String? = null) : TetranoRoute

    /** [savedGameName] is set when resuming a save; null starts a fresh game. */
    @Serializable
    data class ComputerGame(val savedGameName: String? = null) : TetranoRoute

    @Serializable
    data object Challenge : TetranoRoute

    @Serializable
    data object SavedGames : TetranoRoute

    @Serializable
    data class SavedGameViewer(val savedGameName: String) : TetranoRoute

    @Serializable
    data object Settings : TetranoRoute

    @Serializable
    data object Rules : TetranoRoute

    @Serializable
    data object WinningExamples : TetranoRoute
}
