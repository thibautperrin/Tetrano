package fr.thibautperrin.tetrano.ui.setup

data class ComputerSetupUiModel(
    val isLoading: Boolean = true,
    val aiLevel: Int = 10,
    /** Whether the user is the one to (re)start playing next — the "commence"/"ne commence pas" radio choice. */
    val userBegins: Boolean = true,
    val isResumingSavedGame: Boolean = false,
)

sealed interface ComputerSetupAction {
    data class LevelChanged(val level: Int) : ComputerSetupAction
    data object LevelIncremented : ComputerSetupAction
    data object LevelDecremented : ComputerSetupAction
    data object UserBeginsSelected : ComputerSetupAction
    data object ComputerBeginsSelected : ComputerSetupAction
    data object PlayClicked : ComputerSetupAction
}
