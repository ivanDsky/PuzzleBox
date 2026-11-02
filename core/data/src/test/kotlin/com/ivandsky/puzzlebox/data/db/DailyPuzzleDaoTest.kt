package com.ivandsky.puzzlebox.data.db

import androidx.room.Room
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DailyPuzzleDaoTest {

    private lateinit var db: PuzzleBoxDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PuzzleBoxDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entity(gameId: String = "zip", day: String, payload: ByteArray = byteArrayOf(1, 2, 3)) =
        DailyPuzzleEntity(
            gameId = gameId,
            day = day,
            payload = payload,
            fetchedAtMs = 0L,
        )

    @Test
    fun `upsert then read round-trips`() = runTest {
        db.dailyPuzzleDao().upsert(entity(day = "2026-10-31"))

        assertEquals(entity(day = "2026-10-31"), db.dailyPuzzleDao().get("zip", "2026-10-31"))
    }

    @Test
    fun `upsert with same key replaces row`() = runTest {
        val dao = db.dailyPuzzleDao()
        dao.upsert(entity(day = "2026-10-31"))
        dao.upsert(entity(day = "2026-10-31").copy(payload = byteArrayOf(9, 9)))

        assertEquals(1, dao.count())
        assertTrue(dao.get("zip", "2026-10-31")!!.payload.contentEquals(byteArrayOf(9, 9)))
    }

    @Test
    fun `observeByGame filters by game and sorts newest day first`() = runTest {
        val dao = db.dailyPuzzleDao()
        dao.upsert(entity(gameId = "queens", day = "2026-10-31"))
        dao.upsert(entity(day = "2026-10-30"))
        dao.upsert(entity(day = "2026-10-31"))

        dao.observeByGame("zip").test {
            assertEquals(listOf("2026-10-31", "2026-10-30"), awaitItem().map { it.day })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `get returns null for missing key`() = runTest {
        assertNull(db.dailyPuzzleDao().get("zip", "1999-01-01"))
    }

    @Test
    fun `clear removes everything`() = runTest {
        val dao = db.dailyPuzzleDao()
        dao.upsert(entity(day = "2026-10-31"))

        dao.clear()

        assertEquals(0, dao.count())
    }
}
