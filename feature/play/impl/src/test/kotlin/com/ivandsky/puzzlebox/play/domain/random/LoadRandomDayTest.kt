package com.ivandsky.puzzlebox.play.domain.random

import com.ivandsky.puzzlebox.data.repository.DailyDay
import com.ivandsky.puzzlebox.play.domain.LoadPastDay
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.game.TestGames.Ref
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import java.time.LocalDate
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Past days over a two-game fake archive: alpha 10-10..today (8 past days), beta 10-14..today (4); today is 11-18. */
class LoadRandomDayTest {
    private val today = LocalDate.of(2026, 11, 18)
    private val alphaDays = (0L until 8).map { LocalDate.of(2026, 11, 10).plusDays(it) }
    private val betaDays = (0L until 4).map { LocalDate.of(2026, 11, 14).plusDays(it) }
    private val seeds = 1..200

    private val daily = FakeDailyRepository().apply {
        setArchiveRange(alpha, alphaDays.first()..today)
        setArchiveRange(beta, betaDays.first()..today)
        alphaDays.forEach { serve(alpha, it) }
        betaDays.forEach { serve(beta, it) }
    }
    private val completions = FakeCompletionRepository()

    private fun FakeDailyRepository.serve(game: GameId, day: LocalDate, result: DailyDay? = null) =
        setDay(game, day, result ?: DailyDay.Ready(TestGames.puzzle(game), 100))

    private fun load(seed: Int) = LoadRandomDay(
        daily, completions, LoadPastDay(daily, TestGames.registry(listOf(alpha, beta))), TestGames.registry(listOf(alpha, beta)), Random(seed),
    )

    private suspend fun pick(seed: Int, filter: RandomFilter.Archive, last: RandomPick? = null) = load(seed)(filter, last)

    private fun solved(game: GameId, day: LocalDate) = CompletionRecord(
        puzzleRef = Ref(game, PuzzleKey.Daily(day, null)),
        time = 1_000,
        hints = 0,
        stats = RecordStats(schemaVersion = 1, blob = "{}"),
        isRecord = true,
    )

    private fun opens(game: GameId, days: List<LocalDate>) = days.sumOf { daily.dayCalls(game, it) }

    private fun picked(outcome: RandomOutcome) = (outcome as RandomOutcome.Picked).pick as RandomPick.Day

    @Test
    fun `every pick is a past day of the chosen game, never today`() = runTest {
        for (seed in seeds) {
            val one = picked(pick(seed, RandomFilter.Archive(alpha, unsolvedOnly = false)))
            assertEquals(alpha, one.game)
            assertTrue(one.day in alphaDays)

            val any = picked(pick(seed, RandomFilter.Archive(null, unsolvedOnly = false)))
            assertTrue(any.day < today)
            assertTrue(any.day in if (any.game == alpha) alphaDays else betaDays)
        }
    }

    @Test
    fun `under any game each puzzle is equally likely, so the longer archive comes up more`() = runTest {
        val games = seeds.map { picked(pick(it, RandomFilter.Archive(null, unsolvedOnly = false))).game }
        assertTrue(games.count { it == alpha } > games.count { it == beta })
        assertTrue(games.any { it == beta })
    }

    @Test
    fun `a game without an archive adds no candidates`() = runTest {
        daily.setArchiveRange(beta, null)
        for (seed in seeds) assertEquals(alpha, picked(pick(seed, RandomFilter.Archive(null, unsolvedOnly = false))).game)
    }

    @Test
    fun `the last pick is never returned while another puzzle exists`() = runTest {
        val last = RandomPick.Day(alpha, alphaDays[3])
        for (seed in seeds) {
            assertFalse(pick(seed, RandomFilter.Archive(alpha, unsolvedOnly = false), last) == RandomOutcome.Picked(last))
        }
        assertEquals(0, daily.dayCalls(alpha, last.day))
    }

    @Test
    fun `a pool of exactly one puzzle repeats it`() = runTest {
        daily.setArchiveRange(alpha, alphaDays[0]..alphaDays[1])
        val only = RandomPick.Day(alpha, alphaDays[0])
        assertEquals(RandomOutcome.Picked(only), pick(1, RandomFilter.Archive(alpha, unsolvedOnly = false), only))
    }

