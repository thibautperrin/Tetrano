package fr.thibautperrin.tetrano.engine

/**
 * Static, context-free relationships between ring numbers and line numbers.
 *
 * Ports `thibautperrin.tetrano.Intelligence2.Statique` (see docs/GLOSSARY.md
 * and docs/ENGINE_FIDELITY.md). The board has 64 rings (`ring = x + 4*y +
 * 16*size`, each coordinate 0..3) and 76 winning lines of 4 rings each
 * (concentric-in-a-cell, or same/ascending/descending size along a row,
 * column or diagonal).
 *
 * Every table here is a flat primitive array computed once, and every
 * accessor is allocation-free: this is read from the AI's hot search path
 * (see docs/PLAN.md §6.2), so nothing here may allocate per call.
 */
object BoardTables {

    const val RING_COUNT: Int = 64
    const val LINE_COUNT: Int = 76
    private const val RINGS_PER_LINE: Int = 4

    /** Flat [LINE_COUNT] * [RINGS_PER_LINE] table: ring numbers of each line. */
    private val lineRings: ByteArray = buildLineRings()

    /**
     * Flat table, 8 bytes per ring: `ringLines[ring * 8]` is the number of
     * lines that ring belongs to (4 or 7), followed by that many line
     * numbers. Ports `tabAnneauxAlig`.
     */
    private val ringLines: ByteArray = buildRingLines()

    /**
     * Bit `line * 64 + ring` is set iff `ring` belongs to `line`. Ports the
     * `BitSet tabBool` as a flat [LongArray] (76 * 64 = 4864 bits = 76
     * longs, evenly).
     */
    private val ringInLineBits: LongArray = buildRingInLineBits()

    /** True iff [ring] belongs to [line]. Ports `isAnneauAppAlig`. */
    fun ringBelongsToLine(ring: Int, line: Int): Boolean {
        val bitIndex = line * RING_COUNT + ring
        return (ringInLineBits[bitIndex ushr 6] ushr (bitIndex and 63)) and 1L != 0L
    }

    /** Number of lines [ring] belongs to (4 or 7). Ports the count byte of `tabAnneauxAlig`. */
    fun ringLineCount(ring: Int): Int = ringLines[ring * 8].toInt()

    /** The [index]-th line number (0 until [ringLineCount]) that [ring] belongs to. */
    fun ringLineAt(ring: Int, index: Int): Byte = ringLines[ring * 8 + 1 + index]

    /** The [slot]-th ring (0..3) of [line]. */
    fun lineRingAt(line: Int, slot: Int): Byte = lineRings[line * RINGS_PER_LINE + slot]

    /**
     * Fresh copy of the 4 ring numbers of [line]. Only used on cold paths
     * (blinking the winning line, static rule examples) — everywhere else,
     * prefer [lineRingAt] to avoid allocating.
     */
    fun copyLineRings(line: Int): ByteArray {
        val start = line * RINGS_PER_LINE
        return lineRings.copyOfRange(start, start + RINGS_PER_LINE)
    }

    /**
     * Sum, over every line [ring] belongs to, of `weights[lineKind(line)]`.
     * Ports `getPointsSimple`; allocation-free like the original.
     *
     * @param ring the ring whose points are computed (no check that it is
     *   free: the caller has that information, the original doesn't either).
     * @param lines the game's current line-state array (`byte[76]`, the only
     *   piece of information here that depends on the game in progress).
     * @param weights a `byte[10]` weight table, one weight per [LineOps.lineKind].
     */
    fun pointsFor(ring: Int, lines: ByteArray, weights: ByteArray): Byte {
        var result = 0
        val count = ringLineCount(ring)
        val base = ring * 8 + 1
        for (i in 0 until count) {
            val line = ringLines[base + i].toInt()
            result += weights[LineOps.lineKind(lines[line]).toInt()]
        }
        return result.toByte()
    }

    /**
     * True iff [ring] belongs to at least one line currently in state
     * [lineKind]. Ports `isAnneauAppartientTelSorteAlig`; allocation-free.
     */
    fun ringHasLineOfKind(ring: Int, lines: ByteArray, lineKind: Byte): Boolean {
        val count = ringLineCount(ring)
        val base = ring * 8 + 1
        for (i in 0 until count) {
            val line = ringLines[base + i].toInt()
            if (lines[line] == lineKind) return true
        }
        return false
    }

    /**
     * The ring symmetric to [ring] under symmetry [symmetryNumber] (0..47).
     * Ports `getAnneauSymetrique`.
     *
     * The symmetry number is `24*b3 + 12*b2 + 6*b1 + t`, where `t` (0..5)
     * is a coordinate permutation and `b1`/`b2`/`b3` (0/1) flip the new x/y/z
     * coordinate respectively.
     */
    fun symmetricRing(ring: Byte, symmetryNumber: Byte): Byte {
        var x = (ring % 4).toByte()
        var y = ((ring / 4) % 4).toByte()
        var z = (ring / 16).toByte()
        when (symmetryNumber % 6) {
            1 -> {
                // x=x
                val inter = y
                y = z
                z = inter
            }
            2 -> {
                val inter = x
                x = y
                y = inter // z=z
            }
            3 -> {
                val inter = x
                x = y
                y = z
                z = inter
            }
            4 -> {
                val inter = x
                x = z
                z = y
                y = inter
            }
            5 -> {
                // y=y
                val inter = x
                x = z
                z = inter
            }
            // 0: no change.
        }
        if (symmetryNumber / 6 % 2 == 1) x = (3 - x).toByte()
        if (symmetryNumber / 12 % 2 == 1) y = (3 - y).toByte()
        if (symmetryNumber / 24 % 2 == 1) z = (3 - z).toByte()
        return (x + 4 * y + 16 * z).toByte()
    }

