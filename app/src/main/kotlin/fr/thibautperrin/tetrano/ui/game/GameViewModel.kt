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
import fr.thibautperrin.tetrano.domain.model.GameSessionEvent
import fr.thibautperrin.tetrano.domain.model.GameSessionState
import fr.thibautperrin.tetrano.domain.service.GameSessionService
import fr.thibautperrin.tetrano.domain.service.SavedGameService
import fr.thibautperrin.tetrano.ui.components.MovingRing
import fr.thibautperrin.tetrano.ui.setup.PendingComputerGameSetup
import fr.thibautperrin.tetrano.ui.setup.PendingTwoPlayersGameSetup
import fr.thibautperrin.tetrano.ui.setup.keepFirst
import fr.thibautperrin.tetrano.ui.theme.TetranoRingColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * Drives the board's animations with the legacy `SurfaceGraphView` timings:
 * - the computer's ring flying in from the tray ([COMPUTER_FLIGHT_MS],
 *   [COMPUTER_FLIGHT_FRAMES] — `jouerAdversaireAnimer`), tray in the
 *   computer's color and no drag meanwhile, then that ring (or the
 *   winning line) blinking;
 * - Cancel flying the undone ring(s) back to the tray: one ring over
 *   [UNDO_ONE_MS]/[UNDO_ONE_FRAMES], two rings (the computer's reply,
 *   then the user's move) over 2 × [UNDO_TWO_MS]/[UNDO_TWO_FRAMES]
 *   (`cliqueAnnuler`);
 * - while the computer searches, the tray drawn as a partial arc in its
 *   color ([fr.thibautperrin.tetrano.domain.model.AiProgressState.trayAngleDegrees],
 *   `HandlerIntelligence`);
 * - blinking: [BLINK_CYCLES] × ([BLINK_HALF_PERIOD_MS] hidden + as long
 *   shown) — `cliquerClignoter(ring, 5, 400, …)` — in its own job, so it
 *   never delays input or dialogs.
 *
 * The buttons are never disabled: Cancel/Play again tapped during a ring
 * flight wait for it to end ([animationLock] — the legacy `occuped` flag and
 * its 100 ms retries), and are applied at once while the computer is only
 * searching (the search is cancelled, see [GameSessionService]).
 *
 * In [GameMode.VS_COMPUTER] the user's name and both colors follow
 * [TetranoPreferencesDataSource] live, so coming back from Settings applies
 * them (`actualiserReglages`).
 *
 * A finished game is saved automatically ([saveIfFinished]), named after
 * the date and time it ended; finishing it again after a Cancel replaces
 * that save rather than adding another ([KEY_SAVED_NAME]).
 *
 * Process-death survival (docs/PLAN.md P8, acceptance criterion A6): unlike
 * [fr.thibautperrin.tetrano.ui.challenge.ChallengeViewModel], which
 * delegates to the already-persisted
 * [fr.thibautperrin.tetrano.domain.service.ChallengeService], this
 * ViewModel's [session] is purely in-memory ([GameSessionService] isn't
 * backed by storage until the game ends), so an
 * in-progress game — including moves played past whatever [savedGameName]
 * pointed at — would otherwise vanish if the process is killed while
 * backgrounded. [savedStateHandle] mirrors the moves, the starting player
 * and the two colors after every change ([persistSessionState]);
 * [resolveSetup] prefers that mirror over [savedGameName]/
 * [PendingComputerGameSetup]/[PendingTwoPlayersGameSetup] when present,
 * exactly the way the legacy `SauvegardePartie` `Parcelable` and the
 * activity's intent extras rebuilt a game.
 */
