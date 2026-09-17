package fr.thibautperrin.tetrano.domain.service

import fr.thibautperrin.tetrano.data.db.SavedGameDao
import fr.thibautperrin.tetrano.data.db.SavedGameEntity
import fr.thibautperrin.tetrano.domain.model.SavedGameSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedGameServiceTest {

    private class FakeSavedGameDao : SavedGameDao {
        private val games = MutableStateFlow<Map<String, SavedGameEntity>>(emptyMap())

        override fun observeAll(): Flow<List<SavedGameEntity>> = games.map { it.values.toList() }

        override suspend fun getByName(name: String): SavedGameEntity? = games.value[name]
        override suspend fun getAllNames(): List<String> = games.value.keys.toList()

        override suspend fun insert(game: SavedGameEntity): Long {
            if (games.value.containsKey(game.name)) return -1L
            games.value = games.value + (game.name to game)
            return 1L
        }

        override suspend fun rename(previousName: String, newName: String): Int {
            val existing = games.value[previousName] ?: return 0
            if (games.value.containsKey(newName)) return 0
            games.value = (games.value - previousName) + (newName to existing)
            return 1
        }

        override suspend fun delete(name: String): Int {
            if (!games.value.containsKey(name)) return 0
            games.value = games.value - name
            return 1
        }
    }

    @Test
    fun savesUnderTheGivenNameWhenFree() = runTest {
        val service = SavedGameService(FakeSavedGameDao())
        val name = service.save("My game", byteArrayOf(0, 1, 2), userBegins = true, userColor = 1, adversaryColor = 2)
        assertEquals("My game", name)
        assertEquals(listOf<Byte>(0, 1, 2), service.get("My game")?.ringsPlayed?.toList())
    }

    @Test
    fun renamesWithIncrementingSuffixOnConflict() = runTest {
        val service = SavedGameService(FakeSavedGameDao())
        service.save("Game", byteArrayOf(0), userBegins = true, userColor = 1, adversaryColor = 2)
        val second = service.save("Game", byteArrayOf(1), userBegins = true, userColor = 1, adversaryColor = 2)
        val third = service.save("Game", byteArrayOf(2), userBegins = true, userColor = 1, adversaryColor = 2)

        assertEquals("Game (2)", second)
        assertEquals("Game (3)", third)
    }

    @Test
    fun renameFailsIfNewNameTaken() = runTest {
        val service = SavedGameService(FakeSavedGameDao())
        service.save("A", byteArrayOf(0), userBegins = true, userColor = 1, adversaryColor = 2)
        service.save("B", byteArrayOf(1), userBegins = true, userColor = 1, adversaryColor = 2)
        assertTrue(!service.rename("A", "B"))
        assertEquals(listOf<Byte>(0), service.get("A")?.ringsPlayed?.toList())
    }

    @Test
    fun deleteRemovesTheGame() = runTest {
        val service = SavedGameService(FakeSavedGameDao())
        service.save("A", byteArrayOf(0), userBegins = true, userColor = 1, adversaryColor = 2)
        assertTrue(service.delete("A"))
        assertNull(service.get("A"))
    }

    @Test
    fun nameLongerThan100CharsIsTruncated() = runTest {
        val service = SavedGameService(FakeSavedGameDao())
        val longName = "x".repeat(150)
        val saved = service.save(longName, byteArrayOf(0), userBegins = true, userColor = 1, adversaryColor = 2)
        assertEquals(100, saved.length)
    }

    @Test
    fun matrixPlacesRingsAlternatingFromStartingPlayer() {
        val summary = SavedGameSummary(
            name = "x",
            ringsPlayed = byteArrayOf(5, 10, 15),
            userBegins = false,
            userColor = 1,
            adversaryColor = 2,
            createdAt = 0,
        )
        val matrix = summary.matrix()
        assertEquals(2, matrix[5].toInt())
        assertEquals(1, matrix[10].toInt())
        assertEquals(2, matrix[15].toInt())
    }
}
