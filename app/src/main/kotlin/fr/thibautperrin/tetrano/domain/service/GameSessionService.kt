package fr.thibautperrin.tetrano.domain.service

import fr.thibautperrin.tetrano.domain.model.AiProgressState
import fr.thibautperrin.tetrano.domain.model.GameMode
import fr.thibautperrin.tetrano.domain.model.GameSessionState
import fr.thibautperrin.tetrano.engine.Game
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Orchestrates a single game in progress: turn order, playing/undoing
 * moves, and — in [GameMode.VS_COMPUTER] — kicking off the AI's reply.
 * Ports `GestionJeu`/`GestionJeu2Joueurs`/`GestionJeuContreOrdinateur` (see
 * docs/GLOSSARY.md, docs/PLAN.md P6). Bluetooth and online play are out of
 * scope (see docs/PLAN.md §10) — [Factory.create] rejects those modes.
 *
 * Deliberately **not** this class's job: recording finished-game stats
 * and awarding stars (`GestionJeu`'s `logGame`/`GameLog` in the legacy
 * app) — the caller (a ViewModel, in P8) observes [state] for the
 * finishing transition and drives [StarService] itself, and likewise for
 * persisting a save via [SavedGameService] using [playedRings]/
 * [startingPlayer]. Keeping this class to game mechanics only avoids
 * tangling it up with the database and preferences layers.
 *
 * One instance is scoped to a single game screen's lifetime: create it
 * with [Factory], passing a `CoroutineScope` tied to that screen (e.g. a
 * ViewModel's `viewModelScope`) so an in-progress AI search is cancelled
 * if the screen goes away mid-move.
 */
class GameSessionService private constructor(
    private val aiService: AiService,
    private val scope: CoroutineScope,
    val mode: GameMode,
    private val aiLevel: Int,
    private val game: Game,
) {
    private val _state = MutableStateFlow(snapshot())
    val state: StateFlow<GameSessionState> = _state.asStateFlow()

    private var aiJob: Job? = null

    init {
        triggerAiMoveIfNeeded()
    }

    /** Plays [ring] for the current player. False if busy, [ring] is taken, or the game is already over. */
    fun play(ring: Int): Boolean {
        if (_state.value.isBusy || ring !in 0 until 64 || !game.isRingFree(ring)) return false
        if (!game.play(ring)) return false
        publish()
        triggerAiMoveIfNeeded()
        return true
    }

    /**
     * Undoes move(s) so it's the human player's turn to decide again: 1
     * move in [GameMode.TWO_PLAYERS]; in [GameMode.VS_COMPUTER], 2 moves
     * if the computer already replied, or 1 if it's the computer's own
     * unanswered opening move — and nothing at all if the computer's
     * opening move is the *only* move played (there is then no human move
     * to return to). Ports `cliqueAnnuler` exactly, including that last
     * quirk. False if busy or nothing has been played yet.
     */
    fun undo(): Boolean {
        if (_state.value.isBusy || game.board.playedCount == 0) return false
        when (mode) {
            GameMode.TWO_PLAYERS -> game.undo()
            GameMode.VS_COMPUTER -> {
                val lastPlayer = game.winner().takeIf { it != -1 } ?: (3 - game.nextPlayer)
                if (lastPlayer == COMPUTER_PLAYER) {
                    if (game.board.playedCount < 2) return false
                    game.undo()
                    game.undo()
                } else {
                    game.undo()
                }
            }
            else -> error("unsupported mode $mode")
        }
        publish()
        return true
    }

    /**
     * Restarts from an empty board.
     * @see Game.restart for the exact [changingBeginner] semantics.
     */
    fun restart(changingBeginner: Boolean) {
        aiJob?.cancel()
        game.restart(changingBeginner)
        publish()
        triggerAiMoveIfNeeded()
    }

    fun blinkingRings(): ByteArray? = game.blinkingRings()

    /** Rings played so far, in order — for [SavedGameService] to persist. */
    fun playedRings(): ByteArray = game.playedRings()

    /** Which player (1 or 2) made the first move — for [SavedGameService] to persist. */
    fun startingPlayer(): Int = game.startingPlayer()

    private fun triggerAiMoveIfNeeded() {
        if (mode != GameMode.VS_COMPUTER || game.isFinished() || game.nextPlayer != COMPUTER_PLAYER) return
        aiJob = scope.launch {
            _state.update { it.copy(isBusy = true, aiProgress = null) }
            val ring = aiService.chooseRing(game.playedRings(), aiLevel) { phase, current, total ->
                _state.update { it.copy(aiProgress = AiProgressState(phase, current, total)) }
            }
            game.play(ring)
            publish()
        }
    }

    private fun publish() {
        _state.update { snapshot() }
    }

    private fun snapshot(): GameSessionState = GameSessionState(
        rings = game.board.rings.copyOf(),
        nextPlayer = game.nextPlayer,
        playedCount = game.board.playedCount,
        isBusy = false,
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
         *   brand new game.
         * @param startingPlayer who played [initialMoves]'s first ring;
         *   ignored if [initialMoves] is empty (a brand new game always
         *   has player 1 start — flip with [GameSessionService.restart]
         *   afterwards if that's not what's wanted).
         * @param aiLevel ignored outside [GameMode.VS_COMPUTER].
         */
        fun create(
            scope: CoroutineScope,
            mode: GameMode,
            aiLevel: Int = 0,
            initialMoves: ByteArray = ByteArray(0),
            startingPlayer: Int = 1,
        ): GameSessionService {
            require(mode == GameMode.TWO_PLAYERS || mode == GameMode.VS_COMPUTER) {
                "GameSessionService does not support $mode"
            }
            val game = Game()
            if (startingPlayer == 2) {
                game.restart(changingBeginner = true)
            }
            for (ring in initialMoves) {
                game.play(ring.toInt())
            }
            return GameSessionService(aiService, scope, mode, aiLevel, game)
        }
    }

    private companion object {
        const val COMPUTER_PLAYER = 2
    }
}
