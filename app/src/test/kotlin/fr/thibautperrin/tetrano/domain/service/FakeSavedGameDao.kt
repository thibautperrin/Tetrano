package fr.thibautperrin.tetrano.domain.service

import fr.thibautperrin.tetrano.data.db.SavedGameDao
import fr.thibautperrin.tetrano.data.db.SavedGameEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [SavedGameDao]: insertion-ordered like the real `ORDER BY rowid`
 * (a rename keeps the game's position), IGNORE on a duplicate insert, and
 * — like the real raw `UPDATE` — throws on a rename collision.
 */
internal class FakeSavedGameDao : SavedGameDao {
    private val games = MutableStateFlow<List<SavedGameEntity>>(emptyList())

    override fun observeAll(): Flow<List<SavedGameEntity>> = games.map { it.toList() }

    override suspend fun getByName(name: String): SavedGameEntity? = games.value.firstOrNull { it.name == name }

    override suspend fun getAllNames(): List<String> = games.value.map { it.name }

    override suspend fun insert(game: SavedGameEntity): Long {
        if (getByName(game.name) != null) return -1L
        games.value = games.value + game
        return games.value.size.toLong()
    }

    override suspend fun rename(previousName: String, newName: String): Int {
        val existing = getByName(previousName) ?: return 0
        if (newName != previousName && getByName(newName) != null) {
            throw IllegalStateException("UNIQUE constraint failed: saved_games.name")
        }
        games.value = games.value.map {
            if (it === existing) {
                SavedGameEntity(newName, it.rings, it.userBegins, it.userColor, it.adversaryColor, it.createdAt)
            } else {
                it
            }
        }
        return 1
    }

    override suspend fun delete(name: String): Int {
        if (getByName(name) == null) return 0
        games.value = games.value.filterNot { it.name == name }
        return 1
    }
}
