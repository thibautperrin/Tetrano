package fr.thibautperrin.tetrano.ui.setup

import fr.thibautperrin.tetrano.ui.navigation.TetranoRoute
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-shot hand-off from [TwoPlayersSetupScreen] to
 * [fr.thibautperrin.tetrano.ui.game.GameScreen] for a resumed
 * `TWO_PLAYERS` game's colors — the counterpart of [PendingComputerGameSetup].
 *
 * When resuming a save, the legacy `JouerDeuxJoueurActivity` pre-selected
 * the save's colors and let the players change them *without* persisting
 * them as the new defaults, so they can't travel through
 * [fr.thibautperrin.tetrano.data.prefs.TetranoPreferencesDataSource] the
 * way a fresh game's choices do. Names always go through the preferences,
 * as in the legacy app.
 *
 * Consuming resets the value, so a game reached any other way (a fresh
 * game, process recreation) falls back to the save's / preferences' colors.
 */
@Singleton
class PendingTwoPlayersGameSetup @Inject constructor() {
    @Volatile
    private var value: TwoPlayersGameSetup = TwoPlayersGameSetup()

    fun set(setup: TwoPlayersGameSetup) {
        value = setup
    }

    fun consume(): TwoPlayersGameSetup {
        val result = value
        value = TwoPlayersGameSetup()
        return result
    }
}

/** Null colors mean "use the save's, or else the preferences' color". */
data class TwoPlayersGameSetup(
    val player1Color: Int? = null,
    val player2Color: Int? = null,
)

/**
 * The first [ringCount] moves of a saved game — [TetranoRoute.ALL_RINGS]
 * (or anything out of range) keeps them all.
 */
fun ByteArray.keepFirst(ringCount: Int): ByteArray =
    if (ringCount == TetranoRoute.ALL_RINGS || ringCount !in 0..size) this else copyOf(ringCount)
