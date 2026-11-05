package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.domain.GetReplaySession
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.gameui.degradeBannerMessage
import com.ivandsky.puzzlebox.gameui.unavailableMessage
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Origin precedence of a slot's opening (A11): a decodable save before the network, an
 * undecodable save dropped for a fresh start, today's fetch, a past day's replay build
 * carrying the day's puzzleNumber (arch-2), and Unavailable when nothing is playable.
 */
class SlotOpeningTest {

    private val alpha = TestGames.alpha
    private val today = LocalDate.parse("2026-11-04")
    private val yesterday = today.minusDays(1)
    private val todaySlot = PlaySlot(alpha, today)

    private val games = TestGames.registry(listOf(alpha))
    private val daily = FakeDailyRepository()
    private val saved = FakeSavedPlayRepository()
    private val opening = SlotOpening(
        daily = daily,
        replays = GetReplaySession(daily, games),
        saved = saved,
        calendar = FakeGameCalendar(today),
        games = games,
        lane = WriteLane(),
    )

    @Test
    fun `a valid save is restored before the network`() = runTest {
        val blob = SessionBlob.encode(alpha, byteArrayOf(1, 2, 3))
        saved.save(todaySlot, blob, elapsedMs = 0L)
        daily.setToday(alpha, DailyToday.Fresh(TestGames.puzzle(alpha)))

        val origin = opening.open(todaySlot)

        assertArrayEquals(blob, (origin as SlotOpening.Origin.Restored).session)
        assertEquals(0, daily.todayCalls(alpha))
    }

    @Test
    fun `an undecodable save is deleted and today is fetched`() = runTest {
        saved.save(todaySlot, byteArrayOf(7, 7), elapsedMs = 0L)
        val puzzle = TestGames.puzzle(alpha)
        daily.setToday(alpha, DailyToday.Fresh(puzzle))

        val origin = opening.open(todaySlot) as SlotOpening.Origin.Fetched

        assertSame(puzzle, origin.puzzle)
        assertTrue(saved.rows.isEmpty())
    }

    @Test
    fun `a save naming another game is undecodable for the slot`() = runTest {
        saved.save(todaySlot, SessionBlob.encode(TestGames.beta, byteArrayOf(1)), elapsedMs = 0L)
        daily.setToday(alpha, DailyToday.Fresh(TestGames.puzzle(alpha)))

        assertTrue(opening.open(todaySlot) is SlotOpening.Origin.Fetched)
        assertTrue(saved.rows.isEmpty())
    }

    @Test
    fun `today is fetched fresh or from the cache with its banner`() = runTest {
        val fresh = TestGames.puzzle(alpha, tag = 1)
        daily.setToday(alpha, DailyToday.Fresh(fresh))
        val fetched = opening.open(todaySlot) as SlotOpening.Origin.Fetched
        assertSame(fresh, fetched.puzzle)
        assertNull(fetched.banner)

        val cached = TestGames.puzzle(alpha, tag = 2)
        val offline = FetchError.Unavailable("offline")
        daily.setToday(alpha, DailyToday.Cached(cached, offline))
        val degraded = opening.open(todaySlot) as SlotOpening.Origin.Fetched
        assertSame(cached, degraded.puzzle)
        assertEquals(degradeBannerMessage(offline), degraded.banner)
    }

    @Test
    fun `a past day is restored from a replay built with the day's puzzleNumber`() = runTest {
        val puzzle = TestGames.puzzle(alpha, tag = 3)
        daily.setCached(alpha, yesterday, puzzle)
        daily.setHistory(
            alpha,
            listOf(
                DailyEntry(alpha, today, puzzleNumber = 413, variant = "alpha/v1", fetchedAtMs = 2),
                DailyEntry(alpha, yesterday, puzzleNumber = 412, variant = "alpha/v1", fetchedAtMs = 1),
            ),
        )

        val origin = opening.open(PlaySlot(alpha, yesterday)) as SlotOpening.Origin.Restored

        assertArrayEquals(TestGames.replayBlob(alpha, puzzle, yesterday, 412), origin.session)
        assertEquals(0, daily.todayCalls(alpha))
    }

    @Test
    fun `offline without a cache is unavailable`() = runTest {
        val offline = FetchError.Unavailable("offline")
        daily.setToday(alpha, DailyToday.Unavailable(offline))

        val origin = opening.open(todaySlot) as SlotOpening.Origin.Unavailable

        assertEquals(unavailableMessage(offline), origin.message)
    }

    @Test
    fun `a past day missing from the cache is unavailable`() = runTest {
        assertTrue(opening.open(PlaySlot(alpha, yesterday)) is SlotOpening.Origin.Unavailable)
        assertEquals(0, daily.todayCalls(alpha))
    }

    @Test
    fun `an unregistered game is unavailable and keeps its save`() = runTest {
        val slot = PlaySlot(TestGames.beta, today)
        saved.save(slot, SessionBlob.encode(TestGames.beta, byteArrayOf(1)), elapsedMs = 0L)

        assertTrue(opening.open(slot) is SlotOpening.Origin.Unavailable)
        assertEquals(setOf(slot), saved.rows.keys)
    }
}
