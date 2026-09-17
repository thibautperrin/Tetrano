package fr.thibautperrin.tetrano.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [SavedGameEntity::class, ChallengeProgressEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class TetranoDatabase : RoomDatabase() {
    abstract fun savedGameDao(): SavedGameDao
    abstract fun challengeProgressDao(): ChallengeProgressDao

    companion object {
        /**
         * Unchanged from the legacy `SQLiteOpenHelperSavedGame` database
         * file name, on purpose: this is what lets a user updating from
         * the 2015 app keep their saved games (via [MIGRATION_1_2]) rather
         * than starting from an empty database. See docs/PLAN.md P4/P5.
         */
        const val FILE_NAME: String = "savedGames.db"
    }
}
