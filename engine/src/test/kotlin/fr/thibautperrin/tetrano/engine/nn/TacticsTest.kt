package fr.thibautperrin.tetrano.engine.nn

import fr.thibautperrin.tetrano.engine.BoardTables
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TacticsTest {

    private fun bits(vararg cells: Int): Long = cells.fold(0L) { acc, c -> acc or (1L shl c) }

    @Test
    fun geometryIsQubic() {
        assertEquals(76, QubicGeometry.lineMasks.size)
        assertTrue(QubicGeometry.lineMasks.all { java.lang.Long.bitCount(it) == 4 })
        assertEquals(76, QubicGeometry.lineMasks.toSet().size)
        assertEquals(192, QubicGeometry.symmetryCount)
        assertEquals(16, (0 until 64).count { QubicGeometry.cellDegree(it) == 7 })
        assertEquals(48, (0 until 64).count { QubicGeometry.cellDegree(it) == 4 })
        // same lines as the legacy tables
        for (line in 0 until 76) {
            for (slot in 0 until 4) {
                val ring = BoardTables.lineRingAt(line, slot).toInt()
                assertTrue((QubicGeometry.lineMasks[line] ushr ring) and 1L != 0L)
            }
        }
    }

    @Test
    fun cellOrbitsUnderSymmetries() {
        val orbitSizes = (0 until 64).groupBy { c -> (0 until 192).minOf { QubicGeometry.symmetries[it][c] } }
            .values.map { it.size }.sorted()
        assertEquals(listOf(16, 48), orbitSizes)
    }

    @Test
    fun threatsAndWins() {
        // row y=0, z=0: cells 0,1,2,3
        val p = bits(0, 1, 2)
        assertEquals(bits(3), Tactics.threats(p, 0L))
        assertEquals(0L, Tactics.threats(p, bits(3)))
        assertTrue(Tactics.hasWon(p or bits(3)))
        assertTrue(Tactics.wonWith(p or bits(3), 3))
        assertFalse(Tactics.wonWith(p or bits(3), 5))
    }

    @Test
    fun vcfFindsDoubleThreat() {
        // p owns 1,2 (row 0-1-2-3) and 4,8 (column 0-4-8-12): playing 0 threatens both 3 and 12.
        val p = bits(1, 2, 4, 8)
        val o = bits(63, 62, 21, 37)
        val vcf = Vcf()
        assertEquals(0, vcf.solve(p, o, 10_000))
    }

    @Test
    fun vcfRespectsOpponentThreat() {
        val p = bits(1, 2, 4, 8)
        // opponent threatens to complete 48,49,50,51 at 51 -> p must block, and 51 creates nothing
        val o = bits(48, 49, 50)
        val vcf = Vcf()
        assertEquals(Vcf.NONE, vcf.solve(p, o, 10_000))
    }

    @Test
    fun vcfReturnsUnknownWhenBudgetRunsOut() {
        val p = bits(1, 2, 4, 8, 21, 42)
        val o = bits(63, 62)
        val result = Vcf().solve(p, o, 0)
        assertTrue(result == Vcf.UNKNOWN || result >= 0)
    }
}
