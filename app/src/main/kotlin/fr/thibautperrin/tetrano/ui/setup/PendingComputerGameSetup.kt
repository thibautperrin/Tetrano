package fr.thibautperrin.tetrano.ui.setup

import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-shot hand-off from [ComputerSetupScreen] to
 * [fr.thibautperrin.tetrano.ui.game.GameScreen] for the pieces of a
 * `VS_COMPUTER` game's setup that have nowhere else to live.
 *
 * [fr.thibautperrin.tetrano.ui.navigation.TetranoRoute.ComputerGame] only
 * carries a `savedGameName` (see docs/PLAN.md P8's fixed route shapes) —
 * unlike the legacy `Intent` bundle, it can't also carry who begins or,
 * when resuming a save, the possibly role-swapped colors (see
 * `JouerContreOrdiActivity`'s KDoc, ported in [ComputerSetupViewModel]).
 * The AI level and (for a *fresh* game) the user/adversary colors already
 * round-trip through [fr.thibautperrin.tetrano.data.prefs.TetranoPreferencesDataSource]
 * instead, exactly like the legacy `ApplicationTetrano` fields did — this
 * singleton only fills the remaining gap.
 *
 * Consuming resets the value to its default, so a `GameScreen` reached any
 * other way (e.g. process recreation) falls back to the same default the
 * legacy code used for a plain new game: the user begins.
 */
@Singleton
class PendingComputerGameSetup @Inject constructor() {
    @Volatile
    private var value: ComputerGameSetup = ComputerGameSetup(userBegins = true)

    fun set(setup: ComputerGameSetup) {
        value = setup
    }

    fun consume(): ComputerGameSetup {
        val result = value
        value = ComputerGameSetup(userBegins = true)
        return result
    }
}

/**
 * @param userBegins final say on who makes the next move — already
 *   resolved by [ComputerSetupViewModel] (including any role swap for a
 *   resumed game).
 * @param userColor null means "use [fr.thibautperrin.tetrano.data.prefs.TetranoPreferencesDataSource]'s
 *   current color" (a fresh game); non-null is the resolved color for a
 *   resumed game, which may differ from the current preference.
 */
data class ComputerGameSetup(
    val userBegins: Boolean,
    val userColor: Int? = null,
    val adversaryColor: Int? = null,
)
