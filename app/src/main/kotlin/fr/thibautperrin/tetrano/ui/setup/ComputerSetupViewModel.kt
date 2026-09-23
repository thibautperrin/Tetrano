package fr.thibautperrin.tetrano.ui.setup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.thibautperrin.tetrano.data.prefs.TetranoPreferencesDataSource
import fr.thibautperrin.tetrano.domain.model.SavedGameSummary
import fr.thibautperrin.tetrano.domain.service.SavedGameService
import fr.thibautperrin.tetrano.ui.navigation.TetranoRoute
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs [ComputerSetupScreen] — replaces `JouerContreOrdiActivity` (see
 * docs/GLOSSARY.md, docs/PLAN.md P8), including resuming a saved game.
 *
 * Ports `JouerContreOrdiActivity`'s own KDoc verbatim for the resume case:
 * the activity receives whether player 1 (assumed to be the user) began
 * the saved game. By default everything is done to avoid changing roles:
 * if the user is the next one to play, the "I begin" radio is
 * pre-checked, and reciprocally. Only if the user flips the radio away
 * from that default do we actually swap which side is "the user" going
 * forward — [play] reproduces the exact swap (`userBegins` flips, and
 * [SavedGameSummary.userColor]/[SavedGameSummary.adversaryColor] swap
 * with it) rather than discarding the played history.
 *
 * See [PendingComputerGameSetup]'s KDoc for why the result of that swap
 * has to travel to [fr.thibautperrin.tetrano.ui.game.GameViewModel]
 * through an in-memory hand-off instead of a route argument.
 */
@HiltViewModel
class ComputerSetupViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val preferences: TetranoPreferencesDataSource,
    private val savedGameService: SavedGameService,
    private val pendingComputerGameSetup: PendingComputerGameSetup,
) : ViewModel() {
    private val savedGameName: String? = savedStateHandle.toRoute<TetranoRoute.ComputerSetup>().savedGameName

    private val _uiState = MutableStateFlow(ComputerSetupUiModel())
    val uiState: StateFlow<ComputerSetupUiModel> = _uiState.asStateFlow()

    private val _navigateToGame = Channel<Unit>(Channel.BUFFERED)
    val navigateToGame: Flow<Unit> = _navigateToGame.receiveAsFlow()

    private var savedGame: SavedGameSummary? = null

    // Whether, by default (no role swap), it's the user's turn to
    // (re)start playing next — see this class's KDoc.
    private var userBeginByDefault: Boolean = true

    init {
        viewModelScope.launch {
            val level = preferences.aiLevel.first()
            val saved = savedGameName?.let { savedGameService.get(it) }
            savedGame = saved
            userBeginByDefault = if (saved != null) {
                val ringsPlayed = saved.ringsPlayed.size
                (saved.userBegins && ringsPlayed % 2 == 0) || (!saved.userBegins && ringsPlayed % 2 == 1)
            } else {
                true
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    aiLevel = level,
                    userBegins = userBeginByDefault,
                    isResumingSavedGame = saved != null,
                )
            }
        }
    }

    fun onAction(action: ComputerSetupAction) {
        when (action) {
            is ComputerSetupAction.LevelChanged -> setLevel(action.level)
            ComputerSetupAction.LevelIncremented -> setLevel(_uiState.value.aiLevel + 1)
            ComputerSetupAction.LevelDecremented -> setLevel(_uiState.value.aiLevel - 1)
            ComputerSetupAction.UserBeginsSelected -> _uiState.update { it.copy(userBegins = true) }
            ComputerSetupAction.ComputerBeginsSelected -> _uiState.update { it.copy(userBegins = false) }
            ComputerSetupAction.PlayClicked -> play()
        }
    }

    private fun setLevel(level: Int) {
        _uiState.update { it.copy(aiLevel = level.coerceIn(0, 100)) }
    }

    private fun play() {
        val state = _uiState.value
        viewModelScope.launch {
            preferences.setAiLevel(state.aiLevel)
            val saved = savedGame
            val setup = if (saved == null) {
                ComputerGameSetup(userBegins = state.userBegins)
            } else {
                val roleSwapped = state.userBegins != userBeginByDefault
                if (roleSwapped) {
                    ComputerGameSetup(
                        userBegins = !saved.userBegins,
                        userColor = saved.adversaryColor,
                        adversaryColor = saved.userColor,
                    )
                } else {
                    ComputerGameSetup(
                        userBegins = saved.userBegins,
                        userColor = saved.userColor,
                        adversaryColor = saved.adversaryColor,
                    )
                }
            }
            pendingComputerGameSetup.set(setup)
            _navigateToGame.send(Unit)
        }
    }
}
