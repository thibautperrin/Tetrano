package fr.thibautperrin.tetrano.ui.settings

/**
 * Colors are packed ARGB [Int] (as stored by
 * [fr.thibautperrin.tetrano.data.prefs.TetranoPreferencesDataSource]),
 * not `androidx.compose.ui.graphics.Color` — same convention as
 * [fr.thibautperrin.tetrano.ui.setup.TwoPlayersSetupUiModel].
 */
data class SettingsUiModel(
    /** What the player typed; starts empty, like the legacy `editTextNomUtilisateur`. */
    val userNameDraft: String = "",
    /** The saved user name, shown as the field's hint; blank until one was saved (the screen then hints `User`). */
    val currentUserName: String = "",
    val userColor: Int = DEFAULT_USER_COLOR,
    val adversaryColor: Int = DEFAULT_ADVERSARY_COLOR,
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

    /** Confirm, or Back: both save, then close the screen (`avantSortir()`). */
    data object SaveClicked : SettingsAction
}
