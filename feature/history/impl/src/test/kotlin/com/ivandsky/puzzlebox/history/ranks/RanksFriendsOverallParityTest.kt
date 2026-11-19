package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.linkedin.LinkState
import com.ivandsky.puzzlebox.data.linkedin.LinkedInDayData
import com.ivandsky.puzzlebox.data.linkedin.LinkedInGhost
import com.ivandsky.puzzlebox.data.linkedin.LinkedInOwnState
import com.ivandsky.puzzlebox.domain.ranks.OverallState
import com.ivandsky.puzzlebox.domain.ranks.RankWindow
import com.ivandsky.puzzlebox.domain.ranks.connectionUid
import com.ivandsky.puzzlebox.domain.ranks.days
import com.ivandsky.puzzlebox.history.HistoryTestSupport
import com.ivandsky.puzzlebox.history.HistoryTestSupport.ZIP
import com.ivandsky.puzzlebox.history.domain.ranks.RankScope
import com.ivandsky.puzzlebox.resultui.board.BoardRowValue
import com.ivandsky.puzzlebox.resultui.board.DailyBoardState
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The hub's Friends Overall and the domain's [com.ivandsky.puzzlebox.domain.ranks.ObserveFriendsOverall], a second
 * caller of the same pipeline: for the same window and games each player gets the same points, connections included.
 */
class RanksFriendsOverallParityTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 11, 13)
    private val week = RankWindow.Week(today)
    private val games = rankBoards(HistoryTestSupport.registry).filterIsInstance<RanksBoard.Game>().map { it.id }

    private fun entry(uid: String, name: String, game: String, timeMs: Long, day: LocalDate = today) =
        LeaderboardEntry(uid, name, "$game/$day/1", timeMs, 0, 1_700_000_000_000)

    /** Every game's board on every day of [week] so far, answered and empty unless [played]: neither Overall stays partial. */
    private fun boards(played: Map<LeaderboardRef, List<LeaderboardEntry>>) =
        games.flatMap { game -> week.days(today).map { LeaderboardRef(game.value, it) } }.associateWith { played[it].orEmpty() }

    @Test
    fun `the hub's Friends Overall and a second caller give every player the same points`() = runTest {
        // Zed is a stranger on both of today's boards; Cal is a connection on today's Zip; Maya also played yesterday's Zip.
        val yesterday = today.minusDays(1)
        val h = RanksTestHub(
            FakeLeaderboardRepository(
                boards(
                    mapOf(
                        LeaderboardRef("zip", today) to listOf(entry("zed", "Zed", "zip", 50_000), entry("maya", "Maya", "zip", 62_000), entry("u1", "Ann", "zip", 75_000)),
                        LeaderboardRef("tango", today) to listOf(entry("zed", "Zed", "tango", 40_000), entry("u1", "Ann", "tango", 45_000), entry("maya", "Maya", "tango", 90_000)),
                        LeaderboardRef("zip", yesterday) to listOf(entry("maya", "Maya", "zip", 30_000, yesterday)),
                    ),
                ),
            ),
        )
        h.linkedInDays.emit(
            ZIP, today,
            LinkedInDayData(
                link = LinkState.Connected("member-1"), delta = 0, boardInWindow = true, inFlight = false, lastSync = null,
                own = LinkedInOwnState.Unknown, ghosts = listOf(LinkedInGhost("ref-Cal", "Cal", 70_000, hintFree = true, flawless = false)),
                boardFetchedAtMs = 1L, owner = null,
            ),
        )
        h.befriend("maya")
        val viewModel = h.viewModel(linkedIn = true)
        viewModel.select { it.pickBoard(RanksBoard.Game(ZIP)).setScope(RankScope.Friends).setWindow(RanksSelection.Window.Week) }

        // Every game answered (no "k of n games" note) and the connection counted: the hub's Overall is complete.
        val hubRows = viewModel.uiState.first { state ->
            val overall = state.boards.firstOrNull()
            state.scope == RankScope.Friends && state.window == RanksSelection.Window.Week &&
                overall?.note == "with LinkedIn connections" && (overall.state as? DailyBoardState.Ranked)?.rows?.size == 3
        }.let { (it.boards.first().state as DailyBoardState.Ranked).rows }
        val hub = hubRows.map { it.playerId to (it.value as BoardRowValue.Points).total }

        val standings = h.friendsOverall()(week, games)
            .first { it is OverallState.Complete && it.standings.rows.size == 3 }
            .let { (it as OverallState.Complete).standings }
        val secondCaller = standings.rows.map { it.uid to it.total }

        assertEquals(setOf("maya", connectionUid("ref-Cal"), "u1"), hub.map { it.first }.toSet())
        assertEquals(hub, secondCaller)
    }
}
