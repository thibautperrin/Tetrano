package fr.thibautperrin.tetrano.ui.setup

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
 * Backs [TwoPlayersSetupScreen] — replaces `JouerDeuxJoueurActivity` (see
 * docs/GLOSSARY.md, docs/PLAN.md P8). Only covers starting a *new* game —
 * resuming a saved 2-player game isn't wired up yet, matching
 * [fr.thibautperrin.tetrano.ui.navigation.TetranoRoute.TwoPlayersSetup]
 * taking no arguments (P8's saved-games screens land in a later commit).
 */
@HiltViewModel
class TwoPlayersSetupViewModel @Inject constructor(
    private val preferences: TetranoPreferencesDataSource,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TwoPlayersSetupUiModel())
    val uiState: StateFlow<TwoPlayersSetupUiModel> = _uiState.asStateFlow()

    private val _navigateToGame = Channel<Unit>(Channel.BUFFERED)

    /** Emits once the chosen names/colors are persisted — [TwoPlayersSetupRoute] navigates on it. */
    val navigateToGame: Flow<Unit> = _navigateToGame.receiveAsFlow()

    init {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    player1Color = preferences.player1Color.first(),
                    player2Color = preferences.player2Color.first(),
                )
            }
        }
    }

    fun onAction(action: TwoPlayersSetupAction) {
        when (action) {
            is TwoPlayersSetupAction.Player1NameChanged -> _uiState.update { it.copy(player1Name = action.name) }
            is TwoPlayersSetupAction.Player2NameChanged -> _uiState.update { it.copy(player2Name = action.name) }
            is TwoPlayersSetupAction.Player1ColorSelected -> selectPlayer1Color(action.color)
            is TwoPlayersSetupAction.Player2ColorSelected -> selectPlayer2Color(action.color)
            is TwoPlayersSetupAction.PlayClicked -> play(action.player1Name, action.player2Name)
        }
    }

    // JouerDeuxJoueurActivity.couleurJoueur1Listener/couleurJoueur2Listener: a
    // color tap is ignored if it matches the other player's current color.
    private fun selectPlayer1Color(color: Int) {
        _uiState.update { if (color != it.player2Color) it.copy(player1Color = color) else it }
    }

    private fun selectPlayer2Color(color: Int) {
        _uiState.update { if (color != it.player1Color) it.copy(player2Color = color) else it }
    }

    private fun play(player1Name: String, player2Name: String) {
        val colors = _uiState.value
        viewModelScope.launch {
            // Persisted so GameViewModel (which the TwoPlayersGame route can't
            // pass extra arguments to — see docs/PLAN.md P8's fixed route
            // shapes) can read them back for a fresh game.
            preferences.setPlayer1Name(player1Name)
            preferences.setPlayer2Name(player2Name)
            preferences.setPlayer1Color(colors.player1Color)
            preferences.setPlayer2Color(colors.player2Color)
            _navigateToGame.send(Unit)
        }
    }
}
