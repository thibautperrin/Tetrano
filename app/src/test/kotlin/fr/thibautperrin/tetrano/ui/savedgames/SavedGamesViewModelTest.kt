package fr.thibautperrin.tetrano.ui.savedgames

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SavedGamesViewModelTest {

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
    fun listsNamesInInsertionOrder() = runTest {
        val viewModel = SavedGamesViewModel(serviceWith("b", "a"))
        assertEquals(listOf("b", "a"), viewModel.uiState.value.names)
        assertFalse(viewModel.uiState.value.noSavedGame)
    }

    @Test
    fun tappingARowTogglesItsSelection() = runTest {
        val viewModel = SavedGamesViewModel(serviceWith("a", "b"))
        viewModel.onAction(SavedGamesAction.GameClicked("a"))
        assertEquals("a", viewModel.uiState.value.selected)
        viewModel.onAction(SavedGamesAction.GameClicked("b"))
        assertEquals("b", viewModel.uiState.value.selected)
        viewModel.onAction(SavedGamesAction.GameClicked("b"))
        assertNull(viewModel.uiState.value.selected)
    }

    @Test
    fun noSavedGameOnlyWhenOpenedEmpty() = runTest {
        assertTrue(SavedGamesViewModel(serviceWith()).uiState.value.noSavedGame)

        val viewModel = SavedGamesViewModel(serviceWith("a"))
        viewModel.onAction(SavedGamesAction.GameClicked("a"))
        viewModel.onAction(SavedGamesAction.DeleteClicked("a"))
        viewModel.onAction(SavedGamesAction.DeleteConfirmed)
        assertEquals(emptyList<String>(), viewModel.uiState.value.names)
        assertNull(viewModel.uiState.value.selected)
        assertFalse(viewModel.uiState.value.noSavedGame)
    }

    @Test
    fun renameToATakenNameShowsTheErrorAndKeepsTheDialog() = runTest {
        val viewModel = SavedGamesViewModel(serviceWith("a", "b"))
        viewModel.onAction(SavedGamesAction.RenameClicked("a"))
        viewModel.onAction(SavedGamesAction.RenameDraftChanged("b"))
        viewModel.onAction(SavedGamesAction.RenameConfirmed)
        assertTrue(viewModel.uiState.value.renameError)
        assertEquals("a", viewModel.uiState.value.renameTarget)
    }

    @Test
    fun renameClosesTheDialogAndClearsTheSelection() = runTest {
        val viewModel = SavedGamesViewModel(serviceWith("a", "b"))
        viewModel.onAction(SavedGamesAction.GameClicked("a"))
        viewModel.onAction(SavedGamesAction.RenameClicked("a"))
        viewModel.onAction(SavedGamesAction.RenameDraftChanged("c"))
        viewModel.onAction(SavedGamesAction.RenameConfirmed)
        val state = viewModel.uiState.value
        assertNull(state.renameTarget)
        assertNull(state.selected)
        assertEquals(listOf("c", "b"), state.names)
    }
}
