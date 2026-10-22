package fr.thibautperrin.tetrano.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Challenge mode progress. Always a single row (`id = 0`) — ports the six
 * variables `MemoryVarsChallenge` used to keep in a flat binary file (see
 * docs/PLAN.md P4/P5). The legacy `numberStars` is gone: hints are free.
 */
@Entity(tableName = "challenge_progress")
class ChallengeProgressEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = SINGLETON_ID,
    /** 1..245, or 246 once every level has been passed. Ports `levelChallenge`. */
    @ColumnInfo(name = "level")
    val level: Int,
    /** 0..47, which board symmetry the current level's signature is read under. Ports `numberSymetrieChallenge`. */
    @ColumnInfo(name = "symmetry")
    val symmetry: Int,
    /** Rings played normally so far on the current level attempt, or null. Ports `normallyPlayedChallenge`. */
    @ColumnInfo(name = "normally_played", typeAffinity = ColumnInfo.BLOB)
    val normallyPlayed: ByteArray?,
    /**
     * Rings played via hints on the current level attempt, or null. Ports
     * `playedByStarsChallenge` — hints used to cost a star, hence the column
     * name, kept so the schema needs no migration.
     */
    @ColumnInfo(name = "played_by_stars", typeAffinity = ColumnInfo.BLOB)
    val playedByHints: ByteArray?,
    /** Whether the next failure on this level should show an ad prompt. Ports `nextTimeAdvert`. */
    @ColumnInfo(name = "next_time_advert")
    val nextTimeAdvert: Boolean,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChallengeProgressEntity) return false
        return id == other.id &&
            level == other.level &&
            symmetry == other.symmetry &&
            normallyPlayed.contentEqualsOrBothNull(other.normallyPlayed) &&
            playedByHints.contentEqualsOrBothNull(other.playedByHints) &&
            nextTimeAdvert == other.nextTimeAdvert
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + level
        result = 31 * result + symmetry
        result = 31 * result + (normallyPlayed?.contentHashCode() ?: 0)
        result = 31 * result + (playedByHints?.contentHashCode() ?: 0)
        result = 31 * result + nextTimeAdvert.hashCode()
        return result
    }

    override fun toString(): String =
        "ChallengeProgressEntity(id=$id, level=$level, symmetry=$symmetry, " +
            "normallyPlayed=${normallyPlayed?.toList()}, playedByHints=${playedByHints?.toList()}, " +
            "nextTimeAdvert=$nextTimeAdvert)"

    private fun ByteArray?.contentEqualsOrBothNull(other: ByteArray?): Boolean =
        if (this == null || other == null) this == null && other == null else contentEquals(other)

    companion object {
        const val SINGLETON_ID: Int = 0
    }
}
