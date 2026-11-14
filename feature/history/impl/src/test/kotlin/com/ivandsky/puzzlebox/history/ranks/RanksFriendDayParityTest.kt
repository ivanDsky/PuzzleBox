package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.linkedin.LinkState
import com.ivandsky.puzzlebox.data.linkedin.LinkedInDayData
import com.ivandsky.puzzlebox.data.linkedin.LinkedInGhost
import com.ivandsky.puzzlebox.data.linkedin.LinkedInOwnState
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInDay
import com.ivandsky.puzzlebox.domain.ranks.FriendDay
import com.ivandsky.puzzlebox.domain.ranks.ObserveFriendDay
import com.ivandsky.puzzlebox.domain.ranks.ObserveFriendUids
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
import org.junit.Rule
import org.junit.Test

/** The place an entry point reports is the viewer's place on the hub's Friends Day board, as the hub draws it (D4). */
class RanksFriendDayParityTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 11, 13)

    private fun entry(uid: String, name: String, timeMs: Long) = LeaderboardEntry(uid, name, "zip/$today/1", timeMs, 0, 1_700_000_000_000)

    // Strangers first and third; a connection between Maya and the viewer, and one whose time is hidden.
    private val entries = listOf(entry("zed", "Zed", 50_000), entry("maya", "Maya", 62_000), entry("yan", "Yan", 66_000), entry("u1", "Ann", 75_000))

    private val connections = listOf(
        LinkedInGhost("ref-Cal", "Cal", 70_000, hintFree = true, flawless = false),
        LinkedInGhost("ref-Dee", "Dee", null, hintFree = true, flawless = false),
    )

    private fun DailyBoardLine.place() = when (this) {
        is DailyBoardLine.Real -> place
        is DailyBoardLine.Ghost -> place
    }

    @Test
    fun `an entry point reports the viewer's place on the Friends day board the hub draws`() = runTest {
        val h = RanksTestHub(FakeLeaderboardRepository(mapOf(LeaderboardRef("zip", today) to entries)), friendsDefault = true)
        h.linkedInDays.emit(
            ZIP, today,
            LinkedInDayData(
                link = LinkState.Connected("member-1"), delta = 0, boardInWindow = true, inFlight = false, lastSync = null,
                own = LinkedInOwnState.Unknown, ghosts = connections, boardFetchedAtMs = 1L, owner = null, boardComplete = false,
            ),
        )
        h.befriend("maya")

        val viewModel = h.viewModel(linkedIn = true)
        viewModel.select { it.pickBoard(RanksBoard.Game(ZIP)).setScope(RankScope.Friends) }
        val lines = viewModel.uiState.first { state ->
            val drawn = (state.board.state as? DailyBoardState.Ranked)?.lines.orEmpty()
            state.scope == RankScope.Friends && drawn.any { it is DailyBoardLine.Real && it.row.playerId == "maya" }
        }.let { (it.board.state as DailyBoardState.Ranked).lines!! }
        val drawnPlace = lines.filterIsInstance<DailyBoardLine.Real>().single { it.row.isSelf }.place

        val reported = ObserveFriendDay(h.preferences, ObserveFriendUids(h.matches, h.identity), ObserveLinkedInDay(h.linkedInDays, h.registry))(ZIP, today)
            .first { "maya" in it?.friends.orEmpty() }!!
            .standing(entries, "u1", h.registry.rankOrderOf(ZIP))

        assertEquals(FriendDay.Standing(place = 3, count = 3), reported)
        assertEquals(reported?.place, drawnPlace)
        assertEquals(reported?.count, lines.count { it.place() != null })
    }
}
