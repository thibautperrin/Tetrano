package fr.thibautperrin.tetrano.engine.nn

import java.io.DataInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * The Tetrano value/policy network: message passing on the cell <-> line
 * hypergraph, with weights shared by every cell and every line, so its
 * outputs are exactly invariant (value) or equivariant (policy) under the
 * 192 board automorphisms. Trained by `tools/nn` (PyTorch, `model.py`),
 * which defines the reference forward pass this class mirrors.
 *
 * Inputs, from the point of view of the player to move ("mine"):
 * - per cell: state {empty, mine, theirs} and degree class {4 lines, 7 lines};
 * - per line: {empty, mine 1/2/3, theirs 1/2/3, dead (both colours)};
 * - "I am the first player" (equal ring counts).
 *
 * Version 2 adds a third head, the number of plies left until the end of
 * the game ([Workspace.movesLeft]); version 1 files (no such head) still
 * load, with 0 plies left.
 *
 * File format (little endian): int32 magic [MAGIC], int32 version (1 or 2),
 * int32 H, int32 L, int32 parameter count, then float32 parameters in the
 * order read by the constructor. Linear weights are stored transposed,
 * `[in][out]`, so the forward pass is a sequence of vectorizable axpy loops.
 *
 * The weights are immutable and may be shared; [evaluate] needs a
 * per-thread [Workspace].
 */