@HiltViewModel(assistedFactory = GameViewModel.Factory::class)
class GameViewModel @AssistedInject constructor(
    @Assisted private val mode: GameMode,
    @Assisted private val savedGameName: String?,
    @Assisted private val ringCount: Int,
    private val savedStateHandle: SavedStateHandle,
    private val gameSessionFactory: GameSessionService.Factory,
    private val savedGameService: SavedGameService,
    private val preferences: TetranoPreferencesDataSource,
    private val pendingComputerGameSetup: PendingComputerGameSetup,
    private val pendingTwoPlayersGameSetup: PendingTwoPlayersGameSetup,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(mode: GameMode, savedGameName: String?, ringCount: Int): GameViewModel
    }

    private val _uiState = MutableStateFlow(GameUiModel())
    val uiState: StateFlow<GameUiModel> = _uiState.asStateFlow()

    private lateinit var session: GameSessionService
    private var player1Color: Color = TetranoRingColors.Red
    private var player2Color: Color = TetranoRingColors.Blue
    private var player1Name: String = ""
    private var player2Name: String = ""

    /** Held for a whole ring flight: `GestionJeu.occuped`. */
    private val animationLock = Mutex()
    private var blinkJob: Job? = null

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
            persistSessionState()
            render(session.state.value)
            // Subscribed before start(), so the computer's first move can't be missed.
            launch(start = CoroutineStart.UNDISPATCHED) { session.events.collect(::onSessionEvent) }
            launch(start = CoroutineStart.UNDISPATCHED) { session.state.collect(::onSessionState) }
            if (mode == GameMode.VS_COMPUTER) launch { followSettings() }
            session.start()
        }
    }

    fun onAction(action: GameAction) {
        when (action) {
            is GameAction.RingDropped -> ifReady { onRingDropped(action.ring) }
            GameAction.PlayAgainClicked -> ifReady { restart() }
            GameAction.CancelClicked -> ifReady { undo() }
            GameAction.BlinkClicked -> ifReady { startBlink(session.blinkingRings()) }
            GameAction.ReviewClicked -> ifReady { requestReview() }
            GameAction.ReviewHandled -> _uiState.update { it.copy(reviewRequest = null) }
            GameAction.InternalErrorShown -> _uiState.update { it.copy(showInternalError = false) }
            GameAction.BackPressed -> back()
        }
    }

    private fun ifReady(block: () -> Unit) {
        if (::session.isInitialized) block()
    }

    private suspend fun resolveSetup(): ResolvedSetup {
        val base = when (mode) {
            GameMode.TWO_PLAYERS -> {
                val pending = pendingTwoPlayersGameSetup.consume()
                val saved = savedGameName?.let { savedGameService.get(it) }
                // Legacy WatchSavedGameActivity.playTwoPlayers + GestionJeu2Joueurs:
                // the resumed game replays from a fresh Partie, so player 1 is
                // always whoever began the save, and gets that side's color.
                val firstMoverColor = saved?.let { if (it.userBegins) it.userColor else it.adversaryColor }
                val secondMoverColor = saved?.let { if (it.userBegins) it.adversaryColor else it.userColor }
                ResolvedSetup(
                    initialMoves = saved?.ringsPlayed?.keepFirst(ringCount) ?: ByteArray(0),
                    startingPlayer = 1,
                    player1Color = pending.player1Color ?: firstMoverColor ?: preferences.player1Color.first(),
                    player2Color = pending.player2Color ?: secondMoverColor ?: preferences.player2Color.first(),
                    player1Name = preferences.player1Name.first(),
                    player2Name = preferences.player2Name.first(),
                    aiLevel = 0,
                )
            }
            GameMode.VS_COMPUTER -> {
                val pending = pendingComputerGameSetup.consume()
                val saved = savedGameName?.let { savedGameService.get(it) }
                ResolvedSetup(
                    initialMoves = saved?.ringsPlayed?.keepFirst(ringCount) ?: ByteArray(0),
                    startingPlayer = if (pending.userBegins) 1 else 2,
                    player1Color = pending.userColor ?: preferences.userColor.first(),
                    player2Color = pending.adversaryColor ?: preferences.adversaryColor.first(),
                    player1Name = preferences.userName.first(),
                    player2Name = "",
                    aiLevel = preferences.aiLevel.first(),
                )
            }
        }
        // Recovering from process death: the moves actually played this
        // session (possibly more than what savedGameName/PendingComputerGameSetup
        // knows about) and the colors in use take priority — see this class's KDoc.
        val restoredMoves = savedStateHandle.get<ByteArray>(KEY_PLAYED_RINGS)
        val restoredStartingPlayer = savedStateHandle.get<Int>(KEY_STARTING_PLAYER)
        return base.copy(
            initialMoves = restoredMoves ?: base.initialMoves,
            startingPlayer = restoredStartingPlayer.takeIf { restoredMoves != null } ?: base.startingPlayer,
            player1Color = savedStateHandle.get<Int>(KEY_PLAYER1_COLOR) ?: base.player1Color,
            player2Color = savedStateHandle.get<Int>(KEY_PLAYER2_COLOR) ?: base.player2Color,
        )
    }

    private fun persistSessionState() {
        savedStateHandle[KEY_PLAYED_RINGS] = session.playedRings()
        savedStateHandle[KEY_STARTING_PLAYER] = session.startingPlayer()
        savedStateHandle[KEY_PLAYER1_COLOR] = player1Color.toArgb()
        savedStateHandle[KEY_PLAYER2_COLOR] = player2Color.toArgb()
    }

    /** While the computer searches: the progress arc (the other states are rendered by the actions themselves). */
    private fun onSessionState(state: GameSessionState) {
        if (state.isAiThinking) render(state)
    }

    private fun onSessionEvent(event: GameSessionEvent) {
        when (event) {
            is GameSessionEvent.ComputerPlayed -> {
                persistSessionState()
                saveIfFinished()
                viewModelScope.launch {
                    animationLock.withLock { animateComputerMove(event.ring) }
                }
            }
            GameSessionEvent.InternalError -> {
                showInternalError()
                render(session.state.value)
            }
        }
    }

    /** `GestionJeu.postAnneauJoueParAdversaire`. */
    private suspend fun animateComputerMove(ring: Int) {
        val state = session.state.value
        // Already taken back by a Cancel that got the lock first.
        if (state.lastRing != ring) return
        // The title changes at once, except for a winning/last move: it
        // only changes once the ring has landed (finPartie(1500)).
        flyRing(ring, COMPUTER_PLAYER, placing = true, COMPUTER_FLIGHT_MS, COMPUTER_FLIGHT_FRAMES) { moving ->
            render(
                state,
                trayPlayer = COMPUTER_PLAYER,
                dragEnabled = false,
                trayAngleDegrees = FULL_RING,
                movingRing = moving,
                updateTitle = !state.isFinished,
            )
        }
        render(state)
        startBlink(state.blinkingRings)
    }

    /** `SurfaceGraphView.onTouchEvent` → `userTriedPlaying`/`postRingPlayedByUser`. */
    private fun onRingDropped(ring: Int) {
        if (animationLock.isLocked) return
        val played = guarded { session.play(ring) } ?: return
        if (!played) return
        persistSessionState()
        saveIfFinished()
        val state = session.state.value
        render(state)
        if (state.isFinished) startBlink(state.blinkingRings)
    }

    /** `cliqueAnnuler`. */
    private fun undo() {
        viewModelScope.launch {
            animationLock.withLock {
                val undone = guarded { session.undo() }
                if (undone.isNullOrEmpty()) return@withLock
                persistSessionState()
                cancelBlink()
                val state = session.state.value
                // Each ring stays on the board until its own flight back starts.
                val displayed = state.rings.copyOf()
                undone.forEach { displayed[it.ring] = it.player.toByte() }
                val (duration, frames) = if (undone.size == 1) UNDO_ONE_MS to UNDO_ONE_FRAMES else UNDO_TWO_MS to UNDO_TWO_FRAMES
                for (move in undone) {
                    displayed[move.ring] = 0
                    val rings = displayed.copyOf()
                    flyRing(move.ring, move.player, placing = false, duration, frames) { moving ->
                        render(state, rings = rings, trayPlayer = move.player, dragEnabled = false, movingRing = moving)
                    }
                }
                render(state)
            }
        }
    }

    /** `cliqueRecommencer`: always changes who starts. */
    private fun restart() {
        viewModelScope.launch {
            animationLock.withLock {
                cancelBlink()
                session.restart(changingBeginner = true)
                savedStateHandle.remove<String>(KEY_SAVED_NAME)
                persistSessionState()
                render(session.state.value)
            }
        }
    }

    /** `GestionJeu.sortir()`. */
    private fun back() {
        _uiState.update { it.copy(exitRequested = true) }
    }

    /** `cliquerClignoter`: hidden, then shown, [BLINK_CYCLES] times. Restarts any blink in progress. */
    private fun startBlink(rings: ByteArray?) {
        cancelBlink()
        if (rings == null) return
        val hidden = rings.map { it.toInt() }.toSet()
        blinkJob = viewModelScope.launch {
            repeat(BLINK_CYCLES) {
                setHiddenRings(hidden)
                delay(BLINK_HALF_PERIOD_MS)
                setHiddenRings(emptySet())
                delay(BLINK_HALF_PERIOD_MS)
            }
        }
    }

    private fun cancelBlink() {
        blinkJob?.cancel()
        blinkJob = null
        setHiddenRings(emptySet())
    }

    private fun setHiddenRings(rings: Set<Int>) {
        _uiState.update { it.copy(board = it.board.copy(hiddenRings = rings)) }
    }

    /**
     * `SurfaceGraphView.afficherDeplacementAnneau`: frame `i` of [frames],
     * at `i`·[durationMs]/[frames], shows the ring `i/frames` of the way
     * from the tray to its cell — or back, when not [placing].
     */
    private suspend fun flyRing(
        ring: Int,
        player: Int,
        placing: Boolean,
        durationMs: Long,
        frames: Int,
        showFrame: (MovingRing) -> Unit,
    ) {
        for (i in 0 until frames) {
            val fraction = i.toFloat() / frames
            showFrame(MovingRing(ringNumber = ring, player = player, progress = if (placing) fraction else 1f - fraction))
            delay((i + 1) * durationMs / frames - i * durationMs / frames)
        }
    }

    /**
     * Shows [state]. By default the tray is at rest: the next human player's
     * color and draggable; while the computer searches, its color drawn as
     * the progress arc (grey until the first progress report, like the
     * legacy `joueurAnneauSelection = 0` after a drop); grey once finished.
     */
    private fun render(
        state: GameSessionState,
        rings: ByteArray = state.rings,
        trayPlayer: Int = restingTrayPlayer(state),
        dragEnabled: Boolean = mode == GameMode.TWO_PLAYERS || trayPlayer != COMPUTER_PLAYER,
        trayAngleDegrees: Float = state.aiProgress?.trayAngleDegrees ?: FULL_RING,
        movingRing: MovingRing? = null,
        updateTitle: Boolean = true,
    ) {
        _uiState.update { current ->
            current.copy(
                isLoading = false,
                board = current.board.copy(
                    rings = rings,
                    player1Color = player1Color,
                    player2Color = player2Color,
                    trayPlayer = trayPlayer,
                    dragEnabled = dragEnabled,
                    trayAngleDegrees = trayAngleDegrees,
                    movingRing = movingRing,
                ),
                player1Name = player1Name,
                player2Name = player2Name,
                title = if (updateTitle) state.toTitle() else current.title,
            )
        }
    }

    private fun restingTrayPlayer(state: GameSessionState): Int = when {
        state.isFinished -> 0
        mode == GameMode.TWO_PLAYERS -> state.nextPlayer
        state.nextPlayer == 1 -> 1
        state.aiProgress != null -> COMPUTER_PLAYER
        else -> 0
    }

    private fun GameSessionState.toTitle() = GameTitleState(
        nextPlayer = nextPlayer,
        playedCount = playedCount,
        isFinished = isFinished,
        isDraw = isDraw,
        winner = winner,
    )

    /**
     * `actualiserReglages`: the user's name and colors, when changed in
     * Settings. The first value is skipped: it's what [resolveSetup] already
     * applied — or deliberately overrode, for a resumed game's own colors.
     */
    private suspend fun followSettings() {
        combine(preferences.userName, preferences.userColor, preferences.adversaryColor, ::Triple)
            .distinctUntilChanged()
            .drop(1)
            .collect { (name, userColor, adversaryColor) ->
                player1Name = name
                player1Color = Color(userColor)
                player2Color = Color(adversaryColor)
                persistSessionState()
                _uiState.update {
                    it.copy(
                        player1Name = name,
                        board = it.board.copy(player1Color = player1Color, player2Color = player2Color),
                    )
                }
            }
    }

    /** Asks the screen to open the review of the game, with the same player mapping as [save]. */
    private fun requestReview() {
        val request = ReviewRequest(
            rings = session.playedRings().joinToString(","),
            userBegins = session.startingPlayer() == 1,
            userColor = player1Color.toArgb(),
            adversaryColor = player2Color.toArgb(),
        )
        _uiState.update { it.copy(reviewRequest = request) }
    }

    /**
     * Saves the game once it is over, named after the current date and time (in the
     * device's locale), with player 1 = the user; a game of this screen already saved (then
     * taken back with Cancel and finished again) is replaced under the same name.
     */
    private fun saveIfFinished() {
        if (!session.state.value.isFinished) return
        val rings = session.playedRings()
        val userBegins = session.startingPlayer() == 1
        val userColor = player1Color.toArgb()
        val adversaryColor = player2Color.toArgb()
        viewModelScope.launch {
            guarded {
                val previous = savedStateHandle.get<String>(KEY_SAVED_NAME)
                val name = if (previous != null) {
                    savedGameService.replace(previous, rings, userBegins, userColor, adversaryColor)
                    previous
                } else {
                    val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date())
                    savedGameService.save(date, rings, userBegins, userColor, adversaryColor)
                }
                savedStateHandle[KEY_SAVED_NAME] = name
            }
        }
    }

    /** Runs [block]; an exception shows `Internal_Error` (`afficheErreurInterne`) and yields null. */
    private inline fun <T> guarded(block: () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        showInternalError()
        null
    }

    private fun showInternalError() {
        _uiState.update { it.copy(showInternalError = true) }
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
        const val FULL_RING = 360f
        const val COMPUTER_FLIGHT_MS = 1500L
        const val COMPUTER_FLIGHT_FRAMES = 40
        const val UNDO_ONE_MS = 1200L
        const val UNDO_ONE_FRAMES = 30
        const val UNDO_TWO_MS = 800L
        const val UNDO_TWO_FRAMES = 20
        const val BLINK_CYCLES = 5
        const val BLINK_HALF_PERIOD_MS = 200L
        const val KEY_PLAYED_RINGS = "game_played_rings"
        const val KEY_STARTING_PLAYER = "game_starting_player"
        const val KEY_PLAYER1_COLOR = "game_player1_color"
        const val KEY_PLAYER2_COLOR = "game_player2_color"
        const val KEY_SAVED_NAME = "game_saved_name"
    }
}
