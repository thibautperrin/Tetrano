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
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import fr.thibautperrin.tetrano.domain.service.AiService
import javax.inject.Inject

/**
 * Backs [ComputerSetupScreen] — replaces `JouerContreOrdiActivity` (see
 * docs/GLOSSARY.md, docs/PLAN.md P8), including resuming a saved game.
 *
 * Ports `JouerContreOrdiActivity`'s own KDoc for the resume case: the
 * activity receives whether player 1 (assumed to be the user) began the
 * saved game. By default everything is done to avoid changing roles: if
 * the user is the next one to play, the "User" radio is pre-checked, and
 * reciprocally. Only if the user flips the radio away from that default do
 * we actually swap which side is "the user" going forward — [play]
 * reproduces the exact swap (`userBegins` flips, and
 * [SavedGameSummary.userColor]/[SavedGameSummary.adversaryColor] swap with
 * it) rather than discarding the played history. "Next to play" counts only
 * the first `ringCount` moves of the save (see [TetranoRoute.ComputerSetup]):
 * the game resumes from there.
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
    private val route = savedStateHandle.toRoute<TetranoRoute.ComputerSetup>()

    private val _uiState = MutableStateFlow(ComputerSetupUiModel())
    val uiState: StateFlow<ComputerSetupUiModel> = _uiState.asStateFlow()

    private val _navigateToGame = Channel<Unit>(Channel.BUFFERED)
    val navigateToGame: Flow<Unit> = _navigateToGame.receiveAsFlow()

    private var savedGame: SavedGameSummary? = null

    // Whether, by default (no role swap), it's the user's turn to
    // (re)start playing next — see this class's KDoc.
    private var userBeginByDefault: Boolean = true

    // Play waits for it, so a quick tap can't start the game with the
    // defaults instead of the preferences / the save.
    private val loadJob = viewModelScope.launch {
        val level = preferences.aiLevel.first()
        val saved = route.savedGameName?.let { savedGameService.get(it) }
        savedGame = saved
        userBeginByDefault = if (saved != null) {
            val ringsPlayed = saved.ringsPlayed.keepFirst(route.ringCount).size
            (saved.userBegins && ringsPlayed % 2 == 0) || (!saved.userBegins && ringsPlayed % 2 == 1)
        } else {
            true
        }
        _uiState.update { it.copy(aiLevel = level, userBegins = userBeginByDefault) }
    }

    private var playJob: Job? = null

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
        _uiState.update { it.copy(aiLevel = level.coerceIn(MIN_LEVEL, MAX_LEVEL)) }
    }

    private fun play() {
        // One game per tap, however fast the taps come.
        if (playJob?.isActive == true) return
        playJob = viewModelScope.launch {
            loadJob.join()
            val state = _uiState.value
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

    companion object {
        /** The legacy `seekBarLevel` range, plus the neural level [AiService.NEURAL_LEVEL] on top. */
        const val MIN_LEVEL = 0
        const val MAX_LEVEL = AiService.NEURAL_LEVEL
    }
}
