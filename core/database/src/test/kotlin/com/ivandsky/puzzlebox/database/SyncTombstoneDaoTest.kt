package com.ivandsky.puzzlebox.database

import androidx.room.Room
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The sync_tombstones store over real in-memory Room. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncTombstoneDaoTest {

    private lateinit var db: PuzzleBoxDatabase
    private lateinit var dao: SyncTombstoneDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PuzzleBoxDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.syncTombstoneDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun row(docId: String, owner: String = "u1", deletedAtMs: Long = 10L, eligibleAtMs: Long = 20L) =
        SyncTombstoneEntity("draft", docId, owner, deletedAtMs, eligibleAtMs)

    @Test
    fun `pending eligible returns only this owner's unsent tombstones whose delay passed`() = runTest {
        dao.insertOrReplace(row("a", eligibleAtMs = 20L))
        dao.insertOrReplace(row("b", eligibleAtMs = 30L))
        dao.insertOrReplace(row("c", owner = "u2", eligibleAtMs = 20L))

        assertEquals(listOf("a"), dao.pendingEligible("u1", 25L).map { it.docId })
        assertEquals(listOf("a", "b"), dao.pendingEligible("u1", 30L).map { it.docId })
        assertEquals(listOf("c"), dao.pendingEligible("u2", 30L).map { it.docId })
    }

    @Test
    fun `insert replaces the same key and delete removes one row`() = runTest {
        dao.insertOrReplace(row("a", eligibleAtMs = 20L))
        dao.insertOrReplace(row("a", eligibleAtMs = 50L))
        dao.insertOrReplace(row("b"))

        assertEquals(listOf("b"), dao.pendingEligible("u1", 30L).map { it.docId })
        dao.delete("draft", "b", "u1")
        assertEquals(emptyList<String>(), dao.pendingEligible("u1", 30L).map { it.docId })
        assertEquals(listOf("a"), dao.pendingEligible("u1", 50L).map { it.docId })
    }

    @Test
    fun `mark uploaded hides a tombstone and clear uploaded brings back only that owner's`() = runTest {
        dao.insertOrReplace(row("a"))
        dao.insertOrReplace(row("c", owner = "u2"))
        dao.markUploaded("draft", "a", "u1")
        dao.markUploaded("draft", "c", "u2")

        assertEquals(emptyList<String>(), dao.pendingEligible("u1", 99L).map { it.docId })

        dao.clearUploaded("u1")

        assertEquals(listOf("a"), dao.pendingEligible("u1", 99L).map { it.docId })
        assertEquals(emptyList<String>(), dao.pendingEligible("u2", 99L).map { it.docId })
    }
}
