package fr.thibautperrin.tetrano.engine

/**
 * The raw game data of a Tetrano board: which player (if any) occupies each
 * ring, the derived state of each line, and the [LineSet]s that let the AI
 * answer "which lines have exactly N rings for player P" in O(1).
 *
 * Ports `thibautperrin.tetrano.Intelligence2.AnoAlig` (see docs/GLOSSARY.md).
 * This is the AI's hot search path (see docs/PLAN.md §6.2): [play] and
 * [undo] are called millions of times per move at high AI levels and must
 * stay allocation-free.
 */
class BoardState {

    /** `rings[ring]` is 0 (free), 1 or 2 (occupied by that player). */
    val rings: ByteArray = ByteArray(BoardTables.RING_COUNT)

    /** `lines[line]` is that line's state byte — see [LineOps]. */
    val lines: ByteArray = ByteArray(BoardTables.LINE_COUNT)

    /**
     * `lineSets[k]` holds every line currently of [LineOps.lineKind] `k`
     * (0..8: pure counts for player 1 then player 2, 9: dead/mixed lines).
     * `lineSets[0]` starts containing every line (0 rings played by
     * anyone); the others start empty.
     */
    val lineSets: Array<LineSet> = Array(LINE_KIND_COUNT) { index ->
        LineSet(BoardTables.LINE_COUNT.toByte(), filled = index == 0)
    }

    var playedCount: Int = 0
        private set

    /** An empty board. Ports the no-argument `AnoAlig()`. */
    constructor()

    /** An independent copy of [source]. Ports `AnoAlig(AnoAlig)`. */
    constructor(source: BoardState) : this() {
        System.arraycopy(source.rings, 0, rings, 0, rings.size)
        System.arraycopy(source.lines, 0, lines, 0, lines.size)
        for (i in lineSets.indices) {
            lineSets[i] = LineSet(source.lineSets[i])
        }
        playedCount = source.playedCount
    }

    /**
     * Rebuilds a board from a [signature] (see [signature]), assuming
     * player 1 played first unless [invertPlayers] is true. Ports the
     * 2-argument `AnoAlig(byte[], boolean)`.
     */
    constructor(signature: ByteArray, invertPlayers: Boolean) :
        this(signature, symmetryNumber = 0, invertPlayers = invertPlayers)

    /**
     * Rebuilds a board from a [signature] taken under symmetry
     * [symmetryNumber] (see [BoardTables.symmetricRing]/[canonicalSignature]).
     * Ports the 3-argument `AnoAlig(byte[], byte, boolean)`.
     */
    constructor(signature: ByteArray, symmetryNumber: Byte, invertPlayers: Boolean) : this() {
        for (i in 0 until SIGNATURE_SIZE) {
            var power = 1
            for (j in 0 until 5) {
                val plainRing = 5 * i + j
                if (plainRing < BoardTables.RING_COUNT) {
                    val ring = BoardTables.inverseSymmetricRing(plainRing.toByte(), symmetryNumber).toInt()
                    var player = (signature[i] + 128) / power % 3
                    if (player != 0 && invertPlayers) {
                        player = 3 - player
                    }
                    if (player != 0) {
                        play(ring, player)
                    }
                    power *= 3
                }
            }
        }
    }

    /** Records that [player] (1 or 2) played [ring]. Ports `jouer`. */
    fun play(ring: Int, player: Int) {
        playedCount++
        rings[ring] = player.toByte()
        val lineCount = BoardTables.ringLineCount(ring)
        for (i in 0 until lineCount) {
            LineOps.playLine(this, BoardTables.ringLineAt(ring, i).toInt(), player)
        }
    }

    /** Reverses [play] for [player] on [ring]. Ports `dejouer`. */
    fun undo(ring: Int, player: Int) {
        playedCount--
        rings[ring] = 0
        val lineCount = BoardTables.ringLineCount(ring)
        for (i in 0 until lineCount) {
            LineOps.undoLine(this, BoardTables.ringLineAt(ring, i).toInt(), player)
        }
    }

    /** Empties the board. Ports `restart`. */
    fun restart() {
        rings.fill(0)
        lines.fill(0)
        lineSets[0].fill()
        for (i in 1 until lineSets.size) {
            lineSets[i].clear()
        }
        playedCount = 0
    }

    /**
     * A `ByteArray(13)` encoding this board in base 3 (5 rings per byte,
     * biased by [Byte.MIN_VALUE] so the empty board's signature is all
     * `Byte.MIN_VALUE`), with players swapped if [invertPlayers]. Ports
     * `getSignature`.
     *
     * @param out reused instead of allocating, if provided.
     */
    fun signature(invertPlayers: Boolean, out: ByteArray = ByteArray(SIGNATURE_SIZE)): ByteArray {
        for (i in 0 until SIGNATURE_SIZE) {
            var value = Byte.MIN_VALUE.toInt()
            var power = 1
            for (j in 0 until 5) {
                val ring = 5 * i + j
                if (ring < BoardTables.RING_COUNT) {
                    if (rings[ring] > 0) {
                        val playerValue = if (invertPlayers) 3 - rings[ring] else rings[ring].toInt()
                        value += power * playerValue
                    }
                    power *= 3
                }
            }
            out[i] = value.toByte()
        }
        return out
    }

    /**
     * The lexicographically lowest [signature] over all 48 board symmetries
     * — a canonical form independent of rotation/reflection, used to
     * recognize equivalent positions. Ports `getLowestSignature`.
     *
     * @param out reused instead of allocating, if provided.
     */
    fun canonicalSignature(invertPlayers: Boolean, out: ByteArray = ByteArray(SIGNATURE_SIZE)): ByteArray {
        val scratch = ByteArray(SIGNATURE_SIZE)
        var haveResult = false
        for (symmetryNumber in 0 until SYMMETRY_COUNT) {
            for (i in 0 until SIGNATURE_SIZE) {
                var value = Byte.MIN_VALUE.toInt()
                var power = 1
                for (j in 0 until 5) {
                    val plainRing = 5 * i + j
                    if (plainRing < BoardTables.RING_COUNT) {
                        val ring = BoardTables.symmetricRing(plainRing.toByte(), symmetryNumber.toByte()).toInt()
                        if (rings[ring] > 0) {
                            val playerValue = if (invertPlayers) 3 - rings[ring] else rings[ring].toInt()
                            value += power * playerValue
                        }
                        power *= 3
                    }
                }
                scratch[i] = value.toByte()
            }
            if (!haveResult || isStrictlyLess(scratch, out)) {
                System.arraycopy(scratch, 0, out, 0, SIGNATURE_SIZE)
                haveResult = true
            }
        }
        return out
    }

    /** True iff [a] is strictly less than [b] in lexicographic byte order. Ports `inferieurStricte`. */
    private fun isStrictlyLess(a: ByteArray, b: ByteArray): Boolean {
        for (i in a.indices) {
            if (a[i] < b[i]) return true
            if (a[i] > b[i]) return false
        }
        return false
    }

    companion object {
        const val SIGNATURE_SIZE: Int = 13
        const val SYMMETRY_COUNT: Int = 48
        private const val LINE_KIND_COUNT: Int = 10
    }
}
