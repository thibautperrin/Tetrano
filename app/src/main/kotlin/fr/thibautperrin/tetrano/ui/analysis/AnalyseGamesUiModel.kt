package fr.thibautperrin.tetrano.ui.analysis

/** Everything [AnalyseGamesScreen] needs: the saved games, newest first, and the rename/delete dialogs. */
data class AnalyseGamesUiModel(
    val isLoading: Boolean = true,
    val games: List<SavedGameItem> = emptyList(),
    val renameTarget: String? = null,
    val renameDraft: String = "",
    /** True when [renameDraft] is already taken by another saved game. */
    val renameError: Boolean = false,
    val deleteTarget: String? = null,
)

/** One saved game. Colors are packed ARGB. */
data class SavedGameItem(
    val name: String,
    val moves: Int,
    val result: GameResult,
    /** The winner's ring color, for [GameResult.WON]. */
    val winnerColor: Int = 0,
)

enum class GameResult { WON, DRAW, UNFINISHED }

sealed interface AnalyseGamesAction {
    data class RenameClicked(val name: String) : AnalyseGamesAction
    data class RenameDraftChanged(val draft: String) : AnalyseGamesAction
    data object RenameConfirmed : AnalyseGamesAction
    data object RenameDismissed : AnalyseGamesAction
    data class DeleteClicked(val name: String) : AnalyseGamesAction
    data object DeleteConfirmed : AnalyseGamesAction
    data object DeleteDismissed : AnalyseGamesAction
}
