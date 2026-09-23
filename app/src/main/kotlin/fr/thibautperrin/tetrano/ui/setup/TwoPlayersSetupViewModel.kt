package fr.thibautperrin.tetrano.ui.setup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.thibautperrin.tetrano.data.prefs.TetranoPreferencesDataSource
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
import javax.inject.Inject

/**
 * Backs [TwoPlayersSetupScreen] — replaces `JouerDeuxJoueurActivity` (see
 * docs/GLOSSARY.md, docs/PLAN.md P8), for a fresh game as well as for a
 * saved game resumed from the viewer ([TetranoRoute.TwoPlayersSetup]'s
 * `savedGameName`).
 *
 * As in the legacy activity, the names are always asked (hint = last names
 * used) and persisted. The colors differ:
 * - fresh game: pre-selected from, and persisted back to, the preferences;
 * - resumed game: pre-selected from the save and *not* persisted — they
 *   reach [fr.thibautperrin.tetrano.ui.game.GameViewModel] through
 *   [PendingTwoPlayersGameSetup] instead. As in the legacy
 *   `WatchSavedGameActivity.playTwoPlayers`, player 1 is whoever began the
 *   saved game (a fresh legacy `Partie` always starts with player 1 before
 *   replaying the moves): player 1 gets the save's user color if the user
 *   began it, its adversary color otherwise, and player 2 the other one.
 */
@HiltViewModel
class TwoPlayersSetupViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val preferences: TetranoPreferencesDataSource,
    private val savedGameService: SavedGameService,
    private val pendingTwoPlayersGameSetup: PendingTwoPlayersGameSetup,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<TetranoRoute.TwoPlayersSetup>()

    private val _uiState = MutableStateFlow(TwoPlayersSetupUiModel())
    val uiState: StateFlow<TwoPlayersSetupUiModel> = _uiState.asStateFlow()

    private val _navigateToGame = Channel<Unit>(Channel.BUFFERED)

    /** Emits once the chosen names/colors are recorded — [TwoPlayersSetupRoute] navigates on it. */
    val navigateToGame: Flow<Unit> = _navigateToGame.receiveAsFlow()

    // The legacy fromSavedGame flag: a save was found for the route's name.
    private var resumingSavedGame = false

    private var playJob: Job? = null

    // Play waits for it, so a quick tap can't start the game with the
    // default colors instead of the preferences' / the save's.
    private val loadJob = viewModelScope.launch {
        val saved = route.savedGameName?.let { savedGameService.get(it) }
        resumingSavedGame = saved != null
        val player1LastName = preferences.player1Name.first()
        val player2LastName = preferences.player2Name.first()
        val player1Color: Int
        val player2Color: Int
        when {
            saved == null -> {
                player1Color = preferences.player1Color.first()
                player2Color = preferences.player2Color.first()
            }
            saved.userBegins -> {
                player1Color = saved.userColor
                player2Color = saved.adversaryColor
            }
            else -> {
                player1Color = saved.adversaryColor
                player2Color = saved.userColor
            }
        }
        _uiState.update {
            it.copy(
                player1LastName = player1LastName,
                player2LastName = player2LastName,
                player1Color = player1Color,
                player2Color = player2Color,
            )
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
        // One game per tap, however fast the taps come.
        if (playJob?.isActive == true) return
        playJob = viewModelScope.launch {
            loadJob.join()
            val colors = _uiState.value
            // Persisted so GameViewModel (which the TwoPlayersGame route can't
            // pass extra arguments to — see docs/PLAN.md P8's fixed route
            // shapes) can read them back.
            preferences.setPlayer1Name(player1Name)
            preferences.setPlayer2Name(player2Name)
            if (resumingSavedGame) {
                pendingTwoPlayersGameSetup.set(
                    TwoPlayersGameSetup(player1Color = colors.player1Color, player2Color = colors.player2Color),
                )
            } else {
                preferences.setPlayer1Color(colors.player1Color)
                preferences.setPlayer2Color(colors.player2Color)
            }
            _navigateToGame.send(Unit)
        }
    }
}
