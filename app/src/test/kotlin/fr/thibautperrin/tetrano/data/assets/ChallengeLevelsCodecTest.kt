package fr.thibautperrin.tetrano.data.assets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Validates [ChallengeLevelsCodec]'s byte parsing against the real,
 * unmodified `challenge.bin` (see docs/PLAN.md P0/P4) — a plain JVM test,
 * since the codec takes a [ByteArray] rather than an Android `Context`.
 */
class ChallengeLevelsCodecTest {

    @Test
    fun fileHasExpectedSize() {
        assertEquals(ChallengeLevelsCodec.NUMBER_BYTES_CHALLENGE_FILE, data.size)
    }

    @Test
    fun everyLevelHasA13ByteSignatureAndAValidForkOrder() {
        for (level in 1..ChallengeLevelsCodec.NUMBER_CHALLENGE_LEVELS) {
            val signature = ChallengeLevelsCodec.signature(data, level)
            assertEquals("level $level", ChallengeLevelsCodec.SIGNATURE_SIZE, signature.size)

            // Ports Challenge.getAuthorizedRings() == forkOrder + 2, which
            // must be at least 1 for the level to be playable at all.
            val forkOrder = ChallengeLevelsCodec.forkOrder(data, level)
            assertTrue("level $level forkOrder=$forkOrder", forkOrder + 2 >= 1)
        }
    }

    @Test
    fun signatureBytesComeFromTheRightOffset() {
        // Level 1's signature is the file's first 13 bytes; level 2's the
        // next 13 (byte 13 of level 1's 14-byte block is its fork/invert
        // byte, skipped).
        assertEquals(data.copyOfRange(0, 13).toList(), ChallengeLevelsCodec.signature(data, 1).toList())
        assertEquals(data.copyOfRange(14, 27).toList(), ChallengeLevelsCodec.signature(data, 2).toList())
    }

    @Test
    fun forkOrderAndInvertPlayerAgreeOnTheSameByte() {
        // Both are decoded from data[level * 14 - 1]; forkOrder * 2 + 2 (or
        // +1 depending on parity) must reconstruct that same byte's value,
        // proving the two accessors read the same underlying byte
        // consistently rather than by coincidence.
        for (level in 1..ChallengeLevelsCodec.NUMBER_CHALLENGE_LEVELS) {
            val lastByte = data[level * ChallengeLevelsCodec.BYTES_PER_LEVEL - 1].toInt()
            val forkOrder = ChallengeLevelsCodec.forkOrder(data, level)
            val invert = ChallengeLevelsCodec.isInvertPlayer(data, level)
            assertEquals("level $level", lastByte % 2 == 0, invert)
            assertEquals("level $level", lastByte / 2 - 1, forkOrder)
        }
    }

    companion object {
        private lateinit var data: ByteArray

        @BeforeClass
        @JvmStatic
        fun loadChallengeBin() {
            data = requireNotNull(
                ChallengeLevelsCodecTest::class.java.classLoader?.getResourceAsStream("challenge.bin"),
            ) { "challenge.bin missing from test resources" }.use { it.readBytes() }
        }
    }
}
