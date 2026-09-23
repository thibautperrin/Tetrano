package fr.thibautperrin.tetrano.domain.service

import fr.thibautperrin.tetrano.data.db.SavedGameDao
import fr.thibautperrin.tetrano.data.db.SavedGameEntity
import fr.thibautperrin.tetrano.domain.model.SavedGameSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Saved, resumable games. Ports `DAOSavedGame`/the save-related parts of
 * `GestionJeu`/`SavedGame` (see docs/GLOSSARY.md, docs/PLAN.md P6).
 */
@Singleton
class SavedGameService @Inject constructor(
    private val savedGameDao: SavedGameDao,
) {
    /** Every saved game, in insertion order (the legacy list's order, see [SavedGameDao.observeAll]). */
    fun observeAll(): Flow<List<SavedGameSummary>> =
        savedGameDao.observeAll().map { entities -> entities.map { it.toSummary() } }

    suspend fun get(name: String): SavedGameSummary? = savedGameDao.getByName(name)?.toSummary()

    /**
     * Saves a game as [name], colors and all. If [name] is already taken,
     * tries `"$name (2)"`, `"$name (3)"`, ... until one succeeds — ports
     * `GestionJeu.sauvegarderPartiePourPlusTard`'s exact retry loop.
     * Returns the name it was actually saved under.
     */
    suspend fun save(
        name: String,
        ringsPlayed: ByteArray,
        userBegins: Boolean,
        userColor: Int,
        adversaryColor: Int,
    ): String {
        val truncatedName = name.take(MAX_NAME_LENGTH)
        var candidateName = truncatedName
        var suffix = 2
        while (true) {
            val entity = SavedGameEntity(
                name = candidateName,
                rings = ringsPlayed,
                userBegins = userBegins,
                userColor = userColor,
                adversaryColor = adversaryColor,
                createdAt = System.currentTimeMillis(),
            )
            if (savedGameDao.insert(entity) != -1L) return candidateName
            candidateName = "$truncatedName ($suffix)"
            suffix++
        }
    }

    /**
     * Ports `DAOSavedGame.rename`. False if [newName] is blank or already
     * taken by another saved game: a rename never overwrites — checked
     * here because [SavedGameDao.rename]'s raw `UPDATE` would throw on the
     * primary-key collision instead of returning 0. [newName] is truncated
     * like [save]'s.
     */
    suspend fun rename(previousName: String, newName: String): Boolean {
        val truncatedName = newName.take(MAX_NAME_LENGTH)
        if (truncatedName == previousName) return savedGameDao.getByName(previousName) != null
        if (truncatedName.isBlank() || savedGameDao.getByName(truncatedName) != null) return false
        return savedGameDao.rename(previousName, truncatedName) == 1
    }

    suspend fun delete(name: String): Boolean = savedGameDao.delete(name) == 1

    private fun SavedGameEntity.toSummary() = SavedGameSummary(
        name = name,
        ringsPlayed = rings,
        userBegins = userBegins,
        userColor = userColor,
        adversaryColor = adversaryColor,
        createdAt = createdAt,
    )

    private companion object {
        // SavedGame's truncation, unchanged.
        const val MAX_NAME_LENGTH = 100
    }
}
