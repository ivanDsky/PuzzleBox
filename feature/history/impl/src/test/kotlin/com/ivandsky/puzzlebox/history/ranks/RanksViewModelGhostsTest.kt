package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.linkedin.LinkState
import com.ivandsky.puzzlebox.data.linkedin.LinkedInDayData
import com.ivandsky.puzzlebox.data.linkedin.LinkedInGhost
import com.ivandsky.puzzlebox.data.linkedin.LinkedInOwnState
import com.ivandsky.puzzlebox.domain.ranks.RankScope
import com.ivandsky.puzzlebox.gameui.board.DailyBoardLine
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState
import com.ivandsky.puzzlebox.history.HistoryTestSupport.ZIP
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** The Friends boards with the phone's LinkedIn connections counted on them (A15, F21), and Global with none. */
class RanksViewModelGhostsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 11, 13)

    private fun entry(uid: String, name: String, timeMs: Long) = LeaderboardEntry(uid, name, "zip/$today/1", timeMs, 0, 1_700_000_000_000)

    private fun hub(vararg strangers: LeaderboardEntry) = RanksTestHub(
        FakeLeaderboardRepository(mapOf(LeaderboardRef("zip", today) to strangers.toList() + listOf(entry("maya", "Maya", 62_000), entry("u1", "Ann", 75_000)))),
    ).apply {
        linkedInDays.emit(
            ZIP, today,
            LinkedInDayData(
                link = LinkState.Connected("member-1"), delta = 0, boardInWindow = true, inFlight = false, lastSync = null,
                own = LinkedInOwnState.Unknown, ghosts = listOf(LinkedInGhost("ref-Cal", "Cal", 70_000, hintFree = true, flawless = false)),
                boardFetchedAtMs = 1L, owner = null, boardComplete = false,
            ),
        )
    }

    private suspend fun RanksViewModel.card(scope: RankScope): RanksBoardSlice {
        select { it.pickBoard(RanksBoard.Game(ZIP)).setScope(scope) }
        return uiState.first { it.scope == scope && it.board.state is DailyBoardState.Ranked && it.board.title.contains("Zip") }.board
    }

    private fun placed(state: DailyBoardState) = (state as DailyBoardState.Ranked).lines!!.map {
        if (it is DailyBoardLine.Real) it.row.name to it.place else (it as DailyBoardLine.Ghost).let { g -> g.row.name to g.place }
    }

    @Test
    fun `Friends shows the connection among the rows with its shared place, and says so`() = runTest {
        val h = hub()
        h.befriend("maya")
        val card = h.viewModel(linkedIn = true).card(RankScope.Friends)

        assertEquals(listOf("Maya" to 1, "Cal" to 2, "Ann" to 3), placed(card.state))
        assertEquals("with LinkedIn connections", card.note)
    }

    @Test
    fun `a connection takes a friend place, and the viewer's place on the board counts it`() = runTest {
        val h = hub(entry("zed", "Zed", 50_000), entry("yan", "Yan", 55_000))
        h.befriend("maya")
        val viewModel = h.viewModel(linkedIn = true)
        viewModel.select { it.pickBoard(RanksBoard.Game(ZIP)).setScope(RankScope.Friends) }

        val state = viewModel.uiState.first { it.scope == RankScope.Friends && (it.board.state as? DailyBoardState.Ranked)?.lines?.size == 3 }

        // Global has Ann fourth; among friends she is second, and third with the connection ahead of her.
        assertEquals(listOf("Maya" to 1, "Cal" to 2, "Ann" to 3), placed(state.board.state))
        assertEquals(listOf("Maya" to 1, "Ann" to 2), (state.board.state as DailyBoardState.Ranked).rows.map { it.name to it.rank })
        assertEquals(Standing.Place(3), state.places.single { it.name == "Zip" }.standing)
    }

    private fun rowsOf(slice: RanksBoardSlice?) = (slice?.state as? DailyBoardState.Ranked)?.rows.orEmpty()

    @Test
    fun `Friends counts the connection on the game's week and on Overall, each place counting it`() = runTest {
        val h = hub()
        h.befriend("maya")
        val viewModel = h.viewModel(linkedIn = true)
        viewModel.select { it.pickBoard(RanksBoard.Game(ZIP)).setScope(RankScope.Friends).setWindow(RanksSelection.Window.Week) }

        val state = viewModel.uiState.first {
            it.scope == RankScope.Friends && it.window == RanksSelection.Window.Week && rowsOf(it.board).size == 3 && rowsOf(it.boards.firstOrNull()).size == 3
        }

        assertEquals(listOf("Maya" to 1, "Cal" to 2, "Ann" to 3), rowsOf(state.board).map { it.name to it.rank })
        assertEquals(listOf(false, true, false), rowsOf(state.board).map { it.fromLinkedIn })
        assertEquals(true, state.board.note?.endsWith("with LinkedIn connections"))
        val overall = state.boards.first()
        assertEquals(listOf("Maya" to 1, "Cal" to 2, "Ann" to 3), rowsOf(overall).map { it.name to it.rank })
        assertEquals(true, overall.note?.endsWith("with LinkedIn connections"))
        assertEquals(listOf("Overall", "Zip"), state.places.filter { it.standing == Standing.Place(3) }.map { it.name })
    }

    @Test
    fun `Global counts no connection on a week or on Overall`() = runTest {
        val h = hub()
        h.befriend("maya")
        val viewModel = h.viewModel(linkedIn = true)
        viewModel.select { it.pickBoard(RanksBoard.Game(ZIP)).setWindow(RanksSelection.Window.Week) }

        val state = viewModel.uiState.first {
            it.window == RanksSelection.Window.Week && rowsOf(it.board).size == 2 && rowsOf(it.boards.firstOrNull()).size == 2
        }

        assertEquals(listOf("Maya" to 1, "Ann" to 2), rowsOf(state.board).map { it.name to it.rank })
        assertEquals(listOf("Maya", "Ann"), rowsOf(state.boards.first()).map { it.name })
    }

    @Test
    fun `Global and a hub without LinkedIn show no ghosts`() = runTest {
        val h = hub()
        h.befriend("maya")

        assertNull((h.viewModel(linkedIn = true).card(RankScope.Global).state as DailyBoardState.Ranked).lines)
        assertNull((h.viewModel(linkedIn = false).card(RankScope.Friends).state as DailyBoardState.Ranked).lines)
    }
}
