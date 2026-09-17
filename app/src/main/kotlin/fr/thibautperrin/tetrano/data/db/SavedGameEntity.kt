package fr.thibautperrin.tetrano.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A saved, not-yet-finished game.
 *
 * The legacy app (`SavedGame`/`DAOSavedGame`, see docs/PLAN.md P4) never
 * recorded which mode a game was saved from: [ListePartiesEnregistreesActivity]
 * let the player resume it as either 2-players or vs-computer regardless of
 * how it started. This entity keeps that behavior — there is deliberately
 * no "game mode" column.
 */
@Entity(tableName = "saved_games")
class SavedGameEntity(
    @PrimaryKey
    @ColumnInfo(name = "name")
    val name: String,
    /** Ring numbers in play order, one byte each (0..63). Ports `SavedGame.ringsPlayed`. */
    @ColumnInfo(name = "rings", typeAffinity = ColumnInfo.BLOB)
    val rings: ByteArray,
    @ColumnInfo(name = "user_begins")
    val userBegins: Boolean,
    @ColumnInfo(name = "user_color")
    val userColor: Int,
    @ColumnInfo(name = "adversary_color")
    val adversaryColor: Int,
    /** Epoch milliseconds; 0 for rows migrated from the legacy database, which didn't track this. */
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
) {
    // ByteArray breaks data-class-generated equals/hashCode (reference
    // comparison); this entity is compared by value in tests, so both are
    // implemented explicitly instead.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SavedGameEntity) return false
        return name == other.name &&
            rings.contentEquals(other.rings) &&
            userBegins == other.userBegins &&
            userColor == other.userColor &&
            adversaryColor == other.adversaryColor &&
            createdAt == other.createdAt
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + rings.contentHashCode()
        result = 31 * result + userBegins.hashCode()
        result = 31 * result + userColor
        result = 31 * result + adversaryColor
        result = 31 * result + createdAt.hashCode()
        return result
    }

    override fun toString(): String =
        "SavedGameEntity(name='$name', rings=${rings.toList()}, userBegins=$userBegins, " +
            "userColor=$userColor, adversaryColor=$adversaryColor, createdAt=$createdAt)"
}
