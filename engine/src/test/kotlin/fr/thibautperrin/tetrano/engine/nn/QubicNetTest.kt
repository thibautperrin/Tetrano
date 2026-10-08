package fr.thibautperrin.tetrano.engine.nn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Parity with the PyTorch reference (`tools/nn/model.py`): the fixture
 * network and positions are exported by
 * `python export.py --random --hidden 16 --layers 2 --out fixture_net.bin --fixture fixture_positions.bin`.
 */
class QubicNetTest {

    private fun resource(name: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/nn/$name")) { "missing /nn/$name" }.use { it.readBytes() }

    private val net = QubicNet.load(resource("fixture_net.bin").inputStream())

    @Test
    fun matchesPyTorchReference() {
        val buffer = ByteBuffer.wrap(resource("fixture_positions.bin")).order(ByteOrder.LITTLE_ENDIAN)
        val count = buffer.int
        val ws = net.newWorkspace()
        val policy = FloatArray(64)
        val wdl = FloatArray(3)
        var maxError = 0f
        repeat(count) {
            val mine = buffer.long
            val theirs = buffer.long
            net.evaluate(mine, theirs, ws, policy, wdl)
            for (c in 0 until 64) maxError = maxOf(maxError, abs(policy[c] - buffer.float))
            for (o in 0 until 3) maxError = maxOf(maxError, abs(wdl[o] - buffer.float))
        }
        assertTrue("max error $maxError", maxError < 1e-4f)
    }

    @Test
    fun outputsAreInvariantUnderAllSymmetries() {
        val mine = (1L shl 0) or (1L shl 21) or (1L shl 42) or (1L shl 5)
        val theirs = (1L shl 63) or (1L shl 1) or (1L shl 17)
        val ws = net.newWorkspace()
        val policy = FloatArray(64)
        val wdl = FloatArray(3)
        net.evaluate(mine, theirs, ws, policy, wdl)
        val policyS = FloatArray(64)
        val wdlS = FloatArray(3)
        for (s in 0 until QubicGeometry.symmetryCount) {
            net.evaluate(QubicGeometry.transform(s, mine), QubicGeometry.transform(s, theirs), ws, policyS, wdlS)
            for (o in 0 until 3) assertEquals(wdl[o], wdlS[o], 1e-5f)
            for (c in 0 until 64) assertEquals(policy[c], policyS[QubicGeometry.symmetries[s][c]], 1e-4f)
        }
    }
}
