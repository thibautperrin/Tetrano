package fr.thibautperrin.tetrano.ui.home

/**
 * [numberStars]: the Challenge-mode star count, shown in Home's action bar —
 * new in the rewrite (the legacy `MainActivity` only showed "Tetrano"), drawn
 * like `ApplicationTetrano.getSpannableForTitle` (count, then the star).
 */
data class HomeUiModel(val numberStars: Int = 0)

sealed interface HomeAction {
    data object PlayClicked : HomeAction
    data object RulesClicked : HomeAction
    data object SettingsClicked : HomeAction
}
