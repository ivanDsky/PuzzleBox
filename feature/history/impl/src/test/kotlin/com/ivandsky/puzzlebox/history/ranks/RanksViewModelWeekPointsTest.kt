package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.domain.ranks.RankScope
import com.ivandsky.puzzlebox.history.HistoryTestSupport.ZIP
import com.ivandsky.puzzlebox.history.flags.HistoryFlag
import com.ivandsky.puzzlebox.history.ranks.RanksSelection.Window
import com.ivandsky.puzzlebox.resultui.board.BoardRowValue
import com.ivandsky.puzzlebox.resultui.board.DailyBoardState
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** WeekMonthPoints (#1698) ranks a game's Week and Month by its daily place points summed; off in release, averages as before. */
class RanksViewModelWeekPointsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val monday = LocalDate.of(2026, 11, 9)

    private fun entry(day: Int, uid: String, name: String, timeMs: Long) =
        LeaderboardEntry(uid, name, "${ZIP.value}/${monday.plusDays(day.toLong())}/1", timeMs, 0, 1_700_000_000_000)

    // The report on this week's Zip (Monday to today, Friday): Ann won only an easy Monday; Bea won Tuesday to Thursday
    // and was 3rd on Monday and Friday. Cy plays every day, Dan only Friday.
    private val week: Map<LeaderboardRef, List<LeaderboardEntry>> = listOf(
        listOf(entry(0, "u1", "Ann", 30_000), entry(0, "cy", "Cy", 40_000), entry(0, "bea", "Bea", 50_000)),
        listOf(entry(1, "bea", "Bea", 90_000), entry(1, "cy", "Cy", 95_000)),
        listOf(entry(2, "bea", "Bea", 90_000), entry(2, "cy", "Cy", 95_000)),
        listOf(entry(3, "bea", "Bea", 90_000), entry(3, "cy", "Cy", 95_000)),
        listOf(entry(4, "cy", "Cy", 100_000), entry(4, "dan", "Dan", 110_000), entry(4, "bea", "Bea", 120_000)),
    ).mapIndexed { day, entries -> LeaderboardRef(ZIP.value, monday.plusDays(day.toLong())) to entries }.toMap()

    private fun hub() = RanksTestHub(FakeLeaderboardRepository(week))

    private fun rowsOf(slice: RanksBoardSlice) = (slice.state as? DailyBoardState.Ranked)?.rows.orEmpty()

    private suspend fun RanksViewModel.zip(window: Window, scope: RankScope, players: Int): RanksUiState {
        select { it.pickBoard(RanksBoard.Game(ZIP)).setWindow(window).setScope(scope) }
        return uiState.first { it.window == window && it.scope == scope && rowsOf(it.board).size == players }
    }

    @Test
    fun `flag on, Global ranks the week by points summed and says so, and the Month too`() = runTest {
        val viewModel = hub().viewModel(releaseFlags = FakeReleaseFlags(HistoryFlag.WeekMonthPoints))

        val week = viewModel.zip(Window.Week, RankScope.Global, players = 4)

        assertEquals(listOf("Bea" to 1, "Cy" to 2, "Ann" to 3, "Dan" to 4), rowsOf(week.board).map { it.name to it.rank })
        val bea = rowsOf(week.board).first().scoreValue as BoardRowValue.DayPoints
        assertEquals(300.0 + 2 * 100.0 / 3, bea.total, 1e-9)
        assertEquals(5, bea.days)
        assertEquals(WEEK_MONTH_POINTS_CAPTION, week.board.caption)
        assertTrue(week.weekMonthPoints)

        val month = viewModel.zip(Window.Month, RankScope.Global, players = 4)
        assertEquals("Zip · this month", month.board.title)
        assertTrue(rowsOf(month.board).all { it.scoreValue is BoardRowValue.DayPoints })
    }

    @Test
    fun `flag on, Friends ranks the week among friends by their points`() = runTest {
        val h = hub()
        h.befriend("bea")
        h.befriend("cy", code = "AAAAA3")
        val viewModel = h.viewModel(releaseFlags = FakeReleaseFlags(HistoryFlag.WeekMonthPoints))

        val friends = viewModel.zip(Window.Week, RankScope.Friends, players = 3)

        // Among Ann, Bea and Cy, Bea's Friday 3rd is a 2nd: 300 + 33 + 50.
        assertEquals(listOf("Bea" to 1, "Cy" to 2, "Ann" to 3), rowsOf(friends.board).map { it.name to it.rank })
        assertEquals(350.0 + 100.0 / 3, (rowsOf(friends.board).first().scoreValue as BoardRowValue.DayPoints).total, 1e-9)
    }

    @Test
    fun `flag on keeps a game's day timed`() = runTest {
        val viewModel = hub().viewModel(releaseFlags = FakeReleaseFlags(HistoryFlag.WeekMonthPoints))

        val today = viewModel.zip(Window.Day, RankScope.Global, players = 3)

        assertEquals(BoardRowValue.Timed(100_000, 0), rowsOf(today.board).first().scoreValue)
        assertNull(today.board.caption)
    }

    @Test
    fun `flag off ranks the week by average as before, the easy Monday first`() = runTest {
        val viewModel = hub().viewModel(releaseFlags = FakeReleaseFlags())

        val week = viewModel.zip(Window.Week, RankScope.Global, players = 4)

        assertEquals(listOf("Ann", "Cy", "Bea", "Dan"), rowsOf(week.board).map { it.name })
        assertTrue(rowsOf(week.board).all { it.scoreValue is BoardRowValue.Average })
        assertNull(week.board.caption)
        assertFalse(week.weekMonthPoints)
    }
}
