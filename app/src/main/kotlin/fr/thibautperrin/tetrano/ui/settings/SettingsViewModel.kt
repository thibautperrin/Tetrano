package fr.thibautperrin.tetrano.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.thibautperrin.tetrano.data.prefs.TetranoPreferencesDataSource
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
 * Backs [SettingsScreen] — replaces `SettingsActivity` (see
 * docs/GLOSSARY.md, docs/PLAN.md P8).
 *
 * - The name field is pre-filled with the saved name; saving an empty
 *   field keeps the saved name (`avantSortir()`).
 * - "User color and adversary color must differ": a color tap matching
 *   the other picker's current color is ignored, as in the legacy
 *   `tacheUtilisateurListener`/`tacheAdversaireListener` (the screen
 *   shows that color as unavailable).
 * - The saved values load asynchronously: a value changed before they
 *   arrive is kept.
 * - [SettingsAction.SaveClicked] (Back) persists everything once, then
 *   emits [saved] for the screen to toast `Settings_Saved` and close.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: TetranoPreferencesDataSource,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiModel())
    val uiState: StateFlow<SettingsUiModel> = _uiState.asStateFlow()

    private val _saved = Channel<Unit>(Channel.BUFFERED)

    /** One event once the settings are persisted. */
    val saved: Flow<Unit> = _saved.receiveAsFlow()

    private var userColorPicked = false
    private var adversaryColorPicked = false
    private var nameTyped = false
    private var simulationsPicked = false
    private var saving = false

    init {
        viewModelScope.launch {
            val name = preferences.userName.first()
            val userColor = preferences.userColor.first()
            val adversaryColor = preferences.adversaryColor.first()
            val simulations = preferences.neuralSimulations.first()
            _uiState.update {
                val newUserColor = if (userColorPicked) it.userColor else userColor
                val newAdversaryColor = if (adversaryColorPicked) it.adversaryColor else adversaryColor
                it.copy(
                    currentUserName = name,
                    userNameDraft = if (nameTyped) it.userNameDraft else name,
                    neuralSimulations = if (simulationsPicked) it.neuralSimulations else simulations,
                    userColor = newUserColor,
                    // A loaded color never breaks the "colors differ" rule.
                    adversaryColor = if (newAdversaryColor != newUserColor) newAdversaryColor else it.adversaryColor,
                )
            }
        }
    }

    fun onAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.UserNameChanged -> {
                nameTyped = true
                _uiState.update { it.copy(userNameDraft = action.name) }
            }
            is SettingsAction.NeuralSimulationsSelected -> {
                simulationsPicked = true
                _uiState.update { it.copy(neuralSimulations = action.simulations) }
            }
            is SettingsAction.UserColorSelected -> selectUserColor(action.color)
            is SettingsAction.AdversaryColorSelected -> selectAdversaryColor(action.color)
            SettingsAction.SaveClicked -> save()
        }
    }

    private fun selectUserColor(color: Int) {
        if (color == _uiState.value.adversaryColor) return
        userColorPicked = true
        _uiState.update { it.copy(userColor = color) }
    }

    private fun selectAdversaryColor(color: Int) {
        if (color == _uiState.value.userColor) return
        adversaryColorPicked = true
        _uiState.update { it.copy(adversaryColor = color) }
    }

    private fun save() {
        if (saving) return
        saving = true
        val state = _uiState.value
        viewModelScope.launch {
            preferences.setUserSettings(
                userName = state.userNameDraft.takeIf { it.isNotBlank() },
                userColor = state.userColor,
                adversaryColor = state.adversaryColor,
            )
            preferences.setNeuralSimulations(state.neuralSimulations)
            _saved.send(Unit)
        }
    }
}
