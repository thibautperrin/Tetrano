package fr.thibautperrin.tetrano.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.thibautperrin.tetrano.data.prefs.TetranoPreferencesDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs [SettingsScreen] — replaces `SettingsActivity` (see
 * docs/GLOSSARY.md, docs/PLAN.md P8). Ports the legacy "user color and
 * adversary color must differ" constraint the same way
 * [fr.thibautperrin.tetrano.ui.setup.TwoPlayersSetupViewModel] ports it
 * for the two-players screen's own pair of pickers: a color tap that
 * would match the other picker's current color is silently ignored
 * rather than swapping the two or rejecting with an error.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: TetranoPreferencesDataSource,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiModel())
    val uiState: StateFlow<SettingsUiModel> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    userName = preferences.userName.first(),
                    userColor = preferences.userColor.first(),
                    adversaryColor = preferences.adversaryColor.first(),
                    isLoading = false,
                )
            }
        }
    }

    fun onAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.UserNameChanged -> _uiState.update { it.copy(userName = action.name) }
            is SettingsAction.UserColorSelected -> selectUserColor(action.color)
            is SettingsAction.AdversaryColorSelected -> selectAdversaryColor(action.color)
            SettingsAction.SaveClicked -> save()
            SettingsAction.SavedAcknowledged -> _uiState.update { it.copy(justSaved = false) }
        }
    }

    // SettingsActivity's two color pickers: ignore a tap that matches the other picker's current color.
    private fun selectUserColor(color: Int) {
        _uiState.update { if (color != it.adversaryColor) it.copy(userColor = color) else it }
    }

    private fun selectAdversaryColor(color: Int) {
        _uiState.update { if (color != it.userColor) it.copy(adversaryColor = color) else it }
    }

    private fun save() {
        val state = _uiState.value
        viewModelScope.launch {
            preferences.setUserName(state.userName)
            preferences.setUserColor(state.userColor)
            preferences.setAdversaryColor(state.adversaryColor)
            _uiState.update { it.copy(justSaved = true) }
        }
    }
}
