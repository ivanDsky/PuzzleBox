package com.ivandsky.puzzlebox.database

import androidx.room.Room
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The trainer_puzzles cache over real in-memory Room. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrainerPuzzleDaoTest {

    private lateinit var db: PuzzleBoxDatabase
    private lateinit var dao: TrainerPuzzleDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PuzzleBoxDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(RoomQueryThread.executor)
            .build()
        dao = db.trainerPuzzleDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun puzzle(
        number: Int,
        game: String = "queens",
        payload: ByteArray = byteArrayOf(0, 1, -1, 127),
        fetchedAtMs: Long = 5_000L,
    ) = TrainerPuzzleEntity(
        gameId = game,
        puzzleNumber = number,
        day = "2026-11-08",
        variant = "queens/n8",
        formatVersion = 1,
        payload = payload,
        fetchedAtMs = fetchedAtMs,
    )

    @Test
    fun `an inserted puzzle reads back with its payload bytes`() = runTest {
        val stored = puzzle(400)

        assertNotEquals(-1L, dao.insert(stored))

        val read = dao.get("queens", 400)!!
        assertEquals(stored, read)
        assertEquals(listOf<Byte>(0, 1, -1, 127), read.payload.toList())
    }

    @Test
    fun `a second insert for the same game and number keeps the first`() = runTest {
        val first = puzzle(400, payload = byteArrayOf(1), fetchedAtMs = 1_000L)

        assertNotEquals(-1L, dao.insert(first))
        assertEquals(-1L, dao.insert(puzzle(400, payload = byteArrayOf(2), fetchedAtMs = 9_000L)))

        assertEquals(first, dao.get("queens", 400))
    }

    @Test
    fun `get misses another number or game`() = runTest {
        dao.insert(puzzle(400))

        assertNull(dao.get("queens", 401))
        assertNull(dao.get("tango", 400))
    }

    @Test
    fun `the same number of two games is two puzzles`() = runTest {
        dao.insert(puzzle(400, game = "queens", payload = byteArrayOf(1)))
        dao.insert(puzzle(400, game = "tango", payload = byteArrayOf(2)))

        assertEquals(listOf<Byte>(1), dao.get("queens", 400)!!.payload.toList())
        assertEquals(listOf<Byte>(2), dao.get("tango", 400)!!.payload.toList())
    }

    @Test
    fun `numbers lists one game's cached numbers, lowest first`() = runTest {
        listOf(402, 400, 401).forEach { dao.insert(puzzle(it)) }
        dao.insert(puzzle(7, game = "tango"))

        assertEquals(listOf(400, 401, 402), dao.numbers("queens"))
        assertEquals(listOf(7), dao.numbers("tango"))
        assertEquals(emptyList<Int>(), dao.numbers("sudoku"))
    }

    @Test
    fun `the cache is not the daily cache`() = runTest {
        dao.insert(puzzle(400))

        assertEquals(0, db.puzzleDailyDao().count("queens"))
    }
}
