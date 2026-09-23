package fr.thibautperrin.tetrano.ui.setup

import fr.thibautperrin.tetrano.data.prefs.TetranoPreferencesDataSource

/**
 * @param aiLevel the AI difficulty, [ComputerSetupViewModel.MIN_LEVEL]..[ComputerSetupViewModel.MAX_LEVEL]
 *   (the legacy `seekBarLevel`).
 * @param userBegins whether the user is the one to (re)start playing next —
 *   the legacy "User" / "Computer" radio choice.
 */
data class ComputerSetupUiModel(
    val aiLevel: Int = TetranoPreferencesDataSource.DEFAULT_AI_LEVEL,
    val userBegins: Boolean = true,
)

sealed interface ComputerSetupAction {
    data class LevelChanged(val level: Int) : ComputerSetupAction
    data object LevelIncremented : ComputerSetupAction
    data object LevelDecremented : ComputerSetupAction
    data object UserBeginsSelected : ComputerSetupAction
    data object ComputerBeginsSelected : ComputerSetupAction
    data object PlayClicked : ComputerSetupAction
}
