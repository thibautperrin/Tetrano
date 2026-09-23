package fr.thibautperrin.tetrano.ui.game

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.thibautperrin.tetrano.data.prefs.TetranoPreferencesDataSource
import fr.thibautperrin.tetrano.domain.model.GameMode
import fr.thibautperrin.tetrano.domain.model.GameSessionState
import fr.thibautperrin.tetrano.domain.service.GameSessionService
import fr.thibautperrin.tetrano.domain.service.SavedGameService
import fr.thibautperrin.tetrano.domain.service.StarService
import fr.thibautperrin.tetrano.ui.components.MovingRing
import fr.thibautperrin.tetrano.ui.setup.PendingComputerGameSetup
import fr.thibautperrin.tetrano.ui.theme.TetranoRingColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Backs [GameScreen] for both [GameMode.TWO_PLAYERS] and
 * [GameMode.VS_COMPUTER] — replaces `JeuActivity` and its
 * `GestionJeu`/`GestionJeu2Joueurs`/`GestionJeuContreOrdinateur` (see
 * docs/GLOSSARY.md, docs/PLAN.md P8). [mode] is supplied at creation via
 * [Factory] (see [fr.thibautperrin.tetrano.ui.navigation.TetranoNavHost]),
 * one per route, rather than read from a route argument — the two routes
 * only differ in which screen led here.
 *
 * Drives the two animations [GameScreen] itself can't own (see
 * docs/PLAN.md P7.5/§6.3): the AI's move animating in from the tray
 * ([MOVE_IN_DURATION_MS], [MOVE_IN_STEPS] frames — the legacy
 * `jouerAdversaireAnimer`'s own numbers) and the blink cycle
 * ([BLINK_CYCLES] × [BLINK_PERIOD_MS] — `cliquerClignoter`). The
 * undo/restart fly-out animation described in docs/PLAN.md P7.5 is out of
 * this commit's scope (only the two above are asked for) — undo and
 * restart apply instantly here.
 *
 * Process-death survival (docs/PLAN.md P8, acceptance criterion A6): unlike
 * [ChallengeViewModel], which delegates to the already-persisted
 * [fr.thibautperrin.tetrano.domain.service.ChallengeService], this
 * ViewModel's [session] is purely in-memory ([GameSessionService] isn't
 * backed by storage until an explicit [GameAction.SaveConfirmed]), so an
 * in-progress game — including moves played past whatever [savedGameName]
 * pointed at — would otherwise vanish if the process is killed while
 * backgrounded. [savedStateHandle] mirrors [GameSessionService.playedRings]/
 * [GameSessionService.startingPlayer] on every session state change
 * ([persistSessionState]); [resolveSetup] prefers that mirror over
 * [savedGameName]/[PendingComputerGameSetup] when present, exactly the way
 * the legacy `SauvegardePartie` `Parcelable` reconstructed a game from its
 * move list. Player colors/names still come from
 * [TetranoPreferencesDataSource]/[savedGameService] on restore, same as a
 * fresh screen entry — for a role-swapped [GameMode.VS_COMPUTER] resume,
 * this matches [PendingComputerGameSetup]'s own documented degrade on
 * process death (falls back to the preference defaults), rather than
 * inventing a second persistence path for that edge case.
 */
