package fr.thibautperrin.tetrano.engine.nn

import java.io.DataInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * The Tetrano value/policy network: message passing on the cell <-> line
 * hypergraph, with weights shared by every cell and every line, so its
 * outputs are exactly invariant (value) or equivariant (policy) under the
 * 192 board automorphisms. Trained by `tools/nn` (PyTorch, `model.py`),
 * which defines the reference forward pass this class mirrors.
 *
 * Versions 1 and 2 (`QubicNet` in model.py) run on every cell and line. Inputs, from the point of
 * view of the player to move ("mine"):
 * - per cell: state {empty, mine, theirs} and degree class {4 lines, 7 lines};
 * - per line: {empty, mine 1/2/3, theirs 1/2/3, dead (both colours)};
 * - "I am the first player" (equal ring counts).
 * Version 2 adds a third head, the number of plies left until the end of
 * the game ([Workspace.movesLeft]); version 1 files (no such head) still
 * load, with 0 plies left.
 *
 * Version 3 (`QubicNetV3` in model.py) runs on the free cells and the live lines only (a line
 * holding both colours can never be completed; an occupied cell acts only through its live
 * lines), so it gets cheaper as the game goes on; it may have a Q head ([Workspace.q]: the
 * mover's value after each move) and a global context every few layers.
 *
 * File format (little endian): int32 magic [MAGIC], int32 version (1, 2 or 3),
 * int32 H, int32 L, int32 parameter count, [version 3: int32 flags (1: Q head),
 * int32 global context period (0: none)], then float32 parameters in the
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
    val version: Int,
    /** True iff the network has a Q head (version 3 only): [Workspace.q] is then filled. */
    val hasQHead: Boolean = false,
    private val globalEvery: Int = 0,
) {
    /** True iff the file has the moves-left head (version 2 or later). */
    val hasMovesLeft: Boolean = version >= 2
    private val v3 = version >= 3

    private var offset = 0
    private val all = params

    private fun take(size: Int): Int {
        val start = offset
        offset += size
        return start
    }

    private val h = hidden
    private val valueIn = if (v3) 3 * h + 3 else 3 * h + 1

    // Version 1-2 embeddings (cell states); version 3 only has degree, first and lines.
    private val cellEmbed = if (v3) 0 else take(3 * h)
    private val degreeEmbed = take(2 * h)
    private val firstEmbed = take(h)
    private val lineEmbed = take(8 * h)

    private class Block(
        val inSize: Int, val lnGamma: Int, val lnBeta: Int, val w1: Int, val b1: Int, val w2: Int, val b2: Int,
    )

    private fun block(inSize: Int): Block = Block(
        inSize = inSize, lnGamma = take(inSize), lnBeta = take(inSize),
        w1 = take(inSize * h), b1 = take(h), w2 = take(h * h), b2 = take(h),
    )

    private val lineBlocks = arrayOfNulls<Block>(layers)
    private val cellBlocks = arrayOfNulls<Block>(layers)

    /** Version 3: (weights, bias) of the global context layer after layer k, or -1. */
    private val globalW = IntArray(layers) { -1 }
    private val globalB = IntArray(layers) { -1 }

    init {
        for (k in 0 until layers) {
            lineBlocks[k] = block(2 * h)
            cellBlocks[k] = block(if (v3) 3 * h else 2 * h)
            if (v3 && globalEvery > 0 && (k + 1) % globalEvery == 0) {
                globalW[k] = take(2 * h * h)
                globalB[k] = take(h)
            }
        }
    }

    private val polLnGamma = take(h)
    private val polLnBeta = take(h)
    private val polW1 = take(h * h)
    private val polB1 = take(h)
    private val polW2 = take(h)
    private val polB2 = take(1)
    private val qLnGamma = if (hasQHead) take(h) else 0
    private val qLnBeta = if (hasQHead) take(h) else 0
    private val qW1 = if (hasQHead) take(h * h) else 0
    private val qB1 = if (hasQHead) take(h) else 0
    private val qW2 = if (hasQHead) take(h) else 0
    private val qB2 = if (hasQHead) take(1) else 0
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
        internal val input = FloatArray(maxOf(3 * h, valueIn))
        internal val normed = FloatArray(maxOf(3 * h, valueIn))
        internal val mid = FloatArray(h)
        internal val out = FloatArray(h)
        internal val freeCells = IntArray(QubicGeometry.CELLS)
        internal val liveLines = IntArray(QubicGeometry.LINES)
        internal val liveCount = IntArray(QubicGeometry.CELLS)
        internal val lineLive = BooleanArray(QubicGeometry.LINES)

        /** Plies left until the end of the game, as estimated by the last [evaluate] (0 for a version-1 network). */
        var movesLeft: Float = 0f
            internal set

        /** With a Q head: the mover's value (-1..1) after each free cell, from the last [evaluate] (0 for occupied cells). */
        val q: FloatArray = FloatArray(QubicGeometry.CELLS)
    }

    fun newWorkspace(): Workspace = Workspace()

    /**
     * Evaluates the position where the player to move owns [mine] and the
     * opponent [theirs]. Fills [policyLogits] (64 raw logits, one per cell,
     * occupied cells included — mask them) and [wdl] (win, draw, loss
     * probabilities for the player to move).
     */
    fun evaluate(mine: Long, theirs: Long, ws: Workspace, policyLogits: FloatArray, wdl: FloatArray) {
        if (v3) evaluateV3(mine, theirs, ws, policyLogits, wdl) else evaluateV2(mine, theirs, ws, policyLogits, wdl)
    }

    private fun evaluateV2(mine: Long, theirs: Long, ws: Workspace, policyLogits: FloatArray, wdl: FloatArray) {
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
                residualMlp(lb, ws, line, base)
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
                residualMlp(cb2, ws, cell, base)
            }
        }

        // Policy head: one logit per cell.
        for (c in 0 until QubicGeometry.CELLS) policyLogits[c] = cellHead(ws, c, polLnGamma, polLnBeta, polW1, polB1, polW2, polB2)

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
        valueHeads(ws, wdl)
    }

    private fun evaluateV3(mine: Long, theirs: Long, ws: Workspace, policyLogits: FloatArray, wdl: FloatArray) {
        val p = all
        val cell = ws.cell
        val line = ws.line
        val input = ws.input
        val first = java.lang.Long.bitCount(mine) == java.lang.Long.bitCount(theirs)
        val occupied = mine or theirs

        // Live lines (not holding both colours), free cells, live lines per free cell.
        var nl = 0
        for (l in 0 until QubicGeometry.LINES) {
            val mask = QubicGeometry.lineMasks[l]
            val state = lineState(mask, mine, theirs)
            val live = state != 7
            ws.lineLive[l] = live
            if (live) {
                ws.liveLines[nl++] = l
                System.arraycopy(p, lineEmbed + state * h, line, l * h, h)
            }
        }
        var nf = 0
        for (c in 0 until QubicGeometry.CELLS) {
            if ((occupied ushr c) and 1L != 0L) continue
            ws.freeCells[nf++] = c
            val degree = QubicGeometry.cellDegree(c)
            var live = 0
            for (i in 0 until degree) if (ws.lineLive[QubicGeometry.cellLine(c, i)]) live++
            ws.liveCount[c] = live
            val d = degreeEmbed + (if (degree == 7) 1 else 0) * h
            val base = c * h
            for (j in 0 until h) cell[base + j] = p[d + j] + (if (first) p[firstEmbed + j] else 0f)
        }

        for (k in 0 until layers) {
            val lb = lineBlocks[k]!!
            for (i in 0 until nl) {
                val l = ws.liveLines[i]
                val base = l * h
                System.arraycopy(line, base, input, 0, h)
                java.util.Arrays.fill(input, h, 2 * h, 0f)
                var bits = QubicGeometry.lineMasks[l] and occupied.inv()
                while (bits != 0L) {
                    val c = java.lang.Long.numberOfTrailingZeros(bits)
                    bits = bits and (bits - 1)
                    val cb = c * h
                    for (j in 0 until h) input[h + j] += cell[cb + j]
                }
                residualMlp(lb, ws, line, base)
            }
            val cb2 = cellBlocks[k]!!
            for (i in 0 until nf) {
                val c = ws.freeCells[i]
                val base = c * h
                System.arraycopy(cell, base, input, 0, h)
                java.util.Arrays.fill(input, h, 3 * h, 0f)
                val degree = QubicGeometry.cellDegree(c)
                for (d in 0 until degree) {
                    val l = QubicGeometry.cellLine(c, d)
                    if (!ws.lineLive[l]) continue
                    val lbase = l * h
                    for (j in 0 until h) input[h + j] += line[lbase + j]
                }
                val inv = 1f / maxOf(1, ws.liveCount[c])
                for (j in 0 until h) {
                    val s = input[h + j]
                    input[h + j] = s * 0.25f
                    input[2 * h + j] = s * inv
                }
                residualMlp(cb2, ws, cell, base)
            }
            if (globalW[k] >= 0) {
                // Global context: mean and max of the free cells, projected, added to each.
                for (j in 0 until h) {
                    var sum = 0f
                    var max = -1e4f
                    for (i in 0 until nf) {
                        val v = cell[ws.freeCells[i] * h + j]
                        sum += v
                        if (v > max) max = v
                    }
                    input[j] = sum / maxOf(1, nf)
                    input[h + j] = max
                }
                linear(input, 2 * h, globalW[k], globalB[k], h, ws.out)
                for (i in 0 until nf) {
                    val base = ws.freeCells[i] * h
                    for (j in 0 until h) cell[base + j] += ws.out[j]
                }
            }
        }

        java.util.Arrays.fill(policyLogits, 0f)
        java.util.Arrays.fill(ws.q, 0f)
        for (i in 0 until nf) {
            val c = ws.freeCells[i]
            policyLogits[c] = cellHead(ws, c, polLnGamma, polLnBeta, polW1, polB1, polW2, polB2)
            if (hasQHead) ws.q[c] = tanh(cellHead(ws, c, qLnGamma, qLnBeta, qW1, qB1, qW2, qB2))
        }

        // Value head: [mean free cells, max free cells, mean live lines, first, free / 64, live / 76].
        for (j in 0 until h) {
            var sum = 0f
            var max = -1e4f
            for (i in 0 until nf) {
                val v = cell[ws.freeCells[i] * h + j]
                sum += v
                if (v > max) max = v
            }
            var lineSum = 0f
            for (i in 0 until nl) lineSum += line[ws.liveLines[i] * h + j]
            input[j] = sum / maxOf(1, nf)
            input[h + j] = max
            input[2 * h + j] = lineSum / maxOf(1, nl)
        }
        input[3 * h] = if (first) 1f else 0f
        input[3 * h + 1] = nf / 64f
        input[3 * h + 2] = nl / 76f
        valueHeads(ws, wdl)
    }

    /** A per-cell head `W2 · relu(W1 · LN(cell) + b1) + b2` (scalar) on cell [c]. */
    private fun cellHead(ws: Workspace, c: Int, lnG: Int, lnB: Int, w1: Int, b1: Int, w2: Int, b2: Int): Float {
        System.arraycopy(ws.cell, c * h, ws.input, 0, h)
        layerNorm(ws.input, h, lnG, lnB, ws.normed)
        linear(ws.normed, h, w1, b1, h, ws.mid)
        val p = all
        var logit = p[b2]
        for (j in 0 until h) {
            val v = ws.mid[j]
            if (v > 0f) logit += v * p[w2 + j]
        }
        return logit
    }

    /** WDL and moves-left heads on the pooled vector in `ws.input[0 until valueIn]`. */
    private fun valueHeads(ws: Workspace, wdl: FloatArray) {
        val p = all
        val input = ws.input
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
    private fun residualMlp(b: Block, ws: Workspace, target: FloatArray, base: Int) {
        layerNorm(ws.input, b.inSize, b.lnGamma, b.lnBeta, ws.normed)
        linear(ws.normed, b.inSize, b.w1, b.b1, h, ws.mid)
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
            require(version in 1..3) { "unsupported QNET version $version" }
            val hidden = hb.int
            val layers = hb.int
            val count = hb.int
            var flags = 0
            var globalEvery = 0
            if (version >= 3) {
                val extra = ByteArray(8)
                data.readFully(extra)
                val eb = ByteBuffer.wrap(extra).order(ByteOrder.LITTLE_ENDIAN)
                flags = eb.int
                globalEvery = eb.int
            }
            val raw = ByteArray(count * 4)
            data.readFully(raw)
            val params = FloatArray(count)
            ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(params)
            return QubicNet(hidden, layers, params, version, hasQHead = flags and 1 != 0, globalEvery = globalEvery)
        }
    }
}
