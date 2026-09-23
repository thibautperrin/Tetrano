package fr.thibautperrin.tetrano.data.db

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.FileOutputStream

/**
 * Exercises [MIGRATION_1_2] against a real legacy-schema database file
 * (`app/src/androidTest/assets/migration-test/savedGames-v1.db`, generated
 * with the exact `SQLiteOpenHelperSavedGame` schema — see docs/PLAN.md P4).
 *
 * This deliberately does not use [androidx.room.testing.MigrationTestHelper]'s
 * schema-driven `createDatabase(name, version)`: that helper expects a
 * Room-exported schema JSON for the *starting* version, but version 1 here
 * is the legacy hand-written SQLite schema, which was never a Room
 * database and has no such export. Instead, the fixture file is copied
 * directly into place before Room ever opens it — Room then sees a
 * `PRAGMA user_version = 1` database and runs [MIGRATION_1_2] exactly as
 * it would on a real user's upgrade.
 *
 * NOTE: requires a connected device or emulator to run (`androidTest`).
 * Run on a physical device for docs/PLAN.md P10 (see that commit): caught
 * and fixed a wrong-Context bug below that this test's introducing commit
 * couldn't have found without one (see [instrumentation]'s KDoc).
 */
class DatabaseMigrationTest {

    @Test
    fun migrationFromLegacySchemaPreservesSavedGamesAndAddsChallengeTable() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val dbFile = context.getDatabasePath(TetranoDatabase.FILE_NAME)
        dbFile.parentFile?.mkdirs()
        if (dbFile.exists()) dbFile.delete()

        // The fixture is packaged in the *androidTest* APK, not the app
        // APK: it must be read through the instrumentation's own Context
        // (`instrumentation.context`), not `targetContext` (the app under
        // test, whose AssetManager only sees app/src/*/assets and would
        // throw FileNotFoundException for a test-only asset).
        instrumentation.context.assets.open("migration-test/savedGames-v1.db").use { input ->
            FileOutputStream(dbFile).use { output -> input.copyTo(output) }
        }

        val db = Room.databaseBuilder(context, TetranoDatabase::class.java, TetranoDatabase.FILE_NAME)
            .addMigrations(MIGRATION_1_2)
            .build()
        try {
            val games = db.savedGameDao().getAllNames()
            assertEquals(setOf("Game of 2026-01-01", "Another save"), games.toSet())

            val first = db.savedGameDao().getByName("Game of 2026-01-01")
            assertEquals(listOf<Byte>(0, 5, 10, 15, 20), first?.rings?.toList())
            assertEquals(true, first?.userBegins)
            assertEquals(-65536, first?.userColor)
            assertEquals(-16776961, first?.adversaryColor)

            val second = db.savedGameDao().getByName("Another save")
            assertEquals(listOf<Byte>(63, 62, 61), second?.rings?.toList())
            assertEquals(false, second?.userBegins)

            // The challenge_progress table exists and starts empty: nothing
            // in the legacy schema populates it (see Migrations.kt).
            assertNull(db.challengeProgressDao().get())
        } finally {
            db.close()
        }
    }
}
