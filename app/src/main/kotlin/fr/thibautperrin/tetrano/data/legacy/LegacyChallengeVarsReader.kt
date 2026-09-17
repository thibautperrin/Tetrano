package fr.thibautperrin.tetrano.data.legacy

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * The 6 variables `MemoryVarsChallenge` kept in `challengeVars.bin`
 * (`levelChallenge`, `numberStars`, `numberSymetrieChallenge`,
 * `nextTimeAdvert`, `normallyPlayedChallenge`, `playedByStarsChallenge`,
 * in that order — see docs/GLOSSARY.md). Read once during the one-time
 * legacy import (docs/PLAN.md P5); nothing writes this format again.
 */
data class LegacyChallengeVars(
    val level: Int,
    val numberStars: Int,
    val symmetry: Int,
    val nextTimeAdvert: Boolean,
    val normallyPlayed: ByteArray?,
    val playedByStars: ByteArray?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LegacyChallengeVars) return false
        return level == other.level &&
            numberStars == other.numberStars &&
            symmetry == other.symmetry &&
            nextTimeAdvert == other.nextTimeAdvert &&
            normallyPlayed.contentEqualsOrBothNull(other.normallyPlayed) &&
            playedByStars.contentEqualsOrBothNull(other.playedByStars)
    }

    override fun hashCode(): Int {
        var result = level
        result = 31 * result + numberStars
        result = 31 * result + symmetry
        result = 31 * result + nextTimeAdvert.hashCode()
        result = 31 * result + (normallyPlayed?.contentHashCode() ?: 0)
        result = 31 * result + (playedByStars?.contentHashCode() ?: 0)
        return result
    }

    private fun ByteArray?.contentEqualsOrBothNull(other: ByteArray?): Boolean =
        if (this == null || other == null) this == null && other == null else contentEquals(other)
}

class LegacyChallengeVarsReader @Inject constructor(@param:ApplicationContext private val context: Context) {

    /** Reads `challengeVars.bin`, or null if it doesn't exist (a fresh install, or already imported). */
    fun read(): LegacyChallengeVars? {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return null
        return context.openFileInput(FILE_NAME).use { input ->
            LegacyChallengeVars(
                level = LegacyBinaryIO.readInt(input),
                numberStars = LegacyBinaryIO.readInt(input),
                symmetry = LegacyBinaryIO.readByte(input).toInt(),
                nextTimeAdvert = LegacyBinaryIO.readBool(input),
                normallyPlayed = LegacyBinaryIO.readByteArray(input),
                playedByStars = LegacyBinaryIO.readByteArray(input),
            )
        }
    }

    fun delete() {
        File(context.filesDir, FILE_NAME).delete()
    }

    companion object {
        // Matches StaticFields.FILE_CHALLENGE_VARS exactly.
        const val FILE_NAME: String = "challengeVars.bin"
    }
}
