package com.ivandsky.puzzlebox.database

import androidx.room.Room
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The rank_history store over real in-memory Room. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RankHistoryDaoTest {

    private lateinit var db: PuzzleBoxDatabase
    private lateinit var dao: RankHistoryDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PuzzleBoxDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(RoomQueryThread.executor)
            .build()
        dao = db.rankHistoryDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun week(weekStart: String, pointsPerDay: Double = 7.5, ownerUid: String = "ann", gameId: String = "zip") =
        RankHistoryEntity(ownerUid, gameId, weekStart, pointsPerDay, friendCount = 3, formatVersion = 1)

    @Test
    fun `a second insert for the same owner game and week leaves the first row`() = runTest {
        val first = week("2026-11-09", pointsPerDay = 7.5)

        assertNotEquals(-1L, dao.insertOnce(first))
        assertEquals(-1L, dao.insertOnce(week("2026-11-09", pointsPerDay = 1.0)))

        assertEquals(listOf(first), dao.weeks("ann", "zip", "2026-11-01", "2026-11-30"))
    }

    @Test
    fun `weeks reads the inclusive range for one owner and game, oldest first`() = runTest {
        listOf("2026-11-16", "2026-11-02", "2026-11-09", "2026-10-26", "2026-11-23").forEach { dao.insertOnce(week(it)) }
        dao.insertOnce(week("2026-11-09", ownerUid = "bob"))
        dao.insertOnce(week("2026-11-09", gameId = "sudoku"))

        assertEquals(
            listOf("2026-11-02", "2026-11-09", "2026-11-16"),
            dao.weeks("ann", "zip", "2026-11-02", "2026-11-16").map { it.weekStart },
        )
    }

    @Test
    fun `a range that ends before it starts reads nothing`() = runTest {
        dao.insertOnce(week("2026-11-09"))

        assertEquals(emptyList<RankHistoryEntity>(), dao.weeks("ann", "zip", "2026-11-16", "2026-11-02"))
    }
}
