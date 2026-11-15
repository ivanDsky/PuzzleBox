package com.ivandsky.puzzlebox.data.repository

import androidx.room.Room
import com.ivandsky.puzzlebox.common.dispatcher.DefaultDispatcherProvider
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.database.PuzzleDailyEntity
import com.ivandsky.puzzlebox.game.DailyFetch
import com.ivandsky.puzzlebox.game.DailySource
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.FakeDailySource
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.room.RoomQueryThread
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The shared refresh of today's puzzle over real in-memory Room and a fake source: one request per game and
 * day at a time, none within the skip window, and a stored daily only `replaceToday` overwrites.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflineFirstDailyRefreshTest {

    private lateinit var db: PuzzleBoxDatabase
    private lateinit var repository: OfflineFirstDailyRepository

    private val source = FakeDailySource(alpha)
    private val scope = CoroutineScope(SupervisorJob())
    private val today = LocalDate.of(2026, 10, 31)
    private var nowMs = 1_000L

    private val first = TestGames.puzzle(alpha, tag = 1)
    private val revised = TestGames.puzzle(alpha, tag = 2)

    /** Holds every fetch of [gated] until completed; [gatedFetches] counts the fetches that started. */
    private val gate = CompletableDeferred<Unit>()
    private val fetchStarted = CompletableDeferred<Unit>()
    private val gatedFetches = AtomicInteger()
    private val gated = object : DailySource by source {
        override suspend fun fetchDaily(date: LocalDate): FetchResult<DailyFetch> {
            gatedFetches.incrementAndGet()
            fetchStarted.complete(Unit)
            gate.await()
            return source.fetchDaily(date)
        }
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PuzzleBoxDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(RoomQueryThread.executor)
            .build()
        repository = repository(source)
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private fun repository(source: DailySource) = OfflineFirstDailyRepository(
        sources = mapOf(alpha to source),
        registry = TestGames.registry(listOf(alpha)),
        dao = db.puzzleDailyDao(),
        dispatchers = DefaultDispatcherProvider(),
        calendar = FakeGameCalendar(today),
        scope = scope,
        clock = { nowMs },
    )

    private suspend fun row(): PuzzleDailyEntity? = db.puzzleDailyDao().get("alpha", today.toString())

    /** Stores [first] as today's puzzle, then moves past the skip window so the next refresh fetches. */
    private suspend fun storeFirst(puzzleNumber: Int? = 600) {
        source.nextPuzzle = first
        source.nextPuzzleNumber = puzzleNumber
        repository.refreshToday(alpha)
        nowMs += OfflineFirstDailyRepository.REFRESH_SKIP_WINDOW_MS
    }

    @Test
    fun `the first refresh stores the fetched puzzle`() = runTest {
        source.nextPuzzle = first
        source.nextPuzzleNumber = 600

        assertEquals(DailyRefresh.Stored(first), repository.refreshToday(alpha))

        assertEquals(first, repository.cachedPuzzle(alpha, today))
        assertEquals(600, row()!!.puzzleNumber)
        assertEquals(1_000L, row()!!.fetchedAtMs)
    }

    @Test
    fun `two concurrent callers share one request`() = runTest {
        source.nextPuzzle = first
        val shared = repository(gated)
        val one = async { shared.refreshToday(alpha) }
        val two = async { shared.refreshToday(alpha) }
        runCurrent()

        gate.complete(Unit)

        assertEquals(DailyRefresh.Stored(first), one.await())
        assertEquals(DailyRefresh.Stored(first), two.await())
        assertEquals(1, gatedFetches.get())
        assertEquals(1, source.requestedDates.size)
    }

    @Test
    fun `a caller that leaves does not cancel the shared fetch`() = runTest {
        source.nextPuzzle = first
        val shared = repository(gated)
        val leaver = launch { shared.refreshToday(alpha) }
        fetchStarted.await()
        leaver.cancelAndJoin()
        val stayer = async { shared.refreshToday(alpha) }
        runCurrent()

        gate.complete(Unit)

        assertEquals(DailyRefresh.Stored(first), stayer.await())
        assertEquals(1, gatedFetches.get())
        assertEquals(1, source.requestedDates.size)
    }

    @Test
    fun `an equal refetch writes nothing`() = runTest {
        storeFirst()

        assertEquals(DailyRefresh.Unchanged, repository.refreshToday(alpha))

        assertEquals(2, source.requestedDates.size)
        assertEquals(1_000L, row()!!.fetchedAtMs)
    }

    @Test
    fun `a differing refetch is Changed and not written`() = runTest {
        storeFirst()
        source.nextPuzzle = revised

        assertEquals(DailyRefresh.Changed(revised), repository.refreshToday(alpha))

        assertEquals(first, repository.cachedPuzzle(alpha, today))
        assertEquals(1_000L, row()!!.fetchedAtMs)
    }

    @Test
    fun `a new puzzle number alone is Changed`() = runTest {
        storeFirst(puzzleNumber = 600)
        source.nextPuzzleNumber = 601

        assertEquals(DailyRefresh.Changed(first), repository.refreshToday(alpha))

        assertEquals(600, row()!!.puzzleNumber)
    }

    @Test
    fun `a refresh within the skip window makes no request`() = runTest {
        source.nextPuzzle = first
        repository.refreshToday(alpha)
        nowMs += OfflineFirstDailyRepository.REFRESH_SKIP_WINDOW_MS - 1

        assertEquals(DailyRefresh.Unchanged, repository.refreshToday(alpha))

        assertEquals(1, source.requestedDates.size)
    }

    @Test
    fun `a skipped refresh still reports the puzzle that differs from the stored one`() = runTest {
        storeFirst()
        source.nextPuzzle = revised
        repository.refreshToday(alpha)

        assertEquals(DailyRefresh.Changed(revised), repository.refreshToday(alpha))

        assertEquals(2, source.requestedDates.size)
    }

    @Test
    fun `a failed refresh stores nothing and opens no skip window`() = runTest {
        val offline = FetchError.Unavailable("offline")
        source.nextError = offline

        assertEquals(DailyRefresh.Failed(offline), repository.refreshToday(alpha))
        assertEquals(null, row())

        source.nextPuzzle = first
        assertEquals(DailyRefresh.Stored(first), repository.refreshToday(alpha))
        assertEquals(2, source.requestedDates.size)
    }

    @Test
    fun `a source that throws fails its waiters and the next refresh fetches again`() = runTest {
        var broken = true
        val throwing = object : DailySource by source {
            override suspend fun fetchDaily(date: LocalDate): FetchResult<DailyFetch> {
                check(!broken) { "feed broke" }
                return source.fetchDaily(date)
            }
        }
        val flaky = repository(throwing)
        source.nextPuzzle = first

        val failure = runCatching { flaky.refreshToday(alpha) }.exceptionOrNull()
        broken = false

        assertTrue(failure is IllegalStateException)
        assertEquals(DailyRefresh.Stored(first), flaky.refreshToday(alpha))
    }

    @Test
    fun `a stored row that no longer decodes is replaced by the fetch`() = runTest {
        db.puzzleDailyDao().upsert(
            PuzzleDailyEntity("alpha", today.toString(), 600, "alpha/v1", 1, byteArrayOf(1, 2, 3), fetchedAtMs = 1L),
        )
        source.nextPuzzle = first

        assertEquals(DailyRefresh.Stored(first), repository.refreshToday(alpha))

        assertEquals(first, repository.cachedPuzzle(alpha, today))
    }

    @Test
    fun `replaceToday swaps in the changed puzzle while the stored one is still expected`() = runTest {
        storeFirst(puzzleNumber = 600)
        source.nextPuzzle = revised
        source.nextPuzzleNumber = 601
        val changed = repository.refreshToday(alpha) as DailyRefresh.Changed
        nowMs += 5_000L

        assertTrue(repository.replaceToday(alpha, expected = first, fresh = changed.fresh))

        assertEquals(revised, repository.cachedPuzzle(alpha, today))
        assertEquals(601, row()!!.puzzleNumber)
        assertEquals(nowMs, row()!!.fetchedAtMs)
    }

    @Test
    fun `replaceToday with a stale expected returns false and leaves the row`() = runTest {
        storeFirst()
        source.nextPuzzle = revised
        repository.refreshToday(alpha)

        assertFalse(repository.replaceToday(alpha, expected = TestGames.puzzle(alpha, tag = 9), fresh = revised))

        assertEquals(first, repository.cachedPuzzle(alpha, today))
        assertEquals(1_000L, row()!!.fetchedAtMs)
    }

    @Test
    fun `replaceToday refuses a puzzle the source did not serve`() = runTest {
        storeFirst()

        assertFalse(repository.replaceToday(alpha, expected = first, fresh = revised))

        assertEquals(first, repository.cachedPuzzle(alpha, today))
    }
}
