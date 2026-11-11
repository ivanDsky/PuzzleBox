package com.ivandsky.puzzlebox.database.linkedin

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The LinkedIn database's first migration ([LinkedInMigrations.MIGRATION_1_2]); validates against the committed 2.json. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LinkedInDatabaseMigrationTest {

    private val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LinkedInDatabase::class.java,
    )

    private fun SupportSQLiteDatabase.ghost(game: String, day: String, memberRef: String, fetchedAtMs: Long) {
        execSQL(
            "INSERT INTO ghosts (game, day, memberRef, name, timeMs, hintFree, flawless, fetchedAtMs) " +
                "VALUES ('$game', '$day', '$memberRef', 'Name $memberRef', 61000, 1, 0, $fetchedAtMs)",
        )
    }

    private fun SupportSQLiteDatabase.fetchLog(game: String, day: String, item: String, profileId: String, fetchedAtMs: Long) {
        execSQL(
            "INSERT INTO fetch_log (game, day, item, profileId, fetchedAtMs, outcome) " +
                "VALUES ('$game', '$day', '$item', '$profileId', $fetchedAtMs, 'Board')",
        )
    }

    private fun insertV1Rows(db: SupportSQLiteDatabase) {
        // queens/2026-11-01: two ghosts, read at 1000 and 3000, fetched for profile p1.
        db.ghost("queens", "2026-11-01", "m1", 1_000L)
        db.ghost("queens", "2026-11-01", "m2", 3_000L)
        db.fetchLog("queens", "2026-11-01", "Board", "p1", 3_000L)
        // queens/2026-11-02: one ghost, own and board entries of p2.
        db.ghost("queens", "2026-11-02", "m1", 5_000L)
        db.fetchLog("queens", "2026-11-02", "Own", "p2", 4_900L)
        db.fetchLog("queens", "2026-11-02", "Board", "p2", 5_000L)
        // zip/2026-11-01: one ghost, same day as the first board but another game.
        db.ghost("zip", "2026-11-01", "m1", 7_000L)
        db.fetchLog("zip", "2026-11-01", "Board", "p1", 7_000L)
        // tango/2026-11-01: a ghost with no fetch-log entry gets no saved board.
        db.ghost("tango", "2026-11-01", "m1", 9_000L)
        // queens/2026-11-03: a fetch-log entry with no ghost gets no saved board.
        db.fetchLog("queens", "2026-11-03", "Own", "p1", 9_500L)
    }

    private data class Board(
        val game: String,
        val day: String,
        val profileId: String,
        val ownerNull: Boolean,
        val readAtMs: Long,
        val complete: Int,
        val fingerprintNull: Boolean,
    )

    private fun SupportSQLiteDatabase.boards(): List<Board> =
        query(
            "SELECT game, day, profileId, ownerUid IS NULL, readAtMs, complete, uploadedFingerprint IS NULL " +
                "FROM saved_boards ORDER BY game, day",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        Board(
                            cursor.getString(0),
                            cursor.getString(1),
                            cursor.getString(2),
                            cursor.getInt(3) == 1,
                            cursor.getLong(4),
                            cursor.getInt(5),
                            cursor.getInt(6) == 1,
                        ),
                    )
                }
            }
        }

    @Test
    fun `migrate 1 to 2 keeps every ghost and fetch log row and backfills one saved board per day with a profile`() {
        val dbName = "puzzlebox-linkedin-1-2-test.db"
        val v1 = helper.createDatabase(dbName, 1)
        insertV1Rows(v1)
        v1.close()

        val db = helper.runMigrationsAndValidate(dbName, 2, true, LinkedInMigrations.MIGRATION_1_2)

        db.query("SELECT COUNT(*) FROM ghosts").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("every ghost row is kept", 5, cursor.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM fetch_log").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("every fetch-log row is kept", 5, cursor.getInt(0))
        }
        db.query("SELECT name, timeMs, hintFree, flawless, fetchedAtMs FROM ghosts WHERE game = 'queens' AND day = '2026-11-01' AND memberRef = 'm2'")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Name m2", cursor.getString(0))
                assertEquals(61_000L, cursor.getLong(1))
                assertEquals(1, cursor.getInt(2))
                assertEquals(0, cursor.getInt(3))
                assertEquals(3_000L, cursor.getLong(4))
            }

        assertEquals(
            listOf(
                Board("queens", "2026-11-01", "p1", ownerNull = true, readAtMs = 3_000L, complete = 0, fingerprintNull = true),
                Board("queens", "2026-11-02", "p2", ownerNull = true, readAtMs = 5_000L, complete = 0, fingerprintNull = true),
                Board("zip", "2026-11-01", "p1", ownerNull = true, readAtMs = 7_000L, complete = 0, fingerprintNull = true),
            ),
            db.boards(),
        )
        db.close()
    }

    @Test
    fun `a day read for two profiles backfills one board for the profile of the latest board read`() {
        val dbName = "puzzlebox-linkedin-1-2-two-profiles-test.db"
        val v1 = helper.createDatabase(dbName, 1)
        v1.ghost("queens", "2026-11-01", "m1", 2_000L)
        v1.fetchLog("queens", "2026-11-01", "Board", "pa", 1_000L)
        v1.fetchLog("queens", "2026-11-01", "Board", "pb", 2_000L)
        v1.fetchLog("queens", "2026-11-01", "Own", "pc", 3_000L)
        v1.close()

        val db = helper.runMigrationsAndValidate(dbName, 2, true, LinkedInMigrations.MIGRATION_1_2)

        assertEquals(
            listOf(Board("queens", "2026-11-01", "pb", ownerNull = true, readAtMs = 2_000L, complete = 0, fingerprintNull = true)),
            db.boards(),
        )
        db.close()
    }

    @Test
    fun `an empty v1 database migrates to an empty saved_boards table`() {
        val dbName = "puzzlebox-linkedin-1-2-empty-test.db"
        helper.createDatabase(dbName, 1).close()

        val db = helper.runMigrationsAndValidate(dbName, 2, true, LinkedInMigrations.MIGRATION_1_2)

        assertEquals(emptyList<Board>(), db.boards())
        db.close()
    }

    @Test
    fun `saved boards are keyed by game and day`() {
        val dbName = "puzzlebox-linkedin-1-2-key-test.db"
        helper.createDatabase(dbName, 1).close()
        val db = helper.runMigrationsAndValidate(dbName, 2, true, LinkedInMigrations.MIGRATION_1_2)

        db.execSQL("INSERT INTO saved_boards (game, day, profileId, readAtMs, complete) VALUES ('queens', '2026-11-01', 'p1', 1, 1)")
        val samePrimaryKey = runCatching {
            db.execSQL("INSERT INTO saved_boards (game, day, profileId, readAtMs, complete) VALUES ('queens', '2026-11-01', 'p2', 2, 0)")
        }
        assertTrue("(game, day) is unique", samePrimaryKey.isFailure)
        db.execSQL("INSERT INTO saved_boards (game, day, profileId, readAtMs, complete) VALUES ('zip', '2026-11-01', 'p1', 1, 1)")
        db.close()
    }

    @Test
    fun `fresh v2 database is created by the normal builder path`() {
        val dbName = "puzzlebox-linkedin-fresh-v2.db"
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(dbName)
        val database = Room.databaseBuilder(context, LinkedInDatabase::class.java, dbName)
            .addMigrations(*LinkedInMigrations.ALL)
            .build()
        try {
            val db = database.openHelper.writableDatabase
            assertEquals(2, db.version)
            db.query("SELECT COUNT(*) FROM saved_boards").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        } finally {
            database.close()
            context.deleteDatabase(dbName)
        }
    }
}
