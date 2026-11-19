package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.data.identity.Identity
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
import com.ivandsky.puzzlebox.game.CompletionSource
import com.ivandsky.puzzlebox.history.HistoryTestSupport.TANGO
import com.ivandsky.puzzlebox.history.HistoryTestSupport.TANGO_PUZZLE_NUMBER
import com.ivandsky.puzzlebox.history.HistoryTestSupport.ZIP
import com.ivandsky.puzzlebox.history.HistoryTestSupport.tangoRecord
import com.ivandsky.puzzlebox.history.domain.ranks.RankScope
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.resultui.board.DailyBoardLine
import com.ivandsky.puzzlebox.resultui.board.DailyBoardState
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.Instant
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
    private val ann = Identity("u1", "Ann", null)

    private fun entry(uid: String, name: String, timeMs: Long) = LeaderboardEntry(uid, name, "zip/$today/1", timeMs, 0, 1_700_000_000_000)

    private fun DailyBoardLine.place() = when (this) {
        is DailyBoardLine.Real -> place
        is DailyBoardLine.Ghost -> place
    }

    private fun hub(
        entries: List<LeaderboardEntry>,
        connections: List<LinkedInGhost>,
        game: GameId = ZIP,
        day: LocalDate = today,
        own: LinkedInOwnState = LinkedInOwnState.Unknown,
    ) = RanksTestHub(FakeLeaderboardRepository(mapOf(LeaderboardRef(game.value, day) to entries)), friendsDefault = true).apply {
        linkedInDays.emit(
            game, day,
            LinkedInDayData(
                link = LinkState.Connected("member-1"), delta = 0, boardInWindow = true, inFlight = false, lastSync = null,
                own = own, ghosts = connections, boardFetchedAtMs = 1L, owner = null,
            ),
        )
    }

    /** The lines of [game]'s Friends Day board on [day] (null: today) once every one of [players] is drawn on it. */
    private suspend fun RanksTestHub.friendsDayLines(vararg players: String, game: GameId = ZIP, day: LocalDate? = null): List<DailyBoardLine> {
        val viewModel = viewModel(linkedIn = true)
        viewModel.select { it.pickBoard(RanksBoard.Game(game)).setScope(RankScope.Friends).pickFromCalendar(day) }
        return viewModel.uiState.first { state ->
            val drawn = (state.board.state as? DailyBoardState.Ranked)?.lines.orEmpty().filterIsInstance<DailyBoardLine.Real>().map { it.row.playerId }
            state.scope == RankScope.Friends && drawn.containsAll(players.toList())
        }.let { (it.board.state as DailyBoardState.Ranked).lines!! }
    }

    private fun RanksTestHub.friendDay() = ObserveFriendDay(preferences, ObserveFriendUids(matches, identity), ObserveLinkedInDay(linkedInDays, registry), completions)

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

        val reported = h.friendDay()(ZIP, today)
            .first { "maya" in it?.friends.orEmpty() }!!
            .standing(entries, ann, h.registry.rankOrderOf(ZIP))

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

    @Test
    fun `a day the viewer played only on LinkedIn draws them once, at the place their LinkedIn time takes and an entry point reports`() = runTest {
        // #1743: Ann solved the 10th only on LinkedIn, so no board has her; Maya posted, Zed is a stranger, Cal a connection.
        val day = today.minusDays(3)
        val entries = listOf(50_000L to "Zed", 62_000L to "Maya").map { (timeMs, name) ->
            LeaderboardEntry(name.lowercase(), name, "${TANGO.value}/$day/$TANGO_PUZZLE_NUMBER", timeMs, 0, 1_700_000_000_000)
        }
        val record = tangoRecord(day, timeMs = 58_000).copy(firstCompletedAt = Instant.parse("2026-11-12T08:00:00Z"), isRecord = true, source = CompletionSource.LinkedIn)
        val cal = LinkedInGhost("ref-Cal", "Cal", 70_000, hintFree = true, flawless = false)
        val h = hub(entries, listOf(cal), TANGO, day, LinkedInOwnState.Imported(record))
        h.completions.setRecords(listOf(record))
        h.befriend("maya")

        val lines = h.friendsDayLines("maya", "u1", game = TANGO, day = day)
        val you = lines.filterIsInstance<DailyBoardLine.Real>().single { it.row.isSelf }
        val reported = h.friendDay()(TANGO, day).first { "maya" in it?.friends.orEmpty() }!!.standing(entries, ann, h.registry.rankOrderOf(TANGO))

        assertEquals(
            listOf("u1" to 1, "maya" to 2, "ref-Cal" to 3),
            lines.map {
                when (it) {
                    is DailyBoardLine.Real -> it.row.playerId to it.place
                    is DailyBoardLine.Ghost -> it.row.memberRef to it.place
                }
            },
        )
        // Counted, so not the device-only row; LinkedIn's, so marked as on the result screen's own LinkedIn row.
        assertEquals(Triple(false, true, true), Triple(you.row.unofficial, you.row.fromLinkedIn, you.row.timeFromLinkedIn))
        assertEquals(FriendDay.Standing(place = 1, count = 3), reported)
    }
}
