package com.ivandsky.puzzlebox.domain.ranks

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.BoardPage
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.linkedin.LinkState
import com.ivandsky.puzzlebox.data.linkedin.LinkedInDayData
import com.ivandsky.puzzlebox.data.linkedin.LinkedInGhost
import com.ivandsky.puzzlebox.data.linkedin.LinkedInOwnState
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.MatchDraft
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInDay
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInDayView
import com.ivandsky.puzzlebox.domain.linkedin.SyncLinkedInDay
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RecordStats
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

/** F18/F21: Overall among the viewer and their friends over a window, with the LinkedIn connections of its days counted. */
class ObserveFriendsOverallTest {

    private val today = LocalDate.of(2026, 11, 13)
    private val clock = Clock.fixed(Instant.parse("2026-11-13T12:00:00Z"), ZoneOffset.UTC)
    private val zip = GameId("zip")
    private val tango = GameId("tango")
    private val games = listOf(zip, tango)
    private val registry = TestGames.registry(games)
    private val me = Identity("u1", "Ann", null)
    private val identity = FakeIdentityService(initial = me)
    private val matches = FakeMatchRepository().apply { signedInUid = me.uid }
    private val days = FakeLinkedInDays()

    private fun entry(uid: String, name: String, game: GameId, timeMs: Long) =
        LeaderboardEntry(uid, name, "${game.value}/$today/1", timeMs, 0, 1_700_000_000_000)

    // Zed is a stranger, first on both boards; Maya, the viewer's friend, is ahead of the viewer on both.
    private val leaderboard = FakeLeaderboardRepository(
        mapOf(
            LeaderboardRef(zip.value, today) to listOf(entry("zed", "Zed", zip, 50_000), entry("maya", "Maya", zip, 62_000), entry("u1", "Ann", zip, 75_000)),
            LeaderboardRef(tango.value, today) to listOf(entry("zed", "Zed", tango, 40_000), entry("maya", "Maya", tango, 42_000), entry("u1", "Ann", tango, 45_000)),
        ),
    )

    private fun observe(linkedIn: Boolean = true, records: List<CompletionRecord> = emptyList()): ObserveFriendsOverall {
        val completions = FakeCompletionRepository(records)
        return ObserveFriendsOverall(
            ObserveRankWindow(identity, leaderboard, FakeFinalBoardStore(), registry, FakeGameCalendar(today), clock),
            ObserveFriendBoards(
                ObserveFriendUids(matches, identity),
                identity,
                completions,
                ObserveLinkedInDayView(ObserveLinkedInDay(days, registry), SyncLinkedInDay(days, 500), FakeLinkedInLink(), days).takeIf { linkedIn },
            ),
            ObserveOverall(identity, completions),
        )
    }

    private suspend fun befriend(uid: String) {
        val code = checkNotNull(ShareCode.parse("AAAAA2"))
        matches.create(MatchDraft(code, 24, Player(me.uid, me.displayName, null), null, listOf(MatchLeg(LegRef.Daily(zip, today, 1), null)), true, null))
        matches.signedInUid = uid
        matches.claim(code, Player(uid, uid, null))
        matches.signedInUid = me.uid
    }

    /** Zip's day with one connection, Cal, whose time is between Maya's and the viewer's; Cal plays no Tango. */
    private fun connection() = days.emit(
        zip, today,
        LinkedInDayData(
            link = LinkState.Connected("member-1"), delta = 0, boardInWindow = true, inFlight = false, lastSync = null,
            own = LinkedInOwnState.Unknown, ghosts = listOf(LinkedInGhost("ref-Cal", "Cal", 70_000, hintFree = true, flawless = false)),
            boardFetchedAtMs = 1L, owner = null,
        ),
    )

    private suspend fun ObserveFriendsOverall.complete(rows: Int) =
        (invoke(RankWindow.Day(today), games).first { it is OverallState.Complete && it.standings.rows.size == rows } as OverallState.Complete).standings

    @Test
    fun `Overall ranks the viewer and their friends only, a stranger's points left out`() = runTest {
        befriend("maya")

        val standings = observe(linkedIn = false).complete(rows = 2)

        assertEquals(listOf("maya" to 1, "u1" to 2), standings.rows.map { it.uid to it.place })
    }

    @Test
    fun `a LinkedIn connection is counted among the friends on its day's board`() = runTest {
        befriend("maya")
        connection()

        val standings = observe().complete(rows = 3)

        assertEquals(setOf("maya", connectionUid("ref-Cal"), "u1"), standings.rows.map { it.uid }.toSet())
        assertEquals("maya", standings.rows.first().uid)
        assertEquals("Cal" to 1, standings.rows.single { it.uid == connectionUid("ref-Cal") }.let { it.displayName to it.games })
    }

    @Test
    fun `without LinkedIn no connection is counted`() = runTest {
        befriend("maya")
        connection()

        val standings = observe(linkedIn = false).complete(rows = 2)

        assertEquals(listOf("maya", "u1"), standings.rows.map { it.uid })
    }

    @Test
    fun `a signed-out viewer has no Overall`() = runTest {
        identity.signOut()

        assertEquals(OverallState.SignedOut, observe().invoke(RankWindow.Day(today), games).first { it != OverallState.Loading })
    }

    @Test
    fun `a viewer who placed past the full page of a board still counts on it among their friends`() = runTest {
        befriend("maya")
        val board = LeaderboardRef(zip.value, today)
        // 99 strangers and Maya fill the read page; the viewer's slower time is on the board's other 300 places, not on the page.
        leaderboard.setEntries(board, (1..BoardPage.PAGE_SIZE - 1).map { entry("p$it", "P$it", zip, it * 100L) } + entry("maya", "Maya", zip, 62_000))
        leaderboard.setCount(board, 400)
        val posted = CompletionRecord(
            TestGames.Ref(zip, PuzzleKey.Daily(today, 1)), time = 75_000, hints = 0, stats = RecordStats(1, "{}"),
            firstCompletedAt = Instant.parse("2026-11-13T08:00:00Z"), isRecord = true,
        )

        val standings = (observe(linkedIn = false, records = listOf(posted)).invoke(RankWindow.Day(today), listOf(zip)).first { it is OverallState.Complete } as OverallState.Complete).standings

        assertEquals(listOf("maya" to 1, "u1" to 2), standings.rows.map { it.uid to it.place })
        assertEquals(listOf(100.0, 50.0), standings.rows.map { it.total })
    }

    @Test
    fun `one collection reads each board once and listens to each game's days once`() = runTest {
        befriend("maya")
        connection()

        observe().complete(rows = 3)

        assertEquals(games.map { LeaderboardRef(it.value, today) }.toSet(), leaderboard.observedBoards.toSet())
        assertEquals(games.size, leaderboard.observedBoards.size)
        assertEquals(listOf(Triple(zip, today, today), Triple(tango, today, today)), days.observedRanges)
    }
}
