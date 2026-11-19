package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.data.repository.DailyRefresh
import com.ivandsky.puzzlebox.play.domain.FinishLeg
import com.ivandsky.puzzlebox.domain.LoadPastDay
import com.ivandsky.puzzlebox.domain.LoadSharedPuzzle
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.replay.GameReplay
import com.ivandsky.puzzlebox.game.replay.MoveLog
import com.ivandsky.puzzlebox.game.replay.ReplayFacts
import com.ivandsky.puzzlebox.testing.replay.ReplayFixtures
import com.ivandsky.puzzlebox.game.replay.ReplayMove
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchResultOutbox
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSelfMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import java.time.Instant
import java.time.LocalDate
import java.util.IdentityHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Today's board shows from the stored puzzle before its source answers (A3, A4): the refresh runs behind the
 * board, and a changed puzzle replaces board and store only while the player has not acted (A5, A16).
 */
class ActivePlayBoardFirstTest {

    private val game = TestGames.alpha
    private val day = LocalDate.parse("2026-10-31")
    private val key = GameKey(game.value, day.toString())
    private val stored = TestGames.puzzle(game, tag = 1)
    private val revised = TestGames.puzzle(game, tag = 2)

    private val fakes = FakeGames(listOf(game))
    private val ui = LogCapturingUi(fakes[game])
    private val withReplay = GameRegistry(listOf(replayPlugin()))
    private val daily = FakeDailyRepository().apply {
        setCached(game, day, stored)
        setToday(game, revised)
        setHistory(game, listOf(DailyEntry(game, day, 7, "alpha/v1", 0L)))
    }
    private val saved = FakeSavedPlayRepository()
    private val dispatcher = UnconfinedTestDispatcher()
    private val calendar = FakeGameCalendar(day)
    private val lane = WriteLane()

    private fun open(
        repo: FakeDailyRepository = daily,
        games: GameRegistry = withReplay,
        key: GameKey = this.key,
    ): ActivePlay = PlaySessionStore(
        games = games,
        ui = GamePlayRegistry(listOf(ui)),
        opening = SlotOpening(
            repo, LoadPastDay(repo, games), saved, calendar, games, lane, FakeReceivedPuzzleRepository(),
            FakeMatchRepository(),
            LoadSharedPuzzle(games, FakeSharedPuzzleRepository(), FakeReceivedPuzzleRepository(), { Budget.Unlimited }, { Instant.EPOCH }),
            testDispatchers(dispatcher),
        ),
        writers = { SlotWriter(it, saved, lane, CoroutineScope(dispatcher)) },
        completions = FakeCompletionRepository(),
        finishLeg = FinishLeg(FakeMatchResultOutbox(), FakeSelfMatchRepository(), FakeIdentityService()) { Instant.EPOCH },
        calendar = calendar,
        dispatchers = testDispatchers(dispatcher),
    ).open(key)!!

    private fun storedNow(): Puzzle? = runBlocking { daily.cachedPuzzle(game, day) }

    private fun shown(play: ActivePlay): Puzzle = (play.controller as FakeController).puzzle!!

    /** The player's first move: logged by the controller, then published like every game's change. */
    private fun playerMoves(play: ActivePlay) {
        ui.logOf(play.controller).play(ReplayFixtures.replay, ReplayFixtures.initial(total = 2), MOVE, atMs = 50L)
        (play.controller as FakeController).move()
    }

    @Test
    fun `a stored puzzle is ready before the refresh completes`() {
        val gate = daily.holdRefresh(game)

        val play = open()

        assertEquals(PlayBoardStatus.Ready, play.board.value.status)
        assertSame(stored, shown(play))
        assertEquals("the refresh has started and is still out", 1, daily.todayCalls(game))
        gate.complete(Unit)
    }

    @Test
    fun `loading says today's puzzle only for the game's today`() {
        assertEquals(PlayBoardStatus.Loading(todaysDaily = true), loadingOf(key))

        val earlier = day.minusDays(1)
        daily.setCached(game, earlier, stored)
        assertEquals(PlayBoardStatus.Loading(todaysDaily = false), loadingOf(GameKey(game.value, earlier.toString())))
    }

    /** The loading status a retry of [at]'s play passes through, seen by a collector that was already watching. */
    private fun loadingOf(at: GameKey): PlayBoardStatus {
        val play = open(key = at)
        val seen = mutableListOf<PlayBoardStatus>()
        val watching = CoroutineScope(dispatcher).launch { play.board.collect { seen += it.status } }
        play.retry()
        watching.cancel()
        return seen.first { it is PlayBoardStatus.Loading }
    }

