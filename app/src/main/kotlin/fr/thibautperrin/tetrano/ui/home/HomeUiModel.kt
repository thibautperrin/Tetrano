package fr.thibautperrin.tetrano.ui.home

sealed interface HomeAction {
    data object PlayClicked : HomeAction
    data object RulesClicked : HomeAction
    data object SettingsClicked : HomeAction
    data object AboutClicked : HomeAction
}
