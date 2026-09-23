package fr.thibautperrin.tetrano.ui.savedgames

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.thibautperrin.tetrano.domain.service.SavedGameService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs [SavedGamesScreen] — replaces `ListePartiesEnregistreesActivity`
 * (see docs/GLOSSARY.md, docs/PLAN.md P8): a selectable list of names
 * whose Watch / Rename / Delete buttons act on the selected one.
 *
 * Resuming a game ("Continue against computer" / "Continue in 2 players
 * mode") isn't offered here but, as in the legacy app, from the viewer's
 * menu ([SavedGameViewerScreen]), from the ring currently displayed.
 */
@HiltViewModel
class SavedGamesViewModel @Inject constructor(
    private val savedGameService: SavedGameService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SavedGamesUiModel())
    val uiState: StateFlow<SavedGamesUiModel> = _uiState.asStateFlow()

    private var firstLoad = true

    init {
        savedGameService.observeAll()
            .onEach { games ->
                val names = games.map { it.name }
                val noSavedGame = firstLoad && names.isEmpty()
                firstLoad = false
                _uiState.update {
                    it.copy(
                        names = names,
                        selected = it.selected?.takeIf { selected -> selected in names },
                        noSavedGame = it.noSavedGame || noSavedGame,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun onAction(action: SavedGamesAction) {
        when (action) {
            is SavedGamesAction.GameClicked ->
                _uiState.update { it.copy(selected = if (it.selected == action.name) null else action.name) }
            is SavedGamesAction.RenameClicked ->
                _uiState.update { it.copy(renameTarget = action.name, renameDraft = action.name, renameError = false) }
            is SavedGamesAction.RenameDraftChanged -> _uiState.update { it.copy(renameDraft = action.draft, renameError = false) }
            SavedGamesAction.RenameConfirmed -> confirmRename()
            SavedGamesAction.RenameDismissed -> _uiState.update { it.copy(renameTarget = null, renameError = false) }
            is SavedGamesAction.DeleteClicked -> _uiState.update { it.copy(deleteTarget = action.name) }
            SavedGamesAction.DeleteConfirmed -> confirmDelete()
            SavedGamesAction.DeleteDismissed -> _uiState.update { it.copy(deleteTarget = null) }
            // Pure navigation — SavedGamesRoute handles it before it reaches this ViewModel.
            is SavedGamesAction.WatchClicked -> Unit
        }
    }

    private fun confirmRename() {
        val target = _uiState.value.renameTarget ?: return
        val newName = _uiState.value.renameDraft
        viewModelScope.launch {
            if (savedGameService.rename(target, newName)) {
                // The legacy dialog deselected the row after a rename.
                _uiState.update { it.copy(renameTarget = null, renameError = false, selected = null) }
            } else {
                _uiState.update { it.copy(renameError = true) }
            }
        }
    }

    private fun confirmDelete() {
        val target = _uiState.value.deleteTarget ?: return
        _uiState.update { it.copy(deleteTarget = null, selected = null) }
        viewModelScope.launch { savedGameService.delete(target) }
    }
}