    @Test
    fun `a changed puzzle on an untouched board swaps the board and the store`() {
        val gate = daily.holdRefresh(game)
        val play = open()

        gate.complete(Unit)

        assertEquals(PlayBoardStatus.Ready, play.board.value.status)
        assertSame(revised, shown(play))
        assertSame(revised, storedNow())
    }

    @Test
    fun `a changed puzzle after one player input swaps neither`() {
        val gate = daily.holdRefresh(game)
        val play = open()
        val first = play.controller

        playerMoves(play)
        gate.complete(Unit)

        assertSame(first, play.controller)
        assertSame(stored, shown(play))
        assertSame(stored, storedNow())
    }

    @Test
    fun `the adopt's own session and a clock fold do not close the latch`() {
        val gate = daily.holdRefresh(game)
        val play = open()
        val controller = play.controller as FakeController

        controller.advance(5_000L)
        controller.onBackground()
        gate.complete(Unit)

        assertSame(revised, shown(play))
        assertSame(revised, storedNow())
    }

    /** The kinds (refused tap, empty undo, hint dialog) are pinned on the real controllers in the app tests. */
    @Test
    fun `an input that changes and logs nothing still closes the latch`() {
        val gate = daily.holdRefresh(game)
        val play = open()
        val first = play.controller

        (first as FakeController).inputWithoutMove()
        gate.complete(Unit)

        assertSame(first, play.controller)
        assertSame(stored, shown(play))
        assertSame(stored, storedNow())
    }

    @Test
    fun `a failed refresh with a stored board shows no banner and changes nothing`() {
        daily.setRefresh(game, DailyRefresh.Failed(FetchError.Unavailable("offline")))

        val play = open()

        assertEquals(PlayBoardStatus.Ready, play.board.value.status)
        assertSame(stored, shown(play))
        assertSame(stored, storedNow())
    }

    @Test
    fun `an unchanged refresh leaves the board alone`() {
        daily.setRefresh(game, DailyRefresh.Unchanged)
        val play = open()
        val first = play.controller

        assertSame(first, play.controller)
        assertSame(stored, storedNow())
    }

    @Test
    fun `a game that keeps no move log is never swapped`() {
        val gate = daily.holdRefresh(game)
        val play = open(games = fakes.games)

        gate.complete(Unit)

        assertSame(stored, shown(play))
        assertSame(stored, storedNow())
    }

    @Test
    fun `nothing stored shows loading until the one fetch lands and then plays it`() {
        val fresh = FakeDailyRepository().apply {
            setToday(game, revised)
            setHistory(game, listOf(DailyEntry(game, day, 7, "alpha/v1", 0L)))
        }
        val gate = fresh.holdRefresh(game)
        val play = open(fresh)
        assertEquals(PlayBoardStatus.Loading(todaysDaily = true), play.board.value.status)

        gate.complete(Unit)

        assertEquals(PlayBoardStatus.Ready, play.board.value.status)
        assertArrayEquals(revised.payload, shown(play).payload)
        assertEquals("one fetch decided the board; no second refresh", 1, fresh.todayCalls(game))
    }

    /** [inner] with the log each controller was created with kept, so a test can read it. */
    private class LogCapturingUi(private val inner: FakePlayUi) : GamePlayUi by inner {
        private val logs = IdentityHashMap<GamePlayController, MoveLog>()

        fun logOf(controller: GamePlayController): MoveLog = logs.getValue(controller)

        override fun createController(env: PlayEnvironment): GamePlayController =
            inner.createController(env).also { logs[it] = env.moveLog }
    }

    private fun replayPlugin(): GamePlugin<Unit, TestGames.Session, TestGames.Model> {
        val base = TestGames.plugin(game)
        return GamePlugin(
            definition = base.definition,
            rules = base.rules,
            session = base.session,
            score = base.score,
            stateOf = base.stateOf,
            initialSession = base.initialSession,
            refOf = base.refOf,
            playAgainSession = base.playAgainSession,
            rendererModel = base.rendererModel,
            replay = REPLAY,
        )
    }

    private companion object {
        val MOVE = ReplayMove(kind = 0, args = IntArray(0))

        /** A game whose every move is understood and changes nothing. */
        val REPLAY = object : GameReplay<TestGames.Session> {
            override fun step(session: TestGames.Session, move: ReplayMove, atMs: Long): TestGames.Session = session

            override fun factsOf(initial: TestGames.Session): (TestGames.Session) -> ReplayFacts =
                { ReplayFacts(hints = 0, correct = 0, total = 0, solved = false) }
        }
    }
}
