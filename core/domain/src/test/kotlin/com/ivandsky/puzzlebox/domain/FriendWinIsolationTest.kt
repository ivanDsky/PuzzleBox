package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntries
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.game.TestGames.Ref
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Friend-puzzle wins beside the dailies (SC10): Today, History (whose edition number the daily
 * share line reads), the streak, best and stats, and the entries the daily board is fed all
 * read exactly as they do over the dailies alone. The friend wins are faster than every daily
 * and one is of a game whose today is unplayed, so any leak would move a number.
 */
class FriendWinIsolationTest {

    private val today = LocalDate.of(2026, 11, 10)
    private val clock = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC)
    private val games = listOf(alpha, beta)

    /** alpha solved today and the two days before; beta solved yesterday only, today unplayed. */
    private val dailies = listOf(
        daily(alpha, daysAgo = 0, time = 50_000L),
        daily(alpha, daysAgo = 1, time = 40_000L, hints = 1),
        daily(alpha, daysAgo = 2, time = 45_000L),
        daily(beta, daysAgo = 1, time = 70_000L),
    )

    /** First wins of two friend puzzles, today, faster than any daily and hint-free. */
    private val friendWins = listOf(
        friendWin(beta, code = "K7Q2MX", time = 5_000L),
        friendWin(alpha, code = "ABC234", time = 1_000L),
    )

    @Test
    fun `friend wins leave every daily read as the dailies alone leave it`() = runTest {
        assertEquals(dailyReads(dailies), dailyReads(dailies + friendWins))
    }

    @Test
    fun `friend wins alone read as nothing played`() = runTest {
        assertEquals(dailyReads(emptyList()), dailyReads(friendWins))
    }

    @Test
    fun `the dailies alone move every read the friend wins leave alone`() = runTest {
        val reads = dailyReads(dailies)

        assertEquals(
            listOf(TodayStatus.Solved(50_000L), TodayStatus.NotStarted),
            reads.today.map { it.status },
        )
        assertEquals(dailies.size, reads.history.count { it.record != null })
        assertEquals(listOf(NUMBER), reads.history.map { it.puzzleNumber }.distinct())
        assertEquals(3, reads.stats.getValue(alpha).currentStreak)
        assertEquals(1, reads.stats.getValue(beta).currentStreak)
        assertEquals(BestScore(timeMs = 70_000L, hints = 0), reads.stats.getValue(beta).best)
        assertEquals(dailies.size, reads.stats.getValue(null).solved)
        assertEquals(dailies.size, reads.board.size)
    }

    /** Everything the dailies' surfaces read over [records]: Today, History, stats per game and overall, board entries. */
    private suspend fun dailyReads(records: List<CompletionRecord>): DailyReads {
        val registry = TestGames.registry(games)
        val calendar = FakeGameCalendar(today)
        val completions = FakeCompletionRepository(records)
        val daily = FakeDailyRepository().apply {
            games.forEach { setToday(it, DailyToday.Fresh(TestGames.puzzle(it))) }
            dailies.forEach { addEntry(DailyEntry(it.puzzleRef.gameId, dayOf(it), NUMBER, TestGames.variant(it.puzzleRef.gameId).value, 0L)) }
        }
        val history = ObserveGameHistory(registry, daily, completions)
        val stats = ObserveGameStats(registry, history, ComputeStreak(GetPuzzleDay(registry, clock)))
        val sync = SyncTodayPuzzles(registry, daily, calendar)
        return DailyReads(
            today = ObserveTodaySet(registry, FakeSavedPlayRepository(), completions, calendar, daily, sync)().first(),
            history = history().first(),
            stats = (games + null).associateWith { stats(it).first() },
            board = records.mapNotNull { LeaderboardEntries.fromRecord(it, uid = UID, displayName = NAME) },
        )
    }

    private data class DailyReads(
        val today: List<TodayEntry>,
        val history: List<GameHistoryDay>,
        val stats: Map<GameId?, GameStats>,
        val board: List<LeaderboardEntry>,
    )

    private fun dayOf(record: CompletionRecord) = (record.puzzleRef.key as PuzzleKey.Daily).date

    private fun daily(game: GameId, daysAgo: Long, time: Long, hints: Int = 0) =
        record(Ref(game, PuzzleKey.Daily(today.minusDays(daysAgo), NUMBER)), time, hints)

    private fun friendWin(game: GameId, code: String, time: Long) = record(Ref(game, PuzzleKey.Custom(code)), time, hints = 0)

    /** A first win, stamped as the record store stamps one, so a daily is board-eligible. */
    private fun record(ref: Ref, time: Long, hints: Int) = CompletionRecord(
        puzzleRef = ref,
        time = time,
        hints = hints,
        stats = RecordStats(schemaVersion = 1, blob = "{}"),
        firstCompletedAt = clock.instant(),
        isRecord = true,
    )

    private companion object {
        const val NUMBER = 918
        const val UID = "friend"
        const val NAME = "Friend"
    }
}
