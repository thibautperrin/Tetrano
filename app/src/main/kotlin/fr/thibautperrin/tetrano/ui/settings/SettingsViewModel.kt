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
 * - The name field starts empty with the saved name as its hint; saving
 *   an empty field keeps the saved name (`avantSortir()`).
 * - "User color and adversary color must differ": a color tap matching
 *   the other picker's current color is silently ignored, as in the
 *   legacy `tacheUtilisateurListener`/`tacheAdversaireListener`.
 * - The saved values load asynchronously: a color picked before they
 *   arrive is kept (typing never races with the load, the field isn't
 *   pre-filled).
 * - [SettingsAction.SaveClicked] (Confirm, and Back) persists everything
 *   once, then emits [saved] for the screen to toast `Settings_Saved`
 *   and close.
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
    private var saving = false

    init {
        viewModelScope.launch {
            val name = preferences.userName.first()
            val userColor = preferences.userColor.first()
            val adversaryColor = preferences.adversaryColor.first()
            _uiState.update {
                val newUserColor = if (userColorPicked) it.userColor else userColor
                val newAdversaryColor = if (adversaryColorPicked) it.adversaryColor else adversaryColor
                it.copy(
                    currentUserName = name,
                    userColor = newUserColor,
                    // A loaded color never breaks the "colors differ" rule.
                    adversaryColor = if (newAdversaryColor != newUserColor) newAdversaryColor else it.adversaryColor,
                )
            }
        }
    }

    fun onAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.UserNameChanged -> _uiState.update { it.copy(userNameDraft = action.name) }
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
            _saved.send(Unit)
        }
    }
}
