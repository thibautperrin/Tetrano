package fr.thibautperrin.tetrano.selfplay

import fr.thibautperrin.tetrano.engine.nn.ExactSolver
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Training records, format v3 (must match tools/nn/data.py): a 16-byte header (magic [V3_MAGIC],
 * version 3, record size [RECORD3_SIZE], 0) then records of [RECORD3_SIZE] bytes, little endian:
 *
 * | offset | field |
 * |---|---|
 * | 0 | mine (u64), 8 theirs (u64) |
 * | 16 | outcome of the game for the side to move (0 win, 1 draw, 2 loss) |
 * | 17 | flags: 1 policy target, 2 exact (field 152 valid), 4 root value is the chosen move's Q, 8 move values valid |
 * | 18 | ply, 19 plies left until the end of the game (0 unknown) |
 * | 20 | policy target, 64 × u16 (probability × 65535) |
 * | 148 | search value for the side to move (f32) |
 * | 152 | exact value for the side to move: 0 unknown, 1 win, 2 draw, 3 loss |
 * | 153 | source: 0 self-play, 1 winning graph, 2 truth bench |
 * | 154 | reserved (u16) |
 * | 156 | exact-solver evaluations spent on this position (u32) |
 * | 160 | per move, 64 × i8: the mover's value after that move, × 100 (-100..100); [Q_WIN], [Q_DRAW], [Q_LOSS] when proven; [Q_UNKNOWN] |
 *
 * Version 2 files (tools/nn/data.py `RECORD`, 152 bytes, no header) are the first 152 bytes of a
 * v3 record; their "exact" flag means that the game outcome is the exact value.
 */
const val V3_MAGIC: Int = 0x33435254 // "TRC3"
const val RECORD3_SIZE: Int = 224
const val V3_HEADER: Int = 16

internal const val FLAG_CHOSEN_Q = 4
internal const val FLAG_MOVE_VALUES = 8

const val EXACT_UNKNOWN: Int = 0
const val EXACT_WIN: Int = 1
const val EXACT_DRAW: Int = 2
const val EXACT_LOSS: Int = 3

const val Q_WIN: Byte = 101
const val Q_DRAW: Byte = 102
const val Q_LOSS: Byte = 103
const val Q_UNKNOWN: Byte = -128

/** One v3 record, mutable, for reading and writing. */
internal class Record3 {
    var mine = 0L
    var theirs = 0L
    var outcome = 1
    var flags = 0
    var ply = 0
    var movesLeft = 0
    val policy = ShortArray(64)
    var value = 0f
    var exact = EXACT_UNKNOWN
    var source = 0
    var evals = 0L
    val q = ByteArray(64) { Q_UNKNOWN }

    fun write(out: ByteBuffer) {
        out.putLong(mine)
        out.putLong(theirs)
        out.put(outcome.toByte())
        out.put(flags.toByte())
        out.put(ply.toByte())
        out.put(movesLeft.coerceIn(0, 255).toByte())
        for (c in 0 until 64) out.putShort(policy[c])
        out.putFloat(value)
        out.put(exact.toByte())
        out.put(source.toByte())
        out.putShort(0)
        out.putInt(evals.coerceAtMost(0xFFFFFFFFL).toInt())
        out.put(q)
    }

    /** Reads a v2 record (152 bytes) or a v3 record from [buf] at its position. */
    fun read(buf: ByteBuffer, v3: Boolean) {
        mine = buf.long
        theirs = buf.long
        outcome = buf.get().toInt() and 0xFF
        flags = buf.get().toInt() and 0xFF
        ply = buf.get().toInt() and 0xFF
        movesLeft = buf.get().toInt() and 0xFF
        for (c in 0 until 64) policy[c] = buf.short
        value = buf.float
        if (v3) {
            exact = buf.get().toInt() and 0xFF
            source = buf.get().toInt() and 0xFF
            buf.short
            evals = buf.int.toLong() and 0xFFFFFFFFL
            buf.get(q)
        } else {
            exact = if (flags and FLAG_EXACT != 0) outcome + 1 else EXACT_UNKNOWN
            source = if (flags and FLAG_EXACT != 0 && movesLeft == 0 && value.let { it == 1f || it == -1f }) 1 else 0
            evals = 0
            q.fill(Q_UNKNOWN)
        }
    }

    companion object {
        fun exactCode(outcome: ExactSolver.Outcome): Int = when (outcome) {
            ExactSolver.Outcome.WIN -> EXACT_WIN
            ExactSolver.Outcome.DRAW -> EXACT_DRAW
            ExactSolver.Outcome.LOSS -> EXACT_LOSS
            ExactSolver.Outcome.UNKNOWN -> EXACT_UNKNOWN
        }
    }
}

/** The records of a v2 or v3 file. */
internal class RecordFile(val file: File) {
    val v3: Boolean
    val count: Long
    private val dataStart: Long
    private val size: Int

    init {
        RandomAccessFile(file, "r").use { raf ->
            val head = ByteArray(16)
            val n = if (raf.length() >= 16) raf.read(head) else 0
            val bb = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN)
            v3 = n == 16 && bb.getInt(0) == V3_MAGIC && bb.getInt(4) == 3 && bb.getInt(8) == RECORD3_SIZE
            dataStart = if (v3) V3_HEADER.toLong() else 0L
            size = if (v3) RECORD3_SIZE else RECORD_SIZE
            count = (raf.length() - dataStart) / size
        }
    }

    /** Reads records [from] until [to] into a list. */
    fun read(from: Long, to: Long): List<Record3> {
        val n = (to - from).toInt()
        val bytes = ByteArray(n * size)
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(dataStart + from * size)
            raf.readFully(bytes)
        }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return List(n) { Record3().also { r -> r.read(buf, v3) } }
    }
}

internal fun v3Header(): ByteArray =
    ByteBuffer.allocate(V3_HEADER).order(ByteOrder.LITTLE_ENDIAN)
        .putInt(V3_MAGIC).putInt(3).putInt(RECORD3_SIZE).putInt(0).array()
