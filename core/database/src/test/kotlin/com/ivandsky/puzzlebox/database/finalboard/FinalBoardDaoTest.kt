package com.ivandsky.puzzlebox.database.finalboard

import androidx.room.Room
import com.ivandsky.puzzlebox.database.RoomQueryThread
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

/** Final board put/header/entries over real in-memory Room: round trip, empty board, replace, cascade. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FinalBoardDaoTest {

    private lateinit var db: FinalBoardDatabase
    private lateinit var dao: FinalBoardDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), FinalBoardDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(RoomQueryThread.executor)
            .build()
        dao = db.finalBoardDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun header(gameId: String = "zip", day: String = "2026-10-31", count: Int, fetchedAtMs: Long = 5_000L) =
        FinalBoardEntity(gameId, day, count, fetchedAtMs)

    private fun entry(uid: String, gameId: String = "zip", day: String = "2026-10-31", timeMs: Long = 60_000L) =
        FinalBoardEntryEntity(gameId, day, uid, "Name $uid", "ref-$uid", timeMs, 1, 9_000L)

    @Test
    fun `a never fetched board has no header and no entries`() = runTest {
        assertNull(dao.header("zip", "2026-10-31"))
        assertEquals(emptyList<FinalBoardEntryEntity>(), dao.entries("zip", "2026-10-31"))
    }

    @Test
    fun `put round trips the header and its entries`() = runTest {
        val entries = listOf(entry("a"), entry("b", timeMs = 70_000L))
        dao.put(header(count = 2), entries)

        assertEquals(header(count = 2), dao.header("zip", "2026-10-31"))
        assertEquals(entries, dao.entries("zip", "2026-10-31"))
    }

    @Test
    fun `an empty board is a stored header with count 0`() = runTest {
        dao.put(header(count = 0), emptyList())

        assertEquals(header(count = 0), dao.header("zip", "2026-10-31"))
        assertEquals(emptyList<FinalBoardEntryEntity>(), dao.entries("zip", "2026-10-31"))
    }

    @Test
    fun `put replaces the old header and drops entries not in the new board`() = runTest {
        dao.put(header(count = 2), listOf(entry("a"), entry("b")))
        dao.put(header(count = 1, fetchedAtMs = 8_000L), listOf(entry("c")))

        assertEquals(header(count = 1, fetchedAtMs = 8_000L), dao.header("zip", "2026-10-31"))
        assertEquals(listOf(entry("c")), dao.entries("zip", "2026-10-31"))
    }

    @Test
    fun `boards of other games and days stay apart`() = runTest {
        dao.put(header(count = 1), listOf(entry("a")))
        dao.put(header(gameId = "queens", count = 1), listOf(entry("q", gameId = "queens")))
        dao.put(header(day = "2026-11-01", count = 1), listOf(entry("d", day = "2026-11-01")))

        assertEquals(listOf(entry("a")), dao.entries("zip", "2026-10-31"))
        assertEquals(listOf(entry("q", gameId = "queens")), dao.entries("queens", "2026-10-31"))
        assertEquals(listOf(entry("d", day = "2026-11-01")), dao.entries("zip", "2026-11-01"))
    }

    @Test
    fun `deleting a header cascades to its entries only`() = runTest {
        dao.put(header(count = 1), listOf(entry("a")))
        dao.put(header(day = "2026-11-01", count = 1), listOf(entry("d", day = "2026-11-01")))

        dao.delete("zip", "2026-10-31")

        assertNull(dao.header("zip", "2026-10-31"))
        assertEquals(emptyList<FinalBoardEntryEntity>(), dao.entries("zip", "2026-10-31"))
        assertEquals(listOf(entry("d", day = "2026-11-01")), dao.entries("zip", "2026-11-01"))
    }
}
