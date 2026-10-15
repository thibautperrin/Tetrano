package fr.thibautperrin.tetrano.ui.analysis

import fr.thibautperrin.tetrano.domain.service.FakeSavedGameDao
import fr.thibautperrin.tetrano.domain.service.SavedGameService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyseGamesViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun serviceWith(vararg names: String): SavedGameService {
        val service = SavedGameService(FakeSavedGameDao())
        for (name in names) service.save(name, byteArrayOf(0), userBegins = true, userColor = 1, adversaryColor = 2)
        return service
    }

    @Test
    fun listsGamesNewestFirst() = runTest {
        val viewModel = AnalyseGamesViewModel(serviceWith("b", "a"))
        assertEquals(listOf("a", "b"), viewModel.uiState.value.games.map { it.name })
    }

    @Test
    fun resultsComeFromTheMoves() = runTest {
        val service = SavedGameService(FakeSavedGameDao())
        // The first player (the user, who begins) completes 0-1-2-3.
        service.save("won", byteArrayOf(0, 60, 1, 61, 2, 62, 3), userBegins = true, userColor = 11, adversaryColor = 22)
        // Same, but the adversary began: the winner is the adversary.
        service.save("lost", byteArrayOf(0, 60, 1, 61, 2, 62, 3), userBegins = false, userColor = 11, adversaryColor = 22)
        service.save("open", byteArrayOf(0, 60), userBegins = true, userColor = 11, adversaryColor = 22)
        val games = AnalyseGamesViewModel(service).uiState.value.games.associateBy { it.name }
        assertEquals(GameResult.WON, games.getValue("won").result)
        assertEquals(11, games.getValue("won").winnerColor)
        assertEquals(22, games.getValue("lost").winnerColor)
        assertEquals(GameResult.UNFINISHED, games.getValue("open").result)
        assertEquals(2, games.getValue("open").moves)
    }

    @Test
    fun renameToATakenNameShowsTheErrorAndKeepsTheDialog() = runTest {
        val viewModel = AnalyseGamesViewModel(serviceWith("a", "b"))
        viewModel.onAction(AnalyseGamesAction.RenameClicked("a"))
        viewModel.onAction(AnalyseGamesAction.RenameDraftChanged("b"))
        viewModel.onAction(AnalyseGamesAction.RenameConfirmed)
        assertTrue(viewModel.uiState.value.renameError)
        assertEquals("a", viewModel.uiState.value.renameTarget)
    }

    @Test
    fun renameAndDelete() = runTest {
        val viewModel = AnalyseGamesViewModel(serviceWith("a", "b"))
        viewModel.onAction(AnalyseGamesAction.RenameClicked("a"))
        viewModel.onAction(AnalyseGamesAction.RenameDraftChanged("c"))
        viewModel.onAction(AnalyseGamesAction.RenameConfirmed)
        assertNull(viewModel.uiState.value.renameTarget)
        assertEquals(listOf("b", "c"), viewModel.uiState.value.games.map { it.name })
        viewModel.onAction(AnalyseGamesAction.DeleteClicked("b"))
        viewModel.onAction(AnalyseGamesAction.DeleteConfirmed)
        assertEquals(listOf("c"), viewModel.uiState.value.games.map { it.name })
    }
}