@HiltViewModel(assistedFactory = GameViewModel.Factory::class)
class GameViewModel @AssistedInject constructor(
    @Assisted private val mode: GameMode,
    @Assisted private val savedGameName: String?,
    private val savedStateHandle: SavedStateHandle,
    private val gameSessionFactory: GameSessionService.Factory,
    private val savedGameService: SavedGameService,
    private val starService: StarService,
    private val preferences: TetranoPreferencesDataSource,
    private val pendingComputerGameSetup: PendingComputerGameSetup,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(mode: GameMode, savedGameName: String?): GameViewModel
    }

    private val _uiState = MutableStateFlow(GameUiModel())
    val uiState: StateFlow<GameUiModel> = _uiState.asStateFlow()

    private lateinit var session: GameSessionService
    private var player1Color: Color = TetranoRingColors.Red
    private var player2Color: Color = TetranoRingColors.Blue
    private var player1Name: String = ""
    private var player2Name: String = ""
    private var animationJob: Job? = null

    init {
        viewModelScope.launch {
            val setup = resolveSetup()
            player1Color = Color(setup.player1Color)
            player2Color = Color(setup.player2Color)
            player1Name = setup.player1Name
            player2Name = setup.player2Name
            session = gameSessionFactory.create(
                scope = viewModelScope,
                mode = mode,
                aiLevel = setup.aiLevel,
                initialMoves = setup.initialMoves,
                startingPlayer = setup.startingPlayer,
            )
            observeSession()
        }
    }

    fun onAction(action: GameAction) {
        when (action) {
            is GameAction.RingDropped -> if (::session.isInitialized) session.play(action.ring)
            GameAction.RestartClicked -> restart()
            GameAction.UndoClicked -> undo()
            GameAction.BlinkClicked -> manualBlink()
            GameAction.SaveClicked -> openSaveDialog()
            is GameAction.SaveNameChanged -> _uiState.update { it.copy(saveNameDraft = action.name) }
            GameAction.SaveConfirmed -> save()
            GameAction.SaveDismissed -> _uiState.update { it.copy(showSaveDialog = false) }
            GameAction.StarAwardsDismissed -> _uiState.update { it.copy(starAwards = emptyList()) }
            GameAction.SavedNameAcknowledged -> _uiState.update { it.copy(lastSavedName = null) }
        }
    }

    private suspend fun resolveSetup(): ResolvedSetup {
        val base = when (mode) {
            GameMode.TWO_PLAYERS -> {
                val saved = savedGameName?.let { savedGameService.get(it) }
                ResolvedSetup(
                    initialMoves = saved?.ringsPlayed ?: ByteArray(0),
                    startingPlayer = if (saved == null || saved.userBegins) 1 else 2,
                    player1Color = saved?.userColor ?: preferences.player1Color.first(),
                    player2Color = saved?.adversaryColor ?: preferences.player2Color.first(),
                    player1Name = preferences.player1Name.first(),
                    player2Name = preferences.player2Name.first(),
                    aiLevel = 0,
                )
            }
            GameMode.VS_COMPUTER -> {
                val pending = pendingComputerGameSetup.consume()
                val saved = savedGameName?.let { savedGameService.get(it) }
                ResolvedSetup(
                    initialMoves = saved?.ringsPlayed ?: ByteArray(0),
                    startingPlayer = if (pending.userBegins) 1 else 2,
                    player1Color = pending.userColor ?: preferences.userColor.first(),
                    player2Color = pending.adversaryColor ?: preferences.adversaryColor.first(),
                    player1Name = preferences.userName.first(),
                    player2Name = "",
                    aiLevel = preferences.aiLevel.first(),
                )
            }
            else -> error("GameScreen does not support $mode")
        }
        // Recovering from process death: the moves actually played this
        // session (possibly more than what savedGameName/PendingComputerGameSetup
        // knows about) take priority — see this class's KDoc.
        val restoredMoves = savedStateHandle.get<ByteArray>(KEY_PLAYED_RINGS)
        val restoredStartingPlayer = savedStateHandle.get<Int>(KEY_STARTING_PLAYER)
        return if (restoredMoves != null && restoredStartingPlayer != null) {
            base.copy(initialMoves = restoredMoves, startingPlayer = restoredStartingPlayer)
        } else {
            base
        }
    }

    private fun persistSessionState() {
        if (!::session.isInitialized) return
        savedStateHandle[KEY_PLAYED_RINGS] = session.playedRings()
        savedStateHandle[KEY_STARTING_PLAYER] = session.startingPlayer()
    }

    private fun observeSession() {
        viewModelScope.launch {
            var previous: GameSessionState? = null
            session.state.collect { state ->
                persistSessionState()
                val aiJustMoved = mode == GameMode.VS_COMPUTER &&
                    previous?.isBusy == true &&
                    !state.isBusy &&
                    state.playedCount == (previous?.playedCount ?: 0) + 1
                val justFinished = state.isFinished && previous?.isFinished != true
                previous = state

                animationJob?.cancel()
                animationJob = launch {
                    if (aiJustMoved && state.lastRing != null) {
                        animateMoveIn(state, state.lastRing)
                    } else {
                        publish(state)
                    }
                    if (justFinished) onGameFinished(state)
                    if (aiJustMoved || justFinished) {
                        state.blinkingRings?.let { blink(it) }
                    }
                }
            }
        }
    }

    private suspend fun animateMoveIn(state: GameSessionState, ring: Int) {
        for (step in 0..MOVE_IN_STEPS) {
            val progress = step / MOVE_IN_STEPS.toFloat()
            publish(state, movingRing = MovingRing(ringNumber = ring, player = COMPUTER_PLAYER, progress = progress))
            if (step < MOVE_IN_STEPS) delay(MOVE_IN_DURATION_MS / MOVE_IN_STEPS)
        }
        publish(state)
    }

    private suspend fun blink(ringsToBlink: ByteArray) {
        val hidden = ringsToBlink.map { it.toInt() }.toSet()
        repeat(BLINK_CYCLES) {
            _uiState.update { it.copy(board = it.board.copy(hiddenRings = hidden)) }
            delay(BLINK_PERIOD_MS)
            _uiState.update { it.copy(board = it.board.copy(hiddenRings = emptySet())) }
            delay(BLINK_PERIOD_MS)
        }
    }

    private suspend fun onGameFinished(state: GameSessionState) {
        val won = state.winner == 1
        val awards = starService.recordGameFinished(mode, won)
        if (awards.isNotEmpty()) {
            _uiState.update { it.copy(starAwards = it.starAwards + awards) }
        }
    }

    private fun publish(
        state: GameSessionState,
        hiddenRings: Set<Int> = emptySet(),
        movingRing: MovingRing? = null,
    ) {
        _uiState.update { current ->
            current.copy(
                isLoading = false,
                board = current.board.copy(
                    rings = state.rings,
                    player1Color = player1Color,
                    player2Color = player2Color,
                    hiddenRings = hiddenRings,
                    movingRing = movingRing,
                    trayPlayer = trayPlayerFor(state),
                ),
                player1Name = player1Name,
                player2Name = player2Name,
                nextPlayer = state.nextPlayer,
                playedCount = state.playedCount,
                isBusy = state.isBusy,
                isFinished = state.isFinished,
                isDraw = state.isDraw,
                winner = state.winner,
                canUndo = state.playedCount > 0 && !state.isBusy,
                canBlink = state.blinkingRings != null,
            )
        }
    }

    private fun trayPlayerFor(state: GameSessionState): Int = when {
        state.isBusy || state.isFinished -> 0
        mode == GameMode.TWO_PLAYERS -> state.nextPlayer
        state.nextPlayer == 1 -> 1
        else -> 0
    }

    private fun restart() {
        animationJob?.cancel()
        // GestionJeu.cliqueRecommencer(): always changes who starts.
        session.restart(changingBeginner = true)
    }

    private fun undo() {
        animationJob?.cancel()
        session.undo()
    }

    private fun manualBlink() {
        val rings = session.blinkingRings() ?: return
        animationJob?.cancel()
        animationJob = viewModelScope.launch { blink(rings) }
    }

    private fun openSaveDialog() {
        val defaultName = DateFormat.getDateInstance().format(Date())
        _uiState.update { it.copy(showSaveDialog = true, saveNameDraft = defaultName) }
    }

    private fun save() {
        val name = _uiState.value.saveNameDraft
        viewModelScope.launch {
            val savedName = savedGameService.save(
                name = name,
                ringsPlayed = session.playedRings(),
                userBegins = session.startingPlayer() == 1,
                userColor = player1Color.toArgb(),
                adversaryColor = player2Color.toArgb(),
            )
            _uiState.update { it.copy(showSaveDialog = false, lastSavedName = savedName) }
        }
    }

    private data class ResolvedSetup(
        val initialMoves: ByteArray,
        val startingPlayer: Int,
        val player1Color: Int,
        val player2Color: Int,
        val player1Name: String,
        val player2Name: String,
        val aiLevel: Int,
    )

    private companion object {
        const val COMPUTER_PLAYER = 2
        const val MOVE_IN_DURATION_MS = 1500L
        const val MOVE_IN_STEPS = 40
        const val BLINK_CYCLES = 5
        const val BLINK_PERIOD_MS = 400L
        const val KEY_PLAYED_RINGS = "game_played_rings"
        const val KEY_STARTING_PLAYER = "game_starting_player"
    }
}
