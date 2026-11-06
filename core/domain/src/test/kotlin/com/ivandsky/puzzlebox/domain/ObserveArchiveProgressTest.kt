package com.ivandsky.puzzlebox.domain

import app.cash.turbine.test
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.game.TestGames.Ref
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A game's archive progress (spec A26/A27): its range, total and solved counts agree with the stats. */
class ObserveArchiveProgressTest {

    private val day1 = LocalDate.of(2025, 3, 18)
    private val today = LocalDate.of(2026, 11, 5)
    private val clock = Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneOffset.UTC)

    private val registry = GameRegistry(listOf(TestGames.plugin(alpha), TestGames.plugin(beta)))
    private val completions = FakeCompletionRepository()
    private val daily = FakeDailyRepository().apply { setArchiveRange(alpha, day1..today) }
    private val history = ObserveGameHistory(registry, daily, completions)

    private fun progress(game: GameId = alpha) =
        ObserveArchiveProgress(daily, FakeGameCalendar(today), history)(game)

    private fun stats(game: GameId) =
        ObserveGameStats(registry, history, ComputeStreak(GetPuzzleDay(registry, clock)))(game)

    /** [game]'s first solve of [on]; a null [puzzleNumber] is a practice play. */
    private fun solve(on: LocalDate, game: GameId = alpha, puzzleNumber: Int? = 100) =
        CompletionRecord(
            puzzleRef = Ref(game, on, puzzleNumber),
            time = 30_000L,
            hints = 0,
            stats = RecordStats(schemaVersion = 1, blob = "{}"),
            isRecord = true,
        )

    private fun capture(on: LocalDate, archived: Boolean, game: GameId = alpha) =
        daily.addEntry(DailyEntry(game, on, 100, variant = "v", fetchedAtMs = 0L, archived = archived))

    @Test
    fun `a game without an archive has no progress`() = runTest {
        completions.onCompleted(solve(today, game = beta))

        assertNull(progress(beta).first())
    }

    @Test
    fun `the range runs from #1 to today`() = runTest {
        val progress = progress().first()!!

        assertEquals(day1..today, progress.range)
        assertEquals(598, progress.total)
        assertEquals(0, progress.solved)
        assertEquals(0, progress.archivedSolves)
    }

    @Test
    fun `solved counts archived days, matches the stats and ignores practice and other games`() = runTest {
        capture(today.minusDays(10), archived = true)
        completions.onCompleted(solve(today.minusDays(10)))
        capture(today.minusDays(11), archived = true)
        completions.onCompleted(solve(today.minusDays(11)))
        capture(today, archived = false)
        completions.onCompleted(solve(today))
        capture(today.minusDays(12), archived = true)
        completions.onCompleted(solve(today.minusDays(3), puzzleNumber = null))
        completions.onCompleted(solve(today, game = beta))

        val progress = progress().first()!!

        assertEquals(3, progress.solved)
        assertEquals(2, progress.archivedSolves)
        assertEquals(stats(alpha).first().solved, progress.solved)
    }

    @Test
    fun `progress updates on a new solve`() = runTest {
        progress().test {
            assertEquals(0, awaitItem()!!.solved)
            completions.onCompleted(solve(today))
            assertEquals(1, awaitItem()!!.solved)
        }
    }
}
