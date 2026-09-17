package fr.thibautperrin.tetrano.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ports the legacy `EnsembleTest` (see docs/GLOSSARY.md). */
class LineSetTest {

    // Named distinctly from the LineSet member functions (which take/return
    // Byte) so overload resolution can't silently pick the Byte overload
    // and hand assertEquals a boxed Byte to compare against a boxed Int.
    private fun LineSet.containsInt(b: Int): Boolean = contains(b.toByte())
    private fun LineSet.addInt(b: Int) = add(b.toByte())
    private fun LineSet.removeInt(b: Int) = remove(b.toByte())
    private fun LineSet.nextInt(b: Int): Int = next(b.toByte()).toInt()

    @Test
    fun testLineSet() {
        val set = LineSet(76, false)
        assertEquals(0, set.length())
        assertFalse(set.containsInt(5))
        set.addInt(5)
        assertEquals(1, set.length())
        assertTrue(set.containsInt(5))
        assertEquals(5, set.nextInt(-1))
        assertEquals(-1, set.nextInt(5))
        set.removeInt(5)
        assertEquals(0, set.length())
        assertFalse(set.containsInt(5))
        assertEquals(-1, set.nextInt(-1))
        set.addInt(5)
        assertEquals(1, set.length())
        assertEquals(5, set.nextInt(-1))
        assertEquals(-1, set.nextInt(5))
        set.addInt(9)
        assertEquals(2, set.length())
        assertTrue(set.containsInt(9))
        assertEquals(9, set.nextInt(-1))
        assertEquals(5, set.nextInt(9))
        assertEquals(-1, set.nextInt(5))
        set.removeInt(9)
        assertEquals(1, set.length())
        assertEquals(5, set.nextInt(-1))
        assertEquals(-1, set.nextInt(5))
        set.addInt(9)
        assertEquals(2, set.length())
        assertEquals(9, set.nextInt(-1))
        assertEquals(5, set.nextInt(9))
        assertEquals(-1, set.nextInt(5))
        set.removeInt(5)
        assertEquals(1, set.length())
        assertEquals(9, set.nextInt(-1))
        assertEquals(-1, set.nextInt(9))
        set.removeInt(9)
        assertEquals(0, set.length())
        assertEquals(-1, set.nextInt(-1))

        set.addInt(1)
        set.addInt(2)
        set.addInt(3)
        set.removeInt(2)
        assertEquals(3, set.nextInt(-1))
        assertEquals(1, set.nextInt(3))
        assertEquals(-1, set.nextInt(1))
        assertEquals(2, set.length())

        set.clear()
        assertEquals(0, set.length())
        assertEquals(-1, set.nextInt(-1))
    }
}
