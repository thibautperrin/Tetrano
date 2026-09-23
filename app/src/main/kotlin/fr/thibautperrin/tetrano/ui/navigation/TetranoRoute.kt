package fr.thibautperrin.tetrano.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Every screen in the app, as type-safe Navigation Compose destinations.
 * One activity ([fr.thibautperrin.tetrano.MainActivity]) hosts all of
 * them — see docs/PLAN.md P7/P8 for which legacy Activity each replaces.
 *
 * Resuming a saved game carries its [savedGameName] plus `ringCount`: how
 * many of its moves to keep, [ALL_RINGS] for all of them. The legacy
 * viewer's "Continue…" menu resumed from the move currently displayed,
 * not necessarily from the end of the game.
 */
sealed interface TetranoRoute {
    @Serializable
    data object Home : TetranoRoute

    @Serializable
    data object PlayMenu : TetranoRoute

    /** [savedGameName] is set when resuming a save; null starts a fresh game. */
    @Serializable
    data class TwoPlayersSetup(val savedGameName: String? = null, val ringCount: Int = ALL_RINGS) : TetranoRoute

    /** [savedGameName] is set when resuming a save; null starts a fresh game. */
    @Serializable
    data class ComputerSetup(val savedGameName: String? = null, val ringCount: Int = ALL_RINGS) : TetranoRoute

    /** [savedGameName] is set when resuming a save; null starts a fresh game. */
    @Serializable
    data class TwoPlayersGame(val savedGameName: String? = null, val ringCount: Int = ALL_RINGS) : TetranoRoute

    /** [savedGameName] is set when resuming a save; null starts a fresh game. */
    @Serializable
    data class ComputerGame(val savedGameName: String? = null, val ringCount: Int = ALL_RINGS) : TetranoRoute

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

    companion object {
        /** `ringCount` meaning "every move of the saved game". */
        const val ALL_RINGS = -1
    }
}
