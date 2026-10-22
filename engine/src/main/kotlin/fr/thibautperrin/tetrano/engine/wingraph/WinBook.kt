package fr.thibautperrin.tetrano.engine.wingraph

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * The winning graph as shipped: for each canonical attacker-to-move position
 * where no other rule of [WinRules] applies, the move to play.
 *
 * File format (big endian, [DataOutputStream]): int32 [MAGIC], int32 version
 * 1, int32 count, then `count` entries sorted by attacker then defender
 * bitboard (unsigned): int64 attacker, int64 defender, int8 cell.
 */
class WinBook private constructor(
    private val attackers: LongArray,
    private val defenders: LongArray,
    private val moves: ByteArray,
) : WinRules.Book {

    val size: Int get() = moves.size

    override fun move(attacker: Long, defender: Long): Int {
        var lo = 0
        var hi = moves.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            var c = java.lang.Long.compareUnsigned(attackers[mid], attacker)
            if (c == 0) c = java.lang.Long.compareUnsigned(defenders[mid], defender)
            when {
                c < 0 -> lo = mid + 1
                c > 0 -> hi = mid - 1
                else -> return moves[mid].toInt()
            }
        }
        return -1
    }

    fun writeTo(stream: OutputStream) {
        val out = DataOutputStream(stream)
        out.writeInt(MAGIC)
        out.writeInt(1)
        out.writeInt(size)
        for (i in 0 until size) {
            out.writeLong(attackers[i])
            out.writeLong(defenders[i])
            out.writeByte(moves[i].toInt())
        }
        out.flush()
    }

    companion object {
        const val MAGIC: Int = 0x5157494E // "QWIN"

        /** A book of the given entries (any order, no duplicate position). */
        fun of(attackers: LongArray, defenders: LongArray, moves: ByteArray): WinBook {
            val order = (0 until moves.size).sortedWith { x, y ->
                val c = java.lang.Long.compareUnsigned(attackers[x], attackers[y])
                if (c != 0) c else java.lang.Long.compareUnsigned(defenders[x], defenders[y])
            }
            return WinBook(
                LongArray(order.size) { attackers[order[it]] },
                LongArray(order.size) { defenders[order[it]] },
                ByteArray(order.size) { moves[order[it]] },
            )
        }

        fun load(stream: InputStream): WinBook {
            val input = DataInputStream(stream.buffered())
            require(input.readInt() == MAGIC) { "not a winning graph file" }
            require(input.readInt() == 1) { "unknown winning graph version" }
            val n = input.readInt()
            val a = LongArray(n)
            val d = LongArray(n)
            val m = ByteArray(n)
            for (i in 0 until n) {
                a[i] = input.readLong()
                d[i] = input.readLong()
                m[i] = input.readByte()
            }
            return WinBook(a, d, m)
        }
    }
}

/**
 * The Unbeatable player: the first player following [WinRules] with [book].
 * Not thread-safe.
 */
class UnbeatablePlayer(private val book: WinBook) {
    private val form = CanonicalForm()
    private val vcf = WinRules.newVcf()

    /**
     * The ring to play for the first player owning [attacker], the opponent
     * owning [defender] (same number of rings each), or -1 if the position is
     * outside the graph (the opponent cannot get there from a game the
     * player started on an empty board).
     */
    fun chooseRing(attacker: Long, defender: Long): Int {
        form.of(attacker, defender)
        val decision = WinRules.decide(form.attacker, form.defender, book, vcf)
        return if (decision == WinRules.NONE) -1 else form.toReal(WinRules.move(decision))
    }
}
