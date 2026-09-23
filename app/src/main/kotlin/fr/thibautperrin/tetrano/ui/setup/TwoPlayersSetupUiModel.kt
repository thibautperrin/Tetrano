package fr.thibautperrin.tetrano.ui.setup

/**
 * Colors are packed ARGB [Int] (as stored by
 * [fr.thibautperrin.tetrano.data.prefs.TetranoPreferencesDataSource]),
 * not `androidx.compose.ui.graphics.Color` — keeps this a plain data
 * model; the screen converts to `Color` only where it actually draws.
 */
data class TwoPlayersSetupUiModel(
    val player1Name: String = "",
    val player2Name: String = "",
    val player1Color: Int = DEFAULT_PLAYER1_COLOR,
    val player2Color: Int = DEFAULT_PLAYER2_COLOR,
) {
    companion object {
        // ApplicationTetrano.chargeLesReglages defaults, unchanged.
        const val DEFAULT_PLAYER1_COLOR = 0xFFFF0000.toInt()
        const val DEFAULT_PLAYER2_COLOR = 0xFF0000FF.toInt()
    }
}

sealed interface TwoPlayersSetupAction {
    data class Player1NameChanged(val name: String) : TwoPlayersSetupAction
    data class Player2NameChanged(val name: String) : TwoPlayersSetupAction
    data class Player1ColorSelected(val color: Int) : TwoPlayersSetupAction
    data class Player2ColorSelected(val color: Int) : TwoPlayersSetupAction

    /** @param player1Name/[player2Name] the name actually shown (typed value, or the hint if left blank) — resolved by the screen, which owns the localized hint text. */
    data class PlayClicked(val player1Name: String, val player2Name: String) : TwoPlayersSetupAction
}
