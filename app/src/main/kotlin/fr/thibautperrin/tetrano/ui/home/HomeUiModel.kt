package fr.thibautperrin.tetrano.ui.home

/** Ports the star count shown by `ApplicationTetrano.getSpannableForTitle` on the legacy [MainActivity][fr.thibautperrin.tetrano.MainActivity]. */
data class HomeUiModel(val numberStars: Int = 0)

sealed interface HomeAction {
    data object PlayClicked : HomeAction
    data object RulesClicked : HomeAction
    data object SettingsClicked : HomeAction
}
