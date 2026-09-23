package fr.thibautperrin.tetrano.ui.savedgames

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.thibautperrin.tetrano.domain.model.SavedGameSummary
import fr.thibautperrin.tetrano.domain.service.SavedGameService
import fr.thibautperrin.tetrano.ui.navigation.TetranoRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs [SavedGameViewerScreen] — replaces `WatchSavedGameActivity` (see
 * docs/GLOSSARY.md, docs/PLAN.md P8). Ports its `cursor`/`onPrevious`/
 * `onNext`/`getProchainJoueur` exactly: [SavedGameSummary.ringsPlayed]
 * replayed up to `cursor`, alternating from first-player color starting
 * with [SavedGameSummary.userBegins] — the same rule as
 * [SavedGameSummary.matrix], just stoppable partway through.
 *
 * The legacy activity's "play against computer"/"play 2 players" menu
 * (which resumed from the *current* cursor position, not the full saved
 * game) isn't reproduced here: per docs/PLAN.md P8's table, resuming a
 * saved game ("Continuer") is listed under [SavedGamesScreen]'s content,
 * not this screen's (whose column only lists the move-by-move replay) —
 * see [SavedGamesViewModel]'s KDoc for how that's wired instead.
 */
@HiltViewModel
class SavedGameViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val savedGameService: SavedGameService,
) : ViewModel() {
    private val savedGameName: String = savedStateHandle.toRoute<TetranoRoute.SavedGameViewer>().savedGameName

    private val _uiState = MutableStateFlow(SavedGameViewerUiModel())
    val uiState: StateFlow<SavedGameViewerUiModel> = _uiState.asStateFlow()

    private lateinit var game: SavedGameSummary
    private var cursor: Int = 0

    init {
        viewModelScope.launch {
            val saved = savedGameService.get(savedGameName)
            if (saved == null) {
                _uiState.update { it.copy(isLoading = false, notFound = true) }
                return@launch
            }
            game = saved
            cursor = saved.ringsPlayed.size
            publish()
        }
    }

    fun onAction(action: SavedGameViewerAction) {
        when (action) {
            SavedGameViewerAction.PreviousClicked -> onPrevious()
            SavedGameViewerAction.NextClicked -> onNext()
        }
    }

    // WatchSavedGameActivity.onPrevious().
    private fun onPrevious() {
        if (cursor <= 0) return
        cursor--
        publish()
    }

    // WatchSavedGameActivity.onNext().
    private fun onNext() {
        if (cursor >= game.ringsPlayed.size) return
        cursor++
        publish()
    }

    private fun publish() {
        val rings = ByteArray(64)
        var player: Byte = if (game.userBegins) 1 else 2
        for (i in 0 until cursor) {
            rings[game.ringsPlayed[i].toInt()] = player
            player = (3 - player).toByte()
        }
        _uiState.update {
            it.copy(
                isLoading = false,
                gameName = game.name,
                board = it.board.copy(
                    rings = rings,
                    player1Color = Color(game.userColor),
                    player2Color = Color(game.adversaryColor),
                ),
                cursor = cursor,
                totalMoves = game.ringsPlayed.size,
                canGoPrevious = cursor > 0,
                canGoNext = cursor < game.ringsPlayed.size,
            )
        }
    }
}
