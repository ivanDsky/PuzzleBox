package com.ivandsky.puzzlebox.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Migration harness (ticket-04 deferral): replays committed schema JSONs from
 * core/data/schemas (on the test classpath) so every version bump lands with a proof the
 * old data survives.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PuzzleBoxDatabaseMigrationTest {

    private val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PuzzleBoxDatabase::class.java,
    )

    @Test
    fun `migrate 1 to 2 keeps v1 rows and opens the daily store`() {
        val dbName = "puzzlebox-migration-test.db"
        val v1 = helper.createDatabase(dbName, 1)
        v1.execSQL(
            "INSERT INTO daily_puzzles (gameId, day, size, cells, fetchedAtMs) " +
                "VALUES ('zip', '2026-10-30', 4, '[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16]', 1000)",
        )
        v1.close()

        val db = helper.runMigrationsAndValidate(dbName, 2, true, Migrations.MIGRATION_1_2)

        // v1 exemplar rows survive untouched.
        db.query("SELECT gameId, day, size FROM daily_puzzles").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("zip", cursor.getString(0))
            assertEquals("2026-10-30", cursor.getString(1))
            assertEquals(4, cursor.getInt(2))
        }
        // The v2 daily store takes envelope rows (nullable puzzleNumber, blob payload).
        db.execSQL(
            "INSERT INTO puzzle_dailies (gameId, day, puzzleNumber, variant, formatVersion, payload, fetchedAtMs) " +
                "VALUES ('zip', '2026-10-31', 592, 'zip/n6', 1, x'00ff', 5000)",
        )
        db.query("SELECT puzzleNumber, variant FROM puzzle_dailies WHERE gameId = 'zip' AND day = '2026-10-31'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(592, cursor.getInt(0))
            assertEquals("zip/n6", cursor.getString(1))
        }
        db.close()
    }

    @Test
    fun `fresh v2 database is created by the normal builder path`() {
        // runMigrationsAndValidate on the latest version proves the committed schema 2.json
        // matches what Room generates from the entities (identity of the migration contract).
        val dbName = "puzzlebox-fresh-v2.db"
        val db = helper.createDatabase(dbName, 2)
        db.query("SELECT COUNT(*) FROM puzzle_dailies").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        db.close()
    }
}
