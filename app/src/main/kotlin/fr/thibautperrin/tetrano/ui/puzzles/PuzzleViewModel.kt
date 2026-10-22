package fr.thibautperrin.tetrano.ui.puzzles

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.thibautperrin.tetrano.data.prefs.TetranoPreferencesDataSource
import fr.thibautperrin.tetrano.domain.service.PuzzleService
import fr.thibautperrin.tetrano.engine.nn.QubicGeometry
import fr.thibautperrin.tetrano.engine.puzzle.Puzzle
import fr.thibautperrin.tetrano.engine.puzzle.PuzzleAttempt
import fr.thibautperrin.tetrano.ui.components.BoardVisualState
import fr.thibautperrin.tetrano.ui.components.GhostRing
import fr.thibautperrin.tetrano.ui.components.MovingRing
import fr.thibautperrin.tetrano.ui.theme.TetranoRingColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

/** Everything [PuzzleScreen] needs. The player's rings are board player 1, the opponent's 2. */
data class PuzzleUiModel(
    val isLoading: Boolean = true,
    val board: BoardVisualState = BoardVisualState(
        rings = ByteArray(64),
        player1Color = TetranoRingColors.Red,
        player2Color = TetranoRingColors.Blue,
    ),
    val phase: PuzzlePhase = PuzzlePhase.PLAYING,
    /** The player's moves needed to win, from the puzzle's start. */
    val movesToWin: Int = 1,
    /** The player's moves still to find. */
    val movesLeft: Int = 1,
    /** At least one correct move was played in this attempt. */
    val progressing: Boolean = false,
    val rating: Int = 0,
    /** Rating change from this puzzle (after its first attempt), else null. */
    val ratingDelta: Int? = null,
    /** The puzzle's own rating, revealed once it is over. */
    val puzzleRating: Int? = null,
    val attempts: Int = 0,
    val solved: Int = 0,
)

enum class PuzzlePhase {
    /** The opponent's last move or reply is landing: no input. */
    OPPONENT_MOVING,
    PLAYING,
    SOLVED,
    FAILED,
}

sealed interface PuzzleAction {
    data class RingDropped(val ring: Int) : PuzzleAction
    data object RetryClicked : PuzzleAction
    data object NextClicked : PuzzleAction
}

/**
 * Backs [PuzzleScreen]: plays the puzzle from [PuzzleService] — the
 * opponent's last move flies in first, then each correct move of the
 * player is answered by the stored reply. A wrong move ends the attempt and
 * shows a correct move as a translucent ring; the first attempt at a puzzle
 * changes the rating, retries do not.
 */
