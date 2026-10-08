package fr.thibautperrin.tetrano.ui.savedgames

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.thibautperrin.tetrano.domain.model.SavedGameSummary
import fr.thibautperrin.tetrano.domain.service.GameReviewService
import fr.thibautperrin.tetrano.domain.service.SavedGameService
import fr.thibautperrin.tetrano.engine.nn.GameReview
import fr.thibautperrin.tetrano.ui.components.GhostRing
import fr.thibautperrin.tetrano.ui.navigation.TetranoRoute
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs [SavedGameViewerScreen] — replaces `WatchSavedGameActivity` (see
 * docs/GLOSSARY.md, docs/PLAN.md P8). Ports its `cursor`/`onPrevious`/
 * `onNext`/`getProchainJoueur` exactly: [SavedGameSummary.ringsPlayed]
 * replayed up to `cursor`, alternating from first-player color starting
 * with [SavedGameSummary.userBegins] — the same rule as
 * [SavedGameSummary.matrix], just stoppable partway through. The screen's
 * resume menu (`menu_watch_saved_game`) resumes from that cursor: see
 * [SavedGameViewerRoute]. The cursor survives process death.
 *
 * The game is either a save, or (reviewing a game that just ended) given
 * whole by the route. "Analyse" runs [GameReviewService] over the whole
 * game; the displayed position then shows its win/draw/loss outlook, the
 * verdict on the move that led to it and, for a weak move, the better one
 * as a translucent ring.
 */
@HiltViewModel
class SavedGameViewerViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val savedGameService: SavedGameService,
    private val gameReviewService: GameReviewService,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<TetranoRoute.SavedGameViewer>()

    private val _uiState = MutableStateFlow(SavedGameViewerUiModel(canResume = route.savedGameName != null))
    val uiState: StateFlow<SavedGameViewerUiModel> = _uiState.asStateFlow()

    private var game: SavedGameSummary? = null
    private var cursor: Int = 0
    private var reviews: List<GameReview.PositionReview>? = null
    private var reviewJob: Job? = null

    init {
        viewModelScope.launch {
            val saved = route.savedGameName?.let { savedGameService.get(it) } ?: route.rings?.let { rings ->
                SavedGameSummary(
                    name = "",
                    ringsPlayed = rings.split(',').filter { it.isNotBlank() }.map { it.trim().toByte() }.toByteArray(),
                    userBegins = route.userBegins,
                    userColor = route.userColor,
                    adversaryColor = route.adversaryColor,
                    createdAt = 0L,
                )
            }
            if (saved == null) {
                _uiState.update { it.copy(isLoading = false, notFound = true) }
                return@launch
            }
            game = saved
            // The legacy activity opened on the last ring.
            cursor = savedStateHandle.get<Int>(KEY_CURSOR)?.coerceIn(0, saved.ringsPlayed.size) ?: saved.ringsPlayed.size
            publish(saved)
            if (route.analyse) startReview(saved)
        }
    }

    fun onAction(action: SavedGameViewerAction) {
        val game = game ?: return
        when (action) {
            // WatchSavedGameActivity.onPrevious().
            SavedGameViewerAction.PreviousClicked -> if (cursor > 0) cursor-- else return
            // WatchSavedGameActivity.onNext().
            SavedGameViewerAction.NextClicked -> if (cursor < game.ringsPlayed.size) cursor++ else return
            SavedGameViewerAction.AnalyseClicked -> {
                startReview(game)
                return
            }
            SavedGameViewerAction.PreviousMistakeClicked -> cursor = mistakeCursors().lastOrNull { it < cursor } ?: return
            SavedGameViewerAction.NextMistakeClicked -> cursor = mistakeCursors().firstOrNull { it > cursor } ?: return
        }
        savedStateHandle[KEY_CURSOR] = cursor
        publish(game)
    }

    private fun startReview(game: SavedGameSummary) {
        if (reviewJob != null) return
        _uiState.update { it.copy(review = ReviewUiModel(progress = 0f)) }
        reviewJob = viewModelScope.launch {
            val result = gameReviewService.review(game.ringsPlayed) { done, total ->
                _uiState.update { state -> state.copy(review = state.review?.copy(progress = done.toFloat() / total)) }
            }
            reviews = result
            publish(game)
        }
    }

    /** Cursors showing the position right after a mistake. */
    private fun mistakeCursors(): List<Int> =
        reviews.orEmpty().filter { it.verdict?.toUi()?.isMistake == true }.map { it.ply + 1 }

    /** Board player (1 or 2) to move after [moves] moves. */
    private fun boardPlayerToMove(game: SavedGameSummary, moves: Int): Int {
        val first = if (game.userBegins) 1 else 2
        return if (moves % 2 == 0) first else 3 - first
    }

    private fun publish(game: SavedGameSummary) {
        val rings = ByteArray(64)
        var player: Byte = if (game.userBegins) 1 else 2
        for (i in 0 until cursor) {
            rings[game.ringsPlayed[i].toInt()] = player
            player = (3 - player).toByte()
        }
        val reviews = reviews
        var ghost: GhostRing? = null
        val review = if (reviews == null) {
            _uiState.value.review
        } else {
            val current = reviews.getOrNull(cursor)
            val outlook = current?.let {
                val (win, draw, loss) = Triple(it.wdl[0], it.wdl[1], it.wdl[2])
                if (boardPlayerToMove(game, cursor) == 1) Outlook(win, draw, loss) else Outlook(loss, draw, win)
            }
            val previous = if (cursor > 0) reviews.getOrNull(cursor - 1) else null
            val verdict = previous?.verdict?.toUi()
            val lastMovePlayer = boardPlayerToMove(game, cursor - 1)
            if (previous != null && verdict != null && verdict != MoveVerdict.BEST && verdict != MoveVerdict.GOOD &&
                previous.bestRing >= 0 && previous.bestRing != previous.playedRing
            ) {
                ghost = GhostRing(previous.bestRing, lastMovePlayer)
            }
            val mistakes = mistakeCursors()
            ReviewUiModel(
                progress = null,
                outlook = outlook,
                lastMove = verdict,
                lastMovePlayer = lastMovePlayer,
                hasPreviousMistake = mistakes.any { it < cursor },
                hasNextMistake = mistakes.any { it > cursor },
            )
        }
        _uiState.update {
            it.copy(
                isLoading = false,
                gameName = game.name,
                board = it.board.copy(
                    rings = rings,
                    player1Color = Color(game.userColor),
                    player2Color = Color(game.adversaryColor),
                    ghostRing = ghost,
                ),
                cursor = cursor,
                totalMoves = game.ringsPlayed.size,
                review = review,
            )
        }
    }

    private fun GameReview.Verdict.toUi(): MoveVerdict = when (this) {
        GameReview.Verdict.BEST -> MoveVerdict.BEST
        GameReview.Verdict.GOOD -> MoveVerdict.GOOD
        GameReview.Verdict.INACCURACY -> MoveVerdict.INACCURACY
        GameReview.Verdict.MISTAKE -> MoveVerdict.MISTAKE
        GameReview.Verdict.BLUNDER -> MoveVerdict.BLUNDER
        GameReview.Verdict.MISSED_WIN -> MoveVerdict.MISSED_WIN
        GameReview.Verdict.ALLOWED_WIN -> MoveVerdict.ALLOWED_WIN
    }

    private companion object {
        const val KEY_CURSOR = "cursor"
    }
}