    @Test
    fun `the last pick waits behind a candidate that will not open`() = runTest {
        daily.setArchiveRange(alpha, alphaDays[0]..alphaDays[2])
        daily.serve(alpha, alphaDays[1], DailyDay.Failed(FetchError.NotFound()))
        val last = RandomPick.Day(alpha, alphaDays[0])
        for (seed in seeds) {
            assertEquals(RandomOutcome.Picked(last), pick(seed, RandomFilter.Archive(alpha, unsolvedOnly = false), last))
        }
    }

    @Test
    fun `not solved yet never picks a solved day, and only for that game`() = runTest {
        val solvedAlpha = alphaDays.take(5)
        completions.setRecords(solvedAlpha.map { solved(alpha, it) } + solved(beta, alphaDays[6]))
        for (seed in seeds) {
            assertTrue(picked(pick(seed, RandomFilter.Archive(alpha, unsolvedOnly = true))).day !in solvedAlpha)
        }
        // Beta's record on alpha's day 6 does not make alpha's day 6 solved.
        val days = seeds.map { picked(pick(it, RandomFilter.Archive(alpha, unsolvedOnly = true))).day }
        assertTrue(alphaDays[6] in days)
        assertEquals(0, opens(alpha, solvedAlpha))
    }

    @Test
    fun `solved days stay in the pool when not asked to leave`() = runTest {
        completions.setRecords(alphaDays.map { solved(alpha, it) })
        assertTrue(pick(1, RandomFilter.Archive(alpha, unsolvedOnly = false)) is RandomOutcome.Picked)
    }

    @Test
    fun `every day solved is nothing left, with no opens`() = runTest {
        completions.setRecords(alphaDays.map { solved(alpha, it) })
        assertEquals(RandomOutcome.NothingLeft, pick(1, RandomFilter.Archive(alpha, unsolvedOnly = true)))
        assertEquals(0, opens(alpha, alphaDays))
    }

    @Test
    fun `a day the source cannot serve is never picked`() = runTest {
        val bad = listOf(
            DailyDay.NotCaptured,
            DailyDay.OutOfRange,
            DailyDay.Failed(FetchError.NotFound()),
            DailyDay.Failed(FetchError.Untrusted("bad")),
        )
        bad.forEachIndexed { i, result -> daily.serve(alpha, alphaDays[i], result) }
        for (seed in seeds) {
            val day = picked(pick(seed, RandomFilter.Archive(alpha, unsolvedOnly = false))).day
            assertTrue(day in alphaDays.drop(4))
        }
    }

    @Test
    fun `offline stops after one open`() = runTest {
        alphaDays.forEach { daily.serve(alpha, it, DailyDay.Failed(FetchError.Unavailable())) }
        assertEquals(RandomOutcome.Offline, pick(1, RandomFilter.Archive(alpha, unsolvedOnly = false)))
        assertEquals(1, opens(alpha, alphaDays))
    }

    @Test
    fun `throttled stops after one open`() = runTest {
        alphaDays.forEach { daily.serve(alpha, it, DailyDay.Failed(FetchError.Throttled())) }
        assertEquals(RandomOutcome.Throttled, pick(1, RandomFilter.Archive(alpha, unsolvedOnly = false)))
        assertEquals(1, opens(alpha, alphaDays))
    }

    @Test
    fun `five failing days end in none opened with exactly five opens`() = runTest {
        alphaDays.forEach { daily.serve(alpha, it, DailyDay.Failed(FetchError.NotFound())) }
        assertEquals(RandomOutcome.NoneOpened, pick(1, RandomFilter.Archive(alpha, unsolvedOnly = false)))
        assertEquals(5, opens(alpha, alphaDays))
    }

    @Test
    fun `a pool of exactly five failing days is none opened after five opens`() = runTest {
        daily.setArchiveRange(alpha, alphaDays[0]..alphaDays[5])
        alphaDays.take(5).forEach { daily.serve(alpha, it, DailyDay.Failed(FetchError.NotFound())) }
        assertEquals(RandomOutcome.NoneOpened, pick(1, RandomFilter.Archive(alpha, unsolvedOnly = false)))
        assertEquals(5, opens(alpha, alphaDays))
    }

    @Test
    fun `an empty pool is nothing left with no opens`() = runTest {
        daily.setArchiveRange(alpha, null)
        assertEquals(RandomOutcome.NothingLeft, pick(1, RandomFilter.Archive(alpha, unsolvedOnly = false)))
        daily.setArchiveRange(alpha, today..today)
        assertEquals(RandomOutcome.NothingLeft, pick(1, RandomFilter.Archive(alpha, unsolvedOnly = false)))
        assertEquals(0, opens(alpha, alphaDays))
    }
}
