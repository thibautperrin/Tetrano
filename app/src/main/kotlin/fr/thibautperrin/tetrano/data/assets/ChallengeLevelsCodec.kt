package fr.thibautperrin.tetrano.data.assets

/**
 * Pure byte-parsing logic for the Challenge mode level file, kept separate
 * from [ChallengeLevelsDataSource]'s Android resource loading so it can be
 * unit-tested on the plain JVM against the real `challenge.bin` bytes
 * without needing a `Context`.
 *
 * Ports the raw-file half of `MemoryVarsChallenge` — see docs/GLOSSARY.md
 * and [ChallengeLevelsDataSource]'s doc for the byte layout.
 */
internal object ChallengeLevelsCodec {

    const val NUMBER_CHALLENGE_LEVELS: Int = 245
    const val BYTES_PER_LEVEL: Int = 14
    const val SIGNATURE_SIZE: Int = 13
    const val NUMBER_BYTES_CHALLENGE_FILE: Int = BYTES_PER_LEVEL * NUMBER_CHALLENGE_LEVELS

    fun signature(data: ByteArray, level: Int): ByteArray {
        requireValidLevel(level)
        val start = (level - 1) * BYTES_PER_LEVEL
        return data.copyOfRange(start, start + SIGNATURE_SIZE)
    }

    fun forkOrder(data: ByteArray, level: Int): Int {
        requireValidLevel(level)
        return lastByte(data, level) / 2 - 1
    }

    fun isInvertPlayer(data: ByteArray, level: Int): Boolean {
        requireValidLevel(level)
        return lastByte(data, level) % 2 == 0
    }

    private fun lastByte(data: ByteArray, level: Int): Int = data[level * BYTES_PER_LEVEL - 1].toInt()

    private fun requireValidLevel(level: Int) {
        require(level in 1..NUMBER_CHALLENGE_LEVELS) { "level $level not in 1..$NUMBER_CHALLENGE_LEVELS" }
    }
}
