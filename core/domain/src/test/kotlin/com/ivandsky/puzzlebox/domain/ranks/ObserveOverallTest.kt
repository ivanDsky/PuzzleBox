package com.ivandsky.puzzlebox.domain.ranks

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.BoardPage
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeFinalBoardStore
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A11/A12: each completeness state of the Overall view, from scripted window boards. */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveOverallTest {

    private val day = LocalDate.of(2026, 11, 13)
    private val window = RankWindow.Day(day)
    private val zip = GameId("zip")
    private val tango = GameId("tango")
    private val order = RankOrder.TimeThenHints
    private val ann = Identity("ann", "Ann", null)

    private val identity = FakeIdentityService(initial = ann)
    private val completions = FakeCompletionRepository()
    private val observe = ObserveOverall(identity, completions)

    private fun ref(game: GameId, date: LocalDate = day) = LeaderboardRef(game.value, date)

    private fun entry(uid: String, name: String, board: LeaderboardRef, time: Long) = LeaderboardEntry(
        uid = uid,
        displayName = name,
        puzzleRef = "${board.gameId}/${board.day}/1",
        timeMs = time,
        hints = 0,
        completedAtMs = 1L,
    )

    private fun loaded(board: LeaderboardRef, vararg entries: LeaderboardEntry, count: Int = entries.size, final: Boolean = false) =
        BoardLoad.Loaded(board, order, BoardPage(entries.toList(), count), final)

    private fun loading(board: LeaderboardRef) = BoardLoad.Loading(board, order)

    private fun unavailable(board: LeaderboardRef) = BoardLoad.Unavailable(board, order)

    private fun boards(vararg loads: BoardLoad) = RankWindowBoards.Boards(loads.toList())

    private suspend fun last(boards: RankWindowBoards): OverallState = observe(flowOf(boards), window).drop(1).first()

    /** Everything the view emits for [boards], without waiting for a state that is not coming. */
    private suspend fun TestScope.emitted(boards: Flow<RankWindowBoards>): List<OverallState> {
        val seen = mutableListOf<OverallState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { observe(boards, window).toList(seen) }
        runCurrent()
        return seen
    }

    private suspend fun TestScope.emitted(boards: RankWindowBoards) = emitted(flowOf(boards))

    @Test
    fun `no board answered yet is Loading`() = runTest {
        assertEquals(listOf(OverallState.Loading), emitted(boards(loading(ref(zip)), loading(ref(tango)))))
        assertEquals(listOf(OverallState.Loading), emitted(MutableSharedFlow()))
    }

    @Test
    fun `loading boards beside unavailable ones are still Loading`() = runTest {
        assertEquals(listOf(OverallState.Loading), emitted(boards(loading(ref(zip)), unavailable(ref(tango)))))
    }

    @Test
    fun `signed out boards and a signed out identity are SignedOut`() = runTest {
        assertEquals(OverallState.SignedOut, last(RankWindowBoards.SignedOut))
        identity.currentIdentity = null
        assertEquals(OverallState.SignedOut, last(boards(loaded(ref(zip), entry("bob", "Bob", ref(zip), 10)))))
    }

    @Test
    fun `every board loaded and nobody played is Empty`() = runTest {
        assertEquals(OverallState.Empty, last(boards(loaded(ref(zip)), loaded(ref(tango)))))
        assertEquals(OverallState.Empty, last(boards()))
    }

    @Test
    fun `every board loaded with players is Complete`() = runTest {
        val state = last(boards(loaded(ref(zip), entry("bob", "Bob", ref(zip), 10)), loaded(ref(tango), entry("bob", "Bob", ref(tango), 20))))

        val standings = (state as OverallState.Complete).standings
        assertEquals(listOf("bob"), standings.rows.map { it.uid })
        assertEquals(2, standings.rows.single().games)
    }

    @Test
    fun `an unavailable open board beside a loaded one is Partial k of n and never Complete`() = runTest {
        val state = last(
            boards(
                loaded(ref(zip), entry("bob", "Bob", ref(zip), 10)),
                unavailable(ref(tango)),
                loading(ref(GameId("queens"))),
            ),
        )

        val partial = state as OverallState.Partial
        assertEquals(1, partial.loaded)
        assertEquals(3, partial.total)
        assertEquals(1, partial.standings.rows.single().games) // the missing boards are not counted as played
    }

    @Test
    fun `boards still loading beside loaded ones are Partial`() = runTest {
        val state = last(boards(loaded(ref(zip), entry("bob", "Bob", ref(zip), 10)), loading(ref(tango))))

        assertEquals(1, (state as OverallState.Partial).loaded)
        assertEquals(2, state.total)
    }

    @Test
    fun `every board unavailable is Offline without standings`() = runTest {
        assertEquals(OverallState.Offline(null), last(boards(unavailable(ref(zip)), unavailable(ref(tango)))))
    }

    @Test
    fun `only stored final boards loaded with open ones unavailable is Offline with their standings`() = runTest {
        val old = ref(zip, day.minusDays(9))
        val state = last(boards(loaded(old, entry("bob", "Bob", old, 10), final = true), unavailable(ref(zip))))

        val offline = state as OverallState.Offline
        assertEquals(listOf("bob"), offline.standings!!.rows.map { it.uid })
    }

    @Test
    fun `a viewer past the page at the cap scores the floor of place 101 and is named from the profile`() = runTest {
        val board = ref(zip)
        val page = (1..BoardPage.PAGE_SIZE).map { entry("p$it", "P$it", board, it * 10L) }.toTypedArray()
        completions.setRecords(listOf(official(zip, 99_999L)))

        val state = last(boards(loaded(board, *page, count = 400)))

        val row = (state as OverallState.Complete).standings.rows.single { it.uid == "ann" }
        assertEquals("Ann", row.displayName)
        assertTrue(row.truncated)
        assertEquals(1, row.games)
        assertEquals(75.0, row.total, 1e-9) // 100 × (400 − 100) / 400
    }

    @Test
    fun `a viewer without an official solve is not placed past the page`() = runTest {
        val board = ref(zip)
        val page = (1..BoardPage.PAGE_SIZE).map { entry("p$it", "P$it", board, it * 10L) }.toTypedArray()

        val state = last(boards(loaded(board, *page, count = 400)))

        assertTrue((state as OverallState.Complete).standings.rows.none { it.uid == "ann" })
    }

    @Test
    fun `a viewer below the cap is not placed past the page`() = runTest {
        completions.setRecords(listOf(official(zip, 99_999L)))

        val state = last(boards(loaded(ref(zip), entry("bob", "Bob", ref(zip), 10))))

        assertTrue((state as OverallState.Complete).standings.rows.none { it.uid == "ann" })
    }

    @Test
    fun `collecting adds no listener, fetch or count beyond the shared window flow`() = runTest {
        val zipBoard = ref(zip)
        val leaderboard = FakeLeaderboardRepository()
        val registry = TestGames.registry(listOf(zip))
        val window = ObserveRankWindow(identity, leaderboard, FakeFinalBoardStore(), registry, FakeGameCalendar(day))
        val shared = window(this@ObserveOverallTest.window, listOf(zip)).shareIn(backgroundScope, SharingStarted.Eagerly, replay = 1)
        shared.first { it is RankWindowBoards.Boards }
        val listeners = leaderboard.observedBoards.size

        val states = List(3) { observe(shared, this@ObserveOverallTest.window).drop(1).first() }

        assertEquals(listOf(zipBoard), leaderboard.observedBoards)
        assertEquals(1, listeners)
        assertEquals(0, leaderboard.fetchCalls)
        assertEquals(0, leaderboard.countCalls)
        assertEquals(1, states.toSet().size)
    }

    private fun official(game: GameId, time: Long) = CompletionRecord(
        puzzleRef = TestGames.Ref(game, PuzzleKey.Daily(day, 1)),
        time = time,
        hints = 0,
        stats = RecordStats(schemaVersion = 1, blob = "{}"),
        firstCompletedAt = Instant.parse("2026-11-13T08:00:00Z"),
        isRecord = true,
    )
}
