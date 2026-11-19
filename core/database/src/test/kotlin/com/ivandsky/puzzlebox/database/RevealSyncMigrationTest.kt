package com.ivandsky.puzzlebox.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The reveals-across-devices bump ([RevealSyncMigration]); validates against the committed 16.json. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RevealSyncMigrationTest {

    private val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PuzzleBoxDatabase::class.java,
    )

    @Test
    fun `migrate 15 to 16 keeps a seen reveal with its scores, ownerless and not uploaded`() {
        val dbName = "puzzlebox-reveal-sync-15-16-test.db"
        val v15 = helper.createDatabase(dbName, 15)
        v15.execSQL(
            "INSERT INTO matches_local (code, role, opponentName, openedAt, revealSeenAt, dismissed, revealMyScore, " +
                "revealTheirScore) VALUES ('K7Q2MX', 'Invite', 'Sam', 5000, 9000, 0, 3, 1)",
        )
        v15.execSQL(
            "INSERT INTO matches_local (code, role, opponentName, openedAt, dismissed) VALUES ('D7Q2MX', 'Participant', NULL, 6000, 1)",
        )
        v15.close()

        val db = helper.runMigrationsAndValidate(dbName, 16, true, RevealSyncMigration.MIGRATION_15_16)

        db.query(
            "SELECT code, revealSeenAt, revealMyScore, revealTheirScore, dismissed, ownerUid, revealUploaded " +
                "FROM matches_local ORDER BY openedAt",
        ).use { cursor ->
            assertEquals(2, cursor.count)
            assertTrue(cursor.moveToNext())
            assertEquals("K7Q2MX", cursor.getString(0))
            assertEquals(9000L, cursor.getLong(1))
            assertEquals(3, cursor.getInt(2))
            assertEquals(1, cursor.getInt(3))
            assertTrue(cursor.isNull(5))
            assertEquals(0, cursor.getInt(6))
            assertTrue(cursor.moveToNext())
            assertEquals("D7Q2MX", cursor.getString(0))
            assertTrue(cursor.isNull(1))
            assertEquals(1, cursor.getInt(4))
            assertTrue(cursor.isNull(5))
            assertEquals(0, cursor.getInt(6))
        }
        db.close()
    }
}
