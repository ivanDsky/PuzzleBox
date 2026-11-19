package com.ivandsky.puzzlebox.domain.ranks

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.BoardPage
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.MatchDraft
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #1767: the friends a full global page leaves out are read by id and counted on the Friends boards, once per
 * collection and never for a page that already holds the whole board.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoadFriendsPastPageTest {

    private val day = LocalDate.of(2026, 11, 13)
    private val zip = LeaderboardRef("zip", day)
    private val tango = LeaderboardRef("tango", day)
    private val me = Identity("ann", "Ann", null)
    private val friends = setOf("ann", "maya", "bea", "cy")

    private fun entry(uid: String, board: LeaderboardRef, time: Long) =
        LeaderboardEntry(uid, uid.replaceFirstChar(Char::uppercase), "${board.gameId}/${board.day}/1", time, 0, 1L)

    private fun loaded(board: LeaderboardRef, entries: List<LeaderboardEntry>, count: Int = entries.size) =
        BoardLoad.Loaded(board, RankOrder.TimeThenHints, BoardPage(entries, count), final = false)

    /** A full page of 99 strangers and Maya, the board's other 300 places past it. */
    private fun fullPage(board: LeaderboardRef) = (1..BoardPage.PAGE_SIZE - 1).map { entry("s$it", board, it * 10L) } + entry("maya", board, 5_000)

    private val full = RankWindowBoards.Boards(listOf(loaded(zip, fullPage(zip), count = 400)))

    private fun uidsOf(entries: List<LeaderboardEntry>) = entries.map { it.uid }

    private fun leaderboard() = FakeLeaderboardRepository().apply { setPastPage(zip, listOf(entry("bea", zip, 20_000), entry("stranger", zip, 21_000))) }

    /** One collection's reads: the use case with the memo it keeps while the boards are shown. */
    private class Reads(leaderboard: LeaderboardRepository) {
        private val load = LoadFriendsPastPage(leaderboard)
        private val memo = LoadFriendsPastPage.Memo()

        suspend fun of(read: RankWindowBoards, players: Set<String>, viewerUid: String?) = load(memo, read, players, viewerUid)
    }

    @Test
    fun `a friend past the page is on the friends board with the entries merged in, each uid once, non-players left out`() {
        val past = listOf(entry("bea", zip, 20_000), entry("maya", zip, 1), entry("stranger", zip, 21_000))

        val among = (full.amongFriends(friends, friendEntries = mapOf(zip to past)) as RankWindowBoards.Boards).boards.single() as BoardLoad.Loaded
        val ranked = rankBoard(among.board, among.page.entries, among.order, among.page.count)

        // Maya as the page has her, not as the stale past entry says; Bea ranked after her; the stranger and the 99 left out.
        assertEquals(listOf("maya" to 1, "bea" to 2), ranked.rows.map { it.entry.uid to it.rank })
        assertEquals(2, ranked.n)
        assertEquals(listOf(100.0, 50.0), ranked.rows.map { it.points })
    }

    @Test
    fun `no friend is read for a page that is not full, or when every friend is on it`() = runTest {
        val leaderboard = leaderboard()
        val reads = Reads(leaderboard)
        val short = RankWindowBoards.Boards(listOf(loaded(zip, fullPage(zip).take(40))))
        val onPage = RankWindowBoards.Boards(listOf(loaded(zip, fullPage(zip), count = 400)))

        assertEquals(emptyMap<LeaderboardRef, List<LeaderboardEntry>>(), reads.of(short, friends, "ann"))
        assertEquals(emptyMap<LeaderboardRef, List<LeaderboardEntry>>(), reads.of(onPage, setOf("ann", "maya"), "ann"))
        assertEquals(emptyMap<LeaderboardRef, List<LeaderboardEntry>>(), reads.of(RankWindowBoards.SignedOut, friends, "ann"))
        assertTrue(leaderboard.playersFetched.isEmpty())
    }

    @Test
    fun `only the friends off the page are asked for, not the viewer, and what is found is theirs alone`() = runTest {
        val leaderboard = leaderboard()

        val found = Reads(leaderboard).of(full, friends, "ann")

        // Ann is the viewer, Maya is on the page, Cy has no entry; Bea has; the stranger past the page is not a friend.
        assertEquals(listOf(zip to setOf("bea", "cy")), leaderboard.playersFetched)
        assertEquals(mapOf(zip to listOf("bea")), found.mapValues { uidsOf(it.value) })
    }

    @Test
    fun `a read that failed adds nothing and is asked again, one that answered is not`() = runTest {
        val leaderboard = leaderboard()
        val reads = Reads(leaderboard)

        leaderboard.playersAvailable = false
        assertEquals(emptyMap<LeaderboardRef, List<LeaderboardEntry>>(), reads.of(full, friends, "ann"))

        leaderboard.playersAvailable = true
        assertEquals(mapOf(zip to listOf("bea")), reads.of(full, friends, "ann").mapValues { uidsOf(it.value) })
        assertEquals(mapOf(zip to listOf("bea")), reads.of(full, friends, "ann").mapValues { uidsOf(it.value) })

        // The failed ask and the one that answered; the third pass finds Bea and Cy already answered for.
        assertEquals(2, leaderboard.playersFetched.size)
    }

    @Test
    fun `a friend who joins the friends later is the only one asked for next`() = runTest {
        val leaderboard = leaderboard()
        val reads = Reads(leaderboard)

        reads.of(full, setOf("ann", "maya", "bea"), "ann")
        val found = reads.of(full, friends, "ann")

        assertEquals(listOf(zip to setOf("bea"), zip to setOf("cy")), leaderboard.playersFetched)
        assertEquals(mapOf(zip to listOf("bea")), found.mapValues { uidsOf(it.value) })
    }

    @Test
    fun `a read that runs long adds nothing instead of holding the boards`() = runTest {
        val hanging = object : LeaderboardRepository by FakeLeaderboardRepository() {
            override suspend fun fetchPlayers(board: LeaderboardRef, players: Set<String>): List<LeaderboardEntry>? = awaitCancellation()
        }

        val found = Reads(hanging).of(full, friends, "ann")

        assertEquals(emptyMap<LeaderboardRef, List<LeaderboardEntry>>(), found)
    }

    @Test
    fun `every full board is read, the short ones not`() = runTest {
        val leaderboard = leaderboard().apply { setPastPage(tango, listOf(entry("cy", tango, 9_000))) }
        val boards = RankWindowBoards.Boards(listOf(loaded(zip, fullPage(zip), count = 400), loaded(tango, fullPage(tango), count = 150), loaded(LeaderboardRef("queens", day), emptyList())))

        val found = Reads(leaderboard).of(boards, friends, "ann")

        assertEquals(mapOf(zip to listOf("bea"), tango to listOf("cy")), found.mapValues { uidsOf(it.value) })
        assertEquals(setOf(zip, tango), leaderboard.playersFetched.map { it.first }.toSet())
    }

    private suspend fun befriend(matches: FakeMatchRepository, uid: String) {
        val code = checkNotNull(ShareCode.parse("AAAAA2"))
        matches.create(MatchDraft(code, 24, Player(me.uid, me.displayName, null), null, listOf(MatchLeg(LegRef.Daily(GameId("zip"), day, 1), null)), true, null))
        matches.signedInUid = uid
        matches.claim(code, Player(uid, uid, null))
        matches.signedInUid = me.uid
    }

    private suspend fun observe(leaderboard: LeaderboardRepository?, friend: String = "bea"): ObserveFriendBoards {
        val identity = FakeIdentityService(initial = me)
        val matches = FakeMatchRepository().apply { signedInUid = me.uid }
        befriend(matches, friend)
        return ObserveFriendBoards(ObserveFriendUids(matches, identity), identity, FakeCompletionRepository(), linkedIn = null, friendsPast = leaderboard?.let(::LoadFriendsPastPage))
    }

    @Test
    fun `a collection of the Friends boards reads each board once, however many answers, and the next collection reads again`() = runTest {
        val leaderboard = leaderboard()
        val observe = observe(leaderboard)
        val source = MutableStateFlow<Pair<RankWindow, RankWindowBoards>?>(RankWindow.Day(day) to full)
        val answers = mutableListOf<Pair<RankWindow, ObserveFriendBoards.Among>?>()

        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { observe.among(source).collect { answers += it } }
        advanceUntilIdle()
        source.value = RankWindow.Week(day) to full
        advanceUntilIdle()
        job.cancel()

        // Two answers of the window, one read of Bea on the board.
        assertEquals(RankWindow.Week(day), answers.last()?.first)
        assertEquals(listOf("bea"), answers.last()?.second?.past?.get(zip)?.let(::uidsOf))
        assertEquals(1, leaderboard.playersFetched.size)

        observe.among(flowOf(RankWindow.Day(day) to full)).first { it?.second?.past?.isNotEmpty() == true }
        assertEquals(2, leaderboard.playersFetched.size)
    }

    @Test
    fun `without the reader the boards carry no friend past a page and read nothing`() = runTest {
        val leaderboard = leaderboard()
        val observe = observe(leaderboard = null)

        val answer = observe.among(flowOf(RankWindow.Day(day) to full)).first { it != null }!!

        assertEquals(emptyMap<LeaderboardRef, List<LeaderboardEntry>>(), answer.second.past)
        assertTrue(leaderboard.playersFetched.isEmpty())
    }
}
