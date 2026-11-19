package com.ivandsky.puzzlebox.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The rank-history bump ([RankHistoryMigration]); validates against the committed 16.json and 17.json. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RankHistoryMigrationTest {

    private val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PuzzleBoxDatabase::class.java,
    )

    @Test
    fun `migrate 16 to 17 opens an empty rank history and keeps what v16 held`() {
        val dbName = "puzzlebox-rank-history-16-17-test.db"
        val v16 = helper.createDatabase(dbName, 16)
        v16.execSQL(
            "INSERT INTO matches_local (code, role, opponentName, openedAt, dismissed, ownerUid, revealUploaded) " +
                "VALUES ('K7Q2MX', 'Invite', 'Sam', 5000, 0, 'ann-uid', 1)",
        )
        v16.close()

        val db = helper.runMigrationsAndValidate(dbName, 17, true, RankHistoryMigration.MIGRATION_16_17)

        db.query("SELECT COUNT(*) FROM rank_history").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        // Only the owner's own numbers: no friend name or id column.
        db.query("SELECT name FROM pragma_table_info('rank_history') ORDER BY cid").use { cursor ->
            val columns = generateSequence { if (cursor.moveToNext()) cursor.getString(0) else null }.toList()
            assertEquals(listOf("ownerUid", "gameId", "weekStart", "pointsPerDay", "friendCount", "formatVersion"), columns)
        }
        db.query("SELECT code, opponentName, ownerUid, revealUploaded FROM matches_local").use { cursor ->
            assertEquals(1, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals("K7Q2MX", cursor.getString(0))
            assertEquals("Sam", cursor.getString(1))
            assertEquals("ann-uid", cursor.getString(2))
            assertEquals(1, cursor.getInt(3))
        }
        db.close()
    }

    @Test
    fun `the migrated rank history takes a week row`() {
        val dbName = "puzzlebox-rank-history-16-17-insert-test.db"
        helper.createDatabase(dbName, 16).close()

        val db = helper.runMigrationsAndValidate(dbName, 17, true, RankHistoryMigration.MIGRATION_16_17)
        db.execSQL(
            "INSERT INTO rank_history (ownerUid, gameId, weekStart, pointsPerDay, friendCount, formatVersion) " +
                "VALUES ('ann-uid', 'zip', '2026-11-09', 7.5, 3, 1)",
        )

        db.query("SELECT ownerUid, gameId, weekStart, pointsPerDay, friendCount, formatVersion FROM rank_history").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("ann-uid", cursor.getString(0))
            assertEquals("zip", cursor.getString(1))
            assertEquals("2026-11-09", cursor.getString(2))
            assertEquals(7.5, cursor.getDouble(3), 0.0)
            assertEquals(3, cursor.getInt(4))
            assertEquals(1, cursor.getInt(5))
        }
        db.close()
    }
}