@HiltViewModel
class PuzzleViewModel @Inject constructor(
    private val puzzleService: PuzzleService,
    private val preferences: TetranoPreferencesDataSource,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PuzzleUiModel())
    val uiState: StateFlow<PuzzleUiModel> = _uiState.asStateFlow()

    private lateinit var puzzle: Puzzle
    private lateinit var attempt: PuzzleAttempt
    private var playerColor = TetranoRingColors.Red
    private var opponentColor = TetranoRingColors.Blue
    private var animation: Job? = null

    /** Loading the next puzzle: a second tap on Next meanwhile is ignored. */
    private var nextJob: Job? = null

    init {
        viewModelScope.launch {
            playerColor = Color(preferences.userColor.first())
            opponentColor = Color(preferences.adversaryColor.first())
            show(puzzleService.current())
        }
    }

    fun onAction(action: PuzzleAction) {
        if (!::puzzle.isInitialized) return
        when (action) {
            is PuzzleAction.RingDropped -> play(action.ring)
            PuzzleAction.RetryClicked -> {
                animation?.cancel()
                attempt = PuzzleAttempt(puzzle)
                render(PuzzlePhase.PLAYING)
            }
            PuzzleAction.NextClicked -> {
                if (nextJob?.isActive == true) return
                animation?.cancel()
                nextJob = viewModelScope.launch { show(puzzleService.next()) }
            }
        }
    }

    private fun show(round: PuzzleService.Round) {
        puzzle = round.puzzle
        attempt = PuzzleAttempt(puzzle)
        _uiState.update {
            it.copy(
                isLoading = false,
                movesToWin = puzzle.movesToWin,
                rating = round.progress.rating.roundToInt(),
                ratingDelta = null,
                puzzleRating = null,
                attempts = round.progress.attempts,
                solved = round.progress.solved,
            )
        }
        val last = puzzle.lastMove
        if (last < 0) {
            render(PuzzlePhase.PLAYING)
            return
        }
        // The opponent's last move lands first, so the player sees what just happened.
        animation = viewModelScope.launch {
            delay(START_DELAY_MS)
            fly(last, hidden = last)
            render(PuzzlePhase.PLAYING)
        }
    }

    private fun play(ring: Int) {
        if (_uiState.value.phase != PuzzlePhase.PLAYING || attempt.isOver) return
        if (ring !in 0 until 64 || (attempt.mine or attempt.theirs) and (1L shl ring) != 0L) return
        when (val outcome = attempt.play(ring)) {
            is PuzzleAttempt.Outcome.Continue -> {
                render(PuzzlePhase.OPPONENT_MOVING, hidden = outcome.reply)
                animation = viewModelScope.launch {
                    delay(REPLY_DELAY_MS)
                    fly(outcome.reply, hidden = outcome.reply)
                    render(PuzzlePhase.PLAYING)
                }
            }
            PuzzleAttempt.Outcome.Solved -> finish(solved = true, wrongRing = -1, correct = emptyList())
            is PuzzleAttempt.Outcome.Wrong -> finish(solved = false, wrongRing = ring, correct = outcome.correctMoves)
        }
    }

    private fun finish(solved: Boolean, wrongRing: Int, correct: List<Int>) {
        render(if (solved) PuzzlePhase.SOLVED else PuzzlePhase.FAILED, wrongRing = wrongRing, ghost = correct.firstOrNull() ?: -1)
        val finished = puzzle
        viewModelScope.launch {
            val change = puzzleService.record(finished, solved)
            if (finished !== puzzle) return@launch
            _uiState.update {
                it.copy(
                    rating = change.after,
                    ratingDelta = if (_uiState.value.ratingDelta == null) change.after - change.before else it.ratingDelta,
                    puzzleRating = finished.rating,
                    attempts = change.progress.attempts,
                    solved = change.progress.solved,
                )
            }
        }
        if (solved) blinkWinningLine()
    }

    private fun blinkWinningLine() {
        val mine = attempt.mine
        val line = QubicGeometry.lineMasks.firstOrNull { it and mine == it } ?: return
        val cells = (0 until 64).filter { line and (1L shl it) != 0L }.toSet()
        animation = viewModelScope.launch {
            repeat(BLINK_CYCLES) {
                _uiState.update { it.copy(board = it.board.copy(hiddenRings = cells)) }
                delay(BLINK_HALF_PERIOD_MS)
                _uiState.update { it.copy(board = it.board.copy(hiddenRings = emptySet())) }
                delay(BLINK_HALF_PERIOD_MS)
            }
        }
    }

    /** The opponent's ring [ring] flying in from the tray. */
    private suspend fun fly(ring: Int, hidden: Int) {
        for (i in 0 until FLIGHT_FRAMES) {
            render(PuzzlePhase.OPPONENT_MOVING, hidden = hidden, moving = MovingRing(ring, OPPONENT, i.toFloat() / FLIGHT_FRAMES))
            delay(FLIGHT_MS / FLIGHT_FRAMES)
        }
    }

    /**
     * Shows the attempt's position: [hidden] is a ring not drawn yet (landing), [wrongRing]
     * a wrong move of the player (drawn as played), [ghost] a correct move as a translucent ring.
     */
    private fun render(
        phase: PuzzlePhase,
        hidden: Int = -1,
        moving: MovingRing? = null,
        wrongRing: Int = -1,
        ghost: Int = -1,
    ) {
        val rings = ByteArray(64)
        for (c in 0 until 64) {
            val bit = 1L shl c
            rings[c] = when {
                c == hidden -> 0
                attempt.mine and bit != 0L || c == wrongRing -> PLAYER.toByte()
                attempt.theirs and bit != 0L -> OPPONENT.toByte()
                else -> 0
            }
        }
        val movesPlayed = attempt.played.size / 2 + attempt.played.size % 2
        _uiState.update {
            it.copy(
                phase = phase,
                board = BoardVisualState(
                    rings = rings,
                    player1Color = playerColor,
                    player2Color = opponentColor,
                    trayPlayer = when (phase) {
                        PuzzlePhase.PLAYING -> PLAYER
                        PuzzlePhase.OPPONENT_MOVING -> OPPONENT
                        else -> 0
                    },
                    dragEnabled = phase == PuzzlePhase.PLAYING,
                    movingRing = moving,
                    ghostRing = if (ghost >= 0) GhostRing(ghost, PLAYER) else null,
                ),
                movesLeft = (puzzle.movesToWin - movesPlayed).coerceAtLeast(0),
                progressing = attempt.played.isNotEmpty() && phase != PuzzlePhase.FAILED,
            )
        }
    }

    private companion object {
        const val PLAYER = 1
        const val OPPONENT = 2
        const val START_DELAY_MS = 300L
        const val REPLY_DELAY_MS = 250L
        const val FLIGHT_MS = 700L
        const val FLIGHT_FRAMES = 24
        const val BLINK_CYCLES = 4
        const val BLINK_HALF_PERIOD_MS = 220L
    }
}
