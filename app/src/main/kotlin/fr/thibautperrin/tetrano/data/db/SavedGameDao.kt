package fr.thibautperrin.tetrano.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedGameDao {

    /**
     * Every saved game in insertion order, like `DAOSavedGame.getSavedGameNames`
     * (an unordered query, i.e. SQLite's `rowid` order). Ordering by the
     * implicit `rowid` rather than [SavedGameEntity.createdAt] keeps the
     * legacy rows (all migrated with `created_at = 0`, in their original
     * order) first, and a rename (an `UPDATE`) doesn't move a game.
     */
    @Query("SELECT * FROM saved_games ORDER BY rowid ASC")
    fun observeAll(): Flow<List<SavedGameEntity>>

    @Query("SELECT * FROM saved_games WHERE name = :name")
    suspend fun getByName(name: String): SavedGameEntity?

    /**
     * Ignores (rather than replacing) a conflicting primary key, mirroring
     * the legacy `SQLiteDatabase.insert` (not `insertOrThrow`) used by
     * `DAOSavedGame.addSavedGame`: the row id is -1 on conflict, exactly
     * like the raw API's `!= -1` success check.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(game: SavedGameEntity): Long

    /** Inserts [game], or replaces the game saved under the same name. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(game: SavedGameEntity)

    @Query("UPDATE saved_games SET name = :newName WHERE name = :previousName")
    suspend fun rename(previousName: String, newName: String): Int

    @Query("DELETE FROM saved_games WHERE name = :name")
    suspend fun delete(name: String): Int
}
