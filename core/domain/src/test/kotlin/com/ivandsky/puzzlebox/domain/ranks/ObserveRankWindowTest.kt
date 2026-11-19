package com.ivandsky.puzzlebox.domain.ranks

import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.BoardPage
import com.ivandsky.puzzlebox.data.leaderboard.FinalBoardStore
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardSnapshot
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.ScoreModel
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeFinalBoardStore
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A23's read budget with counting fakes: reads = listeners (`observedBoards`) + one-shot fetches
 * (`fetchCalls`) + counts (`countCalls`). The UTC date is 2026-11-13, so Nov 10–13 are open and
 * everything before is Final.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveRankWindowTest {

    private val zip = GameId("zip")
    private val strands = GameId("strands")
    private val games = listOf(zip, GameId("tango"), GameId("queens"), strands, GameId("minisudoku"))
    private val today = LocalDate.of(2026, 11, 13)
    private val finalDay = LocalDate.of(2026, 11, 3)
    private val clock = Clock.fixed(Instant.parse("2026-11-13T12:00:00Z"), ZoneOffset.UTC)
    private val ann = Identity("u1", "Ann", null)

    private val identity = FakeIdentityService(initial = ann)
    private val leaderboard = FakeLeaderboardRepository()
    private val store = FakeFinalBoardStore()
    private val calendar = FakeGameCalendar(today)
    private val registry = GameRegistry(games.map { if (it == strands) hintsFirst(TestGames.plugin(it)) else TestGames.plugin(it) })
    private val observe = observeWith()

    private fun observeWith(finalBoards: FinalBoardStore = store, days: GameCalendar = calendar) =
        ObserveRankWindow(identity, leaderboard, finalBoards, registry, days, clock)

    private fun hintsFirst(plugin: GamePlugin<Unit, TestGames.Session, TestGames.Model>) = GamePlugin(
        definition = plugin.definition,
        rules = plugin.rules,
        session = plugin.session,
        score = object : ScoreModel<TestGames.Session> {
            override fun completion(session: TestGames.Session) = plugin.score.completion(session)
            override val rankOrder = RankOrder.HintsThenTime
        },
        stateOf = plugin.stateOf,
        initialSession = plugin.initialSession,
        refOf = plugin.refOf,
        playAgainSession = plugin.playAgainSession,
        rendererModel = plugin.rendererModel,
    )

    private fun entry(board: LeaderboardRef, uid: String, timeMs: Long, hints: Int = 0) =
        LeaderboardEntry(uid, uid, "${board.gameId}/${board.day}/7", timeMs, hints, 1_700_000_000_000)

    /** [n] entries, fastest first; uids sort the other way round, so a uid-ordered read is not ranked. */
    private fun entries(board: LeaderboardRef, n: Int) = List(n) { entry(board, "p${999 - it}", 1_000L * (it + 1)) }

    private class Collection(val job: Job, private val emissions: List<RankWindowBoards>) {
        val latest: RankWindowBoards get() = emissions.last()
        val boards: List<BoardLoad> get() = (latest as RankWindowBoards.Boards).boards

        fun board(ref: LeaderboardRef): BoardLoad = boards.single { it.board == ref }
    }

    private fun TestScope.collect(flow: Flow<RankWindowBoards>): Collection {
        val emissions = mutableListOf<RankWindowBoards>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect(emissions::add) }
        runCurrent()
        return Collection(job, emissions)
    }

    private val listeners: Int get() = leaderboard.observedBoards.size

    // --- Day: listeners only -------------------------------------------------------------

    @Test
    fun `a day costs one listener per game and no other read`() = runTest {
        val window = collect(observe(RankWindow.Day(today), games))

        assertEquals(games.map { LeaderboardRef(it.value, today) }, leaderboard.observedBoards)
        assertEquals(0, leaderboard.fetchCalls)
        assertEquals(0, leaderboard.countCalls)
        assertEquals(0, store.getCalls)
        assertEquals(games.map { LeaderboardRef(it.value, today) }, window.boards.map { it.board })
    }

    @Test
    fun `an open board below the cap loads with its own length as N`() = runTest {
        val board = LeaderboardRef("zip", today)
        leaderboard.setEntries(board, entries(board, 3))

        val window = collect(observe(RankWindow.Day(today), games))

        assertEquals(
            BoardLoad.Loaded(board, RankOrder.TimeThenHints, BoardPage(entries(board, 3), 3), final = false),
            window.board(board),
        )
        assertEquals(0, leaderboard.countCalls)
    }

    @Test
    fun `an open board with no snapshot is unavailable`() = runTest {
        val window = collect(observe(RankWindow.Day(today), games))

        assertEquals(games.map { BoardLoad.Unavailable(LeaderboardRef(it.value, today), registry.rankOrderOf(it)) }, window.boards)
    }

    @Test
    fun `each board carries its game's rank order`() = runTest {
        val window = collect(observe(RankWindow.Day(today), games))

        assertEquals(RankOrder.HintsThenTime, window.board(LeaderboardRef("strands", today)).order)
        assertEquals(RankOrder.TimeThenHints, window.board(LeaderboardRef("zip", today)).order)
    }

    // --- Count policy at the cap -----------------------------------------------------------

    @Test
    fun `a day costs one count more per board at the cap, reused across snapshots`() = runTest {
        val capped = listOf(LeaderboardRef("zip", today), LeaderboardRef("tango", today))
        capped.forEach { leaderboard.setEntries(it, entries(it, 100)); leaderboard.setCount(it, 340) }

        val window = collect(observe(RankWindow.Day(today), games))

        assertEquals(5, listeners)
        assertEquals(2, leaderboard.countCalls)
        assertEquals(BoardPage(entries(capped[0], 100), 340), (window.board(capped[0]) as BoardLoad.Loaded).page)

        val next = entries(capped[0], 100).map { it.copy(timeMs = it.timeMs + 1) }
        leaderboard.setEntries(capped[0], next)
        runCurrent()

        assertEquals(BoardPage(next, 340), (window.board(capped[0]) as BoardLoad.Loaded).page)
        assertEquals(2, leaderboard.countCalls)
        assertEquals(5, listeners)
    }

    @Test
    fun `a board that reaches the cap later is counted once then`() = runTest {
        val board = LeaderboardRef("zip", today)
        leaderboard.setEntries(board, entries(board, 99))
        leaderboard.setCount(board, 120)
        val window = collect(observe(RankWindow.Day(today), listOf(zip)))
        assertEquals(0, leaderboard.countCalls)

        leaderboard.setEntries(board, entries(board, 100))
        runCurrent()

        assertEquals(BoardPage(entries(board, 100), 120), (window.board(board) as BoardLoad.Loaded).page)
        assertEquals(1, leaderboard.countCalls)
    }

    @Test
    fun `refresh re-counts a board at the cap and leaves the others alone`() = runTest {
        val board = LeaderboardRef("zip", today)
        leaderboard.setEntries(board, entries(board, 100))
        leaderboard.setCount(board, 340)
        val refresh = MutableSharedFlow<Unit>()
        val window = collect(observe(RankWindow.Day(today), games, refresh))

        leaderboard.setCount(board, 355)
        refresh.emit(Unit)
        runCurrent()

        assertEquals(355, (window.board(board) as BoardLoad.Loaded).page.count)
        assertEquals(2, leaderboard.countCalls)
        assertEquals(5, listeners)
    }

    @Test
    fun `an open board whose listener ended degraded is listened to again on the next refresh`() = runTest {
        val board = LeaderboardRef("zip", today)
        val attempts = mutableListOf<Int>()
        val flaky = object : LeaderboardRepository by leaderboard {
            override fun observe(board: LeaderboardRef, order: RankOrder): Flow<LeaderboardSnapshot?> {
                attempts += attempts.size
                // The first listener dies past its retries: the repository's terminal null.
                return if (attempts.size == 1) flowOf(null) else flowOf(LeaderboardSnapshot(board, entries(board, 2)))
            }
        }
        val refresh = MutableSharedFlow<Unit>()
        val window = collect(
            ObserveRankWindow(identity, flaky, store, registry, calendar, clock)(RankWindow.Day(today), listOf(zip), refresh),
        )
        assertEquals(BoardLoad.Unavailable(board, RankOrder.TimeThenHints), window.board(board))

        refresh.emit(Unit)
        runCurrent()

        assertEquals(BoardLoad.Loaded(board, RankOrder.TimeThenHints, BoardPage(entries(board, 2), 2), final = false), window.board(board))
        assertEquals(2, attempts.size)
    }

    @Test
    fun `an empty answer from the cache is unavailable until the server answers`() = runTest {
        val board = LeaderboardRef("zip", today)
        val answers = MutableSharedFlow<LeaderboardSnapshot?>()
        val cached = object : LeaderboardRepository by leaderboard {
            override fun observe(board: LeaderboardRef, order: RankOrder): Flow<LeaderboardSnapshot?> = answers
        }
        val window = collect(
            ObserveRankWindow(identity, cached, store, registry, calendar, clock)(RankWindow.Day(today), listOf(zip)),
        )

        answers.emit(LeaderboardSnapshot(board, emptyList(), fromCache = true))
        assertEquals(BoardLoad.Unavailable(board, RankOrder.TimeThenHints), window.board(board))

        answers.emit(LeaderboardSnapshot(board, entries(board, 1), fromCache = true))
        assertEquals(BoardLoad.Loaded(board, RankOrder.TimeThenHints, BoardPage(entries(board, 1), 1), final = false), window.board(board))

        answers.emit(LeaderboardSnapshot(board, emptyList()))
        assertEquals(BoardLoad.Loaded(board, RankOrder.TimeThenHints, BoardPage(emptyList(), 0), final = false), window.board(board))
    }

    @Test
    fun `a healthy open board keeps its one listener across refreshes`() = runTest {
        val board = LeaderboardRef("zip", today)
        leaderboard.setEntries(board, entries(board, 2))
        val refresh = MutableSharedFlow<Unit>()
        collect(observe(RankWindow.Day(today), listOf(zip), refresh))

        repeat(3) { refresh.emit(Unit) }
        runCurrent()

        assertEquals(listOf(board), leaderboard.observedBoards)
    }

    @Test
    fun `a capped board whose count fails is unavailable until a refresh counts it`() = runTest {
        val board = LeaderboardRef("zip", today)
        leaderboard.setEntries(board, entries(board, 100))
        leaderboard.setCount(board, 340)
        leaderboard.readsAvailable = false
        val refresh = MutableSharedFlow<Unit>()
        val window = collect(observe(RankWindow.Day(today), listOf(zip), refresh))
        assertEquals(BoardLoad.Unavailable(board, RankOrder.TimeThenHints), window.board(board))

        leaderboard.setEntries(board, entries(board, 100).map { it.copy(hints = 1) })
        runCurrent()
        assertEquals("a snapshot alone does not count again", 1, leaderboard.countCalls)

        leaderboard.readsAvailable = true
        refresh.emit(Unit)
        runCurrent()

        assertEquals(340, (window.board(board) as BoardLoad.Loaded).page.count)
        assertEquals(2, leaderboard.countCalls)
    }

    @Test
    fun `a failed re-count keeps the last N`() = runTest {
        val board = LeaderboardRef("zip", today)
        leaderboard.setEntries(board, entries(board, 100))
        leaderboard.setCount(board, 340)
        val refresh = MutableSharedFlow<Unit>()
        val window = collect(observe(RankWindow.Day(today), listOf(zip), refresh))

        leaderboard.readsAvailable = false
        refresh.emit(Unit)
        runCurrent()

        assertEquals(2, leaderboard.countCalls)
        assertEquals(340, (window.board(board) as BoardLoad.Loaded).page.count)
    }

    // --- Month: open days listen, Final days fetch once --------------------------------------

    @Test
    fun `a month with four open days costs twenty listeners and one fetch per unstored final board`() = runTest {
        val window = collect(observe(RankWindow.Month(today), games))

        val openDays = (10..13).map { LocalDate.of(2026, 11, it) }
        assertEquals(20, listeners)
        assertEquals(games.flatMap { game -> openDays.map { LeaderboardRef(game.value, it) } }, leaderboard.observedBoards)
        assertEquals(45, leaderboard.fetchCalls)
        assertEquals(45, store.putCalls)
        assertEquals(0, leaderboard.countCalls)
        assertEquals(65, window.boards.size)
        assertEquals(45, window.boards.count { it is BoardLoad.Loaded && it.final })
    }

    @Test
    fun `a second collection of the same month fetches nothing`() = runTest {
        collect(observe(RankWindow.Month(today), games)).job.cancelAndJoin()

        val second = collect(observe(RankWindow.Month(today), games))

        assertEquals(45, leaderboard.fetchCalls)
        assertEquals(45, store.putCalls)
        assertEquals(40, listeners)
        assertEquals(45, second.boards.count { it is BoardLoad.Loaded && it.final })
    }

    @Test
    fun `boards come in the games' order, each game's days ascending`() = runTest {
        val window = collect(observe(RankWindow.Week(today), listOf(strands, zip)))

        val days = (9..13).map { LocalDate.of(2026, 11, it) }
        assertEquals(
            days.map { LeaderboardRef("strands", it) } + days.map { LeaderboardRef("zip", it) },
            window.boards.map { it.board },
        )
    }

    // --- Final boards ------------------------------------------------------------------------

    @Test
    fun `a final board is fetched and stored, never listened to`() = runTest {
        val board = LeaderboardRef("zip", finalDay)
        leaderboard.setEntries(board, entries(board, 3))

        val window = collect(observe(RankWindow.Day(finalDay), listOf(zip)))

        val page = BoardPage(entries(board, 3), 3)
        assertEquals(BoardLoad.Loaded(board, RankOrder.TimeThenHints, page, final = true), window.board(board))
        assertEquals(page, store.get(board))
        assertEquals(1, leaderboard.fetchCalls)
        assertEquals(0, listeners)
    }

    @Test
    fun `an empty final board is fetched once and stored`() = runTest {
        val board = LeaderboardRef("zip", finalDay)
        val first = collect(observe(RankWindow.Day(finalDay), listOf(zip)))
        val empty = BoardLoad.Loaded(board, RankOrder.TimeThenHints, BoardPage(emptyList(), 0), final = true)
        assertEquals(empty, first.board(board))
        assertEquals(1, store.putCalls)
        first.job.cancelAndJoin()

        val second = collect(observe(RankWindow.Day(finalDay), listOf(zip)))

        assertEquals(empty, second.board(board))
        assertEquals(1, leaderboard.fetchCalls)
        assertEquals(1, store.putCalls)
    }

    @Test
    fun `a stored final board costs no read and arrives ranked by its game's order`() = runTest {
        val board = LeaderboardRef("strands", finalDay)
        val clean = entry(board, "z", timeMs = 90_000, hints = 0)
        val hinted = entry(board, "a", timeMs = 10_000, hints = 2)
        store.put(board, BoardPage(listOf(hinted, clean), 2))

        val window = collect(observe(RankWindow.Day(finalDay), listOf(strands)))

        assertEquals(
            BoardLoad.Loaded(board, RankOrder.HintsThenTime, BoardPage(listOf(clean, hinted), 2), final = true),
            window.board(board),
        )
        assertEquals(0, leaderboard.fetchCalls)
        assertEquals(0, listeners)
    }

    @Test
    fun `a final board at the cap keeps the N its fetch read, with no count of its own`() = runTest {
        val board = LeaderboardRef("zip", finalDay)
        leaderboard.setEntries(board, entries(board, 100))
        leaderboard.setCount(board, 340)

        val window = collect(observe(RankWindow.Day(finalDay), listOf(zip)))

        assertEquals(BoardPage(entries(board, 100), 340), (window.board(board) as BoardLoad.Loaded).page)
        assertEquals(0, leaderboard.countCalls)
    }

    @Test
    fun `a final board whose fetch fails stores nothing and is refetched on refresh until it loads`() = runTest {
        val board = LeaderboardRef("zip", finalDay)
        leaderboard.setEntries(board, entries(board, 3))
        leaderboard.readsAvailable = false
        val refresh = MutableSharedFlow<Unit>()
        val window = collect(observe(RankWindow.Day(finalDay), listOf(zip), refresh))

        assertEquals(BoardLoad.Unavailable(board, RankOrder.TimeThenHints), window.board(board))
        assertEquals(0, store.putCalls)
        assertNull(store.get(board))

        refresh.emit(Unit)
        runCurrent()
        assertEquals(BoardLoad.Unavailable(board, RankOrder.TimeThenHints), window.board(board))
        assertEquals(2, leaderboard.fetchCalls)
        assertEquals(0, store.putCalls)

        leaderboard.readsAvailable = true
        refresh.emit(Unit)
        runCurrent()
        assertEquals(BoardPage(entries(board, 3), 3), (window.board(board) as BoardLoad.Loaded).page)
        assertEquals(3, leaderboard.fetchCalls)
        assertEquals(1, store.putCalls)

        refresh.emit(Unit)
        runCurrent()
        assertEquals("a loaded final board is never read again", 3, leaderboard.fetchCalls)
    }

    @Test
    fun `a board is loading until its first read lands`() = runTest {
        val board = LeaderboardRef("zip", finalDay)
        val gate = CompletableDeferred<Unit>()
        val slowStore = object : FinalBoardStore {
            override suspend fun get(board: LeaderboardRef): BoardPage? {
                gate.await()
                return BoardPage(emptyList(), 0)
            }

            override suspend fun put(board: LeaderboardRef, page: BoardPage) = Unit
        }
        val window = collect(observeWith(finalBoards = slowStore)(RankWindow.Day(finalDay), listOf(zip)))
        assertEquals(RankWindowBoards.Boards(listOf(BoardLoad.Loading(board, RankOrder.TimeThenHints))), window.latest)

        gate.complete(Unit)
        runCurrent()

        assertTrue(window.board(board) is BoardLoad.Loaded)
    }

    // --- Refresh, identity, calendar ---------------------------------------------------------

    @Test
    fun `refresh is collected once for the whole window`() = runTest {
        var collections = 0
        val refresh = flow<Unit> {
            collections++
            awaitCancellation()
        }

        collect(observe(RankWindow.Month(today), games, refresh))

        assertEquals(1, collections)
    }

    @Test
    fun `signed out reads nothing`() = runTest {
        identity.currentIdentity = null

        val window = collect(observe(RankWindow.Month(today), games))

        assertEquals(RankWindowBoards.SignedOut, window.latest)
        assertEquals(0, listeners)
        assertEquals(0, leaderboard.fetchCalls)
        assertEquals(0, store.getCalls)
    }

    @Test
    fun `a new player restarts the boards and a renamed one does not`() = runTest {
        val window = collect(observe(RankWindow.Day(today), games))

        identity.currentIdentity = ann.copy(displayName = "Annie")
        runCurrent()
        assertEquals(5, listeners)

        identity.currentIdentity = Identity("u2", "Bo", null)
        runCurrent()
        assertEquals(10, listeners)
        assertEquals(5, window.boards.size)
    }

    @Test
    fun `a window is clipped at each game's own today`() = runTest {
        calendar.set(zip, today.minusDays(1))

        val window = collect(observe(RankWindow.Day(today), games))

        assertEquals(games.drop(1).map { LeaderboardRef(it.value, today) }, window.boards.map { it.board })
    }

    @Test
    fun `a rollover adds the new day to a current week without fetching again`() = runTest {
        val window = collect(observe(RankWindow.Week(today), games))
        assertEquals(25, window.boards.size)
        assertEquals(5, leaderboard.fetchCalls)

        calendar.setAll(today.plusDays(1))
        runCurrent()

        assertEquals(30, window.boards.size)
        assertTrue(games.all { game -> window.boards.any { it.board == LeaderboardRef(game.value, today.plusDays(1)) } })
        assertEquals(5, leaderboard.fetchCalls)
    }

    @Test
    fun `a rollover leaves a window it does not change alone`() = runTest {
        collect(observe(RankWindow.Day(today), games))

        calendar.setAll(today.plusDays(1))
        runCurrent()

        assertEquals(5, listeners)
    }

    @Test
    fun `a game the calendar does not know has no boards`() = runTest {
        val days = object : GameCalendar by calendar {
            override fun observeToday(game: GameId): Flow<LocalDate> =
                if (game == zip) emptyFlow() else calendar.observeToday(game)
        }

        val window = collect(observeWith(days = days)(RankWindow.Day(today), games))

        assertEquals(games.drop(1).map { LeaderboardRef(it.value, today) }, window.boards.map { it.board })
    }

    @Test
    fun `no games is an empty window, not a silent one`() = runTest {
        val window = collect(observe(RankWindow.Day(today), emptyList()))

        assertEquals(RankWindowBoards.Boards(emptyList()), window.latest)
    }
}
