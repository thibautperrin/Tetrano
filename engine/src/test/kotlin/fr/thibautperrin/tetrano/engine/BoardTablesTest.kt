package fr.thibautperrin.tetrano.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ports the legacy `StatiqueTest` (see docs/GLOSSARY.md). */
class BoardTablesTest {

    @Test
    fun testRingBelongsToLine() {
        for (ring in 0 until 64) {
            for (line in 0 until 76) {
                val belongs = BoardTables.ringBelongsToLine(ring, line)
                val rings = BoardTables.copyLineRings(line)
                val actuallyIn = rings.any { it.toInt() == ring }
                assertEquals("ring=$ring line=$line", actuallyIn, belongs)
            }
        }
    }

    @Test
    fun testRingLines() {
        for (ring in 0 until 64) {
            val count = BoardTables.ringLineCount(ring)
            assertTrue(count == 4 || count == 7)
            for (i in 0 until count) {
                val line = BoardTables.ringLineAt(ring, i).toInt()
                val rings = BoardTables.copyLineRings(line)
                assertTrue(rings.any { it.toInt() == ring })
            }
        }
        for (line in 0 until 76) {
            for (slot in 0 until 4) {
                val ring = BoardTables.lineRingAt(line, slot).toInt()
                val count = BoardTables.ringLineCount(ring)
                var found = false
                for (i in 0 until count) {
                    if (BoardTables.ringLineAt(ring, i).toInt() == line) found = true
                }
                assertTrue("line=$line ring=$ring", found)
            }
        }
    }

    @Test
    fun testSymmetryIsInvertible() {
        for (symmetry in 0 until 48) {
            for (ring in 0 until 64) {
                val forward = BoardTables.symmetricRing(ring.toByte(), symmetry.toByte())
                val back = BoardTables.inverseSymmetricRing(forward, symmetry.toByte())
                assertEquals(ring.toByte(), back)
            }
        }
    }
}
