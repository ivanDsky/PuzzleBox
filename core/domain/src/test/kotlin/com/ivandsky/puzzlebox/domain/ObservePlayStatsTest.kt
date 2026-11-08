package com.ivandsky.puzzlebox.domain

import app.cash.turbine.test
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardSnapshot
import com.ivandsky.puzzlebox.data.leaderboard.SubmitResult
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.game.TestGames.Ref
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tablet play side panel's data for one slot (A15): today's set progress, the game's
 * stats and streak days, its board, and whether the slot is solved. The test games' zone
 * is UTC, so the puzzle day at the fixed clock is Nov 10.
 */
class ObservePlayStatsTest {

    private val today = LocalDate.of(2026, 11, 10)
    private val clock = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC)
    private val registry = GameRegistry(listOf(TestGames.plugin(alpha), TestGames.plugin(beta)))
    private val calendar = FakeGameCalendar(today)
    private val daily = FakeDailyRepository()
    private val completions = FakeCompletionRepository()
    private val ann = Identity("u1", "Ann", null)
    private val slot = PlaySlot.Daily(alpha, today)

    private fun observe(
        leaderboard: LeaderboardRepository = FakeLeaderboardRepository(),
        slot: PlaySlot = this.slot,
    ): Flow<PlayStats> {
        val history = ObserveGameHistory(registry, daily, completions)
        return ObservePlayStats(
            todaySet = ObserveTodaySet(
                registry, FakeSavedPlayRepository(), completions, calendar, daily,
                SyncTodayPuzzles(registry, daily, calendar),
            ),
            stats = ObserveGameStats(registry, history, ComputeStreak(GetPuzzleDay(registry, clock))),
            history = history,
            calendar = calendar,
            leaderboard = ObserveLeaderboard(FakeIdentityService(initial = ann), leaderboard, registry),
        )(slot)
    }

    private fun solve(game: GameId, daysAgo: Long, puzzleNumber: Int? = 100) = CompletionRecord(
        puzzleRef = Ref(game, PuzzleKey.Daily(today.minusDays(daysAgo), puzzleNumber)),
        time = 30_000L,
        hints = 0,
        stats = RecordStats(schemaVersion = 1, blob = "{}"),
        isRecord = true,
    )

    @Test
    fun `the local stats arrive while the board is still loading`() = runTest {
        completions.setRecords(listOf(solve(alpha, daysAgo = 1), solve(alpha, daysAgo = 2), solve(beta, daysAgo = 0)))
        val silent = object : LeaderboardRepository {
            override fun observe(board: LeaderboardRef, order: RankOrder): Flow<LeaderboardSnapshot?> = emptyFlow()
            override suspend fun submit(entry: LeaderboardEntry): SubmitResult = error("unused")
        }

        val stats = observe(silent).first()

        assertEquals(1, stats.solvedToday)
        assertEquals(2, stats.gamesToday)
        assertEquals(2, stats.stats.currentStreak)
        assertEquals(today, stats.today)
        assertEquals(TodayLeaderboard.SignedIn(ann, TodayLeaderboard.Board.Loading), stats.board)
    }

    @Test
    fun `streak days are this game's numbered, non-archived solves`() = runTest {
        completions.setRecords(
            listOf(
                solve(alpha, daysAgo = 1),
                solve(alpha, daysAgo = 2),
                // Practice and another game's solves never ring this game's strip.
                solve(alpha, daysAgo = 3, puzzleNumber = null),
                solve(beta, daysAgo = 4),
            ),
        )
        // Two days ago was opened from the archive (A23): a solve, never a streak day.
        daily.addEntry(DailyEntry(alpha, today.minusDays(2), 98, "v", clock.millis(), archived = true))

        assertEquals(setOf(today.minusDays(1)), observe().first().streakDays)
    }

    @Test
    fun `the slot reads solved once its daily has a record`() = runTest {
        val leaderboard = FakeLeaderboardRepository()
        observe(leaderboard).test {
            assertFalse(awaitItem().solved)
            completions.setRecords(listOf(solve(alpha, daysAgo = 0)))
            var item = awaitItem()
            while (!item.solved) item = awaitItem()
            assertTrue(item.solved)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf(LeaderboardRef(alpha.value, today)), leaderboard.observedBoards)
    }

    @Test
    fun `a custom puzzle has no board and never reads solved, even when today's daily is`() = runTest {
        completions.setRecords(listOf(solve(alpha, daysAgo = 0)))
        val leaderboard = FakeLeaderboardRepository()

        val stats = observe(leaderboard, PlaySlot.Custom(alpha, "ABC123")).first()

        assertEquals(null, stats.board)
        assertFalse(stats.solved)
        assertEquals(emptyList<LeaderboardRef>(), leaderboard.observedBoards)
    }
}
