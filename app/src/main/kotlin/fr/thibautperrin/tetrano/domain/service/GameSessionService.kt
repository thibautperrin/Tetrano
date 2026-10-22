package fr.thibautperrin.tetrano.domain.service

import fr.thibautperrin.tetrano.domain.model.AiProgressState
import fr.thibautperrin.tetrano.domain.model.GameMode
import fr.thibautperrin.tetrano.domain.model.GameSessionEvent
import fr.thibautperrin.tetrano.domain.model.GameSessionState
import fr.thibautperrin.tetrano.domain.model.UndoneMove
import fr.thibautperrin.tetrano.engine.Game
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Orchestrates a single game in progress: turn order, playing/undoing
 * moves, and — in [GameMode.VS_COMPUTER] — running the computer's reply.
 * Ports `GestionJeu`/`GestionJeu2Joueurs`/`GestionJeuContreOrdinateur` (see
 * docs/GLOSSARY.md, docs/PLAN.md P6).
 *
 * Deliberately **not** this class's job: animations, recording
 * and persisting a save via [SavedGameService] using
 * [playedRings]/[startingPlayer] — the caller (the game ViewModel) does
 * those. Keeping this class to game mechanics only avoids tangling it up
 * with the database and preferences layers.
 *
 * The computer (always player 2) starts searching as soon as it's its turn
 * — after [start], a human move or a [restart] — and its answer is applied
 * to the game at once: [state] then holds the new position and [events]
 * reports [GameSessionEvent.ComputerPlayed] so the caller can animate it.
 * [undo] and [restart] are accepted while it searches: they cancel the
 * search, and an answer for a position that has changed is dropped (the
 * legacy signature check of `onOrdinateurAJoue`).
 *
 * One instance is scoped to a single game screen's lifetime: create it
 * with [Factory], passing a `CoroutineScope` tied to that screen (e.g. a
 * ViewModel's `viewModelScope`, whose main-thread dispatcher every method
 * here must be called on) so an in-progress AI search is cancelled if the
 * screen goes away mid-move.
 */
