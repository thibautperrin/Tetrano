package fr.thibautperrin.tetrano.domain.service

import fr.thibautperrin.tetrano.domain.model.SavedGameSummary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedGameServiceTest {

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
    fun renameKeepsTheGameAndItsPosition() = runTest {
        val service = SavedGameService(FakeSavedGameDao())
        service.save("A", byteArrayOf(0), userBegins = true, userColor = 1, adversaryColor = 2)
        service.save("B", byteArrayOf(1), userBegins = true, userColor = 1, adversaryColor = 2)
        assertTrue(service.rename("A", "C"))
        assertEquals(listOf("C", "B"), service.observeAll().first().map { it.name })
        assertEquals(listOf<Byte>(0), service.get("C")?.ringsPlayed?.toList())
    }

    @Test
    fun renameToABlankNameFails() = runTest {
        val service = SavedGameService(FakeSavedGameDao())
        service.save("A", byteArrayOf(0), userBegins = true, userColor = 1, adversaryColor = 2)
        assertFalse(service.rename("A", "  "))
        assertEquals("A", service.get("A")?.name)
    }

    @Test
    fun renameToTheSameNameSucceeds() = runTest {
        val service = SavedGameService(FakeSavedGameDao())
        service.save("A", byteArrayOf(0), userBegins = true, userColor = 1, adversaryColor = 2)
        assertTrue(service.rename("A", "A"))
    }

    @Test
    fun observeAllKeepsInsertionOrder() = runTest {
        val service = SavedGameService(FakeSavedGameDao())
        for (name in listOf("b", "a", "c")) {
            service.save(name, byteArrayOf(0), userBegins = true, userColor = 1, adversaryColor = 2)
        }
        assertEquals(listOf("b", "a", "c"), service.observeAll().first().map { it.name })
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
