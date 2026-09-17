package fr.thibautperrin.tetrano.data.legacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random

/**
 * Ports the legacy `BinaryFileReadingWritingTest` (see docs/GLOSSARY.md),
 * using in-memory streams instead of real files opened via an Android
 * `Context` — [LegacyBinaryIO] operates on plain `InputStream`/
 * `OutputStream`, so this runs as a plain JVM test.
 */
class LegacyBinaryIOTest {

    @Test
    fun testInt() {
        val random = Random(0)
        val values = IntArray(2000) { random.nextInt() }
        val output = ByteArrayOutputStream()
        for (value in values) {
            LegacyBinaryIO.writeInt(output, value)
        }
        val input = ByteArrayInputStream(output.toByteArray())
        for (value in values) {
            assertEquals(value, LegacyBinaryIO.readInt(input))
        }
    }

    @Test
    fun testBool() {
        val output = ByteArrayOutputStream()
        LegacyBinaryIO.writeBool(output, true)
        LegacyBinaryIO.writeBool(output, false)
        val input = ByteArrayInputStream(output.toByteArray())
        assertTrue(LegacyBinaryIO.readBool(input))
        assertFalse(LegacyBinaryIO.readBool(input))
    }

    @Test
    fun testByteArray() {
        val tab1 = ByteArray(200) { it.toByte() }
        val tab2 = ByteArray(56) { (200 + it).toByte() }
        for (b in tab2) assertTrue(b < 0)

        val output = ByteArrayOutputStream()
        LegacyBinaryIO.writeByteArray(output, tab1)
        LegacyBinaryIO.writeByteArray(output, null)
        LegacyBinaryIO.writeByteArray(output, tab2)

        val input = ByteArrayInputStream(output.toByteArray())
        assertArrayEqualsOrBothNull(tab1, LegacyBinaryIO.readByteArray(input))
        assertNull(LegacyBinaryIO.readByteArray(input))
        assertArrayEqualsOrBothNull(tab2, LegacyBinaryIO.readByteArray(input))
    }

    @Test
    fun testByte() {
        val tab = ByteArray(256) { it.toByte() }
        val output = ByteArrayOutputStream()
        for (b in tab) LegacyBinaryIO.writeByte(output, b)
        val input = ByteArrayInputStream(output.toByteArray())
        for (b in tab) assertEquals(b, LegacyBinaryIO.readByte(input))
    }

    private fun assertArrayEqualsOrBothNull(expected: ByteArray?, actual: ByteArray?) {
        if (expected == null || actual == null) {
            assertEquals(expected, actual)
        } else {
            assertTrue(expected.contentEquals(actual))
        }
    }
}
