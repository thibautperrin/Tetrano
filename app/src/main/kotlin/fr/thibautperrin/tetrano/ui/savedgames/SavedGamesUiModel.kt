package fr.thibautperrin.tetrano.ui.savedgames

/**
 * Everything [SavedGamesScreen] needs. Ports `ListePartiesEnregistreesActivity`'s
 * list (`MyArrayAdapter`, its `itemSelected`) and its 3 dialogs.
 */
data class SavedGamesUiModel(
    /** Saved game names, in insertion order. */
    val names: List<String> = emptyList(),
    /** The highlighted row (`itemSelected`) the 3 bottom buttons act on; null when none. */
    val selected: String? = null,
    /**
     * True when the screen was opened with no saved game at all: shows
     * `Sentence_No_Saved_Game`, whose dismissal closes the screen
     * (`displayDialogNoSavedGame`). Deleting the last game afterwards
     * doesn't show it (the legacy check only ran in `onCreate`).
     */
    val noSavedGame: Boolean = false,
    val renameTarget: String? = null,
    val renameDraft: String = "",
    /** True when [renameDraft] is already taken by another saved game. */
    val renameError: Boolean = false,
    val deleteTarget: String? = null,
)

sealed interface SavedGamesAction {
    /** A row tap: selects it, or deselects it if it was already selected (`MyArrayAdapter.select`). */
    data class GameClicked(val name: String) : SavedGamesAction
    data class WatchClicked(val name: String) : SavedGamesAction
    data class RenameClicked(val name: String) : SavedGamesAction
    data class RenameDraftChanged(val draft: String) : SavedGamesAction
    data object RenameConfirmed : SavedGamesAction
    data object RenameDismissed : SavedGamesAction
    data class DeleteClicked(val name: String) : SavedGamesAction
    data object DeleteConfirmed : SavedGamesAction
    data object DeleteDismissed : SavedGamesAction
}
