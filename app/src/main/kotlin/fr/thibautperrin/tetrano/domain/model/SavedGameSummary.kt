package fr.thibautperrin.tetrano.domain.model

/**
 * A saved, resumable game — the domain-facing shape of
 * [fr.thibautperrin.tetrano.data.db.SavedGameEntity], keeping Room out of
 * the domain/UI layers. Ports the legacy `SavedGame` bean (see
 * docs/GLOSSARY.md).
 */
data class SavedGameSummary(
    val name: String,
    val ringsPlayed: ByteArray,
    val userBegins: Boolean,
    val userColor: Int,
    val adversaryColor: Int,
    val createdAt: Long,
) {
    /** `matrix[ring]` is 0 (free), 1 or 2 (occupied by that player) — a static preview of the board. Ports `getMatrix`. */
    fun matrix(): ByteArray {
        val matrix = ByteArray(64)
        var player: Byte = if (userBegins) 1 else 2
        for (ring in ringsPlayed) {
            matrix[ring.toInt()] = player
            player = (3 - player).toByte()
        }
        return matrix
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SavedGameSummary) return false
        return name == other.name &&
            ringsPlayed.contentEquals(other.ringsPlayed) &&
            userBegins == other.userBegins &&
            userColor == other.userColor &&
            adversaryColor == other.adversaryColor &&
            createdAt == other.createdAt
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + ringsPlayed.contentHashCode()
        result = 31 * result + userBegins.hashCode()
        result = 31 * result + userColor
        result = 31 * result + adversaryColor
        result = 31 * result + createdAt.hashCode()
        return result
    }
}
