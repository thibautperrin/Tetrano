package fr.thibautperrin.tetrano.ui.challenge

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.thibautperrin.tetrano.data.prefs.TetranoPreferencesDataSource
import fr.thibautperrin.tetrano.domain.model.ChallengeHintResult
import fr.thibautperrin.tetrano.domain.model.ChallengeMoveResult
import fr.thibautperrin.tetrano.domain.model.ChallengeState
import fr.thibautperrin.tetrano.domain.service.ChallengeService
import fr.thibautperrin.tetrano.ui.components.BoardVisualState
import fr.thibautperrin.tetrano.ui.components.MovingRing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/**
 * Backs [ChallengeScreen] — replaces `ChallengeActivity`/`Challenge`/
 * `MemoryVarsChallenge` (see docs/GLOSSARY.md, docs/PLAN.md P8). Every
 * action is a single call into [ChallengeService] (serialized by
 * [serviceMutex], never cancelled half-way), whose result this ViewModel
 * turns into state updates, replaying the legacy `SurfaceGraphView`
 * choreography (all times from the moment the result is known):
 *
 * - computer reply (`postRingPlayedByUser`): counters at once; the ring
 *   flies in over [COMPUTER_FLIGHT_MS]/[COMPUTER_FLIGHT_FRAMES]
 *   (`jouerAdversaireAnimer(…, 1200, 40)`) with the tray in the computer's
 *   color and drag refused; at 1200 ms the user's tray is unlocked and the
 *   ring starts blinking ([BLINK_CYCLES] × [BLINK_HIDDEN_MS] +
 *   [BLINK_SHOWN_MS], `cliquerClignoter(…, 5, 400, 1200)`);
 * - the user's own ring wins: the line blinks from 0 and the end dialog
 *   shows at once; the user's ring exhausts the budget, or the user gives
 *   up: the end dialog shows at once;
 * - the computer's reply wins: same flight, the tray goes grey at 1200 ms,
 *   the line blinks from 1200 ms, the end dialog shows at
 *   [COMPUTER_WIN_DIALOG_MS];
 * - hint (`useAStar`, free since the stars are gone): the user's ring
 *   flies over 0–1000 ms, the computer's over 1000–2000 ms
 *   ([HINT_FLIGHT_MS]/[HINT_FLIGHT_FRAMES]), then only the computer's ring
 *   blinks and the user may play; a hint that
 *   wins at once flies the user's ring over 1200 ms, blinks the line from
 *   there and shows the end dialog at [HINT_WIN_DIALOG_MS].
 *
 * Blinks run as their own jobs ([blinkJobs]), in parallel with whatever
 * the user does next — never blocking input or dialogs; only a board reset
 * (hint, next attempt) stops them, like `redemarrerPartieDepuis`.
 */
