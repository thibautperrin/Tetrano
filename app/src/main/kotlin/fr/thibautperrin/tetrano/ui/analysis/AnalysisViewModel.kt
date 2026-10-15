package fr.thibautperrin.tetrano.ui.analysis

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.thibautperrin.tetrano.data.prefs.TetranoPreferencesDataSource
import fr.thibautperrin.tetrano.domain.model.SavedGameSummary
import fr.thibautperrin.tetrano.domain.service.GameReviewService
import fr.thibautperrin.tetrano.domain.service.LiveAnalysisService
import fr.thibautperrin.tetrano.domain.service.SavedGameService
import fr.thibautperrin.tetrano.engine.nn.GameReview
import fr.thibautperrin.tetrano.engine.nn.GumbelMcts
import fr.thibautperrin.tetrano.ui.components.GhostRing
import fr.thibautperrin.tetrano.ui.navigation.TetranoRoute
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/**
 * Backs [AnalysisScreen]: a game (a save, a game that just ended, or the
 * empty board) explored move by move with the level-101 network.
 *
 * - The line on display is the game, or a variation of it: dropping a ring
 *   plays it for the player to move; unless it is the game's own next move,
 *   that starts (or changes) a variation, which [AnalysisAction.BackToGameClicked]
 *   leaves. On the empty board the moves played are the game itself.
 * - The position on display is searched without pause ([LiveAnalysisService])
 *   up to [AnalysisUiModel.depth] variations, the recommendation updated after
 *   each step and kept per position, so coming back to it resumes from there.
 * - The game's moves are reviewed ([GameReviewService]): every position gets a
 *   quick evaluation, then a verdict per move. A deeper live evaluation of a
 *   position of the game replaces the quick one and the verdicts of the moves
 *   around it are judged again.
 *
 * The line and the cursor survive process death.
 */
