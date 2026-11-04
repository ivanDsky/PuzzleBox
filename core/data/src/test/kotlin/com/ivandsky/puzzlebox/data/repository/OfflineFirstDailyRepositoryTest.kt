package com.ivandsky.puzzlebox.data.repository

import androidx.room.Room
import app.cash.turbine.test
import com.ivandsky.puzzlebox.common.dispatcher.DefaultDispatcherProvider
import com.ivandsky.puzzlebox.data.model.toEntity
import com.ivandsky.puzzlebox.data.model.toPuzzle
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.FakeDailySource
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.game.TestGames.Ref
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.database.PuzzleDailyEntity
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.model.GameId
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
 * A7/A8 degrade contract for every registered game: fetch-today → cache under the game's
 * id; any fetch failure → today's cache plus the sealed error; history and replay are
 * cache-only. Network never enters this test — fake sources stand in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflineFirstDailyRepositoryTest {

    private lateinit var db: PuzzleBoxDatabase
    private lateinit var alphaSource: FakeDailySource
    private lateinit var betaSource: FakeDailySource
    private lateinit var repository: OfflineFirstDailyRepository

    private val today = LocalDate.of(2026, 10, 31)
    private var nowMs = 1_000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PuzzleBoxDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        alphaSource = FakeDailySource(alpha)
        betaSource = FakeDailySource(beta)
        repository = OfflineFirstDailyRepository(
            sources = listOf(alphaSource, betaSource).associateBy { it.gameId },
            registry = TestGames.registry(listOf(alpha, beta)),
            dao = db.puzzleDailyDao(),
            dispatchers = DefaultDispatcherProvider(),
            calendar = FakeGameCalendar(today),
            clock = { nowMs },
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `fresh fetch caches the envelope and identity`() = runTest {
        alphaSource.nextPuzzle = TestGames.puzzle(alpha)
        alphaSource.nextPuzzleNumber = 600

        val result = repository.today(alpha)

        assertTrue(result is DailyToday.Fresh)
        assertEquals(today, alphaSource.requestedDates.single())
        val dao = db.puzzleDailyDao()
        assertEquals(1, dao.count("alpha"))
        val row = dao.get("alpha", today.toString())!!
        assertEquals(600, row.puzzleNumber)
        assertEquals("alpha/v1", row.variant)
        assertEquals(1, row.formatVersion)
        assertEquals(1_000L, row.fetchedAtMs)
        // Round trip: the stored bytes decode back to the playable envelope.
        assertEquals(alpha, row.toPuzzle(TestGames.definition(alpha))!!.gameId)
    }

    @Test
    fun `refetch the same day overwrites the row`() = runTest {
        alphaSource.nextPuzzle = TestGames.puzzle(alpha)
        repository.today(alpha)
        nowMs = 2_000L

        val result = repository.today(alpha)

        assertTrue(result is DailyToday.Fresh)
        assertEquals(1, db.puzzleDailyDao().count("alpha"))
        assertEquals(2_000L, db.puzzleDailyDao().get("alpha", today.toString())!!.fetchedAtMs)
    }

    @Test
    fun `fetch failure with cache degrades to Cached plus the sealed error`() = runTest {
        alphaSource.nextPuzzle = TestGames.puzzle(alpha)
        repository.today(alpha)
        alphaSource.nextPuzzle = null
        alphaSource.nextError = FetchError.Unavailable("offline")

        val result = repository.today(alpha)

        result as DailyToday.Cached
        assertTrue(result.error is FetchError.Unavailable)
        assertEquals(alpha, result.puzzle.gameId)
        assertEquals(1, db.puzzleDailyDao().count("alpha"))
    }

    @Test
    fun `untrusted fetch degrades to Cached and keeps the trusted row (I1)`() = runTest {
        alphaSource.nextPuzzle = TestGames.puzzle(alpha)
        repository.today(alpha)

        // A source-level trust gate surfaces as Untrusted; the repo must degrade to the
        // saved copy with the sealed error and never let the payload overwrite the row.
        alphaSource.nextPuzzle = null
        alphaSource.nextError = FetchError.Untrusted("daily is not uniquely solvable")
        nowMs = 2_000L

        val result = repository.today(alpha)

        result as DailyToday.Cached
        assertTrue(result.error is FetchError.Untrusted)
        assertEquals(1, db.puzzleDailyDao().count("alpha"))
        assertEquals(1_000L, db.puzzleDailyDao().get("alpha", today.toString())!!.fetchedAtMs)
    }

    @Test
    fun `fetch failure without cache is Unavailable`() = runTest {
        alphaSource.nextError = FetchError.Throttled("100 s cooldown")

        val result = repository.today(alpha)

        assertTrue(result is DailyToday.Unavailable && result.error is FetchError.Throttled)
        assertEquals(0, db.puzzleDailyDao().count("alpha"))
    }

    @Test
    fun `a game without a registered source is Unavailable and never cached`() = runTest {
        val result = repository.today(GameId("strands"))

        assertTrue(result is DailyToday.Unavailable && result.error is FetchError.Unavailable)
        assertNull(repository.cached(GameId("strands"), today))
    }

    @Test
    fun `history lists captured days newest first, cache-only`() = runTest {
        val dao = db.puzzleDailyDao()
        dao.upsert(TestGames.puzzle(alpha).toEntity(Ref(alpha, LocalDate.of(2026, 10, 30), 591), 1L))
        dao.upsert(TestGames.puzzle(alpha).toEntity(Ref(alpha, LocalDate.of(2026, 10, 29), 590), 2L))

        repository.observeHistory(alpha).test {
            val history = awaitItem()
            assertEquals(listOf("2026-10-30", "2026-10-29"), history.map { it.day.toString() })
            assertEquals(listOf(591, 590), history.map { it.puzzleNumber })
            assertEquals(setOf(alpha), history.map { it.gameId }.toSet())
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(alphaSource.requestedDates.isEmpty())
    }

    @Test
    fun `games are isolated - history and cache never cross game ids`() = runTest {
        alphaSource.nextPuzzle = TestGames.puzzle(alpha, tag = 1)
        betaSource.nextPuzzle = TestGames.puzzle(beta, tag = 2)
        repository.today(alpha)
        repository.today(beta)

        assertEquals(alpha, repository.cached(alpha, today)!!.gameId)
        assertEquals(beta, repository.cached(beta, today)!!.gameId)
        assertEquals(listOf(alpha), repository.observeHistory(alpha).first().map { it.gameId })
        assertEquals(listOf(beta), repository.observeHistory(beta).first().map { it.gameId })

        // A failure for one game degrades to ITS cache only.
        db.puzzleDailyDao().count("beta").let { assertEquals(1, it) }
        betaSource.nextPuzzle = null
        val degraded = repository.today(beta) as DailyToday.Cached
        assertEquals(2.toByte(), degraded.puzzle.payload[1])
    }

    @Test
    fun `cached reads Room only and decodes the envelope`() = runTest {
        assertNull(repository.cached(alpha, today))

        alphaSource.nextPuzzle = TestGames.puzzle(alpha)
        repository.today(alpha)

        assertEquals(alpha, repository.cached(alpha, today)!!.gameId)
        assertNull(repository.cached(alpha, LocalDate.of(2020, 1, 1)))
        assertNull(repository.cached(beta, today))
    }

    @Test
    fun `corrupted cache row reads as absent rather than crashing`() = runTest {
        db.puzzleDailyDao().upsert(
            PuzzleDailyEntity(
                gameId = "alpha",
                day = today.toString(),
                puzzleNumber = 600,
                variant = "alpha/v1",
                formatVersion = 1,
                payload = byteArrayOf(1, 2, 3),
                fetchedAtMs = 1L,
            ),
        )

        assertNull(repository.cached(alpha, today))
    }
}
