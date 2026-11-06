package com.ivandsky.puzzlebox.domain

import app.cash.turbine.test
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.game.TestGames.Ref
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Today's set (A12–A14, SC10, SC11) over the core:testing fakes; Offline comes from [SyncTodayPuzzles]. */
class ObserveTodaySetTest {

    private val gamma = GameId("gamma")
    private val today = LocalDate.of(2026, 11, 4)
    private val yesterday = today.minusDays(1)

    private val saved = FakeSavedPlayRepository()
    private val completions = FakeCompletionRepository()
    private val calendar = FakeGameCalendar(today)
    private val daily = FakeDailyRepository().apply {
        listOf(alpha, beta, gamma).forEach { setToday(it, DailyToday.Fresh(TestGames.puzzle(it))) }
    }
    private val sync = SyncTodayPuzzles(TestGames.registry(listOf(alpha, beta, gamma)), daily, calendar)

    private fun todaySet(games: List<GameId> = listOf(alpha, beta, gamma)) =
        ObserveTodaySet(TestGames.registry(games), saved, completions, calendar, daily, sync)()

    /** Today's fetch for [game] finds nothing: no network, no cache. */
    private fun failFetch(game: GameId) {
        daily.setToday(game, DailyToday.Unavailable(FetchError.Unavailable()))
    }

    private fun statuses(entries: List<TodayEntry>) = entries.associate { it.game to it.status }

    private fun record(game: GameId, day: LocalDate, time: Long = 1_000L) = CompletionRecord(
        puzzleRef = Ref(game, day),
        time = time,
        hints = 0,
        stats = RecordStats(schemaVersion = 1, blob = "{}"),
        isRecord = true,
    )

    @Test
    fun `nothing played reads NotStarted for every game`() = runTest {
        val entries = todaySet().first()

        assertEquals(listOf(alpha, beta, gamma), entries.map { it.game })
        assertEquals(listOf("Alpha", "Beta", "Gamma"), entries.map { it.displayName })
        entries.forEach { assertEquals(TodayStatus.NotStarted, it.status) }
    }

    @Test
    fun `today's saved play reads InProgress at its saved elapsed`() = runTest {
        saved.save(PlaySlot(alpha, today), byteArrayOf(1), elapsedMs = 83_000L)
        saved.save(PlaySlot(beta, yesterday), byteArrayOf(1), elapsedMs = 5_000L)

        val status = statuses(todaySet().first())

        assertEquals(TodayStatus.InProgress(83_000L), status[alpha])
        assertEquals(TodayStatus.NotStarted, status[beta])
    }

    @Test
    fun `solved comes from today's records only`() = runTest {
        completions.setRecords(listOf(record(beta, today, time = 61_000L), record(gamma, yesterday)))

        val status = statuses(todaySet().first())

        assertEquals(TodayStatus.Solved(61_000L), status[beta])
        assertEquals(TodayStatus.NotStarted, status[gamma])
    }

    @Test
    fun `registry order is kept`() = runTest {
        assertEquals(listOf(gamma, alpha, beta), todaySet(listOf(gamma, alpha, beta)).first().map { it.game })
    }

    @Test
    fun `today is each game's own calendar day`() = runTest {
        calendar.set(beta, yesterday)
        saved.save(PlaySlot(beta, yesterday), byteArrayOf(1), elapsedMs = 2_000L)
        completions.setRecords(listOf(record(alpha, yesterday)))

        val status = statuses(todaySet().first())

        assertEquals(TodayStatus.InProgress(2_000L), status[beta])
        assertEquals(TodayStatus.NotStarted, status[alpha])
    }

    @Test
    fun `solved beats a saved play and the late delete changes nothing`() = runTest {
        todaySet(listOf(alpha)).test {
            assertEquals(TodayStatus.NotStarted, awaitItem().single().status)

            saved.save(PlaySlot(alpha, today), byteArrayOf(1), elapsedMs = 9_000L)
            assertEquals(TodayStatus.InProgress(9_000L), awaitItem().single().status)

            completions.onCompleted(record(alpha, today, time = 12_000L))
            assertEquals(TodayStatus.Solved(12_000L), awaitItem().single().status)

            // The solve's save delete lands after the record: nothing observable changes.
            saved.delete(PlaySlot(alpha, today))
            expectNoEvents()
        }
    }

