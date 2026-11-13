package com.ivandsky.puzzlebox.data.leaderboard

import androidx.room.Room
import com.ivandsky.puzzlebox.testing.room.RoomQueryThread
import com.ivandsky.puzzlebox.database.finalboard.FinalBoardDatabase
import java.time.LocalDate
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

/** The Room adapter of the Final board seam on a real in-memory database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomFinalBoardStoreTest {

    private lateinit var db: FinalBoardDatabase
    private lateinit var store: RoomFinalBoardStore
    private val board = LeaderboardRef("zip", LocalDate.parse("2026-10-31"))

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), FinalBoardDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(RoomQueryThread.executor)
            .build()
        store = RoomFinalBoardStore(db.finalBoardDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entry(i: Int) =
        LeaderboardEntry("u$i", "Name $i", "zip/2026-10-31/1", 60_000L + i, i % 3, 9_000L + i)

    @Test
    fun `a 100 entry page with count 150 round-trips`() = runTest {
        val page = BoardPage((1..100).map(::entry), count = 150)
        store.put(board, page)
        val got = store.get(board)!!
        assertEquals(150, got.count)
        assertEquals(page.entries.toSet(), got.entries.toSet())
        assertEquals(100, got.entries.size)
    }

    @Test
    fun `an unknown board reads null`() = runTest {
        store.put(board, BoardPage(listOf(entry(1)), 1))
        assertNull(store.get(LeaderboardRef("zip", LocalDate.parse("2026-11-01"))))
        assertNull(store.get(LeaderboardRef("queens", board.day)))
    }

    @Test
    fun `an empty board round-trips as an empty page`() = runTest {
        store.put(board, BoardPage(emptyList(), 0))
        assertEquals(BoardPage(emptyList(), 0), store.get(board))
    }

    @Test
    fun `put replaces the stored board`() = runTest {
        store.put(board, BoardPage(listOf(entry(1), entry(2)), 2))
        store.put(board, BoardPage(listOf(entry(3)), 1))
        assertEquals(BoardPage(listOf(entry(3)), 1), store.get(board))
    }
}
