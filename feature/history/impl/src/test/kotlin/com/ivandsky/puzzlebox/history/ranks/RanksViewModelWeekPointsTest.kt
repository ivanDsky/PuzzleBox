package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.linkedin.LinkState
import com.ivandsky.puzzlebox.data.linkedin.LinkedInDayData
import com.ivandsky.puzzlebox.data.linkedin.LinkedInOwnState
import com.ivandsky.puzzlebox.game.CompletionSource
import com.ivandsky.puzzlebox.history.HistoryTestSupport.ZIP
import com.ivandsky.puzzlebox.history.HistoryTestSupport.TANGO
import com.ivandsky.puzzlebox.history.HistoryTestSupport.TANGO_PUZZLE_NUMBER
import com.ivandsky.puzzlebox.history.HistoryTestSupport.tangoRecord
import com.ivandsky.puzzlebox.history.domain.ranks.RankScope
import com.ivandsky.puzzlebox.history.flags.HistoryFlag
import com.ivandsky.puzzlebox.history.ranks.RanksSelection.Window
import com.ivandsky.puzzlebox.resultui.board.BoardRowValue
import com.ivandsky.puzzlebox.resultui.board.DailyBoardState
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.Instant
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

    // #1743: Ann solved Tango every day from the 1st to today, the 13th, but is on only 9 of its boards. Nobody posted on
    // the 1st and 2nd, which she imported from LinkedIn days later; she played the 5th from the archive; today's solve
    // has not posted yet. Bea posted the 3rd to the 13th.
    private val nov1 = LocalDate.of(2026, 11, 1)

    private fun tangoEntry(day: Int, uid: String, name: String, timeMs: Long) =
        LeaderboardEntry(uid, name, "${TANGO.value}/${nov1.plusDays(day - 1L)}/$TANGO_PUZZLE_NUMBER", timeMs, 0, 1_700_000_000_000)

    private val annOn = setOf(3, 4, 6, 7, 8, 9, 10, 11, 12)

    private val month: Map<LeaderboardRef, List<LeaderboardEntry>> = (1..13).associate { day ->
        LeaderboardRef(TANGO.value, nov1.plusDays(day - 1L)) to listOfNotNull(
            tangoEntry(day, "u1", "Ann", 50_000).takeIf { day in annOn },
            tangoEntry(day, "bea", "Bea", 60_000).takeIf { day >= 3 },
        )
    }

    private fun annSolves() = (1..13).map { day ->
        tangoRecord(nov1.plusDays(day - 1L)).copy(firstCompletedAt = Instant.parse("2026-11-13T08:00:00Z"), isRecord = true)
            .let { if (day <= 2) it.copy(source = CompletionSource.LinkedIn) else it }
    }

    private suspend fun RanksViewModel.tangoMonth(scope: RankScope): RanksUiState {
        select { it.pickBoard(RanksBoard.Game(TANGO)).setWindow(Window.Month).setScope(scope) }
        // Every day in: no "k of n days" while boards load.
        return uiState.first { state ->
            state.window == Window.Month && state.scope == scope && rowsOf(state.board).size == 2 && state.board.note?.contains(" of ") != true
        }
    }

    @Test
    fun `flag on, a month counts only the days on the boards and says the viewer's other solved days earn no points, Global and Friends`() = runTest {
        val h = RanksTestHub(FakeLeaderboardRepository(month))
        h.completions.setRecords(annSolves())
        h.befriend("bea")
        val viewModel = h.viewModel(releaseFlags = FakeReleaseFlags(HistoryFlag.WeekMonthPoints))

        for (scope in listOf(RankScope.Global, RankScope.Friends)) {
            val board = viewModel.tangoMonth(scope).board

            val days = rowsOf(board).associate { it.name to (it.scoreValue as BoardRowValue.DayPoints).days }
            assertEquals("$scope", mapOf("Ann" to 9, "Bea" to 11), days)
            // The 1st, 2nd and 5th can never post; today's is still on its way, so it is not one of them.
            assertEquals("$scope", "3 solved days are unofficial and earn no points", board.note)
        }
    }

    @Test
    fun `flag off, the month's averages say nothing of unofficial days`() = runTest {
        val h = RanksTestHub(FakeLeaderboardRepository(month))
        h.completions.setRecords(annSolves())
        val viewModel = h.viewModel(releaseFlags = FakeReleaseFlags())

        assertNull(viewModel.tangoMonth(RankScope.Global).board.note)
    }

    // #1743, second pass: Ann solved Tango on all 19 days to today, 6 of them only on LinkedIn, so no board has her on
    // those. Bea posted every day, slower.
    private val linkedInOnly = setOf(1, 2, 5, 8, 11, 14)

    private fun annOnLinkedIn(): RanksTestHub {
        val boards = (1..19).associate { day ->
            LeaderboardRef(TANGO.value, nov1.plusDays(day - 1L)) to listOfNotNull(
                tangoEntry(day, "u1", "Ann", 50_000).takeIf { day !in linkedInOnly },
                tangoEntry(day, "bea", "Bea", 60_000),
            )
        }
        val h = RanksTestHub(FakeLeaderboardRepository(boards), today = nov1.plusDays(18))
        val solves = (1..19).associate { day ->
            val record = tangoRecord(nov1.plusDays(day - 1L), timeMs = 50_000).copy(firstCompletedAt = Instant.parse("2026-11-19T08:00:00Z"), isRecord = true)
            day to if (day in linkedInOnly) record.copy(source = CompletionSource.LinkedIn) else record
        }
        h.completions.setRecords(solves.values.toList())
        for (day in linkedInOnly) {
            val imported = LinkedInOwnState.Imported(solves.getValue(day))
            val data = LinkedInDayData(LinkState.Connected("ref-ann"), delta = 0, boardInWindow = false, inFlight = false, lastSync = null, own = imported, ghosts = emptyList(), boardFetchedAtMs = null)
            h.linkedInDays.emit(TANGO, nov1.plusDays(day - 1L), data)
        }
        return h
    }

    private fun RanksBoardSlice.points() = rowsOf(this).associate { row ->
        when (val value = row.scoreValue) {
            is BoardRowValue.DayPoints -> row.name to (value.total to value.days)
            is BoardRowValue.Points -> row.name to (value.total to value.games)
            else -> error("$value")
        }
    }

    @Test
    fun `flag on, Friends counts the viewer's LinkedIn-only days like a connection's, in the month and Overall, Global as before`() = runTest {
        val h = annOnLinkedIn()
        h.befriend("bea")
        val viewModel = h.viewModel(linkedIn = true, releaseFlags = FakeReleaseFlags(HistoryFlag.WeekMonthPoints))

        // First of two every day: 100 points a day to Bea's 50, and nothing left unofficial.
        val friends = viewModel.tangoMonth(RankScope.Friends).board
        assertEquals(mapOf("Ann" to (1900.0 to 19), "Bea" to (950.0 to 19)), friends.points())
        assertNull(friends.note)

        viewModel.select { it.pickBoard(RanksBoard.Overall).setWindow(Window.Month).setScope(RankScope.Friends) }
        // Bea is on every Tango board, so her 19 days say they are all in; the other games' notes don't matter here.
        val overall = viewModel.uiState.first {
            it.board.title.startsWith("Overall") && it.scope == RankScope.Friends && rowsOf(it.board).size == 2 && it.board.points()["Bea"]?.second == 19
        }
        assertEquals(mapOf("Ann" to (1900.0 to 19), "Bea" to (950.0 to 19)), overall.board.points())

        // LinkedIn times count for nobody in Global: Bea alone on the 6 days.
        val global = viewModel.tangoMonth(RankScope.Global).board
        assertEquals(mapOf("Ann" to (1300.0 to 13), "Bea" to (1250.0 to 19)), global.points())
        assertEquals("6 solved days are unofficial and earn no points", global.note)
    }
}
