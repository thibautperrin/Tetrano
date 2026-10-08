package fr.thibautperrin.tetrano.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Raw key/value access over the settings DataStore (`settings.preferences_pb`,
 * see docs/PLAN.md P4). Ports the two legacy SharedPreferences files —
 * `preferences` (colors, names, AI level) and `gameLog` (per-mode game
 * counters) — plus the [legacyImportDone] flag gating the one-time import
 * from the 2015 app's data (see docs/PLAN.md P5).
 *
 * This is a thin, typed data source: default values here are the same
 * ones the legacy code used, but as plain constants rather than string
 * resources (`Joueur1`/`Joueur2`/`User`) — the domain layer (P6) is
 * responsible for resolving a *localized* default name when these are
 * blank, since resource lookup doesn't belong at the data-source level.
 */
@Singleton
class TetranoPreferencesDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val player1Color: Flow<Int> = intFlow(Keys.PLAYER1_COLOR, DefaultColors.RED)
    val player2Color: Flow<Int> = intFlow(Keys.PLAYER2_COLOR, DefaultColors.BLUE)
    val userColor: Flow<Int> = intFlow(Keys.USER_COLOR, DefaultColors.RED)
    val adversaryColor: Flow<Int> = intFlow(Keys.ADVERSARY_COLOR, DefaultColors.BLUE)

    val player1Name: Flow<String> = stringFlow(Keys.PLAYER1_NAME, "")
    val player2Name: Flow<String> = stringFlow(Keys.PLAYER2_NAME, "")
    val userName: Flow<String> = stringFlow(Keys.USER_NAME, "")

    /** AI difficulty, 0..101 (101 = neural network). Ports `StaticFields.LABEL_LEVEL` / `ApplicationTetrano.niveauJeu`. */
    val aiLevel: Flow<Int> = intFlow(Keys.AI_LEVEL, DEFAULT_AI_LEVEL)

    /** Challenge-mode hint currency. Ports `MemoryVarsChallenge.numberStars`. */
    val numberStars: Flow<Int> = intFlow(Keys.NUMBER_STARS, DEFAULT_NUMBER_STARS)

    val gamesPlayedTwoPlayers: Flow<Int> = intFlow(Keys.GAMES_2P, 0)
    val gamesPlayedAgainstComputer: Flow<Int> = intFlow(Keys.GAMES_COMPUTER, 0)
    val gamesWonAgainstComputer: Flow<Int> = intFlow(Keys.GAMES_WON_COMPUTER, 0)
    val gamesPlayedOnline: Flow<Int> = intFlow(Keys.GAMES_ONLINE, 0)
    val gamesPlayedBluetooth: Flow<Int> = intFlow(Keys.GAMES_BLUETOOTH, 0)

    /**
     * True once Challenge mode has been attempted at least once. Bluetooth
     * is gone and online isn't implemented yet (see docs/PLAN.md §10), so
     * [StarService]'s "tried every mode" star uses this instead of the two
     * counters above — see docs/PLAN.md P6 for that adjustment.
     */
    val hasTriedChallenge: Flow<Boolean> = dataStore.data.map { it[Keys.HAS_TRIED_CHALLENGE] == true }

    /** True once the one-time import from the legacy 2015 data has completed. See docs/PLAN.md P5. */
    val legacyImportDone: Flow<Boolean> = dataStore.data.map { it[Keys.LEGACY_IMPORT_DONE] == true }

    suspend fun setPlayer1Color(color: Int) = setInt(Keys.PLAYER1_COLOR, color)
    suspend fun setPlayer2Color(color: Int) = setInt(Keys.PLAYER2_COLOR, color)
    suspend fun setUserColor(color: Int) = setInt(Keys.USER_COLOR, color)
    suspend fun setAdversaryColor(color: Int) = setInt(Keys.ADVERSARY_COLOR, color)

    suspend fun setPlayer1Name(name: String) = setString(Keys.PLAYER1_NAME, name)
    suspend fun setPlayer2Name(name: String) = setString(Keys.PLAYER2_NAME, name)
    suspend fun setUserName(name: String) = setString(Keys.USER_NAME, name)

    /**
     * The settings screen's 3 values in one atomic write — ports
     * `SettingsActivity.avantSortir()`: a null [userName] (the field was
     * left empty) keeps the current name.
     */
    suspend fun setUserSettings(userName: String?, userColor: Int, adversaryColor: Int) {
        dataStore.edit {
            if (userName != null) it[Keys.USER_NAME] = userName
            it[Keys.USER_COLOR] = userColor
            it[Keys.ADVERSARY_COLOR] = adversaryColor
        }
    }

    suspend fun setAiLevel(level: Int) = setInt(Keys.AI_LEVEL, level)
    suspend fun setNumberStars(count: Int) = setInt(Keys.NUMBER_STARS, count)

    suspend fun setGamesPlayedTwoPlayers(count: Int) = setInt(Keys.GAMES_2P, count)
    suspend fun setGamesPlayedAgainstComputer(count: Int) = setInt(Keys.GAMES_COMPUTER, count)
    suspend fun setGamesWonAgainstComputer(count: Int) = setInt(Keys.GAMES_WON_COMPUTER, count)
    suspend fun setGamesPlayedOnline(count: Int) = setInt(Keys.GAMES_ONLINE, count)
    suspend fun setGamesPlayedBluetooth(count: Int) = setInt(Keys.GAMES_BLUETOOTH, count)

    suspend fun setHasTriedChallenge(tried: Boolean) {
        dataStore.edit { it[Keys.HAS_TRIED_CHALLENGE] = tried }
    }

    suspend fun setLegacyImportDone(done: Boolean) {
        dataStore.edit { it[Keys.LEGACY_IMPORT_DONE] = done }
    }

    private fun intFlow(key: Preferences.Key<Int>, default: Int): Flow<Int> =
        dataStore.data.map { it[key] ?: default }

    private fun stringFlow(key: Preferences.Key<String>, default: String): Flow<String> =
        dataStore.data.map { it[key] ?: default }

    private suspend fun setInt(key: Preferences.Key<Int>, value: Int) {
        dataStore.edit { it[key] = value }
    }

    private suspend fun setString(key: Preferences.Key<String>, value: String) {
        dataStore.edit { it[key] = value }
    }

    /** Legacy hardcoded defaults (`ApplicationTetrano.chargeLesReglages`). */
    private object DefaultColors {
        const val RED = 0xffff0000.toInt()
        const val BLUE = 0xff0000ff.toInt()
    }

    private object Keys {
        val PLAYER1_COLOR = intPreferencesKey("player1_color")
        val PLAYER2_COLOR = intPreferencesKey("player2_color")
        val USER_COLOR = intPreferencesKey("user_color")
        val ADVERSARY_COLOR = intPreferencesKey("adversary_color")
        val PLAYER1_NAME = stringPreferencesKey("player1_name")
        val PLAYER2_NAME = stringPreferencesKey("player2_name")
        val USER_NAME = stringPreferencesKey("user_name")
        val AI_LEVEL = intPreferencesKey("ai_level")
        val NUMBER_STARS = intPreferencesKey("number_stars")
        val GAMES_2P = intPreferencesKey("games_two_players")
        val GAMES_COMPUTER = intPreferencesKey("games_against_computer")
        val GAMES_WON_COMPUTER = intPreferencesKey("games_won_against_computer")
        val GAMES_ONLINE = intPreferencesKey("games_online")
        val GAMES_BLUETOOTH = intPreferencesKey("games_bluetooth")
        val HAS_TRIED_CHALLENGE = booleanPreferencesKey("has_tried_challenge")
        val LEGACY_IMPORT_DONE = booleanPreferencesKey("legacy_import_done")
    }

    companion object {
        const val DEFAULT_AI_LEVEL: Int = 10
        const val DEFAULT_NUMBER_STARS: Int = 3
    }
}
