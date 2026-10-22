package fr.thibautperrin.tetrano.data.legacy

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Only the values a user actually changed are copied — a key absent from
 * the legacy file means the user never touched that setting, so this
 * app's own default should apply rather than a re-derived legacy one.
 */
data class LegacySettingsSnapshot(
    val player1Color: Int?,
    val player2Color: Int?,
    val userColor: Int?,
    val adversaryColor: Int?,
    val player1Name: String?,
    val player2Name: String?,
    val userName: String?,
    val aiLevel: Int?,
) {
    val isEmpty: Boolean
        get() = player1Color == null && player2Color == null && userColor == null &&
            adversaryColor == null && player1Name == null && player2Name == null &&
            userName == null && aiLevel == null
}

/**
 * Reads the legacy settings `SharedPreferences` file by its exact legacy
 * name and keys (`StaticFields`, see docs/GLOSSARY.md) for the one-time
 * import in docs/PLAN.md P5, and deletes it along with the game-counters
 * file (`gameLog`, only ever used for the stars, which are gone). Nothing
 * writes to these files again.
 */
class LegacySharedPreferencesReader @Inject constructor(@param:ApplicationContext private val context: Context) {

    fun readSettings(): LegacySettingsSnapshot {
        val prefs = context.getSharedPreferences(SETTINGS_FILE_NAME, Context.MODE_PRIVATE)
        return LegacySettingsSnapshot(
            player1Color = prefs.intOrNull(KEY_PLAYER1_COLOR),
            player2Color = prefs.intOrNull(KEY_PLAYER2_COLOR),
            userColor = prefs.intOrNull(KEY_USER_COLOR),
            adversaryColor = prefs.intOrNull(KEY_ADVERSARY_COLOR),
            player1Name = prefs.stringOrNull(KEY_PLAYER1_NAME),
            // "player2Namme" (sic) is not a typo introduced here — it's the
            // literal legacy SharedPreferences key (StaticFields.
            // LABEL_PLAYER2_NAME), and must be read back exactly as written.
            player2Name = prefs.stringOrNull(KEY_PLAYER2_NAME),
            userName = prefs.stringOrNull(KEY_USER_NAME),
            aiLevel = prefs.intOrNull(KEY_LEVEL),
        )
    }

    /** Deletes both legacy files (and the Bluetooth devices database, never read by this app). */
    fun deleteAll() {
        context.deleteSharedPreferences(SETTINGS_FILE_NAME)
        context.deleteSharedPreferences(STATS_FILE_NAME)
        context.deleteDatabase(BLUETOOTH_DATABASE_FILE_NAME)
    }

    private fun SharedPreferences.intOrNull(key: String): Int? = if (contains(key)) getInt(key, 0) else null
    private fun SharedPreferences.stringOrNull(key: String): String? = if (contains(key)) getString(key, null) else null

    private companion object {
        // File names: StaticFields.FILE_PREFERENCES / GAME_LOG_FILE /
        // BLUETOOTH_DEVICES_DATABASE_FILE.
        const val SETTINGS_FILE_NAME = "preferences"
        const val STATS_FILE_NAME = "gameLog"
        const val BLUETOOTH_DATABASE_FILE_NAME = "bluetoothDevices.db"

        // Keys: StaticFields.LABEL_*.
        const val KEY_PLAYER1_COLOR = "player1Color"
        const val KEY_PLAYER2_COLOR = "player2Color"
        const val KEY_USER_COLOR = "userColor"
        const val KEY_ADVERSARY_COLOR = "adversaryColor"
        const val KEY_PLAYER1_NAME = "player1Name"
        const val KEY_PLAYER2_NAME = "player2Namme"
        const val KEY_USER_NAME = "userName"
        const val KEY_LEVEL = "level"
    }
}
