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

/** The cloud-sync main database bump ([CloudSyncMigration]); validates against the committed 11.json. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CloudSyncMigrationTest {

    private val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PuzzleBoxDatabase::class.java,
    )

    private fun insertV10Rows(db: SupportSQLiteDatabase) {
        for (i in 1..3) {
            db.execSQL(
                "INSERT INTO completions (puzzleRef, timeMs, hints, statsVersion, statsBlob, completedAtMs, " +
                    "firstCompletedAtMs, isRecord) VALUES ('zip/2026-10-3$i/59$i', 9500$i, $i, 1, '{}', 700$i, 700$i, 1)",
            )
        }
        db.execSQL(
            "INSERT INTO drafts (id, gameId, variant, draftFormatVersion, blob, title, updatedAt, imported) " +
                "VALUES ('d1', 'zip', 'zip/n6', 1, x'00ff', 'Draft', 5000, 0)",
        )
        db.execSQL(
            "INSERT INTO published_puzzles (code, gameId, variant, title, authorTimeMs, createdAt, unpublished, authorHints) " +
                "VALUES ('PUB123', 'zip', 'zip/n6', 'Pub', 61000, 6000, 0, 1)",
        )
        db.execSQL(
            "INSERT INTO received_puzzles (code, gameId, variant, sharedFormatVersion, puzzleBytes, title, authorName, " +
                "note, authorTimeMs, authorHints, authorUid, openedAt, unpublished, postState, postAttempts) " +
                "VALUES ('REC456', 'zip', 'zip/n6', 1, x'0102', 'Rec', 'Sam', '', 70000, 0, 'uid-sam', 8000, 0, 'Posted', 0)",
        )
    }

    private fun SupportSQLiteDatabase.longs(sql: String): List<Long> =
        query(sql).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) }
        }

    @Test
    fun `migrate 10 to 11 keeps every row with null owner and gives each completion a distinct syncId`() {
        val dbName = "puzzlebox-cloud-sync-10-11-test.db"
        val v10 = helper.createDatabase(dbName, 10)
        insertV10Rows(v10)
        v10.close()

        val db = helper.runMigrationsAndValidate(dbName, 11, true, CloudSyncMigration.MIGRATION_10_11)

        db.query("SELECT puzzleRef, timeMs, ownerUid, syncId, uploaded FROM completions ORDER BY id").use { cursor ->
            assertEquals(3, cursor.count)
            val ids = mutableSetOf<String>()
            var i = 1
            while (cursor.moveToNext()) {
                assertEquals("zip/2026-10-3$i/59$i", cursor.getString(0))
                assertEquals(9500L * 10 + i, cursor.getLong(1))
                assertTrue(cursor.isNull(2))
                val syncId = cursor.getString(3)
                assertTrue("syncId $syncId is 32 lowercase hex", Regex("[0-9a-f]{32}").matches(syncId))
                ids += syncId
                assertEquals(0, cursor.getInt(4))
                i++
            }
            assertEquals("every backfilled syncId is distinct", 3, ids.size)
        }

        for (table in listOf("drafts", "published_puzzles", "received_puzzles")) {
            db.query("SELECT COUNT(*), COUNT(ownerUid), COUNT(uploadedFingerprint) FROM $table").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("$table keeps its row", 1, cursor.getInt(0))
                assertEquals("$table owner stays null", 0, cursor.getInt(1))
                assertEquals("$table fingerprint stays null", 0, cursor.getInt(2))
            }
        }
        db.query("SELECT title FROM drafts").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Draft", cursor.getString(0))
        }
        db.query("SELECT authorUid FROM received_puzzles").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("uid-sam", cursor.getString(0))
        }
        assertEquals(listOf(0L), db.longs("SELECT COUNT(*) FROM sync_tombstones"))
        db.close()
    }

    @Test
    fun `the syncId column is unique and the tombstone key is kind docId ownerUid`() {
        val dbName = "puzzlebox-cloud-sync-unique-test.db"
        val v10 = helper.createDatabase(dbName, 10)
        insertV10Rows(v10)
        v10.close()

        val db = helper.runMigrationsAndValidate(dbName, 11, true, CloudSyncMigration.MIGRATION_10_11)

        db.execSQL(
            "INSERT INTO completions (puzzleRef, timeMs, hints, statsVersion, statsBlob, completedAtMs, " +
                "firstCompletedAtMs, isRecord, syncId) VALUES ('zip/x/1', 1, 0, 1, '{}', 1, 1, 1, 'aaaa')",
        )
        val duplicate = runCatching {
            db.execSQL(
                "INSERT INTO completions (puzzleRef, timeMs, hints, statsVersion, statsBlob, completedAtMs, " +
                    "firstCompletedAtMs, isRecord, syncId) VALUES ('zip/x/2', 1, 0, 1, '{}', 1, 1, 1, 'aaaa')",
            )
        }
        assertTrue("a second row with the same syncId is rejected", duplicate.isFailure)

        db.execSQL(
            "INSERT INTO sync_tombstones (kind, docId, ownerUid, deletedAtMs, eligibleAtMs) VALUES ('draft', 'd1', 'u1', 10, 20)",
        )
        db.execSQL(
            "INSERT INTO sync_tombstones (kind, docId, ownerUid, deletedAtMs, eligibleAtMs) VALUES ('draft', 'd1', 'u2', 10, 20)",
        )
        val samePrimaryKey = runCatching {
            db.execSQL(
                "INSERT INTO sync_tombstones (kind, docId, ownerUid, deletedAtMs, eligibleAtMs) VALUES ('draft', 'd1', 'u1', 11, 21)",
            )
        }
        assertTrue("the (kind, docId, ownerUid) key is unique", samePrimaryKey.isFailure)
        assertEquals(listOf(0L, 0L), db.longs("SELECT uploaded FROM sync_tombstones"))
        db.close()
    }

    @Test
    fun `v11 schema carries sync defaults for source and uploaded`() {
        val dbName = "puzzlebox-fresh-v11.db"
        val db = helper.createDatabase(dbName, 11)

        // source and uploaded are omitted: the column defaults fill them.
        db.execSQL(
            "INSERT INTO completions " +
                "(puzzleRef, timeMs, hints, statsVersion, statsBlob, completedAtMs, firstCompletedAtMs, isRecord, syncId) " +
                "VALUES ('queens/2026-11-08/400', 61000, 1, 1, '{\"v\":1}', 9200, 9200, 1, 'abc')",
        )
        db.query("SELECT source, uploaded, ownerUid FROM completions").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("app", cursor.getString(0))
            assertEquals(0, cursor.getInt(1))
            assertTrue(cursor.isNull(2))
        }
        db.close()
    }
}
