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

/** A game's Friends day board with the phone's LinkedIn connections on it (A15), and nowhere else. */
class RanksViewModelGhostsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 11, 13)

    private fun entry(uid: String, name: String, timeMs: Long) = LeaderboardEntry(uid, name, "zip/$today/1", timeMs, 0, 1_700_000_000_000)

    private fun hub() = RanksTestHub(
        FakeLeaderboardRepository(mapOf(LeaderboardRef("zip", today) to listOf(entry("maya", "Maya", 62_000), entry("u1", "Ann", 75_000)))),
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

    @Test
    fun `Friends shows the connection among the rows with its shared place, and says so`() = runTest {
        val h = hub()
        h.befriend("maya")
        val card = h.viewModel(linkedIn = true).card(RankScope.Friends)

        val lines = (card.state as DailyBoardState.Ranked).lines!!
        assertEquals(listOf("Maya" to 1, "Cal" to 2, "Ann" to 3), lines.map { if (it is DailyBoardLine.Real) it.row.name to it.place else (it as DailyBoardLine.Ghost).let { g -> g.row.name to g.place } })
        assertEquals("with LinkedIn connections", card.note)
    }

    @Test
    fun `Global and a hub without LinkedIn show no ghosts`() = runTest {
        val h = hub()
        h.befriend("maya")

        assertNull((h.viewModel(linkedIn = true).card(RankScope.Global).state as DailyBoardState.Ranked).lines)
        assertNull((h.viewModel(linkedIn = false).card(RankScope.Friends).state as DailyBoardState.Ranked).lines)
    }
}
