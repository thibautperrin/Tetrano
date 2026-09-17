package fr.thibautperrin.tetrano.engine

/**
 * Operations on a single line's state byte.
 *
 * Ports `thibautperrin.tetrano.Intelligence2.StatiqueAlig` (see
 * docs/GLOSSARY.md). A line's state ("`lineState`", ex `alig`) is **not**
 * the line's number: it is a byte summarizing how many rings each player
 * has placed on it, encoded as `ringCount1 + 5 * ringCount2` (each 0..4,
 * never both non-zero except transiently while [playLine]/[undoLine] moves
 * a line between [BoardState.lineSets]).
 *
 * All of this is on the AI's hot search path (see docs/PLAN.md §6.2): every
 * function here is allocation-free.
 */
object LineOps {

    /** Number of player-1 rings on a line in state [lineState]. Ports `nbAno1`. */
    fun ringCount1(lineState: Byte): Byte = (lineState % 5).toByte()

    /** Number of player-2 rings on a line in state [lineState]. Ports `nbAno2`. */
    fun ringCount2(lineState: Byte): Byte = (lineState / 5).toByte()

    /**
     * Which of the 10 [BoardState.lineSets] a line in state [lineState]
     * belongs to. Ports `numEnsemble`:
     * - 0..4: `ringCount1` rings for player 1, none for player 2.
     * - 5..8: `ringCount2 + 4` rings for player 2, none for player 1.
     * - 9: both players have at least one ring on this line (dead line).
     */
    fun lineKind(lineState: Byte): Byte {
        val v = lineState.toInt()
        val count1 = v % 5
        val count2 = v / 5
        return when {
            count2 == 0 -> count1.toByte()
            count1 == 0 -> (count2 + 4).toByte()
            else -> 9
        }
    }

    /** Records that [player] (1 or 2) played on [line]; moves it between line sets. Ports `jouerAlig`. */
    fun playLine(board: BoardState, line: Int, player: Int) {
        val old = board.lines[line]
        board.lineSets[lineKind(old).toInt()].remove(line.toByte())
        val updated = (old - 3 + 4 * player).toByte()
        board.lineSets[lineKind(updated).toInt()].add(line.toByte())
        board.lines[line] = updated
    }

    /** Reverses [playLine] for [player] on [line]. Ports `dejouerAlig`. */
    fun undoLine(board: BoardState, line: Int, player: Int) {
        val old = board.lines[line]
        board.lineSets[lineKind(old).toInt()].remove(line.toByte())
        val updated = (old + 3 - 4 * player).toByte()
        board.lineSets[lineKind(updated).toInt()].add(line.toByte())
        board.lines[line] = updated
    }

    /**
     * Given a [line] with exactly 2 rings for one player and none for the
     * other, the first (lowest-slot) free ring on it. Ports `trov2ano`;
     * see [lastFreeRing] for the counterpart searching from the other end.
     */
    fun firstFreeRing(board: BoardState, line: Int): Byte {
        val ring0 = BoardTables.lineRingAt(line, 0)
        if (board.rings[ring0.toInt()] == EMPTY) return ring0
        val ring1 = BoardTables.lineRingAt(line, 1)
        if (board.rings[ring1.toInt()] == EMPTY) return ring1
        return BoardTables.lineRingAt(line, 2)
    }

    /** Counterpart to [firstFreeRing], searching from the last slot. Ports `trov2anoBis`. */
    fun lastFreeRing(board: BoardState, line: Int): Byte {
        val ring3 = BoardTables.lineRingAt(line, 3)
        if (board.rings[ring3.toInt()] == EMPTY) return ring3
        val ring2 = BoardTables.lineRingAt(line, 2)
        if (board.rings[ring2.toInt()] == EMPTY) return ring2
        return BoardTables.lineRingAt(line, 1)
    }

    /**
     * Given a [line] with exactly 3 rings for one player and none for the
     * other, the one remaining free ring. Ports `trovLastAno`.
     */
    fun lastRemainingRing(board: BoardState, line: Int): Byte {
        val ring0 = BoardTables.lineRingAt(line, 0)
        if (board.rings[ring0.toInt()] == EMPTY) return ring0
        val ring1 = BoardTables.lineRingAt(line, 1)
        if (board.rings[ring1.toInt()] == EMPTY) return ring1
        val ring2 = BoardTables.lineRingAt(line, 2)
        if (board.rings[ring2.toInt()] == EMPTY) return ring2
        return BoardTables.lineRingAt(line, 3)
    }

    /**
     * Does the work of [firstFreeRing]/[lastFreeRing] at once, plus the
     * sub-index of the free pair obtained, packed into an [Int] instead of
     * the original's `byte[3]` (no allocation on this hot path). Ports
     * `trov2anoIndice`.
     *
     * Bit layout: ring1 in bits 0-5, ring2 in bits 6-11, subIndex in bits
     * 12-14 — unpack with [freePairRing1]/[freePairRing2]/[freePairSubIndex].
     */
    fun packedFreePair(board: BoardState, line: Int): Int {
        val ring0 = BoardTables.lineRingAt(line, 0)
        val ring1 = BoardTables.lineRingAt(line, 1)
        val ring2 = BoardTables.lineRingAt(line, 2)
        val ring3 = BoardTables.lineRingAt(line, 3)
        val numAno1: Int
        val numAno2: Int
        val subIndex: Int
        if (board.rings[ring0.toInt()] == EMPTY) {
            numAno1 = ring0.toInt()
            when {
                board.rings[ring1.toInt()] == EMPTY -> {
                    numAno2 = ring1.toInt()
                    subIndex = 0
                }
                board.rings[ring2.toInt()] == EMPTY -> {
                    numAno2 = ring2.toInt()
                    subIndex = 1
                }
                else -> {
                    numAno2 = ring3.toInt()
                    subIndex = 2
                }
            }
        } else if (board.rings[ring1.toInt()] == EMPTY) {
            numAno1 = ring1.toInt()
            if (board.rings[ring2.toInt()] == EMPTY) {
                numAno2 = ring2.toInt()
                subIndex = 3
            } else {
                numAno2 = ring3.toInt()
                subIndex = 4
            }
        } else {
            numAno1 = ring2.toInt()
            numAno2 = ring3.toInt()
            subIndex = 5
        }
        return numAno1 or (numAno2 shl 6) or (subIndex shl 12)
    }

    fun freePairRing1(packed: Int): Byte = (packed and 0x3F).toByte()
    fun freePairRing2(packed: Int): Byte = ((packed ushr 6) and 0x3F).toByte()
    fun freePairSubIndex(packed: Int): Byte = ((packed ushr 12) and 0x7).toByte()

    private const val EMPTY: Byte = 0
}
