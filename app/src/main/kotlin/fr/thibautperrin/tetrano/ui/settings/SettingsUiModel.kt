package fr.thibautperrin.tetrano.ui.settings

/**
 * Colors are packed ARGB [Int] (as stored by
 * [fr.thibautperrin.tetrano.data.prefs.TetranoPreferencesDataSource]),
 * not `androidx.compose.ui.graphics.Color` — same convention as
 * [fr.thibautperrin.tetrano.ui.setup.TwoPlayersSetupUiModel].
 */
data class SettingsUiModel(
    val userName: String = "",
    val userColor: Int = DEFAULT_USER_COLOR,
    val adversaryColor: Int = DEFAULT_ADVERSARY_COLOR,
    val isLoading: Boolean = true,
    /** True right after [SettingsAction.SaveClicked] persists — the screen shows `Settings_Saved` and acknowledges it. */
    val justSaved: Boolean = false,
) {
    companion object {
        // TetranoPreferencesDataSource.DefaultColors, unchanged.
        const val DEFAULT_USER_COLOR = 0xFFFF0000.toInt()
        const val DEFAULT_ADVERSARY_COLOR = 0xFF0000FF.toInt()
    }
}

sealed interface SettingsAction {
    data class UserNameChanged(val name: String) : SettingsAction
    data class UserColorSelected(val color: Int) : SettingsAction
    data class AdversaryColorSelected(val color: Int) : SettingsAction
    data object SaveClicked : SettingsAction
    data object SavedAcknowledged : SettingsAction
}
