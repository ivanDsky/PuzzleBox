package com.ivandsky.puzzlebox.data.repository

import androidx.room.Room
import app.cash.turbine.test
import com.ivandsky.puzzlebox.common.dispatcher.DefaultDispatcherProvider
import com.ivandsky.puzzlebox.data.db.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.data.db.toTangoEntity
import com.ivandsky.puzzlebox.data.db.toTangoPuzzle
import com.ivandsky.puzzlebox.data.network.TangoWireParser
import com.ivandsky.puzzlebox.data.testing.FakeTangoDailySource
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.TangoPuzzleRef
import com.ivandsky.puzzlebox.games.tango.codec.TangoCodec
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
 * A7/A8 degrade contract for Tango: fetch-today → cache; any fetch failure → today's
 * cache plus the sealed error; history is cache-only. Rows share the `puzzle_dailies`
 * store keyed by "tango" (zero Room schema change). Network never enters this test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflineFirstTangoDailyRepositoryTest {

    private lateinit var db: PuzzleBoxDatabase
    private lateinit var source: FakeTangoDailySource
    private lateinit var repository: OfflineFirstTangoDailyRepository

    private val today = LocalDate.of(2026, 11, 2)
    private var nowMs = 1_000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PuzzleBoxDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        source = FakeTangoDailySource()
        repository = OfflineFirstTangoDailyRepository(
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

    private fun envelope(puzzleNumber: Int? = 301): Puzzle {
        val daily = TangoWireParser.parse(n6Json, today, puzzleNumber?.toString())
        daily as DecodeResult.Ok
        return Puzzle(
            gameId = TangoDefinition.id,
            variant = daily.value.puzzle.variant,
            formatVersion = TangoCodec.VERSION,
            payload = TangoCodec.encode(daily.value.puzzle),
        )
    }

    @Test
    fun `fresh fetch caches the envelope and identity`() = runTest {
        source.nextPuzzle = envelope(301)
        source.nextPuzzleNumber = 301

        val result = repository.today()

        assertTrue(result is TangoToday.Fresh)
        assertEquals(today, source.requestedDates.single())
        val dao = db.puzzleDailyDao()
        assertEquals(1, dao.count("tango"))
        val row = dao.get("tango", today.toString())!!
        assertEquals(301, row.puzzleNumber)
        assertEquals("tango/n6", row.variant)
        assertEquals(TangoCodec.VERSION, row.formatVersion)
        assertEquals(1_000L, row.fetchedAtMs)
        // Round trip: the stored bytes decode back to the playable envelope.
        assertEquals("tango", row.toTangoPuzzle()!!.gameId.value)
    }

    @Test
    fun `refetch the same day overwrites the row`() = runTest {
        source.nextPuzzle = envelope()
        repository.today()
        nowMs = 2_000L
        source.nextPuzzle = envelope()

        val result = repository.today()

        assertTrue(result is TangoToday.Fresh)
        assertEquals(1, db.puzzleDailyDao().count("tango"))
        assertEquals(2_000L, db.puzzleDailyDao().get("tango", today.toString())!!.fetchedAtMs)
    }

    @Test
    fun `fetch failure with cache degrades to Cached plus the sealed error`() = runTest {
        source.nextPuzzle = envelope()
        repository.today()
        source.nextPuzzle = null
        source.nextError = FetchError.Unavailable("offline")

        val result = repository.today()

        result as TangoToday.Cached
        assertTrue(result.error is FetchError.Unavailable)
        assertEquals("tango", result.puzzle.gameId.value)
        assertEquals(1, db.puzzleDailyDao().count("tango"))
    }

    @Test
    fun `untrusted fetch degrades to Cached and keeps the trusted row (I1)`() = runTest {
        source.nextPuzzle = envelope()
        repository.today()

        // The source-level A15 gate (fix-round-1) surfaces as Untrusted; the repo must
        // degrade to the saved copy with the sealed error attached and never let the
        // untrusted payload overwrite the row (design §6 degraded-untrusted).
        source.nextPuzzle = null
        source.nextError = FetchError.Untrusted("daily is not uniquely solvable")

        val result = repository.today()

        result as TangoToday.Cached
        assertTrue(result.error is FetchError.Untrusted)
        assertEquals("tango", result.puzzle.gameId.value)
        assertEquals(1, db.puzzleDailyDao().count("tango"))
        assertEquals(1_000L, db.puzzleDailyDao().get("tango", today.toString())!!.fetchedAtMs)
    }

    @Test
    fun `fetch failure without cache is Unavailable`() = runTest {
        source.nextPuzzle = null
        source.nextError = FetchError.Throttled("100 s cooldown")

        val result = repository.today()

        assertTrue(result is TangoToday.Unavailable && result.error is FetchError.Throttled)
        assertEquals(0, db.puzzleDailyDao().count("tango"))
    }

    @Test
    fun `history lists captured days newest first, cache-only`() = runTest {
        val dao = db.puzzleDailyDao()
        dao.upsert(envelope(300).toTangoEntity(TangoPuzzleRef(LocalDate.of(2026, 11, 1), 300), 1L))
        dao.upsert(envelope(299).toTangoEntity(TangoPuzzleRef(LocalDate.of(2026, 10, 31), 299), 2L))

        repository.observeHistory().test {
            val history = awaitItem()
            assertEquals(listOf("2026-11-01", "2026-10-31"), history.map { it.day.toString() })
            assertEquals(listOf(300, 299), history.map { it.puzzleNumber })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cached reads Room only and decodes the envelope`() = runTest {
        assertNull(repository.cached(today))

        source.nextPuzzle = envelope()
        repository.today()

        assertEquals("tango", repository.cached(today)!!.gameId.value)
        assertNull(repository.cached(LocalDate.of(2020, 1, 1)))
    }

    @Test
    fun `corrupted cache row reads as absent rather than crashing`() = runTest {
        db.puzzleDailyDao().upsert(
            com.ivandsky.puzzlebox.data.db.PuzzleDailyEntity(
                gameId = "tango",
                day = today.toString(),
                puzzleNumber = 301,
                variant = "tango/n6",
                formatVersion = 1,
                payload = byteArrayOf(1, 2, 3),
                fetchedAtMs = 1L,
            ),
        )

        assertNull(repository.cached(today))
    }

    private companion object {
        val n6Json = """
            {"gridSize":6,
             "given":[["S","","","","",""],["","M","","","",""],["","","","S","",""],
                      ["","","","S","",""],["","","","","",""],["","","","","","M"]],
             "constraints":[{"r1":0,"c1":0,"r2":0,"c2":1,"kind":"="},
                            {"r1":2,"c1":3,"r2":3,"c2":3,"kind":"x"}]}
        """.trimIndent()
    }
}
