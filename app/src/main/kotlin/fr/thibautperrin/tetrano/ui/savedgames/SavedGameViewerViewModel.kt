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
 * [SavedGameSummary.matrix], just stoppable partway through. The screen's
 * resume menu (`menu_watch_saved_game`) resumes from that cursor: see
 * [SavedGameViewerRoute]. The cursor survives process death.
 */
@HiltViewModel
class SavedGameViewerViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val savedGameService: SavedGameService,
) : ViewModel() {
    private val savedGameName: String = savedStateHandle.toRoute<TetranoRoute.SavedGameViewer>().savedGameName

    private val _uiState = MutableStateFlow(SavedGameViewerUiModel())
    val uiState: StateFlow<SavedGameViewerUiModel> = _uiState.asStateFlow()

    private var game: SavedGameSummary? = null
    private var cursor: Int = 0

    init {
        viewModelScope.launch {
            val saved = savedGameService.get(savedGameName)
            if (saved == null) {
                _uiState.update { it.copy(isLoading = false, notFound = true) }
                return@launch
            }
            game = saved
            // The legacy activity opened on the last ring.
            cursor = savedStateHandle.get<Int>(KEY_CURSOR)?.coerceIn(0, saved.ringsPlayed.size) ?: saved.ringsPlayed.size
            publish(saved)
        }
    }

    fun onAction(action: SavedGameViewerAction) {
        val game = game ?: return
        when (action) {
            // WatchSavedGameActivity.onPrevious().
            SavedGameViewerAction.PreviousClicked -> if (cursor > 0) cursor-- else return
            // WatchSavedGameActivity.onNext().
            SavedGameViewerAction.NextClicked -> if (cursor < game.ringsPlayed.size) cursor++ else return
        }
        savedStateHandle[KEY_CURSOR] = cursor
        publish(game)
    }

    private fun publish(game: SavedGameSummary) {
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
            )
        }
    }

    private companion object {
        const val KEY_CURSOR = "cursor"
    }
}
