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

/**
 * The hub's Friends Day board, whose places come from the domain's Friends pipeline (D4): the place an entry point
 * reports is the viewer's place on it, and its rows stay in the order the board drew them in before.
 */
class RanksFriendDayParityTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 11, 13)

    private fun entry(uid: String, name: String, timeMs: Long) = LeaderboardEntry(uid, name, "zip/$today/1", timeMs, 0, 1_700_000_000_000)

    private fun DailyBoardLine.place() = when (this) {
        is DailyBoardLine.Real -> place
        is DailyBoardLine.Ghost -> place
    }

    private fun hub(entries: List<LeaderboardEntry>, connections: List<LinkedInGhost>) =
        RanksTestHub(FakeLeaderboardRepository(mapOf(LeaderboardRef("zip", today) to entries)), friendsDefault = true).apply {
            linkedInDays.emit(
                ZIP, today,
                LinkedInDayData(
                    link = LinkState.Connected("member-1"), delta = 0, boardInWindow = true, inFlight = false, lastSync = null,
                    own = LinkedInOwnState.Unknown, ghosts = connections, boardFetchedAtMs = 1L, owner = null, boardComplete = false,
                ),
            )
        }

    /** The lines of Zip's Friends Day board once every one of [friends] is drawn on it. */
    private suspend fun RanksTestHub.friendsDayLines(vararg friends: String): List<DailyBoardLine> {
        val viewModel = viewModel(linkedIn = true)
        viewModel.select { it.pickBoard(RanksBoard.Game(ZIP)).setScope(RankScope.Friends) }
        return viewModel.uiState.first { state ->
            val drawn = (state.board.state as? DailyBoardState.Ranked)?.lines.orEmpty().filterIsInstance<DailyBoardLine.Real>().map { it.row.playerId }
            state.scope == RankScope.Friends && drawn.containsAll(friends.toList())
        }.let { (it.board.state as DailyBoardState.Ranked).lines!! }
    }

    @Test
    fun `an entry point reports the viewer's place on the Friends day board the hub draws`() = runTest {
        // Strangers first and third; a connection between Maya and the viewer, and one whose time is hidden.
        val entries = listOf(entry("zed", "Zed", 50_000), entry("maya", "Maya", 62_000), entry("yan", "Yan", 66_000), entry("u1", "Ann", 75_000))
        val h = hub(
            entries,
            listOf(
                LinkedInGhost("ref-Cal", "Cal", 70_000, hintFree = true, flawless = false),
                LinkedInGhost("ref-Dee", "Dee", null, hintFree = true, flawless = false),
            ),
        )
        h.befriend("maya")

        val lines = h.friendsDayLines("maya")
        val drawnPlace = lines.filterIsInstance<DailyBoardLine.Real>().single { it.row.isSelf }.place

        val reported = ObserveFriendDay(h.preferences, ObserveFriendUids(h.matches, h.identity), ObserveLinkedInDay(h.linkedInDays, h.registry))(ZIP, today)
            .first { "maya" in it?.friends.orEmpty() }!!
            .standing(entries, "u1", h.registry.rankOrderOf(ZIP))

        assertEquals(FriendDay.Standing(place = 3, count = 3), reported)
        assertEquals(reported?.place, drawnPlace)
        assertEquals(reported?.count, lines.count { it.place() != null })
    }

    @Test
    fun `friends tied by a connection's time keep their order, the connection tied with them after`() = runTest {
        // Maya is her own connection: LinkedIn's 70s replaces her 62s and ties her with Bea; Cal has the same time.
        val h = hub(
            listOf(entry("maya", "Maya", 62_000), entry("bea", "Bea", 70_000), entry("u1", "Ann", 75_000)),
            listOf(
                LinkedInGhost("ref-Maya", "Maya", 70_000, hintFree = true, flawless = false),
                LinkedInGhost("ref-Cal", "Cal", 70_000, hintFree = true, flawless = false),
            ),
        )
        h.befriend("maya")
        h.befriend("bea", code = "AAAAA3")

        val lines = h.friendsDayLines("maya", "bea")

        val drawn = lines.map {
            when (it) {
                is DailyBoardLine.Real -> it.row.playerId to it.place
                is DailyBoardLine.Ghost -> it.row.memberRef to it.place
            }
        }
        assertEquals(listOf("maya" to 1, "bea" to 1, "ref-Cal" to 1, "u1" to 4), drawn)
    }
}
