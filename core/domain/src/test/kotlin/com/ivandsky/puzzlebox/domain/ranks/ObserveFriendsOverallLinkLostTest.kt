package com.ivandsky.puzzlebox.domain.ranks

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.linkedin.LinkState
import com.ivandsky.puzzlebox.data.linkedin.LinkedInDayData
import com.ivandsky.puzzlebox.data.linkedin.LinkedInOwnState
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.MatchDraft
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInDay
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInDayView
import com.ivandsky.puzzlebox.domain.linkedin.SyncLinkedInDay
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.CompletionSource
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead.Rejected.Kind
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInDays
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInLink
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeFinalBoardStore
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A day the viewer played only on LinkedIn counts on Friends Overall as long as the phone holds the import: while the
 * link is connected, while it is paused, and after a disconnect, which keeps imports.
 */
class ObserveFriendsOverallLinkLostTest {

    private val today = LocalDate.of(2026, 11, 13)
    private val clock = Clock.fixed(Instant.parse("2026-11-13T12:00:00Z"), ZoneOffset.UTC)
    private val zip = GameId("zip")
    private val me = Identity("u1", "Ann", null)
    private val identity = FakeIdentityService(initial = me)
    private val matches = FakeMatchRepository().apply { signedInUid = me.uid }
    private val days = FakeLinkedInDays()

    // The viewer is not on the board: their only result of the day is the one imported from LinkedIn.
    private val leaderboard = FakeLeaderboardRepository(
        mapOf(
            LeaderboardRef(zip.value, today) to listOf(
                LeaderboardEntry("zed", "Zed", "zip/$today/1", 50_000, 0, 1_700_000_000_000),
                LeaderboardEntry("maya", "Maya", "zip/$today/1", 62_000, 0, 1_700_000_000_000),
            ),
        ),
    )

    private val overall = FakeCompletionRepository().let { completions ->
        ObserveFriendsOverall(
            ObserveRankWindow(identity, leaderboard, FakeFinalBoardStore(), TestGames.registry(listOf(zip)), FakeGameCalendar(today), clock),
            ObserveFriendBoards(
                ObserveFriendUids(matches, identity),
                identity,
                completions,
                ObserveLinkedInDayView(ObserveLinkedInDay(days, TestGames.registry(listOf(zip))), SyncLinkedInDay(days, 500), FakeLinkedInLink(), days),
            ),
            ObserveOverall(identity, completions),
        )
    }

    private suspend fun befriendMaya() {
        val code = checkNotNull(ShareCode.parse("AAAAA2"))
        matches.create(MatchDraft(code, 24, Player(me.uid, me.displayName, null), null, listOf(MatchLeg(LegRef.Daily(zip, today, 1), null)), true, null))
        matches.signedInUid = "maya"
        matches.claim(code, Player("maya", "maya", null))
        matches.signedInUid = me.uid
    }

    private fun importedWhile(link: LinkState) = days.emit(
        zip, today,
        LinkedInDayData(
            link = link, delta = 0, boardInWindow = true, inFlight = false, lastSync = null,
            own = LinkedInOwnState.Imported(
                CompletionRecord(TestGames.Ref(zip, PuzzleKey.Daily(today, 235)), time = 70_000, hints = 0, stats = RecordStats(1, "{}"), source = CompletionSource.LinkedIn),
            ),
            ghosts = emptyList(), boardFetchedAtMs = null,
        ),
    )

    private suspend fun placesWhile(link: LinkState): List<Pair<String, Int>> {
        importedWhile(link)
        val state = overall(RankWindow.Day(today), listOf(zip)).first { it is OverallState.Complete } as OverallState.Complete
        return state.standings.rows.map { it.uid to it.place }
    }

    @Test
    fun `the viewer's LinkedIn-only day counts while the link is connected`() = runTest {
        befriendMaya()
        assertEquals(listOf("maya" to 1, "u1" to 2), placesWhile(LinkState.Connected("member-1")))
    }

    @Test
    fun `the viewer's LinkedIn-only day still counts while the link is paused`() = runTest {
        befriendMaya()
        for (kind in Kind.entries) {
            assertEquals(listOf("maya" to 1, "u1" to 2), placesWhile(LinkState.Paused("member-1", kind)))
        }
    }

    @Test
    fun `the viewer's LinkedIn-only day still counts after a disconnect, which keeps imports`() = runTest {
        befriendMaya()
        assertEquals(listOf("maya" to 1, "u1" to 2), placesWhile(LinkState.NotConnected))
    }
}