@HiltViewModel
class AnalysisViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val savedGameService: SavedGameService,
    gameReviewService: GameReviewService,
    private val liveAnalysisService: LiveAnalysisService,
    private val preferences: TetranoPreferencesDataSource,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<TetranoRoute.Analysis>()

    private val _uiState = MutableStateFlow(AnalysisUiModel())
    val uiState: StateFlow<AnalysisUiModel> = _uiState.asStateFlow()

    private lateinit var game: SavedGameSummary
    private var freePlay = false

    /** The game's moves. */
    private var main = ByteArray(0)

    /** The line on display: [main], or a variation of it. */
    private var line = ByteArray(0)
    private var cursor = 0
    private var depth = TetranoPreferencesDataSource.DEFAULT_ANALYSIS_SIMULATIONS

    private val reviewSession = gameReviewService.session()
    private var evaluations = arrayOfNulls<GameReview.Evaluation>(1)
    private var verdicts = arrayOfNulls<GameReview.Verdict>(0)
    private var reviewProgress: Float? = null
    private val judgeLock = Mutex()

    /** Live results per position (the line's prefix), most recently used last. */
    private val liveCache = object : LinkedHashMap<String, LiveAnalysisService.Snapshot>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LiveAnalysisService.Snapshot>) = size > LIVE_CACHE_SIZE
    }
    private var live: LiveAnalysisService.Snapshot? = null
    private var liveJob: Job? = null

    init {
        viewModelScope.launch {
            val loaded = load()
            if (loaded == null) {
                _uiState.update { it.copy(isLoading = false, notFound = true) }
                return@launch
            }
            game = loaded
            main = loaded.ringsPlayed
            line = savedStateHandle.get<ByteArray>(KEY_LINE) ?: main
            if (freePlay) main = line
            cursor = savedStateHandle.get<Int>(KEY_CURSOR)?.coerceIn(0, line.size) ?: line.size
            depth = preferences.analysisSimulations.first()
            evaluations = arrayOfNulls(main.size + 1)
            verdicts = arrayOfNulls(main.size)
            publish()
            if (!freePlay && main.isNotEmpty()) startReview()
            restartLive()
        }
    }

    private suspend fun load(): SavedGameSummary? {
        route.savedGameName?.let { return savedGameService.get(it) }
        val rings = route.rings
        if (rings != null) {
            return SavedGameSummary(
                name = "",
                ringsPlayed = rings.split(',').filter { it.isNotBlank() }.map { it.trim().toByte() }.toByteArray(),
                userBegins = route.userBegins,
                userColor = route.userColor,
                adversaryColor = route.adversaryColor,
                createdAt = 0L,
            )
        }
        freePlay = true
        return SavedGameSummary(
            name = "",
            ringsPlayed = ByteArray(0),
            userBegins = true,
            userColor = preferences.userColor.first(),
            adversaryColor = preferences.adversaryColor.first(),
            createdAt = 0L,
        )
    }

    fun onAction(action: AnalysisAction) {
        if (!::game.isInitialized) return
        when (action) {
            AnalysisAction.FirstClicked -> cursor = 0
            AnalysisAction.PreviousClicked -> if (cursor > 0) cursor-- else return
            AnalysisAction.NextClicked -> if (cursor < line.size) cursor++ else return
            AnalysisAction.LastClicked -> cursor = line.size
            AnalysisAction.PreviousMistakeClicked -> jumpToMistake(next = false) ?: return
            AnalysisAction.NextMistakeClicked -> jumpToMistake(next = true) ?: return
            is AnalysisAction.RingDropped -> if (!play(action.ring)) return
            AnalysisAction.BackToGameClicked -> {
                val branch = divergence() ?: return
                line = main
                cursor = minOf(cursor, branch)
            }
            is AnalysisAction.DepthSelected -> {
                depth = action.simulations
                viewModelScope.launch { preferences.setAnalysisSimulations(action.simulations) }
            }
        }
        savedStateHandle[KEY_LINE] = line
        savedStateHandle[KEY_CURSOR] = cursor
        publish()
        restartLive()
    }

    /** Plays [ring] for the player to move; false if it cannot be played there. */
    private fun play(ring: Int): Boolean {
        if (ring !in 0 until 64 || GameReview.finalEvaluation(line, cursor) != null) return false
        for (i in 0 until cursor) if (line[i].toInt() == ring) return false
        when {
            cursor < line.size && line[cursor].toInt() == ring -> Unit
            cursor < main.size && main[cursor].toInt() == ring && onMainLine() -> line = main
            else -> line = line.copyOf(cursor) + ring.toByte()
        }
        cursor++
        if (freePlay) {
            if (!main.contentEquals(line)) resetReview(line)
        } else if (isPrefixOfMain(line)) {
            line = main
        }
        return true
    }

    /** The game's index where [line] leaves it, or null if [line] is part of the game. */
    private fun divergence(): Int? {
        if (freePlay) return null
        for (i in line.indices) if (i >= main.size || line[i] != main[i]) return i
        return null
    }

    private fun isPrefixOfMain(moves: ByteArray): Boolean =
        moves.size <= main.size && moves.indices.all { moves[it] == main[it] }

    /** True iff the position on display is one of the game's. */
    private fun onMainLine(): Boolean = (divergence() ?: Int.MAX_VALUE) >= cursor

    private fun mistakeCursors(): List<Int> =
        verdicts.indices.filter { verdicts[it]?.toUi()?.isMistake == true }.map { it + 1 }

    private fun jumpToMistake(next: Boolean): Unit? {
        val position = if (onMainLine()) cursor else (divergence() ?: cursor)
        val target = if (next) mistakeCursors().firstOrNull { it > position } else mistakeCursors().lastOrNull { it < position }
        target ?: return null
        line = main
        cursor = target
        return Unit
    }

    // ---- review of the game's moves ----

    private fun startReview() {
        reviewProgress = 0f
        val moves = main
        viewModelScope.launch {
            reviewSession.evaluateAll(moves).collect { (index, evaluation) ->
                if (moves !== main) return@collect
                offerEvaluation(index, evaluation)
                reviewProgress = (index + 1f) / (moves.size + 1)
                publish()
            }
            reviewProgress = null
            publish()
        }
    }

    /**
     * On the empty board, the moves played are the game: when they change, what is known of
     * the positions they still share with the previous moves is kept.
     */
    private fun resetReview(moves: ByteArray) {
        var common = 0
        while (common < minOf(main.size, moves.size) && main[common] == moves[common]) common++
        val oldEvaluations = evaluations
        val oldVerdicts = verdicts
        main = moves
        evaluations = arrayOfNulls(main.size + 1)
        verdicts = arrayOfNulls(main.size)
        for (i in 0..common) evaluations[i] = oldEvaluations.getOrNull(i)
        for (i in 0 until common) verdicts[i] = oldVerdicts.getOrNull(i)
    }

    /** Keeps [evaluation] of the game's position [index] if it is better informed than the one known. */
    private fun offerEvaluation(index: Int, evaluation: GameReview.Evaluation) {
        if (index !in evaluations.indices) return
        val known = evaluations[index]
        if (known != null && !isBetter(evaluation, known)) return
        evaluations[index] = evaluation
        if (index > 0) judge(index - 1)
        if (index < main.size) judge(index)
    }

    private fun isBetter(a: GameReview.Evaluation, b: GameReview.Evaluation): Boolean {
        val aProven = a.status != GumbelMcts.Status.UNKNOWN
        val bProven = b.status != GumbelMcts.Status.UNKNOWN
        return if (aProven != bProven) aProven else !bProven && a.simulations > b.simulations
    }

    private fun judge(ply: Int) {
        val moves = main
        val before = evaluations[ply] ?: return
        val after = evaluations[ply + 1] ?: return
        viewModelScope.launch {
            val judgement = judgeLock.withLock { reviewSession.judge(moves, ply, before, after) }
            if (moves !== main || evaluations[ply] !== before || evaluations[ply + 1] !== after) return@launch
            verdicts[ply] = judgement.verdict
            publish()
        }
    }

    // ---- live search of the position on display ----

    private fun restartLive() {
        liveJob?.cancel()
        val moves = line.copyOf(cursor)
        val key = moves.joinToString(",")
        val cached = liveCache[key]
        live = cached
        publish()
        if (cached != null && cached.finished &&
            (cached.simulations >= depth || cached.evaluation.status != GumbelMcts.Status.UNKNOWN || cached.simulations == 0)
        ) {
            return
        }
        val onMain = onMainLine()
        val index = cursor
        liveJob = viewModelScope.launch {
            liveAnalysisService.analyse(moves, depth).collect { snapshot ->
                val current = live
                val better = current == null || snapshot.simulations >= current.simulations ||
                    snapshot.evaluation.status != GumbelMcts.Status.UNKNOWN
                if (better) {
                    live = snapshot
                    liveCache[key] = snapshot
                    if (onMain) offerEvaluation(index, snapshot.evaluation)
                }
                publish()
            }
            publish()
        }
    }

    // ---- rendering ----

    /** Board player (1 or 2) who plays move [index] (0-based) of the line. */
    private fun playerOfMove(index: Int): Int {
        val first = if (game.userBegins) 1 else 2
        return if (index % 2 == 0) first else 3 - first
    }

    private fun publish() {
        val rings = ByteArray(64)
        for (i in 0 until cursor) rings[line[i].toInt()] = playerOfMove(i).toByte()
        val gameOver = GameReview.finalEvaluation(line, cursor) != null
        val toMove = playerOfMove(cursor)
        val snapshot = live
        val running = liveJob?.isActive == true && snapshot?.finished != true
        val liveUi = snapshot?.let {
            val (win, draw, loss) = Triple(it.wdl[0], it.wdl[1], it.wdl[2])
            LiveUiModel(
                outlook = if (toMove == 1) Outlook(win, draw, loss) else Outlook(loss, draw, win),
                simulations = it.simulations,
                running = running,
                bestRing = if (gameOver) -1 else it.evaluation.bestRing,
                playerToMove = toMove,
                provenWinner = when (it.evaluation.status) {
                    GumbelMcts.Status.WIN -> toMove
                    GumbelMcts.Status.LOSS -> 3 - toMove
                    GumbelMcts.Status.DRAW -> 0
                    GumbelMcts.Status.UNKNOWN -> null
                },
            )
        }
        val ghost = liveUi?.bestRing?.takeIf { it >= 0 && rings[it].toInt() == 0 }?.let { GhostRing(it, toMove) }
        val branch = divergence()
        val onMain = onMainLine()
        val review = if (freePlay && main.isEmpty()) {
            null
        } else {
            val mistakes = mistakeCursors()
            val position = if (onMain) cursor else (branch ?: cursor)
            ReviewUiModel(
                progress = reviewProgress,
                lastMove = if (onMain && cursor > 0) verdicts.getOrNull(cursor - 1)?.toUi() else null,
                lastMovePlayer = playerOfMove(cursor - 1),
                hasPreviousMistake = mistakes.any { it < position },
                hasNextMistake = mistakes.any { it > position },
            )
        }
        _uiState.update {
            it.copy(
                isLoading = false,
                gameName = game.name,
                freePlay = freePlay,
                canResume = route.savedGameName != null && onMain,
                board = it.board.copy(
                    rings = rings,
                    player1Color = Color(game.userColor),
                    player2Color = Color(game.adversaryColor),
                    ghostRing = ghost,
                    trayPlayer = if (gameOver) 0 else toMove,
                    dragEnabled = !gameOver,
                ),
                cursor = cursor,
                lineLength = line.size,
                variation = branch?.let { b -> VariationUiModel(fromMove = b + 1) },
                live = liveUi,
                depth = depth,
                review = review,
                gameOver = gameOver,
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
        const val KEY_LINE = "analysis_line"
        const val KEY_CURSOR = "cursor"
        const val LIVE_CACHE_SIZE = 200
    }
}
