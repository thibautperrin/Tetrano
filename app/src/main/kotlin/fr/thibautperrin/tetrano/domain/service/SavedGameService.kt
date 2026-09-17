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
        val truncatedName = if (name.length > MAX_NAME_LENGTH) name.substring(0, MAX_NAME_LENGTH) else name
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

    /** False if [newName] is already taken by another saved game (a rename never overwrites). */
    suspend fun rename(previousName: String, newName: String): Boolean =
        savedGameDao.rename(previousName, newName) == 1

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
