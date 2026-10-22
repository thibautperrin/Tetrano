package fr.thibautperrin.tetrano.wingraph

import fr.thibautperrin.tetrano.engine.nn.Tactics
import fr.thibautperrin.tetrano.engine.wingraph.CanonicalForm

/**
 * Positions are (attacker, defender) bitboards; the attacker is the first
 * player, to move iff both have as many rings. Every position stored by this
 * module is canonical ([CanonicalForm]).
 */
internal object Board {
    const val FULL: Long = -1L

    fun attackerToMove(a: Long, d: Long): Boolean = java.lang.Long.bitCount(a) == java.lang.Long.bitCount(d)

    /**
     * The moves worth considering for the side to move, which has no
     * immediate win: the forced block when the other side threatens to
     * complete a line, else every free cell.
     */
    fun candidateMoves(a: Long, d: Long): Long =
        if (attackerToMove(a, d)) {
            val td = Tactics.threats(d, a)
            if (td != 0L) td else (a or d).inv()
        } else {
            val ta = Tactics.threats(a, d)
            if (ta != 0L) ta else (a or d).inv()
        }

    fun cells(bits: Long, action: (Int) -> Unit) {
        var rest = bits
        while (rest != 0L) {
            val cell = java.lang.Long.numberOfTrailingZeros(rest)
            rest = rest and (rest - 1)
            action(cell)
        }
    }
}

/**
 * The distinct canonical children of a canonical position for a set of
 * moves: moves leading to the same canonical position (symmetric moves)
 * give one child, listed under its first move. Reusable, not thread-safe.
 */
internal class Children {
    val a = LongArray(64)
    val d = LongArray(64)
    val move = IntArray(64)

    /** For each child, the bitboard of all the moves leading to it. */
    val moveClass = LongArray(64)
    var count = 0
        private set
    private val form = CanonicalForm()

    fun generate(parentA: Long, parentD: Long, moves: Long): Children {
        count = 0
        val attacker = Board.attackerToMove(parentA, parentD)
        Board.cells(moves) { cell ->
            val bit = 1L shl cell
            form.of(if (attacker) parentA or bit else parentA, if (attacker) parentD else parentD or bit)
            val ca = form.attacker
            val cd = form.defender
            for (i in 0 until count) {
                if (a[i] == ca && d[i] == cd) {
                    moveClass[i] = moveClass[i] or bit
                    return@cells
                }
            }
            a[count] = ca
            d[count] = cd
            move[count] = cell
            moveClass[count] = bit
            count++
        }
        return this
    }
}

/**
 * Where the strategy starts: the real moves played from the empty board, an
 * odd number of them (the attacker's first, then alternately), ending with
 * the defender to move: the root of the top tree. The default, just the
 * corner `0`, is the real problem; a longer start is for tests and trials.
 */
class Start(val moves: IntArray) {
    init {
        require(moves.size % 2 == 1) { "start: an odd number of moves (the attacker's last)" }
        require(moves.distinct().size == moves.size && moves.all { it in 0..63 }) { "start: invalid moves" }
    }

    /** The whole game from the empty board (the strategy includes the first move). */
    val fromEmpty: Boolean get() = moves.size == 1 && moves[0] == 0

    val rootA: Long
    val rootD: Long

    init {
        var a = 0L
        var d = 0L
        moves.forEachIndexed { i, cell -> if (i % 2 == 0) a = a or (1L shl cell) else d = d or (1L shl cell) }
        val form = CanonicalForm().of(a, d)
        rootA = form.attacker
        rootD = form.defender
    }

    /**
     * Walks the strategy against every defender reply from the start; true
     * iff it wins everywhere. From the empty board the first move is a book
     * node (empty board -> cell 0).
     */
    fun walkAll(walker: Walker): Boolean {
        if (fromEmpty) return walker.walk(0L, 0L) >= 0
        if (java.lang.Long.bitCount(Tactics.threats(rootA, rootD)) >= 2) return true
        val kids = Children().generate(rootA, rootD, Board.candidateMoves(rootA, rootD))
        for (i in 0 until kids.count) if (walker.walk(kids.a[i], kids.d[i]) < 0) return false
        return true
    }

    override fun toString(): String = moves.joinToString(",")

    companion object {
        val CORNER = Start(intArrayOf(0))

        fun parse(text: String?): Start =
            if (text.isNullOrBlank()) CORNER else Start(text.split(",").map { it.trim().toInt() }.toIntArray())
    }
}
