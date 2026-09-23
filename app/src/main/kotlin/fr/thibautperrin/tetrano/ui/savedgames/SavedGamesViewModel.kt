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
 * (see docs/GLOSSARY.md, docs/PLAN.md P8).
 *
 * [fr.thibautperrin.tetrano.data.db.SavedGameEntity]'s own KDoc records
 * that the legacy app never tracked which mode a saved game was started
 * from, and let the player resume it as either 2-players or vs-computer
 * regardless of how it began — this screen keeps that behavior by always
 * offering both [SavedGamesAction.ContinueTwoPlayersClicked] and
 * [SavedGamesAction.ContinueAgainstComputerClicked] per row (judgment
 * call — no per-row mode to read, and asking first would be an extra tap
 * for no benefit). "2 players" resumes straight into
 * [fr.thibautperrin.tetrano.ui.navigation.TetranoRoute.TwoPlayersGame] —
 * it needs no setup step, [fr.thibautperrin.tetrano.ui.game.GameViewModel]
 * already reads the save directly from that route's `savedGameName`.
 * "vs computer" goes through
 * [fr.thibautperrin.tetrano.ui.navigation.TetranoRoute.ComputerSetup],
 * which already knows how to resume a save (see
 * [fr.thibautperrin.tetrano.ui.setup.ComputerSetupViewModel]'s KDoc) —
 * the AI level and who-begins still need picking there. Both are plain
 * navigation, handled by [SavedGamesRoute] rather than this ViewModel.
 */
@HiltViewModel
class SavedGamesViewModel @Inject constructor(
    private val savedGameService: SavedGameService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SavedGamesUiModel())
    val uiState: StateFlow<SavedGamesUiModel> = _uiState.asStateFlow()

    init {
        savedGameService.observeAll()
            .onEach { games -> _uiState.update { it.copy(isLoading = false, games = games) } }
            .launchIn(viewModelScope)
    }

    fun onAction(action: SavedGamesAction) {
        when (action) {
            is SavedGamesAction.RenameClicked ->
                _uiState.update { it.copy(renameTarget = action.name, renameDraft = action.name, renameError = false) }
            is SavedGamesAction.RenameDraftChanged -> _uiState.update { it.copy(renameDraft = action.draft, renameError = false) }
            SavedGamesAction.RenameConfirmed -> confirmRename()
            SavedGamesAction.RenameDismissed -> _uiState.update { it.copy(renameTarget = null, renameError = false) }
            is SavedGamesAction.DeleteClicked -> _uiState.update { it.copy(deleteTarget = action.name) }
            SavedGamesAction.DeleteConfirmed -> confirmDelete()
            SavedGamesAction.DeleteDismissed -> _uiState.update { it.copy(deleteTarget = null) }
            // Pure navigation — SavedGamesRoute handles these before they reach this ViewModel.
            is SavedGamesAction.WatchClicked,
            is SavedGamesAction.ContinueTwoPlayersClicked,
            is SavedGamesAction.ContinueAgainstComputerClicked,
            -> Unit
        }
    }

    private fun confirmRename() {
        val target = _uiState.value.renameTarget ?: return
        val newName = _uiState.value.renameDraft
        viewModelScope.launch {
            // SavedGameDao.rename's raw UPDATE has no declared conflict
            // strategy (unlike @Insert) — a collision with another row's
            // primary key throws rather than returning 0. Caught here
            // defensively so a taken name shows the same "false" outcome
            // SavedGameService.rename's KDoc promises, without touching
            // domain/service to guarantee it there instead.
            val renamed = runCatching { savedGameService.rename(target, newName) }.getOrDefault(false)
            if (renamed) {
                _uiState.update { it.copy(renameTarget = null, renameError = false) }
            } else {
                _uiState.update { it.copy(renameError = true) }
            }
        }
    }

    private fun confirmDelete() {
        val target = _uiState.value.deleteTarget ?: return
        viewModelScope.launch {
            savedGameService.delete(target)
            _uiState.update { it.copy(deleteTarget = null) }
        }
    }
}
