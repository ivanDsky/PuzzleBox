package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.domain.ObserveLeaderboard
import com.ivandsky.puzzlebox.domain.ObserveResultRank
import com.ivandsky.puzzlebox.domain.ResultRank
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInDay
import com.ivandsky.puzzlebox.domain.ranks.LoadFriendsPastPage
import com.ivandsky.puzzlebox.domain.ranks.ObserveFriendDay
import com.ivandsky.puzzlebox.domain.ranks.ObserveFriendUids
import com.ivandsky.puzzlebox.history.HistoryTestSupport
import com.ivandsky.puzzlebox.history.HistoryTestSupport.ZIP
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
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * #1767: a friend who placed past the global page still ranks on that day's Friends board, and so counts in the
 * Friends Week, Month and Overall and in the viewer's own place among friends.
 * #1779: the Friends chip on Result, Today and the game page counts that friend the same way.
 */
class RanksFriendsPastPageTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val nov1 = LocalDate.of(2026, 11, 1)
    private val today = LocalDate.of(2026, 11, 13)

    // Today and Tuesday: 99 strangers and Maya, the viewer's friend, fill the first 100 of 400 places. Bea, a second
    // friend, and Ann, the viewer, are both past them, so only a read of their own entries finds Bea.
    private val beyond = listOf(LocalDate.of(2026, 11, 10), today)

    private val flags = FakeReleaseFlags(HistoryFlag.WeekMonthPoints)

    private fun entry(day: LocalDate, uid: String, name: String, timeMs: Long) =
        LeaderboardEntry(uid, name, "${ZIP.value}/$day/${HistoryTestSupport.PUZZLE_NUMBER}", timeMs, 0, 1_700_000_000_000)

    private suspend fun hub(): RanksTestHub {
        val boards = (0L..12L).associate { offset ->
            val day = nov1.plusDays(offset)
            val page = if (day in beyond) (1..99).map { entry(day, "s$it", "S$it", it * 100L) } + entry(day, "maya", "Maya", 62_000) else emptyList()
            LeaderboardRef(ZIP.value, day) to page
        }
        val leaderboard = FakeLeaderboardRepository(boards)
        beyond.forEach {
            leaderboard.setCount(LeaderboardRef(ZIP.value, it), 400)
            leaderboard.setPastPage(LeaderboardRef(ZIP.value, it), listOf(entry(it, "bea", "Bea", 70_000)))
        }
        return RanksTestHub(leaderboard).apply {
            completions.setRecords(beyond.map { HistoryTestSupport.record(it, timeMs = 75_000, hints = 0).copy(firstCompletedAt = Instant.parse("2026-11-13T08:00:00Z"), isRecord = true) })
            befriend("maya")
            befriend("bea", code = "AAAAA3")
        }
    }

    private fun rowsOf(slice: RanksBoardSlice) = (slice.state as? DailyBoardState.Ranked)?.rows.orEmpty()

    /** [window]'s Friends board of Zip once Maya and the viewer are drawn on it and every day of it has answered. */
    private suspend fun RanksViewModel.friends(window: Window, day: LocalDate? = null): RanksBoardSlice {
        select { it.pickBoard(RanksBoard.Game(ZIP)).setWindow(window).setScope(RankScope.Friends).pickFromCalendar(day) }
        return uiState.first { state ->
            state.window == window && state.scope == RankScope.Friends && rowsOf(state.board).let { rows -> rows.any { it.name == "Maya" } && rows.any { it.isSelf } } &&
                state.board.note?.contains(" of ") != true
        }.board
    }

    private fun RanksBoardSlice.points() = rowsOf(this).associate { row ->
        when (val value = row.scoreValue) {
            is BoardRowValue.DayPoints -> row.name to (value.total to value.days)
            is BoardRowValue.Points -> row.name to (value.total to value.games)
            else -> error("$value")
        }
    }

    private fun assertPoints(message: String, total: Double, days: Int, actual: Pair<Double, Int>) {
        assertEquals(message, total, actual.first, 1e-9)
        assertEquals(message, days, actual.second)
    }

    @Test
    fun `the Friends Day board ranks a friend past the global page, and the viewer's place counts them`() = runTest {
        for (day in beyond) {
            val rows = rowsOf(hub().viewModel(releaseFlags = flags).friends(Window.Day, day))

            // Maya on the page, Bea past it, Ann, slower, past it too: 3rd of 3, not 2nd of 2.
            assertEquals("$day", listOf("Maya" to 1, "Bea" to 2, "Ann" to 3), rows.map { it.name to it.rank })
            assertEquals("$day", false, rows.single { it.isSelf }.unofficial)
        }
    }

    @Test
    fun `the Friends Week and Month count the friend past the global page on each day they placed there`() = runTest {
        for (window in listOf(Window.Week, Window.Month)) {
            val board = hub().viewModel(releaseFlags = flags).friends(window)

            // Two days among Maya, Bea and Ann: 100, 66.67 and 33.33 points a day.
            val points = board.points()
            assertEquals("$window", setOf("Maya", "Bea", "Ann"), points.keys)
            assertPoints("$window", 200.0, 2, points.getValue("Maya"))
            assertPoints("$window", 400.0 / 3, 2, points.getValue("Bea"))
            assertPoints("$window", 200.0 / 3, 2, points.getValue("Ann"))
            assertNull("$window", board.note)
        }
    }

    @Test
    fun `the Friends Overall counts the friend past the global page`() = runTest {
        val viewModel = hub().viewModel(releaseFlags = flags)
        viewModel.select { it.pickBoard(RanksBoard.Overall).setWindow(Window.Month).setScope(RankScope.Friends) }

        // Maya's two days say Zip's are in; Bea is found on the same two days by her own read.
        val overall = viewModel.uiState.first {
            it.board.title.startsWith("Overall") && it.scope == RankScope.Friends && it.board.points().keys == setOf("Maya", "Bea", "Ann")
        }.board.points()

        assertPoints("Overall", 200.0, 2, overall.getValue("Maya"))
        assertPoints("Overall", 400.0 / 3, 2, overall.getValue("Bea"))
        assertPoints("Overall", 200.0 / 3, 2, overall.getValue("Ann"))
    }

    private suspend fun chip(h: RanksTestHub) = ObserveResultRank(
        ObserveLeaderboard(h.identity, h.leaderboard, h.registry, h.calendar),
        h.registry,
        ObserveFriendDay(
            h.preferences, ObserveFriendUids(h.matches, h.identity), ObserveLinkedInDay(h.linkedInDays, h.registry), h.completions,
            friendsPast = LoadFriendsPastPage(h.leaderboard),
        ),
    ).also { h.preferences.setRanksFriendsDefault(true) }

    @Test
    fun `the Friends chip counts the friend past the global page, the place the Friends Day board draws`() = runTest {
        for (day in beyond) {
            val h = hub()
            val drawn = rowsOf(h.viewModel(releaseFlags = flags).friends(Window.Day, day)).single { it.isSelf }.rank

            val reported = chip(h)(ZIP, day).first { it is ResultRank.Ranked && it.amongFriends } as ResultRank.Ranked

            // Maya on the page, Bea past it, Ann, slower, past it too: 3rd of 3, not 2nd of 2.
            assertEquals("$day", 3, drawn)
            assertEquals("$day", ResultRank.Ranked(position = drawn, count = 3, topPercent = 100, amongFriends = true), reported)
        }
    }
}
