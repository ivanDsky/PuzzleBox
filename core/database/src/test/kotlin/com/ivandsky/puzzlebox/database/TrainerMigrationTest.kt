package com.ivandsky.puzzlebox.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Trainer bump ([TrainerMigration]); validates against the committed 17.json and 18.json. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrainerMigrationTest {

    private val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PuzzleBoxDatabase::class.java,
    )

    private fun SupportSQLiteDatabase.tables(): List<String> =
        query(
            "SELECT name FROM sqlite_master WHERE type = 'table' " +
                "AND name NOT LIKE 'sqlite_%' AND name NOT IN ('android_metadata', 'room_master_table') ORDER BY name",
        ).use { cursor -> generateSequence { if (cursor.moveToNext()) cursor.getString(0) else null }.toList() }

    private fun SupportSQLiteDatabase.columns(table: String): List<String> =
        query("SELECT name FROM pragma_table_info('$table') ORDER BY cid")
            .use { cursor -> generateSequence { if (cursor.moveToNext()) cursor.getString(0) else null }.toList() }

    private fun SupportSQLiteDatabase.count(table: String): Int =
        query("SELECT COUNT(*) FROM $table").use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }

    @Test
    fun `migrate 17 to 18 opens two empty tables and keeps what v17 held`() {
        val dbName = "puzzlebox-trainer-17-18-test.db"
        val v17 = helper.createDatabase(dbName, 17)
        v17.execSQL(
            "INSERT INTO matches_local (code, role, opponentName, openedAt, dismissed, ownerUid, revealUploaded) " +
                "VALUES ('K7Q2MX', 'Invite', 'Sam', 5000, 0, 'ann-uid', 1)",
        )
        v17.execSQL(
            "INSERT INTO puzzle_dailies (gameId, day, puzzleNumber, variant, formatVersion, payload, fetchedAtMs) " +
                "VALUES ('queens', '2026-11-08', 400, 'queens/n8', 1, x'00ff', 5000)",
        )
        v17.execSQL(
            "INSERT INTO rank_history (ownerUid, gameId, weekStart, pointsPerDay, friendCount, formatVersion) " +
                "VALUES ('ann-uid', 'zip', '2026-11-09', 7.5, 3, 1)",
        )
        val tablesAt17 = v17.tables()
        v17.close()

        val db = helper.runMigrationsAndValidate(dbName, 18, true, TrainerMigration.MIGRATION_17_18)

        // Nothing but the two new tables appears, and both are empty: no backfill.
        assertEquals((tablesAt17 + listOf("trainer_progress", "trainer_puzzles")).sorted(), db.tables())
        assertEquals(0, db.count("trainer_progress"))
        assertEquals(0, db.count("trainer_puzzles"))
        assertEquals(
            listOf("id", "ownerUid", "gameId", "techniqueId", "mark", "inRow", "answeredAtMs", "uploaded"),
            db.columns("trainer_progress"),
        )
        assertEquals(
            listOf("gameId", "puzzleNumber", "day", "variant", "formatVersion", "payload", "fetchedAtMs"),
            db.columns("trainer_puzzles"),
        )
        // The existing rows are untouched.
        db.query("SELECT code, opponentName, ownerUid, revealUploaded FROM matches_local").use { cursor ->
            assertEquals(1, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals("K7Q2MX", cursor.getString(0))
            assertEquals("Sam", cursor.getString(1))
            assertEquals("ann-uid", cursor.getString(2))
            assertEquals(1, cursor.getInt(3))
        }
        assertEquals(1, db.count("puzzle_dailies"))
        assertEquals(1, db.count("rank_history"))
        db.close()
    }

    @Test
    fun `the migrated tables take rows, an unmarked progress row starting not uploaded`() {
        val dbName = "puzzlebox-trainer-17-18-insert-test.db"
        helper.createDatabase(dbName, 17).close()

        val db = helper.runMigrationsAndValidate(dbName, 18, true, TrainerMigration.MIGRATION_17_18)
        db.execSQL(
            "INSERT INTO trainer_progress (ownerUid, gameId, techniqueId, mark, inRow, answeredAtMs) " +
                "VALUES (NULL, 'queens', 'line-lock', 'practicing', 2, 9000)",
        )
        db.execSQL(
            "INSERT INTO trainer_puzzles (gameId, puzzleNumber, day, variant, formatVersion, payload, fetchedAtMs) " +
                "VALUES ('queens', 400, '2026-11-08', 'queens/n8', 1, x'00ff10', 5000)",
        )

        db.query("SELECT id, ownerUid, gameId, techniqueId, mark, inRow, answeredAtMs, uploaded FROM trainer_progress").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(0))
            assertTrue(cursor.isNull(1))
            assertEquals("queens", cursor.getString(2))
            assertEquals("line-lock", cursor.getString(3))
            assertEquals("practicing", cursor.getString(4))
            assertEquals(2, cursor.getInt(5))
            assertEquals(9000L, cursor.getLong(6))
            assertEquals(0, cursor.getInt(7))
        }
        db.query("SELECT payload FROM trainer_puzzles WHERE gameId = 'queens' AND puzzleNumber = 400").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(3, cursor.getBlob(0).size)
        }
        db.close()
    }
}
