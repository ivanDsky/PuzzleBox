package com.ivandsky.puzzlebox.database

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
 * core/database/schemas (on the test classpath) so every version bump lands with a proof the
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
        // runMigrationsAndValidate on the latest version proves the committed schema JSON
        // matches what Room generates from the entities (identity of the migration contract).
        val dbName = "puzzlebox-fresh-v2.db"
        val db = helper.createDatabase(dbName, 2)
        db.query("SELECT COUNT(*) FROM puzzle_dailies").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        db.close()
    }

    @Test
    fun `migrate 2 to 3 keeps v2 rows and opens the completions store`() {
        val dbName = "puzzlebox-migration-2-3-test.db"
        val v2 = helper.createDatabase(dbName, 2)
        v2.execSQL(
            "INSERT INTO puzzle_dailies (gameId, day, puzzleNumber, variant, formatVersion, payload, fetchedAtMs) " +
                "VALUES ('zip', '2026-10-31', 592, 'zip/n6', 1, x'00ff', 5000)",
        )
        v2.close()

        val db = helper.runMigrationsAndValidate(dbName, 3, true, Migrations.MIGRATION_2_3)

        // v2 exemplar rows survive untouched.
        db.query("SELECT gameId, day, puzzleNumber FROM puzzle_dailies").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("zip", cursor.getString(0))
            assertEquals("2026-10-31", cursor.getString(1))
            assertEquals(592, cursor.getInt(2))
        }
        // The v3 completions store takes rows (nullable firstCompletedAtMs on replays).
        db.execSQL(
            "INSERT INTO completions (puzzleRef, timeMs, hints, statsVersion, statsBlob, completedAtMs, firstCompletedAtMs, isRecord) " +
                "VALUES ('zip/2026-10-31/592', 95000, 2, 1, '{\"v\":1}', 7000, 7000, 1)",
        )
        db.query("SELECT puzzleRef, timeMs, firstCompletedAtMs, isRecord FROM completions").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("zip/2026-10-31/592", cursor.getString(0))
            assertEquals(95000L, cursor.getLong(1))
            assertEquals(7000L, cursor.getLong(2))
            assertEquals(1, cursor.getInt(3))
        }
        db.close()
    }

    @Test
    fun `fresh v3 database is created by the normal builder path`() {
        val dbName = "puzzlebox-fresh-v3.db"
        val db = helper.createDatabase(dbName, 3)
        db.query("SELECT COUNT(*) FROM completions").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        db.close()
    }

    @Test
    fun `migrate 3 to 4 reshapes daily_puzzles into the opaque envelope`() {
        val dbName = "puzzlebox-migration-3-4-test.db"
        val v3 = helper.createDatabase(dbName, 3)
        v3.execSQL(
            "INSERT INTO daily_puzzles (gameId, day, size, cells, fetchedAtMs) " +
                "VALUES ('zip', '2026-10-30', 4, '[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16]', 1000)",
        )
        v3.close()

        val db = helper.runMigrationsAndValidate(dbName, 4, true, Migrations.MIGRATION_3_4)

        // Identity survives; the parsed projection is dropped for the opaque envelope
        // (payload starts as zero bytes — the cache refills from the wire, ADR-0002).
        db.query("SELECT gameId, day, fetchedAtMs, LENGTH(payload) FROM daily_puzzles").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("zip", cursor.getString(0))
            assertEquals("2026-10-30", cursor.getString(1))
            assertEquals(1000L, cursor.getLong(2))
            assertEquals(0, cursor.getInt(3))
        }
        // The envelope store accepts real payloads (primary key gameId+day).
        db.execSQL(
            "INSERT INTO daily_puzzles (gameId, day, payload, fetchedAtMs) " +
                "VALUES ('zip', '2026-10-31', x'00ff10', 5000)",
        )
        db.query("SELECT payload FROM daily_puzzles WHERE day = '2026-10-31'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(3, cursor.getBlob(0).size)
        }
        db.close()
    }

    @Test
    fun `fresh v4 database is created by the normal builder path`() {
        val dbName = "puzzlebox-fresh-v4.db"
        val db = helper.createDatabase(dbName, 4)
        db.query("SELECT COUNT(*) FROM daily_puzzles").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        db.close()
    }

    @Test
    fun `migrate 4 to 5 drops daily_puzzles and keeps dailies and completions`() {
        val dbName = "puzzlebox-migration-4-5-test.db"
        val v4 = helper.createDatabase(dbName, 4)
        v4.execSQL(
            "INSERT INTO daily_puzzles (gameId, day, payload, fetchedAtMs) " +
                "VALUES ('zip', '2026-10-30', x'00ff10', 1000)",
        )
        v4.execSQL(
            "INSERT INTO puzzle_dailies (gameId, day, puzzleNumber, variant, formatVersion, payload, fetchedAtMs) " +
                "VALUES ('zip', '2026-10-31', 592, 'zip/n6', 1, x'00ff', 5000)",
        )
        v4.execSQL(
            "INSERT INTO completions (puzzleRef, timeMs, hints, statsVersion, statsBlob, completedAtMs, firstCompletedAtMs, isRecord) " +
                "VALUES ('zip/2026-10-31/592', 95000, 2, 1, '{\"v\":1}', 7000, 7000, 1)",
        )
        v4.close()

        val db = helper.runMigrationsAndValidate(dbName, 5, true, Migrations.MIGRATION_4_5)

        // The legacy exemplar table is gone.
        db.query("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'daily_puzzles'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        // Daily envelopes and completions survive untouched.
        db.query("SELECT gameId, day, puzzleNumber FROM puzzle_dailies").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("zip", cursor.getString(0))
            assertEquals("2026-10-31", cursor.getString(1))
            assertEquals(592, cursor.getInt(2))
        }
        db.query("SELECT puzzleRef, timeMs, isRecord FROM completions").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("zip/2026-10-31/592", cursor.getString(0))
            assertEquals(95000L, cursor.getLong(1))
            assertEquals(1, cursor.getInt(2))
        }
        db.close()
    }

    @Test
    fun `fresh v5 database is created by the normal builder path`() {
        val dbName = "puzzlebox-fresh-v5.db"
        val db = helper.createDatabase(dbName, 5)
        db.query("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'daily_puzzles'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        db.close()
    }

    @Test
    fun `migrate 5 to 6 keeps dailies and completions and opens the saved plays store`() {
        val dbName = "puzzlebox-migration-5-6-test.db"
        val v5 = helper.createDatabase(dbName, 5)
        v5.execSQL(
            "INSERT INTO puzzle_dailies (gameId, day, puzzleNumber, variant, formatVersion, payload, fetchedAtMs) " +
                "VALUES ('zip', '2026-10-31', 592, 'zip/n6', 1, x'00ff', 5000)",
        )
        v5.execSQL(
            "INSERT INTO completions (puzzleRef, timeMs, hints, statsVersion, statsBlob, completedAtMs, firstCompletedAtMs, isRecord) " +
                "VALUES ('zip/2026-10-31/592', 95000, 2, 1, '{\"v\":1}', 7000, 7000, 1)",
        )
        v5.close()

        val db = helper.runMigrationsAndValidate(dbName, 6, true, Migrations.MIGRATION_5_6)

        db.query("SELECT gameId, day, puzzleNumber FROM puzzle_dailies").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("zip", cursor.getString(0))
            assertEquals("2026-10-31", cursor.getString(1))
            assertEquals(592, cursor.getInt(2))
        }
        db.query("SELECT puzzleRef, timeMs, isRecord FROM completions").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("zip/2026-10-31/592", cursor.getString(0))
            assertEquals(95000L, cursor.getLong(1))
            assertEquals(1, cursor.getInt(2))
        }
        // The v6 saved plays store starts empty and takes blob rows keyed by (gameId, day).
        db.execSQL(
            "INSERT INTO saved_plays (gameId, day, blob, elapsedMs, updatedAt) " +
                "VALUES ('zip', '2026-11-01', x'0102', 4200, 9000)",
        )
        db.query("SELECT gameId, day, LENGTH(blob), elapsedMs, updatedAt FROM saved_plays").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("zip", cursor.getString(0))
            assertEquals("2026-11-01", cursor.getString(1))
            assertEquals(2, cursor.getInt(2))
            assertEquals(4200L, cursor.getLong(3))
            assertEquals(9000L, cursor.getLong(4))
        }
        db.close()
    }

    @Test
    fun `fresh v6 database is created by the normal builder path`() {
        val dbName = "puzzlebox-fresh-v6.db"
        val db = helper.createDatabase(dbName, 6)
        db.query("SELECT COUNT(*) FROM saved_plays").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        db.close()
    }

    @Test
    fun `migrate 6 to 7 keeps every daily record and saved play and opens four empty tables`() {
        val dbName = "puzzlebox-migration-6-7-test.db"
        val v6 = helper.createDatabase(dbName, 6)
        v6.execSQL(
            "INSERT INTO puzzle_dailies (gameId, day, puzzleNumber, variant, formatVersion, payload, fetchedAtMs) " +
                "VALUES ('zip', '2026-10-31', 592, 'zip/n6', 1, x'00ff', 5000)",
        )
        v6.execSQL(
            "INSERT INTO completions (puzzleRef, timeMs, hints, statsVersion, statsBlob, completedAtMs, firstCompletedAtMs, isRecord) " +
                "VALUES ('zip/2026-10-31/592', 95000, 2, 1, '{\"v\":1}', 7000, 7000, 1)",
        )
        v6.execSQL(
            "INSERT INTO saved_plays (gameId, day, blob, elapsedMs, updatedAt) " +
                "VALUES ('queens', '2026-11-01', x'0102', 4200, 9000)",
        )
        v6.close()

        // Validates against the committed 7.json (S17).
        val db = helper.runMigrationsAndValidate(dbName, 7, true, Migrations.MIGRATION_6_7)

        db.query(
            "SELECT gameId, day, puzzleNumber, variant, formatVersion, LENGTH(payload), fetchedAtMs FROM puzzle_dailies",
        ).use { cursor ->
            assertEquals(1, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals("zip", cursor.getString(0))
            assertEquals("2026-10-31", cursor.getString(1))
            assertEquals(592, cursor.getInt(2))
            assertEquals("zip/n6", cursor.getString(3))
            assertEquals(1, cursor.getInt(4))
            assertEquals(2, cursor.getInt(5))
            assertEquals(5000L, cursor.getLong(6))
        }
        db.query(
            "SELECT puzzleRef, timeMs, hints, statsVersion, statsBlob, completedAtMs, firstCompletedAtMs, isRecord FROM completions",
        ).use { cursor ->
            assertEquals(1, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals("zip/2026-10-31/592", cursor.getString(0))
            assertEquals(95000L, cursor.getLong(1))
            assertEquals(2, cursor.getInt(2))
            assertEquals(1, cursor.getInt(3))
            assertEquals("{\"v\":1}", cursor.getString(4))
            assertEquals(7000L, cursor.getLong(5))
            assertEquals(7000L, cursor.getLong(6))
            assertEquals(1, cursor.getInt(7))
        }
        db.query("SELECT gameId, day, blob, elapsedMs, updatedAt FROM saved_plays").use { cursor ->
            assertEquals(1, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals("queens", cursor.getString(0))
            assertEquals("2026-11-01", cursor.getString(1))
            assertTrue(byteArrayOf(1, 2).contentEquals(cursor.getBlob(2)))
            assertEquals(4200L, cursor.getLong(3))
            assertEquals(9000L, cursor.getLong(4))
        }
        for (table in listOf("drafts", "published_puzzles", "received_puzzles", "custom_saved_plays")) {
            db.query("SELECT COUNT(*) FROM `$table`").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
        db.close()
    }

    @Test
    fun `fresh v7 database is created by the normal builder path`() {
        val dbName = "puzzlebox-fresh-v7.db"
        val db = helper.createDatabase(dbName, 7)
        for (table in listOf("drafts", "published_puzzles", "received_puzzles", "custom_saved_plays")) {
            db.query("SELECT COUNT(*) FROM `$table`").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
        db.close()
    }
}
