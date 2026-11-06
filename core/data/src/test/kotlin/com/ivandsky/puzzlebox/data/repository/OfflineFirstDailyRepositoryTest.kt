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
import com.ivandsky.puzzlebox.game.DailySource
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * cache-only. By-date reads (ADR-0012) fetch a missed past day only for a source that
 * declares an archive. Network never enters this test — fake sources stand in.
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
        repository = repository(alphaSource, betaSource)
    }

    private fun repository(vararg sources: DailySource) = OfflineFirstDailyRepository(
        sources = sources.associateBy { it.gameId },
        registry = TestGames.registry(listOf(alpha, beta)),
        dao = db.puzzleDailyDao(),
        dispatchers = DefaultDispatcherProvider(),
        calendar = FakeGameCalendar(today),
        clock = { nowMs },
    )

    /** [alphaSource] serving any date from [archiveStart] (fetches still land in its log). */
    private fun archiveRepository(): OfflineFirstDailyRepository {
        val archiveAlpha = object : DailySource by alphaSource {
            override val archiveSince: LocalDate = archiveStart
        }
        return repository(archiveAlpha, betaSource)
    }

    private val archiveStart = LocalDate.of(2026, 10, 1)
    private val pastDay = LocalDate.of(2026, 10, 15)

    /** Epoch millis of noon UTC on [day] (FakeGameCalendar reads instants in UTC). */
    private fun noonOf(day: LocalDate): Long = day.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

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
        assertEquals(DailyDay.NotCaptured, repository.day(GameId("strands"), today))
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

        assertEquals(alpha, (repository.day(alpha, today) as DailyDay.Ready).puzzle.gameId)
        assertEquals(beta, (repository.day(beta, today) as DailyDay.Ready).puzzle.gameId)
        assertEquals(listOf(alpha), repository.observeHistory(alpha).first().map { it.gameId })
        assertEquals(listOf(beta), repository.observeHistory(beta).first().map { it.gameId })

        // A failure for one game degrades to ITS cache only.
        db.puzzleDailyDao().count("beta").let { assertEquals(1, it) }
        betaSource.nextPuzzle = null
        val degraded = repository.today(beta) as DailyToday.Cached
        assertEquals(2.toByte(), degraded.puzzle.payload[1])
    }

    @Test
    fun `a captured day reads Room only and decodes the envelope`() = runTest {
        assertEquals(DailyDay.NotCaptured, repository.day(alpha, today))

        alphaSource.nextPuzzle = TestGames.puzzle(alpha)
        repository.today(alpha)

        assertEquals(alpha, (repository.day(alpha, today) as DailyDay.Ready).puzzle.gameId)
        assertEquals(DailyDay.NotCaptured, repository.day(alpha, LocalDate.of(2020, 1, 1)))
        assertEquals(DailyDay.NotCaptured, repository.day(beta, today))
        assertEquals(listOf(today), alphaSource.requestedDates)
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

        assertEquals(DailyDay.NotCaptured, repository.day(alpha, today))
    }

    @Test
    fun `a cached past day is Ready for a game without an archive, never fetched`() = runTest {
        db.puzzleDailyDao().upsert(TestGames.puzzle(alpha).toEntity(Ref(alpha, pastDay, 584), noonOf(pastDay)))

        val result = repository.day(alpha, pastDay)

        result as DailyDay.Ready
        assertEquals(alpha, result.puzzle.gameId)
        assertEquals(584, result.puzzleNumber)
        assertTrue(alphaSource.requestedDates.isEmpty())
    }

    @Test
    fun `an uncached past day without an archive is NotCaptured, never fetched`() = runTest {
        alphaSource.nextPuzzle = TestGames.puzzle(alpha)

        assertEquals(DailyDay.NotCaptured, repository.day(alpha, pastDay))
        assertTrue(alphaSource.requestedDates.isEmpty())
        assertEquals(0, db.puzzleDailyDao().count("alpha"))
    }

    @Test
    fun `an unregistered game's day is NotCaptured`() = runTest {
        assertEquals(DailyDay.NotCaptured, archiveRepository().day(GameId("strands"), pastDay))
    }

    @Test
    fun `an archive miss in range fetches once, persists, then opens offline from the cache`() = runTest {
        val archive = archiveRepository()
        alphaSource.nextPuzzle = TestGames.puzzle(alpha, tag = 7)
        alphaSource.nextPuzzleNumber = 584

        val first = archive.day(alpha, pastDay)

        first as DailyDay.Ready
        assertEquals(584, first.puzzleNumber)
        assertEquals(listOf(pastDay), alphaSource.requestedDates)
        val row = db.puzzleDailyDao().get("alpha", pastDay.toString())!!
        assertEquals(584, row.puzzleNumber)
        assertEquals(1_000L, row.fetchedAtMs)

        alphaSource.nextPuzzle = null
        alphaSource.nextError = FetchError.Unavailable("offline")
        val second = archive.day(alpha, pastDay)

        second as DailyDay.Ready
        assertEquals(7.toByte(), second.puzzle.payload[1])
        assertEquals(584, second.puzzleNumber)
        assertEquals(1, alphaSource.requestedDates.size)
    }

    @Test
    fun `days outside the archive range are OutOfRange, never fetched`() = runTest {
        val archive = archiveRepository()
        alphaSource.nextPuzzle = TestGames.puzzle(alpha)

        assertEquals(DailyDay.OutOfRange, archive.day(alpha, archiveStart.minusDays(1)))
        assertEquals(DailyDay.OutOfRange, archive.day(alpha, today.plusDays(1)))
        assertTrue(alphaSource.requestedDates.isEmpty())
    }

    @Test
    fun `the archive range's both ends are fetchable`() = runTest {
        val archive = archiveRepository()
        alphaSource.nextPuzzle = TestGames.puzzle(alpha)

        assertTrue(archive.day(alpha, archiveStart) is DailyDay.Ready)
        assertTrue(archive.day(alpha, today) is DailyDay.Ready)
        assertEquals(listOf(archiveStart, today), alphaSource.requestedDates)
    }

    @Test
    fun `an archive fetch failure is Failed with the source's error and caches nothing`() = runTest {
        alphaSource.nextError = FetchError.NotFound("404")

        val result = archiveRepository().day(alpha, pastDay)

        assertTrue(result is DailyDay.Failed && result.error is FetchError.NotFound)
        assertEquals(1, alphaSource.requestedDates.size)
        assertEquals(0, db.puzzleDailyDao().count("alpha"))
    }

    @Test
    fun `archiveRange spans archiveSince to today, null without an archive`() {
        val archive = archiveRepository()

        assertEquals(archiveStart..today, archive.archiveRange(alpha))
        assertNull(archive.archiveRange(beta))
        assertNull(archive.archiveRange(GameId("strands")))
        assertNull(repository.archiveRange(alpha))
    }

    @Test
    fun `a past day fetches through fetchPast, today through fetchDaily`() = runTest {
        val archive = archiveRepository()
        alphaSource.nextPuzzle = TestGames.puzzle(alpha)

        archive.day(alpha, pastDay)
        archive.day(alpha, today)

        assertEquals(listOf(pastDay), alphaSource.requestedPastDates)
        assertEquals(listOf(pastDay, today), alphaSource.requestedDates)
    }

    @Test
    fun `today's live path never calls fetchPast`() = runTest {
        alphaSource.nextPuzzle = TestGames.puzzle(alpha)

        archiveRepository().today(alpha)

        assertEquals(listOf(today), alphaSource.requestedDates)
        assertTrue(alphaSource.requestedPastDates.isEmpty())
    }

    @Test
    fun `a stored placeholder is a miss - refetched by date and replaced by the real puzzle`() = runTest {
        val stand = TestGames.puzzle(alpha, tag = 9)
        alphaSource.placeholder = stand
        db.puzzleDailyDao().upsert(stand.toEntity(Ref(alpha, pastDay, 584), noonOf(pastDay)))
        alphaSource.nextPuzzle = TestGames.puzzle(alpha, tag = 7)
        alphaSource.nextPuzzleNumber = 584
        nowMs = 5_000L

        val result = archiveRepository().day(alpha, pastDay)

        result as DailyDay.Ready
        assertEquals(7.toByte(), result.puzzle.payload[1])
        assertEquals(listOf(pastDay), alphaSource.requestedPastDates)
        val row = db.puzzleDailyDao().get("alpha", pastDay.toString())!!
        assertEquals(7.toByte(), row.payload[1])
        assertEquals(5_000L, row.fetchedAtMs)
    }

    @Test
    fun `a placeholder whose refetch fails is Failed, never served, and stays stored`() = runTest {
        val stand = TestGames.puzzle(alpha, tag = 9)
        alphaSource.placeholder = stand
        db.puzzleDailyDao().upsert(stand.toEntity(Ref(alpha, pastDay, 584), noonOf(pastDay)))
        alphaSource.nextError = FetchError.Unavailable("offline")

        val result = archiveRepository().day(alpha, pastDay)

        assertTrue(result is DailyDay.Failed && result.error is FetchError.Unavailable)
        assertEquals(listOf(pastDay), alphaSource.requestedPastDates)
        assertEquals(9.toByte(), db.puzzleDailyDao().get("alpha", pastDay.toString())!!.payload[1])
    }

    @Test
    fun `a stored real puzzle never refetches, even for a source that declares placeholders`() = runTest {
        alphaSource.placeholder = TestGames.puzzle(alpha, tag = 9)
        db.puzzleDailyDao().upsert(TestGames.puzzle(alpha, tag = 7).toEntity(Ref(alpha, pastDay, 584), noonOf(pastDay)))
        alphaSource.nextPuzzle = TestGames.puzzle(alpha, tag = 8)

        val result = archiveRepository().day(alpha, pastDay)

        assertEquals(7.toByte(), (result as DailyDay.Ready).puzzle.payload[1])
        assertTrue(alphaSource.requestedDates.isEmpty())
    }

    @Test
    fun `cachedPuzzle reads a past placeholder row as null, never fetching`() = runTest {
        val stand = TestGames.puzzle(alpha, tag = 9)
        alphaSource.placeholder = stand
        db.puzzleDailyDao().upsert(stand.toEntity(Ref(alpha, pastDay, 584), noonOf(pastDay)))
        alphaSource.nextPuzzle = TestGames.puzzle(alpha, tag = 7)

        assertNull(archiveRepository().cachedPuzzle(alpha, pastDay))
        assertTrue(alphaSource.requestedDates.isEmpty())
    }

    @Test
    fun `cachedPuzzle serves a past real row, never fetching`() = runTest {
        alphaSource.placeholder = TestGames.puzzle(alpha, tag = 9)
        db.puzzleDailyDao().upsert(TestGames.puzzle(alpha, tag = 7).toEntity(Ref(alpha, pastDay, 584), noonOf(pastDay)))

        assertEquals(7.toByte(), archiveRepository().cachedPuzzle(alpha, pastDay)!!.payload[1])
        assertTrue(alphaSource.requestedDates.isEmpty())
    }

    @Test
    fun `cachedPuzzle is null for an in-range day without a row, never fetching`() = runTest {
        alphaSource.nextPuzzle = TestGames.puzzle(alpha)

        assertNull(archiveRepository().cachedPuzzle(alpha, pastDay))
        assertTrue(alphaSource.requestedDates.isEmpty())
    }

    @Test
    fun `cachedPuzzle serves today's row, even a placeholder, never fetching`() = runTest {
        val stand = TestGames.puzzle(alpha, tag = 9)
        alphaSource.placeholder = stand
        db.puzzleDailyDao().upsert(stand.toEntity(Ref(alpha, today, 600), noonOf(today)))
        alphaSource.nextPuzzle = TestGames.puzzle(alpha, tag = 7)

        assertEquals(9.toByte(), archiveRepository().cachedPuzzle(alpha, today)!!.payload[1])
        assertTrue(alphaSource.requestedDates.isEmpty())
    }

    @Test
    fun `puzzleNumber passes the source's number through, null without one`() {
        alphaSource.puzzleNumbers = { day -> if (day == pastDay) 584 else null }
        val archive = archiveRepository()

        assertEquals(584, archive.puzzleNumber(alpha, pastDay))
        assertNull(archive.puzzleNumber(alpha, today))
        assertNull(archive.puzzleNumber(beta, pastDay))
        assertNull(archive.puzzleNumber(GameId("strands"), pastDay))
        assertTrue(alphaSource.requestedDates.isEmpty())
    }

    @Test
    fun `history marks archived only rows fetched after their own day`() = runTest {
        val dao = db.puzzleDailyDao()
        val live = LocalDate.of(2026, 10, 30)
        dao.upsert(TestGames.puzzle(alpha).toEntity(Ref(alpha, live, 591), noonOf(live)))
        dao.upsert(TestGames.puzzle(alpha).toEntity(Ref(alpha, pastDay, 584), noonOf(today)))

        val history = repository.observeHistory(alpha).first()

        assertEquals(listOf(live, pastDay), history.map { it.day })
        assertFalse(history[0].archived)
        assertTrue(history[1].archived)
    }
}
