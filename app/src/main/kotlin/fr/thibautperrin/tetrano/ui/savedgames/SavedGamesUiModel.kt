package fr.thibautperrin.tetrano.ui.savedgames

import fr.thibautperrin.tetrano.domain.model.SavedGameSummary

/** Everything [SavedGamesScreen] needs. Ports `ListePartiesEnregistreesActivity`'s list + its 2 dialogs. */
data class SavedGamesUiModel(
    val isLoading: Boolean = true,
    val games: List<SavedGameSummary> = emptyList(),
    val renameTarget: String? = null,
    val renameDraft: String = "",
    /** True when [renameDraft] is already taken by another saved game. */
    val renameError: Boolean = false,
    val deleteTarget: String? = null,
)

sealed interface SavedGamesAction {
    data class WatchClicked(val name: String) : SavedGamesAction
    data class ContinueTwoPlayersClicked(val name: String) : SavedGamesAction
    data class ContinueAgainstComputerClicked(val name: String) : SavedGamesAction
    data class RenameClicked(val name: String) : SavedGamesAction
    data class RenameDraftChanged(val draft: String) : SavedGamesAction
    data object RenameConfirmed : SavedGamesAction
    data object RenameDismissed : SavedGamesAction
    data class DeleteClicked(val name: String) : SavedGamesAction
    data object DeleteConfirmed : SavedGamesAction
    data object DeleteDismissed : SavedGamesAction
}
