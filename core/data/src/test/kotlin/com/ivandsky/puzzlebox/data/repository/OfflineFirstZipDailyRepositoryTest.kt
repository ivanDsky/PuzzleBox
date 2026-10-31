package com.ivandsky.puzzlebox.data.repository

import androidx.room.Room
import app.cash.turbine.test
import com.ivandsky.puzzlebox.common.dispatcher.DefaultDispatcherProvider
import com.ivandsky.puzzlebox.data.db.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.data.db.PuzzleDailyEntity
import com.ivandsky.puzzlebox.data.db.toEntity
import com.ivandsky.puzzlebox.data.db.toPuzzle
import com.ivandsky.puzzlebox.data.testing.FakeZipDailySource
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.ZipPuzzleRef
import com.ivandsky.puzzlebox.games.zip.codec.ZipCodec
import com.ivandsky.puzzlebox.games.zip.codec.ZipWireParser
import java.time.LocalDate
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

/**
 * A7/A8 degrade contract: fetch-today → cache; any fetch failure → today's cache plus the
 * sealed error; history is cache-only. Network never enters this test — the fake stands in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflineFirstZipDailyRepositoryTest {

    private lateinit var db: PuzzleBoxDatabase
    private lateinit var source: FakeZipDailySource
    private lateinit var repository: OfflineFirstZipDailyRepository

    private val today = LocalDate.of(2026, 10, 31)
    private var nowMs = 1_000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PuzzleBoxDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        source = FakeZipDailySource()
        repository = OfflineFirstZipDailyRepository(
            source = source,
            dao = db.puzzleDailyDao(),
            dispatchers = DefaultDispatcherProvider(),
            clock = { nowMs },
            todayProvider = { today },
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** Structurally valid 5x5 daily (the forced-uniqueness board); the fake bypasses the A16 gate. */
    private val n5Json =
        """
        {"gridSize":5,
         "orderedSequence":[0,12,24],
         "solution":[0,1,2,3,4,9,8,7,6,5,10,11,12,13,14,19,18,17,16,15,20,21,22,23,24],
         "walls":[
           {"cellIdx":0,"direction":"WallDirection_DOWN"},{"cellIdx":1,"direction":"WallDirection_DOWN"},
           {"cellIdx":2,"direction":"WallDirection_DOWN"},{"cellIdx":3,"direction":"WallDirection_DOWN"},
           {"cellIdx":6,"direction":"WallDirection_DOWN"},{"cellIdx":7,"direction":"WallDirection_DOWN"},
           {"cellIdx":8,"direction":"WallDirection_DOWN"},{"cellIdx":9,"direction":"WallDirection_DOWN"},
           {"cellIdx":10,"direction":"WallDirection_DOWN"},{"cellIdx":11,"direction":"WallDirection_DOWN"},
           {"cellIdx":12,"direction":"WallDirection_DOWN"},{"cellIdx":13,"direction":"WallDirection_DOWN"},
           {"cellIdx":16,"direction":"WallDirection_DOWN"},{"cellIdx":17,"direction":"WallDirection_DOWN"},
           {"cellIdx":18,"direction":"WallDirection_DOWN"},{"cellIdx":19,"direction":"WallDirection_DOWN"}]}
        """.trimIndent()

    private fun envelope(puzzleNumber: String = "600"): Puzzle {
        val daily = ZipWireParser.parse(n5Json, today, puzzleNumber)
        daily as DecodeResult.Ok
        return Puzzle(
            gameId = ZipDefinition.id,
            variant = daily.value.puzzle.variant,
            formatVersion = ZipCodec.VERSION,
            payload = ZipCodec.encode(daily.value.puzzle),
        )
    }

    @Test
    fun `fresh fetch caches the envelope and identity`() = runTest {
        source.nextPuzzle = envelope("600")
        source.nextPuzzleNumber = 600

        val result = repository.today()

        assertTrue(result is ZipToday.Fresh)
        assertEquals(today, source.requestedDates.single())
        val dao = db.puzzleDailyDao()
        assertEquals(1, dao.count("zip"))
        val row = dao.get("zip", today.toString())!!
        assertEquals(600, row.puzzleNumber)
        assertEquals("zip/n5", row.variant)
        assertEquals(ZipCodec.VERSION, row.formatVersion)
        assertEquals(1_000L, row.fetchedAtMs)
        // Round trip: the stored bytes decode back to the playable envelope.
        assertEquals("zip", row.toPuzzle()!!.gameId.value)
    }

    @Test
    fun `refetch the same day overwrites the row`() = runTest {
        source.nextPuzzle = envelope()
        repository.today()
        nowMs = 2_000L
        source.nextPuzzle = envelope()

        val result = repository.today()

        assertTrue(result is ZipToday.Fresh)
        assertEquals(1, db.puzzleDailyDao().count("zip"))
        assertEquals(2_000L, db.puzzleDailyDao().get("zip", today.toString())!!.fetchedAtMs)
    }

    @Test
    fun `fetch failure with cache degrades to Cached plus the sealed error`() = runTest {
        source.nextPuzzle = envelope()
        repository.today()
        source.nextPuzzle = null
        source.nextError = FetchError.Unavailable("offline")

        val result = repository.today()

        assertTrue(result is ZipToday.Cached)
        result as ZipToday.Cached
        assertTrue(result.error is FetchError.Unavailable)
        assertEquals("zip", result.puzzle.gameId.value)
        assertEquals(1, db.puzzleDailyDao().count("zip"))
    }

    @Test
    fun `fetch failure without cache is Unavailable`() = runTest {
        source.nextPuzzle = null
        source.nextError = FetchError.Unavailable("airplane mode")

        val result = repository.today()

        assertTrue(result is ZipToday.Unavailable && result.error is FetchError.Unavailable)
        assertEquals(0, db.puzzleDailyDao().count("zip"))
    }

    @Test
    fun `untrusted payload with cache still degrades to cache (A7 clear state)`() = runTest {
        source.nextPuzzle = envelope()
        repository.today()
        source.nextPuzzle = null
        source.nextError = FetchError.Untrusted("solver disagrees")

        val result = repository.today()

        assertTrue(result is ZipToday.Cached && result.error is FetchError.Untrusted)
    }

    @Test
    fun `history lists captured days newest first, cache-only`() = runTest {
        val dao = db.puzzleDailyDao()
        dao.upsert(envelope().toEntity(ZipPuzzleRef(LocalDate.of(2026, 10, 30), 591), 1L))
        dao.upsert(envelope().toEntity(ZipPuzzleRef(LocalDate.of(2026, 10, 29), 590), 2L))

        repository.observeHistory().test {
            val history = awaitItem()
            assertEquals(listOf("2026-10-30", "2026-10-29"), history.map { it.day.toString() })
            assertEquals(listOf(591, 590), history.map { it.puzzleNumber })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cached reads Room only and decodes the envelope`() = runTest {
        assertNull(repository.cached(today))

        source.nextPuzzle = envelope()
        repository.today()

        val puzzle = repository.cached(today)
        assertEquals("zip", puzzle!!.gameId.value)
        assertNull(repository.cached(LocalDate.of(2020, 1, 1)))
    }

    @Test
    fun `corrupted cache row reads as absent rather than crashing`() = runTest {
        db.puzzleDailyDao().upsert(
            PuzzleDailyEntity(
                gameId = "zip",
                day = today.toString(),
                puzzleNumber = 600,
                variant = "zip/n5",
                formatVersion = 1,
                payload = byteArrayOf(1, 2, 3),
                fetchedAtMs = 1L,
            ),
        )

        assertNull(repository.cached(today))
    }
}
