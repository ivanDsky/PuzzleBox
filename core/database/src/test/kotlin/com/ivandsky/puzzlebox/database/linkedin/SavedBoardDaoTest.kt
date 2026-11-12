package com.ivandsky.puzzlebox.database.linkedin

import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The saved_boards store over real in-memory Room: upsert, reads, owner scoping, upload marker and adoption. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SavedBoardDaoTest {

    private lateinit var db: LinkedInDatabase
    private lateinit var dao: SavedBoardDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), LinkedInDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.savedBoardDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun board(
        game: String = "queens",
        day: String = "2026-11-01",
        profileId: String = "p1",
        ownerUid: String? = null,
        readAtMs: Long = 1_000L,
        complete: Boolean = false,
        uploadedFingerprint: String? = null,
    ) = SavedBoardEntity(game, day, profileId, ownerUid, readAtMs, complete, uploadedFingerprint)

    @Test
    fun `upsert inserts then replaces the board of the same game and day`() = runTest {
        dao.upsert(board(profileId = "p1", readAtMs = 1_000L, complete = false))
        dao.upsert(board(profileId = "p2", readAtMs = 2_000L, complete = true, ownerUid = "u1"))

        assertEquals(board(profileId = "p2", readAtMs = 2_000L, complete = true, ownerUid = "u1"), dao.get("queens", "2026-11-01"))
        assertNull(dao.get("queens", "2026-11-02"))
        assertNull(dao.get("zip", "2026-11-01"))
    }

    @Test
    fun `observe emits null then the board and re-emits on change`() = runTest {
        assertNull(dao.observe("queens", "2026-11-01").first())
        dao.upsert(board(readAtMs = 1_000L))
        assertEquals(1_000L, dao.observe("queens", "2026-11-01").first()?.readAtMs)
        dao.upsert(board(readAtMs = 2_000L))
        assertEquals(2_000L, dao.observe("queens", "2026-11-01").first()?.readAtMs)
    }

    @Test
    fun `observeDays lists the days with a board for one game in order`() = runTest {
        dao.upsert(board(day = "2026-11-03"))
        dao.upsert(board(day = "2026-11-01"))
        dao.upsert(board(game = "zip", day = "2026-11-02"))

        assertEquals(listOf("2026-11-01", "2026-11-03"), dao.observeDays("queens").first())
        assertEquals(listOf("2026-11-02"), dao.observeDays("zip").first())
        assertEquals(emptyList<String>(), dao.observeDays("tango").first())
    }

    @Test
    fun `observeVisibleDays lists the ownerless and the visible owner's days, all while no owner is visible`() = runTest {
        dao.upsert(board(day = "2026-11-01", ownerUid = "u1"))
        dao.upsert(board(day = "2026-11-02", ownerUid = "u2"))
        dao.upsert(board(day = "2026-11-03", ownerUid = null))
        dao.upsert(board(game = "zip", day = "2026-11-04", ownerUid = "u1"))

        assertEquals(listOf("2026-11-01", "2026-11-03"), dao.observeVisibleDays("queens", "u1").first())
        assertEquals(listOf("2026-11-02", "2026-11-03"), dao.observeVisibleDays("queens", "u2").first())
        assertEquals(listOf("2026-11-01", "2026-11-02", "2026-11-03"), dao.observeVisibleDays("queens", null).first())
        assertEquals(listOf("2026-11-04"), dao.observeVisibleDays("zip", "u1").first())
    }

    @Test
    fun `delete removes one board and leaves its ghosts and other boards`() = runTest {
        db.ghostDao().replace(
            "queens",
            "2026-11-01",
            listOf(GhostEntity("queens", "2026-11-01", "m1", "Mia", 61_000L, true, false, 1_000L)),
        )
        dao.upsert(board(day = "2026-11-01"))
        dao.upsert(board(day = "2026-11-02"))

        dao.delete("queens", "2026-11-01")

        assertNull(dao.get("queens", "2026-11-01"))
        assertEquals("2026-11-02", dao.get("queens", "2026-11-02")?.day)
        assertEquals(1, db.ghostDao().observe("queens", "2026-11-01").first().size)
    }

    @Test
    fun `deleteForOwner removes only that owner's boards`() = runTest {
        dao.upsert(board(day = "2026-11-01", ownerUid = "u1"))
        dao.upsert(board(day = "2026-11-02", ownerUid = "u2"))
        dao.upsert(board(day = "2026-11-03", ownerUid = null))

        dao.deleteForOwner("u1")

        assertEquals(listOf("2026-11-02", "2026-11-03"), dao.observeDays("queens").first())
    }

    @Test
    fun `pending lists the owner's boards without an acknowledged fingerprint`() = runTest {
        dao.upsert(board(day = "2026-11-01", ownerUid = "u1", uploadedFingerprint = null))
        dao.upsert(board(day = "2026-11-02", ownerUid = "u1", uploadedFingerprint = "fp"))
        dao.upsert(board(day = "2026-11-03", ownerUid = "u2", uploadedFingerprint = null))
        dao.upsert(board(day = "2026-11-04", ownerUid = null, uploadedFingerprint = null))
        dao.upsert(board(game = "zip", day = "2026-11-01", ownerUid = "u1", uploadedFingerprint = null))

        assertEquals(
            listOf("queens/2026-11-01", "zip/2026-11-01"),
            dao.pending("u1").map { "${it.game}/${it.day}" },
        )
    }

    @Test
    fun `setFingerprint records the acknowledged upload and a rewrite clears it`() = runTest {
        dao.upsert(board(ownerUid = "u1"))

        dao.setFingerprint("queens", "2026-11-01", "fp1")
        assertEquals("fp1", dao.get("queens", "2026-11-01")?.uploadedFingerprint)
        assertEquals(emptyList<SavedBoardEntity>(), dao.pending("u1"))

        dao.upsert(board(ownerUid = "u1", readAtMs = 2_000L, uploadedFingerprint = null))
        assertEquals(1, dao.pending("u1").size)
    }

    @Test
    fun `adoptNullOwner claims only the unowned boards`() = runTest {
        dao.upsert(board(day = "2026-11-01", ownerUid = null))
        dao.upsert(board(day = "2026-11-02", ownerUid = "u2"))

        dao.adoptNullOwner("u1")

        assertEquals("u1", dao.get("queens", "2026-11-01")?.ownerUid)
        assertEquals("u2", dao.get("queens", "2026-11-02")?.ownerUid)
    }
}
