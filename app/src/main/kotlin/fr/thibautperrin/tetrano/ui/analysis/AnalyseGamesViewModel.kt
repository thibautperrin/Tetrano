package fr.thibautperrin.tetrano.ui.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.thibautperrin.tetrano.domain.model.SavedGameSummary
import fr.thibautperrin.tetrano.domain.service.SavedGameService
import fr.thibautperrin.tetrano.engine.nn.Position
import fr.thibautperrin.tetrano.engine.nn.Tactics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs [AnalyseGamesScreen], which replaces the saved-games list
 * (`ListePartiesEnregistreesActivity`): the saved games (finished games are
 * saved automatically), newest first, each with its length and result, and
 * the legacy Rename / Delete dialogs. Opening a game, or the empty board, is
 * pure navigation.
 */
@HiltViewModel
class AnalyseGamesViewModel @Inject constructor(
    private val savedGameService: SavedGameService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyseGamesUiModel())
    val uiState: StateFlow<AnalyseGamesUiModel> = _uiState.asStateFlow()

    init {
        savedGameService.observeAll()
            .onEach { games -> _uiState.update { it.copy(isLoading = false, games = games.reversed().map(::item)) } }
            .launchIn(viewModelScope)
    }

    fun onAction(action: AnalyseGamesAction) {
        when (action) {
            is AnalyseGamesAction.RenameClicked ->
                _uiState.update { it.copy(renameTarget = action.name, renameDraft = action.name, renameError = false) }
            is AnalyseGamesAction.RenameDraftChanged -> _uiState.update { it.copy(renameDraft = action.draft, renameError = false) }
            AnalyseGamesAction.RenameConfirmed -> confirmRename()
            AnalyseGamesAction.RenameDismissed -> _uiState.update { it.copy(renameTarget = null, renameError = false) }
            is AnalyseGamesAction.DeleteClicked -> _uiState.update { it.copy(deleteTarget = action.name) }
            AnalyseGamesAction.DeleteConfirmed -> confirmDelete()
            AnalyseGamesAction.DeleteDismissed -> _uiState.update { it.copy(deleteTarget = null) }
        }
    }

    private fun confirmRename() {
        val target = _uiState.value.renameTarget ?: return
        val newName = _uiState.value.renameDraft
        viewModelScope.launch {
            if (savedGameService.rename(target, newName)) {
                _uiState.update { it.copy(renameTarget = null, renameError = false) }
            } else {
                _uiState.update { it.copy(renameError = true) }
            }
        }
    }

    private fun confirmDelete() {
        val target = _uiState.value.deleteTarget ?: return
        _uiState.update { it.copy(deleteTarget = null) }
        viewModelScope.launch { savedGameService.delete(target) }
    }

    private fun item(game: SavedGameSummary): SavedGameItem {
        val moves = game.ringsPlayed
        val count = moves.size
        val lastMoveWon = count > 0 && Tactics.wonWith(Position.after(moves).theirs, moves[count - 1].toInt())
        return when {
            lastMoveWon -> {
                // The first ring is the user's iff userBegins (player 1, drawn in userColor).
                val winnerIsFirst = count % 2 == 1
                val winnerIsUser = winnerIsFirst == game.userBegins
                SavedGameItem(game.name, count, GameResult.WON, if (winnerIsUser) game.userColor else game.adversaryColor)
            }
            count == 64 -> SavedGameItem(game.name, count, GameResult.DRAW)
            else -> SavedGameItem(game.name, count, GameResult.UNFINISHED)
        }
    }
}
