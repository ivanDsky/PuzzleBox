package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.repository.DailyRefresh
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Today's prefetch (A12): uncached games are fetched once per sync, and failures are kept for Offline. */
class SyncTodayPuzzlesTest {

    private val today = LocalDate.of(2026, 11, 4)
    private val calendar = FakeGameCalendar(today)
    private val daily = FakeDailyRepository().apply {
        setRefresh(alpha, DailyRefresh.Stored(TestGames.puzzle(alpha)))
        setRefresh(beta, DailyRefresh.Stored(TestGames.puzzle(beta)))
    }
    private val sync = SyncTodayPuzzles(TestGames.registry(listOf(alpha, beta)), daily, calendar)

    @Test
    fun `a failed fetch is recorded for its game only`() = runTest {
        daily.setRefresh(alpha, DailyRefresh.Failed(FetchError.Unavailable()))

        sync()

        assertEquals(setOf(alpha), sync.failed.value)
        assertEquals(1, daily.todayCalls(alpha))
        assertEquals(1, daily.todayCalls(beta))
    }

    @Test
    fun `a refresh that stores nothing is not a failure`() = runTest {
        daily.setRefresh(alpha, DailyRefresh.Changed(TestGames.puzzle(alpha, tag = 1)))

        sync()

        assertEquals(emptySet<GameId>(), sync.failed.value)
    }

    @Test
    fun `a puzzle already cached for today is not fetched`() = runTest {
        daily.setCached(alpha, today, TestGames.puzzle(alpha))

        sync()

        assertEquals(0, daily.todayCalls(alpha))
        assertEquals(1, daily.todayCalls(beta))
    }

    @Test
    fun `a later successful fetch clears the failure`() = runTest {
        daily.setRefresh(alpha, DailyRefresh.Failed(FetchError.Unavailable()))
        sync()

        daily.setRefresh(alpha, DailyRefresh.Stored(TestGames.puzzle(alpha)))
        sync()

        assertEquals(emptySet<GameId>(), sync.failed.value)
        assertEquals(2, daily.todayCalls(alpha))
    }

    @Test
    fun `a game the calendar does not know is skipped`() = runTest {
        val alphaOnly = object : GameCalendar by calendar {
            override fun today(game: GameId): LocalDate? = if (game == alpha) calendar.today(game) else null
        }
        daily.setRefresh(beta, DailyRefresh.Failed(FetchError.Unavailable()))

        SyncTodayPuzzles(TestGames.registry(listOf(alpha, beta)), daily, alphaOnly)()

        assertEquals(1, daily.todayCalls(alpha))
        assertEquals(0, daily.todayCalls(beta))
    }
}