    /**
     * The inverse of [symmetricRing]: given the *result* of applying
     * [symmetryNumber] to some ring, returns that original ring.
     *
     * Ports `getAnneauSymetrieInverse`. **Not** a byte-for-byte mirror of
     * [symmetricRing]'s `when` (cases 3 and 4 are swapped there): this is
     * intentional and matches the legacy behavior exactly — see
     * docs/PLAN.md §6.1.7. "Symmetry" here is really a similitude, not
     * reflexive in the strict sense.
     */
    fun inverseSymmetricRing(ring: Byte, symmetryNumber: Byte): Byte {
        var x = (ring % 4).toByte()
        var y = ((ring / 4) % 4).toByte()
        var z = (ring / 16).toByte()
        if (symmetryNumber / 6 % 2 == 1) x = (3 - x).toByte()
        if (symmetryNumber / 12 % 2 == 1) y = (3 - y).toByte()
        if (symmetryNumber / 24 % 2 == 1) z = (3 - z).toByte()
        when (symmetryNumber % 6) {
            1 -> {
                // x=x
                val inter = y
                y = z
                z = inter
            }
            2 -> {
                val inter = x
                x = y
                y = inter // z=z
            }
            3 -> {
                val inter = x
                x = z
                z = y
                y = inter
            }
            4 -> {
                val inter = x
                x = y
                y = z
                z = inter
            }
            5 -> {
                // y=y
                val inter = x
                x = z
                z = inter
            }
            // 0: no change.
        }
        return (x + 4 * y + 16 * z).toByte()
    }

    // ---- One-time table construction (ports the three initialiseTabXxx methods) ----

    private fun buildLineRings(): ByteArray = byteArrayOf(
        0, 4, 8, 12,
        0, 16, 32, 48,
        0, 1, 2, 3,
        16, 20, 24, 28,
        4, 20, 36, 52,
        16, 17, 18, 19,
        32, 36, 40, 44,
        8, 24, 40, 56,
        32, 33, 34, 35,
        48, 52, 56, 60,
        12, 28, 44, 60,
        48, 49, 50, 51,
        1, 5, 9, 13,
        1, 17, 33, 49,
        4, 5, 6, 7,
        17, 21, 25, 29,
        5, 21, 37, 53,
        20, 21, 22, 23,
        33, 37, 41, 45,
        9, 25, 41, 57,
        36, 37, 38, 39,
        49, 53, 57, 61,
        13, 29, 45, 61,
        52, 53, 54, 55,
        2, 6, 10, 14,
        2, 18, 34, 50,
        8, 9, 10, 11,
        18, 22, 26, 30,
        6, 22, 38, 54,
        24, 25, 26, 27,
        34, 38, 42, 46,
        10, 26, 42, 58,
        40, 41, 42, 43,
        50, 54, 58, 62,
        14, 30, 46, 62,
        56, 57, 58, 59,
        3, 7, 11, 15,
        3, 19, 35, 51,
        12, 13, 14, 15,
        19, 23, 27, 31,
        7, 23, 39, 55,
        28, 29, 30, 31,
        35, 39, 43, 47,
        11, 27, 43, 59,
        44, 45, 46, 47,
        51, 55, 59, 63,
        15, 31, 47, 63,
        60, 61, 62, 63,
        0, 20, 40, 60,
        0, 5, 10, 15,
        0, 17, 34, 51,
        48, 36, 24, 12,
        12, 9, 6, 3,
        3, 18, 33, 48,
        1, 21, 41, 61,
        16, 21, 26, 31,
        4, 21, 38, 55,
        49, 37, 25, 13,
        28, 25, 22, 19,
        7, 22, 37, 52,
        2, 22, 42, 62,
        32, 37, 42, 47,
        8, 25, 42, 59,
        50, 38, 26, 14,
        44, 41, 38, 35,
        11, 26, 41, 56,
        3, 23, 43, 63,
        48, 53, 58, 63,
        12, 29, 46, 63,
        51, 39, 27, 15,
        60, 57, 54, 51,
        15, 30, 45, 60,
        0, 21, 42, 63,
        3, 22, 41, 60,
        12, 25, 38, 51,
        15, 26, 37, 48,
    )

    private fun buildRingLines(): ByteArray {
        val table = ByteArray(RING_COUNT * 8)
        for (line in 0 until LINE_COUNT) {
            for (slot in 0 until RINGS_PER_LINE) {
                val ring = lineRings[line * RINGS_PER_LINE + slot].toInt()
                val countIndex = ring * 8
                table[countIndex]++
                table[countIndex + table[countIndex]] = line.toByte()
            }
        }
        return table
    }

    private fun buildRingInLineBits(): LongArray {
        val bits = LongArray(LINE_COUNT * RING_COUNT / 64)
        for (line in 0 until LINE_COUNT) {
            for (slot in 0 until RINGS_PER_LINE) {
                val ring = lineRings[line * RINGS_PER_LINE + slot].toInt()
                val bitIndex = line * RING_COUNT + ring
                bits[bitIndex ushr 6] = bits[bitIndex ushr 6] or (1L shl (bitIndex and 63))
            }
        }
        return bits
    }
}
