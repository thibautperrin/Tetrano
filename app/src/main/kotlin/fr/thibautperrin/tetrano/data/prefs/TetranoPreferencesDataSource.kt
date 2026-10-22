package fr.thibautperrin.tetrano.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Raw key/value access over the settings DataStore (`settings.preferences_pb`,
 * see docs/PLAN.md P4). Ports the legacy `preferences` SharedPreferences
 * file (colors, names, AI level), plus the [legacyImportDone] flag gating
 * the one-time import from the 2015 app's data (see docs/PLAN.md P5).
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

    /** Simulations ("variations tested") per move of the level-101 network in a game. */
    val neuralSimulations: Flow<Int> = intFlow(Keys.NEURAL_SIMULATIONS, DEFAULT_NEURAL_SIMULATIONS)

    /** Simulations the analysis screen runs on the position on display before it stops. */
    val analysisSimulations: Flow<Int> = intFlow(Keys.ANALYSIS_SIMULATIONS, DEFAULT_ANALYSIS_SIMULATIONS)

    /** The Puzzles mode's rating, history and puzzle in progress. */
    val puzzleProgress: Flow<PuzzleProgress> = dataStore.data.map {
        PuzzleProgress(
            rating = it[Keys.PUZZLE_RATING] ?: PuzzleProgress.INITIAL_RATING,
            attempts = it[Keys.PUZZLE_ATTEMPTS] ?: 0,
            solved = it[Keys.PUZZLE_SOLVED] ?: 0,
            seen = it[Keys.PUZZLE_SEEN]?.mapNotNull(String::toIntOrNull)?.toSet().orEmpty(),
            currentId = it[Keys.PUZZLE_CURRENT] ?: 0,
            currentSymmetry = it[Keys.PUZZLE_SYMMETRY] ?: 0,
        )
    }

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
    suspend fun setNeuralSimulations(simulations: Int) = setInt(Keys.NEURAL_SIMULATIONS, simulations)
    suspend fun setAnalysisSimulations(simulations: Int) = setInt(Keys.ANALYSIS_SIMULATIONS, simulations)

    /** Writes [progress] whole, in one transaction. */
    suspend fun setPuzzleProgress(progress: PuzzleProgress) {
        dataStore.edit {
            it[Keys.PUZZLE_RATING] = progress.rating
            it[Keys.PUZZLE_ATTEMPTS] = progress.attempts
            it[Keys.PUZZLE_SOLVED] = progress.solved
            it[Keys.PUZZLE_SEEN] = progress.seen.mapTo(HashSet()) { id -> id.toString() }
            it[Keys.PUZZLE_CURRENT] = progress.currentId
            it[Keys.PUZZLE_SYMMETRY] = progress.currentSymmetry
        }
    }

    /**
     * Drops the keys of the removed star mechanism (the star count, the
     * per-mode game counters that earned them, "Challenge tried"), which
     * 5.0 builds before their removal may have written.
     */
    suspend fun removeObsoleteKeys() {
        dataStore.edit { prefs -> Keys.OBSOLETE.forEach { prefs.remove(it) } }
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
        val NEURAL_SIMULATIONS = intPreferencesKey("neural_simulations")
        val ANALYSIS_SIMULATIONS = intPreferencesKey("analysis_simulations")
        val PUZZLE_RATING = floatPreferencesKey("puzzle_rating")
        val PUZZLE_ATTEMPTS = intPreferencesKey("puzzle_attempts")
        val PUZZLE_SOLVED = intPreferencesKey("puzzle_solved")
        val PUZZLE_SEEN = stringSetPreferencesKey("puzzle_seen")
        val PUZZLE_CURRENT = intPreferencesKey("puzzle_current")
        val PUZZLE_SYMMETRY = intPreferencesKey("puzzle_symmetry")
        val LEGACY_IMPORT_DONE = booleanPreferencesKey("legacy_import_done")
        val OBSOLETE: List<Preferences.Key<*>> = listOf(
            intPreferencesKey("number_stars"),
            intPreferencesKey("games_two_players"),
            intPreferencesKey("games_against_computer"),
            intPreferencesKey("games_won_against_computer"),
            intPreferencesKey("games_online"),
            intPreferencesKey("games_bluetooth"),
            booleanPreferencesKey("has_tried_challenge"),
        )
    }

    companion object {
        const val DEFAULT_AI_LEVEL: Int = 10
        const val DEFAULT_NEURAL_SIMULATIONS: Int = 512
        const val DEFAULT_ANALYSIS_SIMULATIONS: Int = 5_000
    }
}

/**
 * The Puzzles mode's state: an Elo-like [rating], counters, the puzzles already
 * attempted ([seen], by id) and the one on display ([currentId], 0 = none yet,
 * shown under board symmetry [currentSymmetry]).
 */
data class PuzzleProgress(
    val rating: Float = INITIAL_RATING,
    val attempts: Int = 0,
    val solved: Int = 0,
    val seen: Set<Int> = emptySet(),
    val currentId: Int = 0,
    val currentSymmetry: Int = 0,
) {
    companion object {
        const val INITIAL_RATING: Float = 1200f
    }
}
