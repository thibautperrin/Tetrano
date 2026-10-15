package fr.thibautperrin.tetrano.ui.settings

/**
 * Colors are packed ARGB [Int] (as stored by
 * [fr.thibautperrin.tetrano.data.prefs.TetranoPreferencesDataSource]),
 * not `androidx.compose.ui.graphics.Color` — same convention as
 * [fr.thibautperrin.tetrano.ui.setup.TwoPlayersSetupUiModel].
 */
data class SettingsUiModel(
    /** The name field, pre-filled with the saved name once loaded. */
    val userNameDraft: String = "",
    /** The saved user name; blank until one was saved (the screen then hints `User`). */
    val currentUserName: String = "",
    val userColor: Int = DEFAULT_USER_COLOR,
    val adversaryColor: Int = DEFAULT_ADVERSARY_COLOR,
    /** Variations (simulations) the level-101 network tests per move in a game. */
    val neuralSimulations: Int = DEFAULT_NEURAL_SIMULATIONS,
) {
    companion object {
        // TetranoPreferencesDataSource.DefaultColors, unchanged.
        const val DEFAULT_USER_COLOR = 0xFFFF0000.toInt()
        const val DEFAULT_ADVERSARY_COLOR = 0xFF0000FF.toInt()
        const val DEFAULT_NEURAL_SIMULATIONS = 512

        /** The choices offered for [neuralSimulations]. */
        val SIMULATION_CHOICES = listOf(32, 64, 128, 256, 512, 1024, 2048, 4096, 8192)
    }
}

sealed interface SettingsAction {
    data class UserNameChanged(val name: String) : SettingsAction
    data class UserColorSelected(val color: Int) : SettingsAction
    data class AdversaryColorSelected(val color: Int) : SettingsAction
    data class NeuralSimulationsSelected(val simulations: Int) : SettingsAction

    /** Back (arrow or system): saves, then closes the screen. */
    data object SaveClicked : SettingsAction
}
