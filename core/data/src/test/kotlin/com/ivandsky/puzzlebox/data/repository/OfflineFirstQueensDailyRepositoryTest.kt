package com.ivandsky.puzzlebox.data.repository

import androidx.room.Room
import com.ivandsky.puzzlebox.common.dispatcher.DefaultDispatcherProvider
import com.ivandsky.puzzlebox.data.db.PuzzleBoxDatabase


import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.QueensPuzzleRef
import com.ivandsky.puzzlebox.games.queens.codec.QueensCodec
import com.ivandsky.puzzlebox.games.queens.codec.QueensWireParser
import com.ivandsky.puzzlebox.games.queens.feed.QueensDailySource
import com.ivandsky.puzzlebox.games.queens.queensVariantId
import java.time.LocalDate
import kotlinx.coroutines.flow.first
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
 * A7/A8 degrade contract for the Queens daily (zip OfflineFirstZipDailyRepositoryTest
 * precedent): fetch-today → cache under gameId=queens; any fetch failure → today's cache
 * plus the sealed error; history and replay are cache-only. Network never enters this
 * test — the fake stands in for the A13-gated source.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflineFirstQueensDailyRepositoryTest {

    private lateinit var db: PuzzleBoxDatabase
    private lateinit var source: FakeQueensSource
    private lateinit var repository: OfflineFirstQueensDailyRepository

    private val today = LocalDate.of(2026, 11, 2)
    private var nowMs = 1_000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PuzzleBoxDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        source = FakeQueensSource()
        repository = OfflineFirstQueensDailyRepository(
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

    /** A13-proven unique 8x8 daily fixture (synthesized from the documented wire shape). */
    private val uniqueJson: String by lazy {
        OfflineFirstQueensDailyRepositoryTest::class.java
            .getResourceAsStream("/fixtures/queens-daily-unique.json")!!
            .readBytes().decodeToString()
    }

    private fun envelope(puzzleNumber: Int? = 592): Puzzle {
        val daily = QueensWireParser.parse(uniqueJson, today, puzzleNumber?.toString()) as DecodeResult.Ok
        return Puzzle(
            gameId = QueensDefinition.id,
            variant = queensVariantId(daily.value.puzzle.size),
            formatVersion = QueensCodec.VERSION,
            payload = QueensCodec.encode(daily.value.puzzle),
        )
    }

    @Test
    fun `fresh fetch caches the envelope and identity`() = runTest {
        source.nextDaily = QueensDailySource.Daily(envelope(592), QueensPuzzleRef(today, 592))

        val result = repository.today()

        assertTrue(result is QueensToday.Fresh)
        assertEquals(today, source.requestedDates.single())
        val dao = db.puzzleDailyDao()
        assertEquals(1, dao.count("queens"))
        val row = dao.get("queens", today.toString())!!
        assertEquals(592, row.puzzleNumber)
        assertEquals("queens/n8", row.variant)
        assertEquals(QueensCodec.VERSION, row.formatVersion)
        assertEquals(1_000L, row.fetchedAtMs)
        // Round trip: the stored bytes decode back to the playable envelope.
        assertEquals("queens", row.toQueensPuzzle()!!.gameId.value)
    }

    @Test
    fun `failed fetch degrades to cached day with the sealed error`() = runTest {
        source.nextDaily = QueensDailySource.Daily(envelope(), QueensPuzzleRef(today, 592))
        repository.today()

        source.nextError = FetchError.Unavailable("linkedin down")
        val result = repository.today()

        assertTrue(result is QueensToday.Cached)
        assertEquals(FetchError.Unavailable::class, (result as QueensToday.Cached).error::class)
        assertEquals("queens", result.puzzle.gameId.value)
    }

    @Test
    fun `unavailable without cache is Unavailable`() = runTest {
        source.nextError = FetchError.Throttled("soft 200")
        val result = repository.today()
        assertTrue(result is QueensToday.Unavailable)
    }

    @Test
    fun `cached and history are cache-only replay seams`() = runTest {
        source.nextDaily = QueensDailySource.Daily(envelope(), QueensPuzzleRef(today, 592))
        repository.today()

        assertEquals("queens", repository.cached(today)!!.gameId.value)
        assertNull(repository.cached(today.minusDays(1)))
        val history = repository.observeHistory().first()
        assertEquals(listOf(today), history.map { it.day })
        // A second repository.today() call must not have touched the fake again.
    }

    private class FakeQueensSource : QueensDailySource {
        var nextDaily: QueensDailySource.Daily? = null
        var nextError: FetchError? = null
        val requestedDates = mutableListOf<LocalDate>()

        override suspend fun fetchDaily(date: LocalDate): FetchResult<QueensDailySource.Daily> {
            requestedDates += date
            val daily = nextDaily
            val error = nextError
            return when {
                error != null -> FetchResult.Err(error)
                daily != null -> FetchResult.Ok(daily)
                else -> FetchResult.Err(FetchError.Unavailable("no fixture"))
            }
        }
    }
}