class GameSessionService private constructor(
    private val aiService: AiService,
    private val scope: CoroutineScope,
    val mode: GameMode,
    private val aiLevel: Int,
    private val game: Game,
) {
    private var aiJob: Job? = null
    private val aiThinking: Boolean get() = aiJob?.isActive == true

    private val _state = MutableStateFlow(snapshot())
    val state: StateFlow<GameSessionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<GameSessionEvent>(extraBufferCapacity = EVENT_BUFFER)
    val events: SharedFlow<GameSessionEvent> = _events.asSharedFlow()

    /**
     * Lets the computer play if the game (new or resumed) is its turn —
     * `GestionJeuContreOrdinateur.begin()`. Call once, after subscribing to
     * [events].
     */
    fun start() {
        triggerAiMoveIfNeeded()
    }

    /**
     * Plays [ring] for the human player whose turn it is. False if it's
     * the computer's turn, [ring] is taken or out of range, or the game is
     * already over (`userTriedPlaying`).
     */
    fun play(ring: Int): Boolean {
        if (isComputerTurn() || ring !in 0 until RING_COUNT || !game.isRingFree(ring)) return false
        if (!game.play(ring)) return false
        publish()
        triggerAiMoveIfNeeded()
        return true
    }

    /**
     * Undoes move(s) so it's a human player's turn to decide again, and
     * returns them, most recent first (empty if nothing was undone): 1 move
     * in [GameMode.TWO_PLAYERS]; in [GameMode.VS_COMPUTER], 2 moves if the
     * computer played last, or 1 if the user did (the computer may still be
     * searching its reply: the search is cancelled) — and nothing at all if
     * the computer's opening move is the *only* move played (there is then
     * no human move to return to). Ports `cliqueAnnuler` exactly, including
     * that last quirk.
     */
    fun undo(): List<UndoneMove> {
        val played = game.board.playedCount
        if (played == 0) return emptyList()
        // The last player to have moved: GestionJeuContreOrdinateur's
        // `lastJoueur` (the winner of a won game is always its last mover).
        val lastPlayer = 3 - game.nextPlayer
        val count = when (mode) {
            GameMode.TWO_PLAYERS -> 1
            GameMode.VS_COMPUTER -> when {
                lastPlayer != COMPUTER_PLAYER -> 1
                played >= 2 -> 2
                else -> return emptyList()
            }
        }
        cancelAi()
        val undone = List(count) {
            val player = 3 - game.nextPlayer
            UndoneMove(ring = game.undo(), player = player)
        }
        publish()
        return undone
    }

    /**
     * Restarts from an empty board, cancelling any search in progress.
     * @see Game.restart for the exact [changingBeginner] semantics.
     */
    fun restart(changingBeginner: Boolean) {
        cancelAi()
        game.restart(changingBeginner)
        publish()
        triggerAiMoveIfNeeded()
    }

    fun blinkingRings(): ByteArray? = game.blinkingRings()

    /** Rings played so far, in order — for [SavedGameService] to persist. */
    fun playedRings(): ByteArray = game.playedRings()

    /** Which player (1 or 2) made the first move — for [SavedGameService] to persist. */
    fun startingPlayer(): Int = game.startingPlayer()

    private fun isComputerTurn(): Boolean =
        mode == GameMode.VS_COMPUTER && !game.isFinished() && game.nextPlayer == COMPUTER_PLAYER

    private fun triggerAiMoveIfNeeded() {
        if (!isComputerTurn()) return
        val position = game.playedRings()
        val job = scope.launch {
            val ring = try {
                aiService.chooseRing(position, aiLevel) { phase, current, total ->
                    // Called from the search's own thread: may land after a
                    // cancellation, hence the guard.
                    _state.update {
                        if (it.isAiThinking) it.copy(aiProgress = AiProgressState(phase, current, total)) else it
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failAiMove()
                return@launch
            }
            // onOrdinateurAJoue's signature check: an answer for a position
            // that has changed since (undo/restart) is dropped.
            if (!game.playedRings().contentEquals(position)) return@launch
            aiJob = null
            if (!game.isRingFree(ring) || !game.play(ring)) {
                failAiMove()
                return@launch
            }
            publish()
            _events.tryEmit(GameSessionEvent.ComputerPlayed(ring))
        }
        if (job.isActive) {
            aiJob = job
            publish()
        }
    }

    private fun failAiMove() {
        aiJob = null
        publish()
        _events.tryEmit(GameSessionEvent.InternalError)
    }

    private fun cancelAi() {
        aiJob?.cancel()
        aiJob = null
    }

    private fun publish() {
        _state.value = snapshot()
    }

    private fun snapshot(): GameSessionState = GameSessionState(
        rings = game.board.rings.copyOf(),
        nextPlayer = game.nextPlayer,
        playedCount = game.board.playedCount,
        isAiThinking = aiThinking,
        isFinished = game.isFinished(),
        isDraw = game.isDraw(),
        winner = game.winner().takeIf { it != -1 },
        lastRing = if (game.board.playedCount > 0) game.lastRing() else null,
        blinkingRings = game.blinkingRings(),
        aiProgress = null,
    )

    class Factory @Inject constructor(private val aiService: AiService) {
        /**
         * @param initialMoves rings already played, in order — empty for a
         *   brand new game. Replay stops at the first invalid ring (a
         *   corrupt save) rather than corrupting the board.
         * @param startingPlayer who plays [initialMoves]'s first ring — or
         *   the first move of a brand new game.
         * @param aiLevel ignored outside [GameMode.VS_COMPUTER].
         */
        fun create(
            scope: CoroutineScope,
            mode: GameMode,
            aiLevel: Int = 0,
            initialMoves: ByteArray = ByteArray(0),
            startingPlayer: Int = 1,
        ): GameSessionService {
            val game = Game()
            if (startingPlayer == 2) {
                game.restart(changingBeginner = true)
            }
            for (ring in initialMoves) {
                val index = ring.toInt()
                if (index !in 0 until RING_COUNT || !game.isRingFree(index) || !game.play(index)) break
            }
            return GameSessionService(aiService, scope, mode, aiLevel, game)
        }
    }

    private companion object {
        const val COMPUTER_PLAYER = 2
        const val RING_COUNT = 64
        const val EVENT_BUFFER = 8
    }
}
