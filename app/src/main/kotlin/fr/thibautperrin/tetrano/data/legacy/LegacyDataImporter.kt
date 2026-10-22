package fr.thibautperrin.tetrano.data.legacy

import fr.thibautperrin.tetrano.data.db.ChallengeProgressDao
import fr.thibautperrin.tetrano.data.db.ChallengeProgressEntity
import fr.thibautperrin.tetrano.data.prefs.TetranoPreferencesDataSource
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-time import of everything the legacy 2015 app kept outside its
 * SQLite database (which Room migrates on its own via `MIGRATION_1_2`):
 * `challengeVars.bin` and the settings `SharedPreferences` file (the game
 * counters of the other one only ever fed the stars, which are gone). See
 * docs/PLAN.md P5.
 *
 * Idempotent and safe to call on every app start: it does nothing once
 * [TetranoPreferencesDataSource.legacyImportDone] is true, and each source
 * is imported independently — a problem reading one (a corrupt or
 * unexpectedly shaped file) does not block the others, and never crashes
 * the app; it just leaves that source's data at this app's own defaults.
 */
@Singleton
class LegacyDataImporter @Inject constructor(
    private val preferences: TetranoPreferencesDataSource,
    private val preferencesReader: LegacySharedPreferencesReader,
    private val challengeVarsReader: LegacyChallengeVarsReader,
    private val challengeProgressDao: ChallengeProgressDao,
) {
    suspend fun importIfNeeded() {
        if (preferences.legacyImportDone.first()) return

        runCatching { importSettings() }
        runCatching { importChallengeProgress() }

        // Only the files this importer itself is responsible for. The
        // legacy saved-games database is migrated in place by Room
        // (MIGRATION_1_2) and is never touched here.
        runCatching { challengeVarsReader.delete() }
        runCatching { preferencesReader.deleteAll() }

        preferences.setLegacyImportDone(true)
    }

    private suspend fun importSettings() {
        val settings = preferencesReader.readSettings()
        if (settings.isEmpty) return
        settings.player1Color?.let { preferences.setPlayer1Color(it) }
        settings.player2Color?.let { preferences.setPlayer2Color(it) }
        settings.userColor?.let { preferences.setUserColor(it) }
        settings.adversaryColor?.let { preferences.setAdversaryColor(it) }
        settings.player1Name?.let { preferences.setPlayer1Name(it) }
        settings.player2Name?.let { preferences.setPlayer2Name(it) }
        settings.userName?.let { preferences.setUserName(it) }
        settings.aiLevel?.let { preferences.setAiLevel(it) }
    }

    private suspend fun importChallengeProgress() {
        val vars = challengeVarsReader.read() ?: return
        if (!vars.isPlausible()) return

        // The star count is dropped: hints are free now.
        challengeProgressDao.upsert(
            ChallengeProgressEntity(
                level = vars.level,
                symmetry = vars.symmetry,
                normallyPlayed = vars.normallyPlayed,
                playedByHints = vars.playedByStars,
                nextTimeAdvert = vars.nextTimeAdvert,
            ),
        )
    }

    /**
     * Cheap sanity bounds, not a full replay-and-verify like the legacy
     * `verifyCorrectAndSaveData` — good enough to reject an obviously
     * corrupt file without ever throwing. Ports the bound checks (not the
     * board-replay ones) from docs/PLAN.md P5.
     */
    private fun LegacyChallengeVars.isPlausible(): Boolean {
        if (level !in 1..(ChallengeVarsBounds.MAX_LEVEL + 1)) return false
        if (numberStars < 0) return false
        if (symmetry !in 0..47) return false
        if (normallyPlayed != null && normallyPlayed.size % 2 != 0) return false
        if (playedByStars != null && playedByStars.size % 2 != 0) return false
        return true
    }

    private object ChallengeVarsBounds {
        const val MAX_LEVEL = 245
    }
}
