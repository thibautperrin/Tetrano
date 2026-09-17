package fr.thibautperrin.tetrano.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Converts the legacy hand-written schema (`SQLiteOpenHelperSavedGame`,
 * `DATABASE_VERSION = 1`, table `savedgames`: `name TEXT PRIMARY KEY,
 * rings TEXT, begin INTEGER, ucolor INTEGER, acolor INTEGER`) into the
 * Room-managed one, and adds the `challenge_progress` table. See
 * docs/PLAN.md P4/P5.
 *
 * `rings` is declared `TEXT` in the legacy schema but always holds raw
 * bytes (`DAOSavedGame` puts a `byte[]` into it via `ContentValues`, and
 * SQLite's manifest typing keeps BLOB values as-is regardless of a
 * column's declared affinity), so a plain `INSERT ... SELECT` copies it
 * correctly without any cast.
 *
 * If no `savedGames.db` file exists yet (a fresh install), Room creates
 * the version-2 schema directly and this migration never runs.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `saved_games` (
                `name` TEXT NOT NULL,
                `rings` BLOB NOT NULL,
                `user_begins` INTEGER NOT NULL,
                `user_color` INTEGER NOT NULL,
                `adversary_color` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL,
                PRIMARY KEY(`name`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `saved_games` (name, rings, user_begins, user_color, adversary_color, created_at)
            SELECT name, rings, begin, ucolor, acolor, 0
            FROM `savedgames`
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE `savedgames`")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `challenge_progress` (
                `id` INTEGER NOT NULL,
                `level` INTEGER NOT NULL,
                `symmetry` INTEGER NOT NULL,
                `normally_played` BLOB,
                `played_by_stars` BLOB,
                `next_time_advert` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
    }
}
