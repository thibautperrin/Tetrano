package fr.thibautperrin.tetrano.ui.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import fr.thibautperrin.tetrano.data.prefs.TetranoPreferencesDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var preferences: TetranoPreferencesDataSource

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        preferences = TetranoPreferencesDataSource(
            PreferenceDataStoreFactory.create(scope = dataStoreScope) { File(folder.root, "settings.preferences_pb") },
        )
    }

    @After
    fun tearDown() {
        dataStoreScope.cancel()
        Dispatchers.resetMain()
    }

    private suspend fun loadedViewModel(): SettingsViewModel {
        val viewModel = SettingsViewModel(preferences)
        withTimeout(5_000) { viewModel.uiState.first { it.currentUserName.isNotEmpty() } }
        return viewModel
    }

    @Test
    fun loadsTheSavedValuesAsHintAndColorsWithAnEmptyField() = runBlocking {
        preferences.setUserSettings("Bob", GREEN, BLACK)
        val state = loadedViewModel().uiState.value
        assertEquals("Bob", state.currentUserName)
        assertEquals("", state.userNameDraft)
        assertEquals(GREEN, state.userColor)
        assertEquals(BLACK, state.adversaryColor)
    }

    @Test
    fun aColorEqualToTheOtherPickersIsIgnored() = runBlocking {
        preferences.setUserSettings("Bob", GREEN, BLACK)
        val viewModel = loadedViewModel()
        viewModel.onAction(SettingsAction.UserColorSelected(BLACK))
        viewModel.onAction(SettingsAction.AdversaryColorSelected(GREEN))
        assertEquals(GREEN, viewModel.uiState.value.userColor)
        assertEquals(BLACK, viewModel.uiState.value.adversaryColor)
    }

    @Test
    fun savingAnEmptyNameKeepsTheSavedOne() = runBlocking {
        preferences.setUserSettings("Bob", GREEN, BLACK)
        val viewModel = loadedViewModel()
        viewModel.onAction(SettingsAction.UserColorSelected(RED))
        viewModel.onAction(SettingsAction.SaveClicked)
        withTimeout(5_000) { viewModel.saved.first() }
        assertEquals("Bob", preferences.userName.first())
        assertEquals(RED, preferences.userColor.first())
        assertEquals(BLACK, preferences.adversaryColor.first())
    }

    @Test
    fun savingATypedNamePersistsIt() = runBlocking {
        preferences.setUserSettings("Bob", GREEN, BLACK)
        val viewModel = loadedViewModel()
        viewModel.onAction(SettingsAction.UserNameChanged("Alice"))
        viewModel.onAction(SettingsAction.SaveClicked)
        withTimeout(5_000) { viewModel.saved.first() }
        assertEquals("Alice", preferences.userName.first())
    }

    private companion object {
        const val RED = 0xFFFF0000.toInt()
        const val GREEN = 0xFF00CC00.toInt()
        const val BLACK = 0xFF000000.toInt()
    }
}
