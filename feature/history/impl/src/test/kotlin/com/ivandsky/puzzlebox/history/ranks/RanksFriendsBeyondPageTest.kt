package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.domain.ObserveLeaderboard
import com.ivandsky.puzzlebox.domain.ObserveResultRank
import com.ivandsky.puzzlebox.domain.ResultRank
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInDay
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * #1747: a posted solve outside the global top 100 still ranks the viewer on that day's Friends board (A11), so the
 * Friends Day, Week and Month count it. Global's top-100 page is as it was.
 */
class RanksFriendsBeyondPageTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val nov1 = LocalDate.of(2026, 11, 1)
    private val today = LocalDate.of(2026, 11, 13)

    // Today, in the submit window, and Tuesday, past it: on both, 99 strangers and Maya, the viewer's friend, fill the
    // first 100 of 400 places and Ann, slower, is past them. Her solves are official records of her own.
    private val beyond = listOf(LocalDate.of(2026, 11, 10), today)

    private fun entry(day: LocalDate, uid: String, name: String, timeMs: Long) =
        LeaderboardEntry(uid, name, "${ZIP.value}/$day/${HistoryTestSupport.PUZZLE_NUMBER}", timeMs, 0, 1_700_000_000_000)

    private suspend fun hub(): RanksTestHub {
        val boards = (0L..12L).associate { offset ->
            val day = nov1.plusDays(offset)
            val page = if (day in beyond) (1..99).map { entry(day, "s$it", "S$it", it * 100L) } + entry(day, "maya", "Maya", 62_000) else emptyList()
            LeaderboardRef(ZIP.value, day) to page
        }
        val leaderboard = FakeLeaderboardRepository(boards)
        beyond.forEach { leaderboard.setCount(LeaderboardRef(ZIP.value, it), 400) }
        return RanksTestHub(leaderboard).apply {
            completions.setRecords(beyond.map { HistoryTestSupport.record(it, timeMs = 75_000, hints = 0).copy(firstCompletedAt = Instant.parse("2026-11-13T08:00:00Z"), isRecord = true) })
            befriend("maya")
        }
    }

    private fun rowsOf(slice: RanksBoardSlice) = (slice.state as? DailyBoardState.Ranked)?.rows.orEmpty()

    /** [window]'s Friends board of Zip once Maya is drawn on it and every day of it has answered. */
    private suspend fun RanksViewModel.friends(window: Window, day: LocalDate? = null): RanksBoardSlice {
        select { it.pickBoard(RanksBoard.Game(ZIP)).setWindow(window).setScope(RankScope.Friends).pickFromCalendar(day) }
        return uiState.first { state ->
            state.window == window && state.scope == RankScope.Friends && rowsOf(state.board).any { it.name == "Maya" } && state.board.note?.contains(" of ") != true
        }.board
    }

    @Test
    fun `the Friends Day board ranks a viewer who placed past the global page among their friends, in the submit window or past it`() = runTest {
        for (day in beyond) {
            val rows = rowsOf(hub().viewModel().friends(Window.Day, day))

            assertEquals("$day", listOf("Maya" to 1, "Ann" to 2), rows.map { it.name to it.rank })
            // A board row of their own, not the device-only row of a solve that cannot post.
            assertFalse("$day", rows.single { it.isSelf }.unofficial)
        }
    }

    @Test
    fun `the Friends chip on Result, Today and the game page reads the place the Friends Day board draws for that viewer`() = runTest {
        for (day in beyond) {
            val h = hub()
            val drawn = rowsOf(h.viewModel().friends(Window.Day, day)).single { it.isSelf }.rank
            val chip = ObserveResultRank(
                ObserveLeaderboard(h.identity, h.leaderboard, h.registry, h.calendar),
                h.registry,
                ObserveFriendDay(h.preferences, ObserveFriendUids(h.matches, h.identity), ObserveLinkedInDay(h.linkedInDays, h.registry), h.completions),
            )
            h.preferences.setRanksFriendsDefault(true)

            val reported = chip(ZIP, day).first { it is ResultRank.Ranked && it.amongFriends } as ResultRank.Ranked

            assertEquals("$day", 2, drawn)
            assertEquals("$day", ResultRank.Ranked(position = drawn, count = 2, topPercent = 100, amongFriends = true), reported)
        }
    }

    @Test
    fun `the Friends Week and Month count the days the viewer placed past the global page, none called unofficial`() = runTest {
        for (window in listOf(Window.Week, Window.Month)) {
            val board = hub().viewModel(releaseFlags = FakeReleaseFlags(HistoryFlag.WeekMonthPoints)).friends(window)

            // Two days among Maya and Ann: 100 points for 1st, 50 for 2nd, each day.
            val points = rowsOf(board).associate { it.name to (it.scoreValue as BoardRowValue.DayPoints).let { value -> value.total to value.days } }
            assertEquals("$window", mapOf("Maya" to (200.0 to 2), "Ann" to (100.0 to 2)), points)
            assertNull("$window", board.note)
        }
    }
}
