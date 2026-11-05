package com.ivandsky.puzzlebox.database

import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The saved_plays store over real in-memory Room: upsert per daily, cap pruning, delete, slots. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SavedPlayDaoTest {

    private lateinit var db: PuzzleBoxDatabase
    private lateinit var dao: SavedPlayDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PuzzleBoxDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.savedPlayDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun row(gameId: String, day: String, updatedAt: Long, vararg bytes: Byte, elapsedMs: Long = 0L) =
        SavedPlayEntity(gameId = gameId, day = day, blob = bytes, elapsedMs = elapsedMs, updatedAt = updatedAt)

    @Test
    fun `saving the same daily again replaces its blob and elapsed`() = runTest {
        dao.saveAndPrune(row("zip", "2026-11-01", 1_000L, 1, 2, elapsedMs = 5_000L), cap = 5)
        dao.saveAndPrune(row("zip", "2026-11-01", 2_000L, 3, 4, 5, elapsedMs = 7_000L), cap = 5)

        assertArrayEquals(byteArrayOf(3, 4, 5), dao.blob("zip", "2026-11-01"))
        assertEquals(listOf(SavedPlaySlotRow("zip", "2026-11-01", 7_000L)), dao.observeSlots().first())
    }

    @Test
    fun `missing slot reads as null`() = runTest {
        assertNull(dao.blob("zip", "2026-11-01"))
    }

    @Test
    fun `saving past the cap prunes the oldest slots`() = runTest {
        dao.saveAndPrune(row("zip", "2026-11-01", 1_000L, 1), cap = 2)
        dao.saveAndPrune(row("tango", "2026-11-01", 2_000L, 2), cap = 2)
        dao.saveAndPrune(row("zip", "2026-11-02", 3_000L, 3), cap = 2)

        assertNull(dao.blob("zip", "2026-11-01"))
        assertEquals(
            listOf(SavedPlaySlotRow("zip", "2026-11-02", 0L), SavedPlaySlotRow("tango", "2026-11-01", 0L)),
            dao.observeSlots().first(),
        )
    }

    @Test
    fun `resaving an old slot refreshes it so another slot is pruned`() = runTest {
        dao.saveAndPrune(row("zip", "2026-11-01", 1_000L, 1), cap = 2)
        dao.saveAndPrune(row("tango", "2026-11-01", 2_000L, 2), cap = 2)
        dao.saveAndPrune(row("zip", "2026-11-01", 3_000L, 9), cap = 2)
        dao.saveAndPrune(row("queens", "2026-11-01", 4_000L, 4), cap = 2)

        assertEquals(
            listOf(SavedPlaySlotRow("queens", "2026-11-01", 0L), SavedPlaySlotRow("zip", "2026-11-01", 0L)),
            dao.observeSlots().first(),
        )
    }

    @Test
    fun `delete removes only that slot`() = runTest {
        dao.saveAndPrune(row("zip", "2026-11-01", 1_000L, 1), cap = 5)
        dao.saveAndPrune(row("zip", "2026-11-02", 2_000L, 2), cap = 5)

        dao.delete("zip", "2026-11-01")

        assertNull(dao.blob("zip", "2026-11-01"))
        assertArrayEquals(byteArrayOf(2), dao.blob("zip", "2026-11-02"))
        assertEquals(listOf(SavedPlaySlotRow("zip", "2026-11-02", 0L)), dao.observeSlots().first())
    }
}