class QubicNet private constructor(
    val hidden: Int,
    val layers: Int,
    params: FloatArray,
    /** True iff the file has the moves-left head (version 2). */
    val hasMovesLeft: Boolean,
) {

    private var offset = 0
    private val all = params

    private fun take(size: Int): Int {
        val start = offset
        offset += size
        return start
    }

    private val h = hidden
    private val valueIn = 3 * h + 1

    private val cellEmbed = take(3 * h)
    private val degreeEmbed = take(2 * h)
    private val firstEmbed = take(h)
    private val lineEmbed = take(8 * h)

    private class Block(
        val lnGamma: Int, val lnBeta: Int, val w1: Int, val b1: Int, val w2: Int, val b2: Int,
    )

    private fun block(inSize: Int): Block = Block(
        lnGamma = take(inSize), lnBeta = take(inSize),
        w1 = take(inSize * h), b1 = take(h), w2 = take(h * h), b2 = take(h),
    )

    private val lineBlocks = arrayOfNulls<Block>(layers)
    private val cellBlocks = arrayOfNulls<Block>(layers)

    init {
        for (k in 0 until layers) {
            lineBlocks[k] = block(2 * h)
            cellBlocks[k] = block(2 * h)
        }
    }

    private val polLnGamma = take(h)
    private val polLnBeta = take(h)
    private val polW1 = take(h * h)
    private val polB1 = take(h)
    private val polW2 = take(h)
    private val polB2 = take(1)
    private val valLnGamma = take(valueIn)
    private val valLnBeta = take(valueIn)
    private val valW1 = take(valueIn * h)
    private val valB1 = take(h)
    private val valW2 = take(h * 3)
    private val valB2 = take(3)
    private val mlLnGamma = if (hasMovesLeft) take(valueIn) else 0
    private val mlLnBeta = if (hasMovesLeft) take(valueIn) else 0
    private val mlW1 = if (hasMovesLeft) take(valueIn * h) else 0
    private val mlB1 = if (hasMovesLeft) take(h) else 0
    private val mlW2 = if (hasMovesLeft) take(h) else 0
    private val mlB2 = if (hasMovesLeft) take(1) else 0

    init {
        require(offset == params.size) { "parameter count ${params.size}, expected $offset" }
    }

    /** Per-thread scratch memory for [evaluate]. */
    inner class Workspace {
        internal val cell = FloatArray(QubicGeometry.CELLS * h)
        internal val line = FloatArray(QubicGeometry.LINES * h)
        internal val input = FloatArray(maxOf(2 * h, valueIn))
        internal val normed = FloatArray(maxOf(2 * h, valueIn))
        internal val mid = FloatArray(h)
        internal val out = FloatArray(h)

        /** Plies left until the end of the game, as estimated by the last [evaluate] (0 for a version-1 network). */
        var movesLeft: Float = 0f
            internal set
    }

    fun newWorkspace(): Workspace = Workspace()

    /**
     * Evaluates the position where the player to move owns [mine] and the
     * opponent [theirs]. Fills [policyLogits] (64 raw logits, one per cell,
     * occupied cells included — mask them) and [wdl] (win, draw, loss
     * probabilities for the player to move).
     */
    fun evaluate(mine: Long, theirs: Long, ws: Workspace, policyLogits: FloatArray, wdl: FloatArray) {
        val p = all
        val cell = ws.cell
        val line = ws.line
        val first = java.lang.Long.bitCount(mine) == java.lang.Long.bitCount(theirs)

        for (c in 0 until QubicGeometry.CELLS) {
            val state = when {
                (mine ushr c) and 1L != 0L -> 1
                (theirs ushr c) and 1L != 0L -> 2
                else -> 0
            }
            val degreeClass = if (QubicGeometry.cellDegree(c) == 7) 1 else 0
            val base = c * h
            val e = cellEmbed + state * h
            val d = degreeEmbed + degreeClass * h
            for (j in 0 until h) {
                cell[base + j] = p[e + j] + p[d + j] + (if (first) p[firstEmbed + j] else 0f)
            }
        }
        for (l in 0 until QubicGeometry.LINES) {
            val mask = QubicGeometry.lineMasks[l]
            val e = lineEmbed + lineState(mask, mine, theirs) * h
            System.arraycopy(p, e, line, l * h, h)
        }

        val input = ws.input
        for (k in 0 until layers) {
            val lb = lineBlocks[k]!!
            for (l in 0 until QubicGeometry.LINES) {
                val base = l * h
                System.arraycopy(line, base, input, 0, h)
                java.util.Arrays.fill(input, h, 2 * h, 0f)
                var bits = QubicGeometry.lineMasks[l]
                while (bits != 0L) {
                    val c = java.lang.Long.numberOfTrailingZeros(bits)
                    bits = bits and (bits - 1)
                    val cb = c * h
                    for (j in 0 until h) input[h + j] += cell[cb + j]
                }
                residualMlp(lb, 2 * h, ws, line, base)
            }
            val cb2 = cellBlocks[k]!!
            for (c in 0 until QubicGeometry.CELLS) {
                val base = c * h
                System.arraycopy(cell, base, input, 0, h)
                java.util.Arrays.fill(input, h, 2 * h, 0f)
                val degree = QubicGeometry.cellDegree(c)
                for (i in 0 until degree) {
                    val lbase = QubicGeometry.cellLine(c, i) * h
                    for (j in 0 until h) input[h + j] += line[lbase + j]
                }
                val inv = 1f / degree
                for (j in h until 2 * h) input[j] *= inv
                residualMlp(cb2, 2 * h, ws, cell, base)
            }
        }

        // Policy head: one logit per cell.
        for (c in 0 until QubicGeometry.CELLS) {
            System.arraycopy(cell, c * h, input, 0, h)
            layerNorm(input, h, polLnGamma, polLnBeta, ws.normed)
            linear(ws.normed, h, polW1, polB1, h, ws.mid)
            var logit = p[polB2]
            for (j in 0 until h) {
                val v = ws.mid[j]
                if (v > 0f) logit += v * p[polW2 + j]
            }
            policyLogits[c] = logit
        }

        // Value head: [mean cells, max cells, mean lines, first].
        for (j in 0 until h) {
            var sum = 0f
            var max = Float.NEGATIVE_INFINITY
            for (c in 0 until QubicGeometry.CELLS) {
                val v = cell[c * h + j]
                sum += v
                if (v > max) max = v
            }
            var lineSum = 0f
            for (l in 0 until QubicGeometry.LINES) lineSum += line[l * h + j]
            input[j] = sum / QubicGeometry.CELLS
            input[h + j] = max
            input[2 * h + j] = lineSum / QubicGeometry.LINES
        }
        input[3 * h] = if (first) 1f else 0f
        layerNorm(input, valueIn, valLnGamma, valLnBeta, ws.normed)
        linear(ws.normed, valueIn, valW1, valB1, h, ws.mid)
        for (j in 0 until h) if (ws.mid[j] < 0f) ws.mid[j] = 0f
        var maxLogit = Float.NEGATIVE_INFINITY
        for (o in 0 until 3) {
            var logit = p[valB2 + o]
            for (j in 0 until h) logit += ws.mid[j] * p[valW2 + j * 3 + o]
            wdl[o] = logit
            if (logit > maxLogit) maxLogit = logit
        }
        var total = 0f
        for (o in 0 until 3) {
            wdl[o] = exp(wdl[o] - maxLogit)
            total += wdl[o]
        }
        for (o in 0 until 3) wdl[o] /= total

        // Moves-left head, on the same pooled input: plies left / 64.
        if (hasMovesLeft) {
            layerNorm(input, valueIn, mlLnGamma, mlLnBeta, ws.normed)
            linear(ws.normed, valueIn, mlW1, mlB1, h, ws.mid)
            var plies = p[mlB2]
            for (j in 0 until h) {
                val v = ws.mid[j]
                if (v > 0f) plies += v * p[mlW2 + j]
            }
            ws.movesLeft = maxOf(0f, plies) * MOVES_LEFT_SCALE
        } else {
            ws.movesLeft = 0f
        }
    }

    /** `target[base until base+h] += W2 · relu(W1 · LN(input) + b1) + b2`. */
    private fun residualMlp(b: Block, inSize: Int, ws: Workspace, target: FloatArray, base: Int) {
        layerNorm(ws.input, inSize, b.lnGamma, b.lnBeta, ws.normed)
        linear(ws.normed, inSize, b.w1, b.b1, h, ws.mid)
        for (j in 0 until h) if (ws.mid[j] < 0f) ws.mid[j] = 0f
        linear(ws.mid, h, b.w2, b.b2, h, ws.out)
        for (j in 0 until h) target[base + j] += ws.out[j]
    }

    private fun layerNorm(x: FloatArray, n: Int, gamma: Int, beta: Int, y: FloatArray) {
        var mean = 0f
        for (i in 0 until n) mean += x[i]
        mean /= n
        var variance = 0f
        for (i in 0 until n) {
            val d = x[i] - mean
            variance += d * d
        }
        variance /= n
        val inv = 1f / sqrt(variance + LN_EPS)
        val p = all
        for (i in 0 until n) y[i] = (x[i] - mean) * inv * p[gamma + i] + p[beta + i]
    }

    /** `y = Wt^T x + b` with `Wt` stored `[inSize][outSize]` at [w]. Skips zero inputs (post-ReLU). */
    private fun linear(x: FloatArray, inSize: Int, w: Int, b: Int, outSize: Int, y: FloatArray) {
        val p = all
        System.arraycopy(p, b, y, 0, outSize)
        for (i in 0 until inSize) {
            val xi = x[i]
            if (xi == 0f) continue
            val row = w + i * outSize
            for (j in 0 until outSize) y[j] += xi * p[row + j]
        }
    }

    companion object {
        /** "QNET" read as a little-endian int32. */
        const val MAGIC: Int = 0x54454E51
        private const val LN_EPS = 1e-5f

        /** The moves-left head predicts plies left divided by this. */
        const val MOVES_LEFT_SCALE: Float = 64f

        /** Line input state: 0 empty, 1..3 mine, 4..6 theirs, 7 dead. */
        fun lineState(mask: Long, mine: Long, theirs: Long): Int {
            val m = java.lang.Long.bitCount(mask and mine)
            val t = java.lang.Long.bitCount(mask and theirs)
            return when {
                m > 0 && t > 0 -> 7
                m > 0 -> minOf(m, 3)
                t > 0 -> 3 + minOf(t, 3)
                else -> 0
            }
        }

        fun load(stream: InputStream): QubicNet {
            val data = DataInputStream(stream.buffered())
            val header = ByteArray(20)
            data.readFully(header)
            val hb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            require(hb.int == MAGIC) { "not a QNET file" }
            val version = hb.int
            require(version == 1 || version == 2) { "unsupported QNET version $version" }
            val hidden = hb.int
            val layers = hb.int
            val count = hb.int
            val raw = ByteArray(count * 4)
            data.readFully(raw)
            val params = FloatArray(count)
            ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(params)
            return QubicNet(hidden, layers, params, hasMovesLeft = version >= 2)
        }
    }
}
