package fr.thibautperrin.tetrano.engine.nn

import fr.thibautperrin.tetrano.engine.BoardTables

/**
 * The board as bitboards: bit `ring` of a `Long` is set iff that ring is
 * occupied (same ring numbering as [BoardTables]: `ring = x + 4*y + 16*size`).
 *
 * Tetrano is Qubic (4x4x4 tic-tac-toe): its 76 lines and 64 cells form a
 * hypergraph whose automorphism group has 192 elements — the 48 symmetries
 * of the cube times 4 maps applying the same permutation of {0,1,2,3} to all
 * three coordinates (one commuting with `i -> 3-i`, e.g. `(1 2)` which turns
 * the board inside out). [BoardTables.symmetricRing] only knows the 48.
 */
object QubicGeometry {
    const val CELLS: Int = 64
    const val LINES: Int = BoardTables.LINE_COUNT

    /** `lineMasks[line]` = the 4 rings of that line, as a bitboard (same line numbers as [BoardTables]). */
    val lineMasks: LongArray = LongArray(LINES) { line ->
        var mask = 0L
        for (slot in 0 until 4) mask = mask or (1L shl BoardTables.lineRingAt(line, slot).toInt())
        mask
    }

    /** Flat table, 8 ints per cell: count (4 or 7) then the lines through that cell. */
    private val cellLineTable: IntArray = IntArray(CELLS * 8).also { table ->
        for (line in 0 until LINES) {
            var bits = lineMasks[line]
            while (bits != 0L) {
                val cell = java.lang.Long.numberOfTrailingZeros(bits)
                bits = bits and (bits - 1)
                val base = cell * 8
                table[base]++
                table[base + table[base]] = line
            }
        }
    }

    /** Number of lines through [cell]: 7 for the 16 "strong" cells (corners and centres), else 4. */
    fun cellDegree(cell: Int): Int = cellLineTable[cell * 8]

    /** The [index]-th line (0 until [cellDegree]) through [cell]. */
    fun cellLine(cell: Int, index: Int): Int = cellLineTable[cell * 8 + 1 + index]

    /** `symmetries[s][cell]` = image of cell under automorphism s; `symmetries[0]` is the identity. */
    val symmetries: Array<IntArray> = buildSymmetries()

    val symmetryCount: Int get() = symmetries.size

    /** Byte-wise lookup tables: `transformTables[s][byte * 256 + value]` = image bits. */
    private val transformTables: Array<LongArray> = Array(symmetries.size) { s ->
        LongArray(8 * 256) { index ->
            val byteIndex = index / 256
            val value = index % 256
            var image = 0L
            for (bit in 0 until 8) {
                if ((value ushr bit) and 1 != 0) image = image or (1L shl symmetries[s][8 * byteIndex + bit])
            }
            image
        }
    }

    /** Image of bitboard [bits] under automorphism [symmetry]. */
    fun transform(symmetry: Int, bits: Long): Long {
        val t = transformTables[symmetry]
        var result = 0L
        for (byteIndex in 0 until 8) {
            result = result or t[byteIndex * 256 + ((bits ushr (8 * byteIndex)) and 0xFF).toInt()]
        }
        return result
    }

    fun popCount(bits: Long): Int = java.lang.Long.bitCount(bits)

    fun lowestCell(bits: Long): Int = java.lang.Long.numberOfTrailingZeros(bits)

    private fun cellOf(x: Int, y: Int, z: Int): Int = x + 4 * y + 16 * z

    private fun coordinateMap(f: (Int, Int, Int) -> IntArray): IntArray = IntArray(CELLS) { cell ->
        val image = f(cell and 3, (cell shr 2) and 3, cell shr 4)
        cellOf(image[0], image[1], image[2])
    }

    /** Closure of 5 generators; checked to map every line onto a line. */
    private fun buildSymmetries(): Array<IntArray> {
        val inside = intArrayOf(0, 2, 1, 3)
        val pairs = intArrayOf(1, 0, 3, 2)
        val generators = listOf(
            coordinateMap { x, y, z -> intArrayOf(y, x, z) },
            coordinateMap { x, y, z -> intArrayOf(y, z, x) },
            coordinateMap { x, y, z -> intArrayOf(3 - x, y, z) },
            coordinateMap { x, y, z -> intArrayOf(inside[x], inside[y], inside[z]) },
            coordinateMap { x, y, z -> intArrayOf(pairs[x], pairs[y], pairs[z]) },
        )
        val identity = IntArray(CELLS) { it }
        val group = mutableListOf(identity)
        val seen = hashSetOf(identity.toList())
        var frontier = 0
        while (frontier < group.size) {
            val current = group[frontier++]
            for (generator in generators) {
                val composed = IntArray(CELLS) { generator[current[it]] }
                if (seen.add(composed.toList())) group.add(composed)
            }
        }
        val lineSet = lineMasks.toHashSet()
        for (permutation in group) {
            for (mask in lineMasks) {
                var image = 0L
                var bits = mask
                while (bits != 0L) {
                    image = image or (1L shl permutation[lowestCell(bits)])
                    bits = bits and (bits - 1)
                }
                check(image in lineSet) { "not an automorphism" }
            }
        }
        return group.toTypedArray()
    }
}