@HiltViewModel
class ChallengeViewModel @Inject constructor(
    private val challengeService: ChallengeService,
    preferences: TetranoPreferencesDataSource,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChallengeUiModel())
    val uiState: StateFlow<ChallengeUiModel> = _uiState.asStateFlow()

    private val _exit = Channel<ChallengeExit>(Channel.BUFFERED)
    val exit: Flow<ChallengeExit> = _exit.receiveAsFlow()

    /** Serializes calls into the singleton, non-thread-safe [ChallengeService]. */
    private val serviceMutex = Mutex()

    /** The running timed sequence: ring flights, then a delayed tray unlock or end dialog. */
    private var sequenceJob: Job? = null

    /** Independent blinks (legacy `cliquerClignoter` messages), see the class KDoc. */
    private val blinkJobs = mutableListOf<Job>()

    /** True from the end of an attempt until the next one is loaded (and before the first load). */
    private var isLevelOver = true

    private val colors: Flow<Pair<Color, Color>> =
        combine(preferences.userColor, preferences.adversaryColor) { user, adversary -> Color(user) to Color(adversary) }

    init {
        viewModelScope.launch {
            // Kept collecting: colors changed in Settings apply on return (legacy onActivityResult → refreshSettings).
            colors.collect(::applyColors)
        }
        viewModelScope.launch {
            applyColors(colors.first()) // never draw the first position in the default colors
            loadCurrentLevel(showExplanationsIfFirstLevel = true)
        }
    }

    fun onAction(action: ChallengeAction) {
        when (action) {
            is ChallengeAction.RingDropped -> playRing(action.ring)
            ChallengeAction.AbandonClicked -> abandon()
            ChallengeAction.RingsCounterClicked -> _uiState.update { it.copy(showRingsInfoDialog = true) }
            ChallengeAction.RingsInfoDismissed -> _uiState.update { it.copy(showRingsInfoDialog = false) }
            ChallengeAction.ExplanationsMenuClicked -> _uiState.update { it.copy(showExplanationsPage = 1) }
            ChallengeAction.ExplanationsNextClicked -> _uiState.update { it.copy(showExplanationsPage = 2) }
            ChallengeAction.ExplanationsDismissed -> _uiState.update { it.copy(showExplanationsPage = null) }
            ChallengeAction.HintMenuClicked -> useHint()
            ChallengeAction.EndDialogContinueClicked -> continueAfterEndDialog()
            ChallengeAction.EndDialogQuitClicked -> _exit.trySend(ChallengeExit.Quit)
        }
    }

    private fun applyColors(colors: Pair<Color, Color>) {
        _uiState.update { it.copy(board = it.board.copy(player1Color = colors.first, player2Color = colors.second)) }
    }

    /** Ports `onCreate`/`followingGame`: (re)loads the persisted attempt and lets the user play. */
    private suspend fun loadCurrentLevel(showExplanationsIfFirstLevel: Boolean) {
        val state = serviceMutex.withLock {
            if (challengeService.startCurrentLevel()) challengeService.snapshot() else null
        }
        if (state == null) {
            _exit.send(ChallengeExit.AllLevelsPassed)
            return
        }
        isLevelOver = false
        _uiState.update {
            it.copy(
                board = it.board.copy(
                    rings = state.rings,
                    hiddenRings = emptySet(),
                    movingRing = null,
                    trayPlayer = PLAYER_USER,
                    dragEnabled = true,
                ),
                level = state.level,
                usedRings = state.usedRings,
                authorizedRings = state.authorizedRings,
                canAbandon = state.canAbandon,
                showExplanationsPage = if (showExplanationsIfFirstLevel && state.level == 1) 1 else it.showExplanationsPage,
                endDialog = null,
            )
        }
    }

    private fun playRing(ring: Int) {
        val board = _uiState.value.board
        if (isLevelOver || board.trayPlayer != PLAYER_USER || !board.dragEnabled) return
        // userTriedPlaying refuses an occupied (or out-of-range) cell: the tray stays available.
        if (board.rings.getOrNull(ring) != EMPTY) return
        // Accepted: the ring stays where it was dropped and the tray is put away (MOVE_CHOICE_ACCEPTED).
        updateBoard { it.copy(rings = it.rings.copyOf().also { rings -> rings[ring] = PLAYER_USER.toByte() }, trayPlayer = NOBODY) }
        viewModelScope.launch {
            val reply = callService { challengeService.playUserRing(ring) }
            when (val result = reply.result) {
                ChallengeMoveResult.InvalidMove -> {
                    publishCounters(reply.state)
                    updateBoard { it.copy(rings = reply.state.rings, trayPlayer = if (reply.state.isOver) NOBODY else PLAYER_USER) }
                }
                is ChallengeMoveResult.ComputerReplied -> onComputerReplied(result.computerRing, reply.state)
                is ChallengeMoveResult.LevelPassed -> endAttempt(
                    reply = reply,
                    dialog = ChallengeEndDialog(ChallengeEndSituation.USER_WON, result.level),
                )
                is ChallengeMoveResult.LevelFailed -> {
                    val computerRing = result.computerRing
                    if (computerRing == null) {
                        endAttempt(
                            reply = reply,
                            dialog = ChallengeEndDialog(ChallengeEndSituation.RAN_OUT_OF_RINGS, result.level),
                        )
                    } else {
                        endAttempt(
                            reply = reply,
                            dialog = ChallengeEndDialog(ChallengeEndSituation.COMPUTER_WON, result.level),
                            flight = Flight(computerRing, PLAYER_COMPUTER, COMPUTER_FLIGHT_MS, COMPUTER_FLIGHT_FRAMES),
                            dialogDelayMs = COMPUTER_WIN_DIALOG_MS,
                        )
                    }
                }
            }
        }
    }

    /** `jouerAdversaireAnimer(ring, 2, 1, 1200, 40)` + `cliquerClignoter(ring, 5, 400, 1200)`. */
    private fun onComputerReplied(computerRing: Int, state: ChallengeState) {
        publishCounters(state)
        runSequence {
            flyIn(state.rings, Flight(computerRing, PLAYER_COMPUTER, COMPUTER_FLIGHT_MS, COMPUTER_FLIGHT_FRAMES))
            updateBoard { it.copy(trayPlayer = PLAYER_USER, dragEnabled = true) }
            startBlink(listOf(computerRing))
        }
    }

    /** Ports `buttonAbandon`'s click listener: `displayDialogEnd(level, SITUATION_ABANDONED)`, immediately. */
    private fun abandon() {
        if (isLevelOver || !_uiState.value.canAbandon) return
        updateBoard { it.copy(dragEnabled = false) }
        viewModelScope.launch {
            val reply = callService { challengeService.abandon() }
            val result = reply.result ?: return@launch
            endAttempt(
                reply = reply,
                dialog = ChallengeEndDialog(ChallengeEndSituation.ABANDONED, result.level),
            )
        }
    }

    /**
     * Every way an attempt ends: counters at once, the optional [flight],
     * the tray put away, the winning line (if any) blinking, then the end
     * dialog [dialogDelayMs] after the result (legacy `displayDialogEnd`
     * with or without its `delay`).
     */
    private fun endAttempt(
        reply: ServiceReply<*>,
        dialog: ChallengeEndDialog,
        flight: Flight? = null,
        dialogDelayMs: Long = 0L,
    ) {
        publishCounters(reply.state)
        runSequence {
            if (flight != null) flyIn(reply.state.rings, flight) else updateBoard { it.copy(rings = reply.state.rings) }
            updateBoard { it.copy(trayPlayer = NOBODY, dragEnabled = false) }
            if (reply.winningLine.isNotEmpty()) startBlink(reply.winningLine.map { it.toInt() })
            delay(dialogDelayMs - (flight?.durationMs ?: 0L))
            _uiState.update { it.copy(endDialog = dialog) }
        }
    }

    private fun continueAfterEndDialog() {
        cancelSequence()
        cancelBlinks()
        _uiState.update { it.copy(endDialog = null) }
        viewModelScope.launch { loadCurrentLevel(showExplanationsIfFirstLevel = false) }
    }

    /** Ports the `menu_use_a_star` item and `useAStar`, minus the star: no confirmation, no limit. */
    private fun useHint() {
        if (isLevelOver) return
        // autoriserDeplacement(0, 0): no drag (nor pending flight) until the hint has played out.
        cancelSequence()
        updateBoard { it.copy(dragEnabled = false) }
        viewModelScope.launch {
            val reply = callService { challengeService.useHint() }
            when (val result = reply.result) {
                ChallengeHintResult.LevelOver -> Unit
                is ChallengeHintResult.LevelWon -> {
                    cancelBlinks()
                    endAttempt(
                        reply = reply,
                        dialog = ChallengeEndDialog(ChallengeEndSituation.USER_WON, result.level),
                        flight = Flight(result.ringForUser, PLAYER_USER, COMPUTER_FLIGHT_MS, COMPUTER_FLIGHT_FRAMES),
                        dialogDelayMs = HINT_WIN_DIALOG_MS,
                    )
                }
                is ChallengeHintResult.Hint -> {
                    cancelBlinks()
                    val finalRings = reply.state.rings
                    val afterUserRing = finalRings.copyOf().also { it[result.ringForComputer] = EMPTY }
                    publishCounters(reply.state)
                    runSequence {
                        flyIn(afterUserRing, Flight(result.ringForUser, PLAYER_USER, HINT_FLIGHT_MS, HINT_FLIGHT_FRAMES))
                        flyIn(finalRings, Flight(result.ringForComputer, PLAYER_COMPUTER, HINT_FLIGHT_MS, HINT_FLIGHT_FRAMES))
                        updateBoard { it.copy(trayPlayer = PLAYER_USER, dragEnabled = true) }
                        startBlink(listOf(result.ringForComputer))
                    }
                }
            }
        }
    }

    // ---- Service access ----

    /** A service result plus the position right after it, read under the same lock. */
    private class ServiceReply<T>(val result: T, val state: ChallengeState, val winningLine: ByteArray)

    private suspend fun <T> callService(call: suspend () -> T): ServiceReply<T> {
        val reply = serviceMutex.withLock {
            val result = call()
            ServiceReply(result, challengeService.snapshot(), challengeService.blinkingRings())
        }
        isLevelOver = reply.state.isOver
        return reply
    }

    // ---- Board choreography ----

    /** One ring moving from the tray to its cell (legacy `jouerAdversaireAnimer`/`afficherDeplacementAnneau`). */
    private class Flight(val ring: Int, val player: Int, val durationMs: Long, val frames: Int)

    private fun updateBoard(transform: (BoardVisualState) -> BoardVisualState) {
        _uiState.update { it.copy(board = transform(it.board)) }
    }

    private fun publishCounters(state: ChallengeState) {
        _uiState.update {
            it.copy(usedRings = state.usedRings, authorizedRings = state.authorizedRings, canAbandon = state.canAbandon)
        }
    }

    private fun runSequence(block: suspend CoroutineScope.() -> Unit) {
        cancelSequence()
        sequenceJob = viewModelScope.launch(block = block)
    }

    /** Stops the running sequence; a ring caught mid-flight simply lands. */
    private fun cancelSequence() {
        sequenceJob?.cancel()
        sequenceJob = null
        updateBoard { it.copy(movingRing = null) }
    }

    /**
     * Shows [finalRings] with [Flight.ring] travelling from the tray, the
     * tray in the flying ring's color and drag refused
     * (`afficherAnneauASelectionner(joueur, 0)`); the ring lands after
     * [Flight.durationMs].
     */
    private suspend fun flyIn(finalRings: ByteArray, flight: Flight) {
        for (frame in 0 until flight.frames) {
            val progress = frame / flight.frames.toFloat()
            updateBoard {
                it.copy(
                    rings = finalRings,
                    movingRing = MovingRing(ringNumber = flight.ring, player = flight.player, progress = progress),
                    trayPlayer = flight.player,
                    dragEnabled = false,
                )
            }
            delay((frame + 1) * flight.durationMs / flight.frames - frame * flight.durationMs / flight.frames)
        }
        updateBoard { it.copy(rings = finalRings, movingRing = null) }
    }

    /** `cliquerClignoter(ring, 5, 400, 0)` for each of [rings], as an independent job. */
    private fun startBlink(rings: Collection<Int>) {
        val blinking = rings.toSet()
        val job = viewModelScope.launch {
            repeat(BLINK_CYCLES) {
                updateBoard { it.copy(hiddenRings = it.hiddenRings + blinking) }
                delay(BLINK_HIDDEN_MS)
                updateBoard { it.copy(hiddenRings = it.hiddenRings - blinking) }
                delay(BLINK_SHOWN_MS)
            }
        }
        blinkJobs += job
        job.invokeOnCompletion { blinkJobs -= job }
    }

    private fun cancelBlinks() {
        blinkJobs.toList().forEach { it.cancel() }
        blinkJobs.clear()
        updateBoard { it.copy(hiddenRings = emptySet()) }
    }

    private companion object {
        const val EMPTY: Byte = 0
        const val NOBODY = 0
        const val PLAYER_USER = 1
        const val PLAYER_COMPUTER = 2

        const val COMPUTER_FLIGHT_MS = 1200L
        const val COMPUTER_FLIGHT_FRAMES = 40
        const val COMPUTER_WIN_DIALOG_MS = 2200L
        const val HINT_FLIGHT_MS = 1000L
        const val HINT_FLIGHT_FRAMES = 30
        const val HINT_WIN_DIALOG_MS = 2000L

        const val BLINK_CYCLES = 5
        const val BLINK_HIDDEN_MS = 200L
        const val BLINK_SHOWN_MS = 200L
    }
}
