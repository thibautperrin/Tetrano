package fr.thibautperrin.tetrano.engine

/**
 * A game in progress: a [BoardState] plus whose turn it is and the order
 * rings were played in (needed for [undo] and to redraw the last move).
 *
 * Ports `thibautperrin.tetrano.Intelligence2.Partie` (see docs/GLOSSARY.md).
 */
class Game {

    val board: BoardState
    var nextPlayer: Int
        private set

    /** `history[i]` is the ring played as the i-th move (0 until `board.playedCount`). */
    private val history: ByteArray

    /** A fresh, empty game where player 1 starts. Ports the no-argument `Partie()`. */
    constructor() {
        board = BoardState()
        nextPlayer = 1
        history = ByteArray(BoardTables.RING_COUNT)
    }

    /**
     * Rebuilds a game from a [signature] (see [BoardState.signature]).
     * Since a signature only records final occupancy, not move order, the
     * per-player move order is reconstructed assuming each player played
     * their rings in ascending ring-number order — an arbitrary but
     * deterministic and stable convention. Ports the 2-argument
     * `Partie(byte[], boolean)`.
     */
    constructor(signature: ByteArray, invertPlayers: Boolean) {
        board = BoardState(signature, invertPlayers)
        val firstPlayer = if (invertPlayers) 2 else 1
        nextPlayer = if (board.playedCount % 2 == 0) firstPlayer else 3 - firstPlayer
        history = ByteArray(BoardTables.RING_COUNT)
        // Index 0 is unused (a player is always 1 or 2), mirroring the
        // legacy numAno[]{0,-1,-1} scratch array.
        val lastRingSeen = intArrayOf(0, -1, -1)
        var currentPlayer = firstPlayer
        for (moveIndex in 0 until board.playedCount) {
            do {
                lastRingSeen[currentPlayer]++
            } while (board.rings[lastRingSeen[currentPlayer]] != currentPlayer.toByte())
            history[moveIndex] = lastRingSeen[currentPlayer].toByte()
            currentPlayer = 3 - currentPlayer
        }
    }

    /** True once a player has 4 in a line, or the board is full. Ports `estFini`. */
    fun isFinished(): Boolean =
        board.playedCount == BoardTables.RING_COUNT ||
            board.lineSets[4].length() > 0 ||
            board.lineSets[8].length() > 0

    /** True iff the board is full with no winner. Ports `estPartieNulle`. */
    fun isDraw(): Boolean =
        board.playedCount == BoardTables.RING_COUNT &&
            board.lineSets[4].length() == 0 &&
            board.lineSets[8].length() == 0

    /**
     * Undoes the last move, returning the ring it freed up, or -1 if there
     * was no move to undo. Ports `dejouer`.
     */
    fun undo(): Int {
        if (board.playedCount == 0) return -1
        val ring = history[board.playedCount - 1].toInt()
        board.undo(ring, 3 - nextPlayer)
        nextPlayer = 3 - nextPlayer
        return ring
    }

    /**
     * Plays [ring] for the current player, if the game isn't over and
     * [ring] is in range. Returns whether the move was made. Ports `jouer`.
     */
    fun play(ring: Int): Boolean {
        if (isFinished() || ring < 0 || ring >= BoardTables.RING_COUNT) return false
        history[board.playedCount] = ring.toByte()
        board.play(ring, nextPlayer)
        nextPlayer = 3 - nextPlayer
        return true
    }

    /**
     * Restarts the game from an empty board.
     *
     * @param changingBeginner if true, and the finished game had a winner,
     *   the loser starts next; if it ended in a draw (or isn't finished),
     *   the other player from [startingPlayer] starts. If false, the same
     *   player as [startingPlayer] starts again. Ports `restart`.
     */
    fun restart(changingBeginner: Boolean) {
        val beginner = if (changingBeginner) {
            val winningPlayer = winner()
            if (winningPlayer != -1) 3 - winningPlayer else 3 - startingPlayer()
        } else {
            startingPlayer()
        }
        board.restart()
        nextPlayer = beginner
    }

    /** Which player (1 or 2) made the first move of the game. Ports `getJoueurCommencant`. */
    fun startingPlayer(): Int =
        if (board.playedCount % 2 == 0) nextPlayer else 3 - nextPlayer

    /**
     * The winning player (1 or 2), or -1 if the game isn't won (not
     * finished, or a draw). Ports `getGagnant`.
     */
    fun winner(): Int = when {
        board.lineSets[4].length() > 0 -> 1
        board.lineSets[8].length() > 0 -> 2
        else -> -1
    }

    /** True iff [ring] has no player's mark on it. Ports `isAnneauEstLibre`. */
    fun isRingFree(ring: Int): Boolean = board.rings[ring] == 0.toByte()

    /** A fresh copy of the rings played so far, in order. Ports `getAnneauxJoues`. */
    fun playedRings(): ByteArray = history.copyOf(board.playedCount)

    /** The most recently played ring. Ports `getLastAnneau` (undefined if no move has been played). */
    fun lastRing(): Int = history[board.playedCount - 1].toInt()

    /**
     * The rings that should blink: the winning line if the game is won,
     * otherwise just the last move played, or null if no move has been
     * played yet. Ports `getAnneauxClignotants`.
     */
    fun blinkingRings(): ByteArray? {
        if (board.lineSets[4].length() > 0) {
            return BoardTables.copyLineRings(board.lineSets[4].next(-1).toInt())
        }
        if (board.lineSets[8].length() > 0) {
            return BoardTables.copyLineRings(board.lineSets[8].next(-1).toInt())
        }
        if (board.playedCount > 0) {
            return byteArrayOf(history[board.playedCount - 1])
        }
        return null
    }

    /** See [BoardState.signature]; players are swapped so the starting player is always "1". Ports `getSignature`. */
    fun signature(out: ByteArray = ByteArray(BoardState.SIGNATURE_SIZE)): ByteArray =
        board.signature(invertPlayers = startingPlayer() == 2, out = out)

    /** See [BoardState.canonicalSignature]; same player convention as [signature]. Ports `getLowestSignature`. */
    fun canonicalSignature(out: ByteArray = ByteArray(BoardState.SIGNATURE_SIZE)): ByteArray =
        board.canonicalSignature(invertPlayers = startingPlayer() == 2, out = out)
}
