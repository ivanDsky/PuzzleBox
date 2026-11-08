package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.game.TestGames.Ref
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The game solved most recently on its own today (A10): recency, per-game day, exclusions. */
class ObserveLatestSolvedTodayTest {

    private val today = LocalDate.of(2026, 11, 5)
    private val registry = GameRegistry(listOf(TestGames.plugin(alpha), TestGames.plugin(beta)))
    private val completions = FakeCompletionRepository()
    private val calendar = FakeGameCalendar(today)

    private suspend fun latest() = ObserveLatestSolvedToday(completions, calendar, registry)().first()

    private suspend fun solve(
        game: GameId,
        at: String?,
        on: LocalDate = today,
        number: Int? = 100,
    ) = completions.onCompleted(
        CompletionRecord(
            puzzleRef = Ref(game, PuzzleKey.Daily(on, number)),
            time = 30_000L,
            hints = 0,
            stats = RecordStats(schemaVersion = 1, blob = "{}"),
            firstCompletedAt = at?.let(Instant::parse),
            isRecord = true,
        ),
    )

    @Test
    fun `no solve today is null`() = runTest {
        solve(alpha, "2026-11-04T10:00:00Z", on = today.minusDays(1))

        assertNull(latest())
    }

    @Test
    fun `the later of today's two solves wins over an older-day solve`() = runTest {
        solve(alpha, "2026-11-05T08:00:00Z")
        solve(beta, "2026-11-05T09:00:00Z")
        solve(alpha, "2026-11-04T23:00:00Z", on = today.minusDays(1), number = 99)

        assertEquals(beta, latest())
    }

    @Test
    fun `an older-day solve never wins even when it is the latest`() = runTest {
        solve(alpha, "2026-11-05T08:00:00Z")
        solve(beta, "2026-11-06T09:00:00Z", on = today.minusDays(1), number = 99)

        assertEquals(alpha, latest())
    }

    @Test
    fun `practice and custom never win`() = runTest {
        solve(alpha, "2026-11-05T08:00:00Z")
        solve(beta, "2026-11-05T12:00:00Z", number = null)
        completions.onCompleted(
            CompletionRecord(
                puzzleRef = Ref(beta, PuzzleKey.Custom("ABCDEF")),
                time = 1L,
                hints = 0,
                stats = RecordStats(schemaVersion = 1, blob = "{}"),
                firstCompletedAt = Instant.parse("2026-11-05T13:00:00Z"),
                isRecord = true,
            ),
        )

        assertEquals(alpha, latest())
    }

    @Test
    fun `a null firstCompletedAt sorts last`() = runTest {
        solve(alpha, null)
        solve(beta, "2026-11-05T08:00:00Z")

        assertEquals(beta, latest())
    }

    @Test
    fun `a lone solve without a timestamp still counts`() = runTest {
        solve(alpha, null)

        assertEquals(alpha, latest())
    }

    @Test
    fun `each game is judged on its own day`() = runTest {
        calendar.set(beta, today.plusDays(1))
        solve(alpha, "2026-11-05T08:00:00Z")
        solve(beta, "2026-11-05T09:00:00Z")

        assertEquals(alpha, latest())
    }
}