    @Test
    fun `a calendar day change re-emits`() = runTest {
        saved.save(PlaySlot(alpha, today), byteArrayOf(1), elapsedMs = 1_000L)
        completions.setRecords(listOf(record(beta, today)))

        todaySet().test {
            val before = statuses(awaitItem())
            assertEquals(TodayStatus.InProgress(1_000L), before[alpha])
            assertEquals(TodayStatus.Solved(1_000L), before[beta])

            calendar.setAll(today.plusDays(1))
            // Each game's day rolls over on its own flow; settle on the last emission.
            var after = awaitItem()
            while (after.any { it.status != TodayStatus.NotStarted }) after = awaitItem()
        }
    }

    @Test
    fun `a not-started game whose fetch failed reads Offline`() = runTest {
        failFetch(alpha)
        sync()

        val status = statuses(todaySet().first())

        assertEquals(TodayStatus.Offline, status[alpha])
        assertEquals(TodayStatus.NotStarted, status[beta])
    }

    @Test
    fun `a cached game is never Offline`() = runTest {
        failFetch(alpha)
        daily.setCached(alpha, today, TestGames.puzzle(alpha))
        sync()

        assertEquals(TodayStatus.NotStarted, statuses(todaySet().first())[alpha])
    }

    @Test
    fun `a saved or solved play beats Offline`() = runTest {
        failFetch(alpha)
        failFetch(beta)
        sync()
        saved.save(PlaySlot(alpha, today), byteArrayOf(1), elapsedMs = 4_000L)
        completions.setRecords(listOf(record(beta, today, time = 7_000L)))

        val status = statuses(todaySet().first())

        assertEquals(TodayStatus.InProgress(4_000L), status[alpha])
        assertEquals(TodayStatus.Solved(7_000L), status[beta])
    }

    @Test
    fun `capturing today's puzzle after a failed sync clears Offline`() = runTest {
        failFetch(alpha)
        sync()

        todaySet(listOf(alpha)).test {
            assertEquals(TodayStatus.Offline, awaitItem().single().status)

            // The play route fetched it after all: the day is now in the cache.
            daily.addEntry(DailyEntry(alpha, today, puzzleNumber = 1, variant = "alpha/v1", fetchedAtMs = 0L))
            assertEquals(TodayStatus.NotStarted, awaitItem().single().status)
        }
    }

    @Test
    fun `a later successful sync clears Offline`() = runTest {
        failFetch(alpha)
        sync()

        todaySet(listOf(alpha)).test {
            assertEquals(TodayStatus.Offline, awaitItem().single().status)

            daily.setToday(alpha, DailyToday.Fresh(TestGames.puzzle(alpha)))
            sync()
            assertEquals(TodayStatus.NotStarted, awaitItem().single().status)
        }
    }

    @Test
    fun `nextUnsolved wraps and excludes the current game`() {
        val set = listOf(
            TodayEntry(alpha, "Alpha", TodayStatus.Solved(1L)),
            TodayEntry(beta, "Beta", TodayStatus.InProgress(1L)),
            TodayEntry(gamma, "Gamma", TodayStatus.NotStarted),
        )

        assertEquals(beta, set.nextUnsolved(after = alpha))
        assertEquals(beta, set.nextUnsolved(after = gamma))
        assertEquals(gamma, set.nextUnsolved(after = beta))
    }

    @Test
    fun `nextUnsolved is null when every other game is solved`() {
        val set = listOf(
            TodayEntry(alpha, "Alpha", TodayStatus.Solved(1L)),
            TodayEntry(beta, "Beta", TodayStatus.Solved(1L)),
            TodayEntry(gamma, "Gamma", TodayStatus.NotStarted),
        )

        assertNull(set.nextUnsolved(after = gamma))
        assertNull(set.map { it.copy(status = TodayStatus.Solved(1L)) }.nextUnsolved(after = alpha))
    }
}
