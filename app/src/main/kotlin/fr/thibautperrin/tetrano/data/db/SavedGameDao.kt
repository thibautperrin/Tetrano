package fr.thibautperrin.tetrano.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedGameDao {

    @Query("SELECT * FROM saved_games ORDER BY created_at DESC, name ASC")
    fun observeAll(): Flow<List<SavedGameEntity>>

    @Query("SELECT * FROM saved_games WHERE name = :name")
    suspend fun getByName(name: String): SavedGameEntity?

    @Query("SELECT name FROM saved_games")
    suspend fun getAllNames(): List<String>

    /**
     * Ignores (rather than replacing) a conflicting primary key, mirroring
     * the legacy `SQLiteDatabase.insert` (not `insertOrThrow`) used by
     * `DAOSavedGame.addSavedGame`: the row id is -1 on conflict, exactly
     * like the raw API's `!= -1` success check.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(game: SavedGameEntity): Long

    @Query("UPDATE saved_games SET name = :newName WHERE name = :previousName")
    suspend fun rename(previousName: String, newName: String): Int

    @Query("DELETE FROM saved_games WHERE name = :name")
    suspend fun delete(name: String): Int
}
