package fr.thibautperrin.tetrano.ui.challenge

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.thibautperrin.tetrano.data.prefs.TetranoPreferencesDataSource
import fr.thibautperrin.tetrano.domain.model.ChallengeHintResult
import fr.thibautperrin.tetrano.domain.model.ChallengeMoveResult
import fr.thibautperrin.tetrano.domain.service.ChallengeService
import fr.thibautperrin.tetrano.domain.service.StarService
import fr.thibautperrin.tetrano.ui.components.MovingRing
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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
 * Backs [ChallengeScreen] — replaces `ChallengeActivity`/`Challenge`/
 * `MemoryVarsChallenge` (see docs/GLOSSARY.md, docs/PLAN.md P8). Unlike
 * [fr.thibautperrin.tetrano.ui.game.GameViewModel], [ChallengeService] has
 * no continuous session state to observe: every action here is a single
 * suspend call into it, whose result this ViewModel turns into a state
 * update (and, where the legacy activity animated, a short animation
 * first, reusing docs/PLAN.md P7.5/§6.3's approach).
 *
 * Animation fidelity notes:
 * - the computer's reply flies in from the tray ([MOVE_IN_DURATION_MS]/
 *   [MOVE_IN_STEPS] — `jouerAdversaireAnimer`'s Challenge-specific
 *   1200 ms/40-step numbers), then blinks ([BLINK_CYCLES] x
 *   [BLINK_PERIOD_MS] — `cliquerClignoter`'s numbers, unchanged);
 * - the star hint's forced pair ([ChallengeHintResult.Hint]) is
 *   simplified to two sequential fly-ins (user's ring, then computer's),
 *   reusing the same constants, rather than `useAStar`'s overlapping
 *   choreography (the computer ring's own fly-in starts 1000 ms into the
 *   user ring's) — judged excessive scope for a hint animation;
 * - a level-ending move made by the user directly (not a computer reply)
 *   blinks immediately with no fly-in, exactly like
 *   [fr.thibautperrin.tetrano.ui.game.GameViewModel]'s equivalent case —
 *   it was already dragged onto the board live.
 *
 * [ChallengeService.finishAttempt] already calls
 * [StarService.recordChallengeAttempted] internally and discards its
 * result (see [ChallengeService]'s KDoc). Rather than threading a
 * [fr.thibautperrin.tetrano.domain.model.StarAward] through
 * [ChallengeService]/[fr.thibautperrin.tetrano.domain.model.ChallengeMoveResult]
 * (out of this commit's scope — domain/service is a no-touch area here),
 * this ViewModel detects the award by diffing [StarService.numberStars]
 * across any call that can trigger it ([withStarAwardDetection]). The one
 * known gap: spending a star and earning this award in the very same call
 * (a hint that also happens to complete "tried all modes") nets to no
 * visible change and goes undetected — accepted as a rare edge case.
 */
@HiltViewModel
class ChallengeViewModel @Inject constructor(
    private val challengeService: ChallengeService,
    private val starService: StarService,
    private val preferences: TetranoPreferencesDataSource,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChallengeUiModel())
    val uiState: StateFlow<ChallengeUiModel> = _uiState.asStateFlow()

    private val _exit = Channel<ChallengeExit>(Channel.BUFFERED)
    val exit: Flow<ChallengeExit> = _exit.receiveAsFlow()

    private var animationJob: Job? = null

    init {
        viewModelScope.launch {
            val userColor = Color(preferences.userColor.first())
            val adversaryColor = Color(preferences.adversaryColor.first())
            _uiState.update { it.copy(board = it.board.copy(player1Color = userColor, player2Color = adversaryColor)) }
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
            ChallengeAction.UseStarMenuClicked -> onUseStarMenuClicked()
            ChallengeAction.NoStarsAcknowledged -> _uiState.update { it.copy(showNoStarsMessage = false) }
            ChallengeAction.UseStarConfirmed -> useStarHint()
            ChallengeAction.UseStarDismissed -> _uiState.update { it.copy(showConfirmUseStar = false) }
            ChallengeAction.EndDialogContinueClicked -> continueAfterEndDialog()
            ChallengeAction.EndDialogQuitClicked -> _exit.trySend(ChallengeExit.Quit)
            ChallengeAction.AllModesTriedAwardAcknowledged -> _uiState.update { it.copy(showAllModesTriedAward = false) }
        }
    }

    private suspend fun loadCurrentLevel(showExplanationsIfFirstLevel: Boolean) {
        val hasLevel = challengeService.startCurrentLevel()
        if (!hasLevel) {
            _exit.send(ChallengeExit.AllLevelsPassed)
            return
        }
        val state = challengeService.snapshot()
        _uiState.update {
            it.copy(
                isLoading = false,
                board = it.board.copy(rings = state.rings, hiddenRings = emptySet(), movingRing = null, trayPlayer = 1),
                level = state.level,
                usedRings = state.usedRings,
                authorizedRings = state.authorizedRings,
                canAbandon = state.canAbandon,
                numberStars = starService.numberStars.first(),
                showExplanationsPage = if (showExplanationsIfFirstLevel && state.level == 1) 1 else null,
                endDialog = null,
            )
        }
    }

    private fun playRing(ring: Int) {
        if (_uiState.value.board.trayPlayer == 0) return
        animationJob?.cancel()
        animationJob = viewModelScope.launch {
            val before = _uiState.value.board.rings
            setBusy()
            when (val result = withStarAwardDetection { challengeService.playUserRing(ring) }) {
                ChallengeMoveResult.InvalidMove -> _uiState.update { it.copy(board = it.board.copy(trayPlayer = 1)) }
                is ChallengeMoveResult.ComputerReplied -> {
                    val state = challengeService.snapshot()
                    animateMoveIn(state.rings, result.computerRing, PLAYER_COMPUTER)
                    blink(byteArrayOf(result.computerRing.toByte()), state.rings)
                    _uiState.update {
                        it.copy(
                            board = it.board.copy(rings = state.rings, hiddenRings = emptySet(), trayPlayer = 1),
                            usedRings = state.usedRings,
                            authorizedRings = state.authorizedRings,
                            canAbandon = state.canAbandon,
                        )
                    }
                }
                is ChallengeMoveResult.LevelPassed -> finishLevel(ChallengeEndSituation.USER_WON, result.level, before)
                is ChallengeMoveResult.LevelFailed -> {
                    val situation = if (result.computerWon) ChallengeEndSituation.COMPUTER_WON else ChallengeEndSituation.RAN_OUT_OF_RINGS
                    finishLevel(situation, result.level, before)
                }
            }
        }
    }

    private fun abandon() {
        animationJob?.cancel()
        animationJob = viewModelScope.launch {
            val before = _uiState.value.board.rings
            setBusy()
            val result = withStarAwardDetection { challengeService.abandon() }
            finishLevel(ChallengeEndSituation.ABANDONED, result.level, before)
        }
    }

    /** Ports `postRingPlayedByUser`/`onClickNbRings` (buttonAbandon)'s end-of-attempt handling, for every way an attempt can end. */
    private suspend fun finishLevel(situation: ChallengeEndSituation, level: Int, boardBeforeMove: ByteArray) {
        val state = challengeService.snapshot()
        val computerRing = newlyPlacedRing(boardBeforeMove, state.rings, player = PLAYER_COMPUTER)
        if (computerRing != null) {
            animateMoveIn(state.rings, computerRing, PLAYER_COMPUTER)
        }
        val blinkingRings = challengeService.blinkingRings()
        if (blinkingRings.isNotEmpty()) {
            blink(blinkingRings, state.rings)
        } else {
            publish(state.rings, trayPlayer = 0)
        }
        _uiState.update {
            it.copy(
                usedRings = state.usedRings,
                authorizedRings = state.authorizedRings,
                canAbandon = state.canAbandon,
                endDialog = ChallengeEndDialog(situation, level),
            )
        }
    }

    private fun continueAfterEndDialog() {
        animationJob?.cancel()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, endDialog = null) }
            loadCurrentLevel(showExplanationsIfFirstLevel = false)
        }
    }

    private fun onUseStarMenuClicked() {
        viewModelScope.launch {
            val stars = starService.numberStars.first()
            _uiState.update {
                it.copy(numberStars = stars, showNoStarsMessage = stars <= 0, showConfirmUseStar = stars > 0)
            }
        }
    }

    private fun useStarHint() {
        _uiState.update { it.copy(showConfirmUseStar = false) }
        animationJob?.cancel()
        animationJob = viewModelScope.launch {
            setBusy()
            when (val result = withStarAwardDetection { challengeService.useStarHint() }) {
                ChallengeHintResult.NoStarsAvailable -> _uiState.update {
                    it.copy(showNoStarsMessage = true, board = it.board.copy(trayPlayer = 1))
                }
                is ChallengeHintResult.LevelWon -> {
                    val state = challengeService.snapshot()
                    animateMoveIn(state.rings, result.ringForUser, PLAYER_USER)
                    val blinkingRings = challengeService.blinkingRings()
                    if (blinkingRings.isNotEmpty()) blink(blinkingRings, state.rings) else publish(state.rings, trayPlayer = 0)
                    _uiState.update {
                        it.copy(
                            usedRings = state.usedRings,
                            authorizedRings = state.authorizedRings,
                            canAbandon = state.canAbandon,
                            endDialog = ChallengeEndDialog(ChallengeEndSituation.USER_WON, result.level),
                        )
                    }
                }
                is ChallengeHintResult.Hint -> {
                    val baseRings = result.boardBeforeHint ?: _uiState.value.board.rings
                    if (result.boardBeforeHint != null) publish(baseRings, trayPlayer = 0)
                    val afterUser = baseRings.copyOf().also { it[result.ringForUser] = PLAYER_USER.toByte() }
                    animateMoveIn(afterUser, result.ringForUser, PLAYER_USER)
                    val state = challengeService.snapshot()
                    animateMoveIn(state.rings, result.ringForComputer, PLAYER_COMPUTER)
                    blink(byteArrayOf(result.ringForUser.toByte(), result.ringForComputer.toByte()), state.rings)
                    _uiState.update {
                        it.copy(
                            board = it.board.copy(trayPlayer = 1),
                            usedRings = state.usedRings,
                            authorizedRings = state.authorizedRings,
                            canAbandon = state.canAbandon,
                            numberStars = starService.numberStars.first(),
                        )
                    }
                }
            }
        }
    }

    private suspend fun <T> withStarAwardDetection(block: suspend () -> T): T {
        val before = starService.numberStars.first()
        val result = block()
        if (starService.numberStars.first() > before) {
            _uiState.update { it.copy(showAllModesTriedAward = true) }
        }
        return result
    }

    private fun setBusy() {
        _uiState.update { it.copy(board = it.board.copy(trayPlayer = 0)) }
    }

    private fun publish(rings: ByteArray, hiddenRings: Set<Int> = emptySet(), movingRing: MovingRing? = null, trayPlayer: Int = 0) {
        _uiState.update {
            it.copy(board = it.board.copy(rings = rings, hiddenRings = hiddenRings, movingRing = movingRing, trayPlayer = trayPlayer))
        }
    }

    private suspend fun animateMoveIn(finalRings: ByteArray, ring: Int, player: Int) {
        for (step in 0..MOVE_IN_STEPS) {
            val progress = step / MOVE_IN_STEPS.toFloat()
            publish(finalRings, movingRing = MovingRing(ringNumber = ring, player = player, progress = progress))
            if (step < MOVE_IN_STEPS) delay(MOVE_IN_DURATION_MS / MOVE_IN_STEPS)
        }
        publish(finalRings)
    }

    private suspend fun blink(ringsToBlink: ByteArray, finalRings: ByteArray) {
        val hidden = ringsToBlink.map { it.toInt() }.toSet()
        repeat(BLINK_CYCLES) {
            publish(finalRings, hiddenRings = hidden)
            delay(BLINK_PERIOD_MS)
            publish(finalRings)
            delay(BLINK_PERIOD_MS)
        }
    }

    /** The ring [player] newly occupies in [after] but not in [before], if any. */
    private fun newlyPlacedRing(before: ByteArray, after: ByteArray, player: Int): Int? {
        for (ring in after.indices) {
            if (before[ring] == EMPTY && after[ring].toInt() == player) return ring
        }
        return null
    }

    private companion object {
        const val EMPTY: Byte = 0
        const val PLAYER_USER = 1
        const val PLAYER_COMPUTER = 2
        const val MOVE_IN_DURATION_MS = 1200L
        const val MOVE_IN_STEPS = 40
        const val BLINK_CYCLES = 5
        const val BLINK_PERIOD_MS = 400L
    }
}
