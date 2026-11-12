package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.MatchDraft
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.domain.FinishLeg
import com.ivandsky.puzzlebox.domain.LoadPastDay
import com.ivandsky.puzzlebox.domain.LoadSharedPuzzle
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.replay.GameReplay
import com.ivandsky.puzzlebox.game.replay.MoveLog
import com.ivandsky.puzzlebox.game.replay.ReplayFacts
import com.ivandsky.puzzlebox.game.replay.ReplayMove
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.MatchLegKey
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchResultOutbox
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import java.time.Instant
import java.time.LocalDate
import java.util.IdentityHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which move log a play gets (D1, D6): the flag decides whether a play records at all, the
 * slot's origin decides whether its log starts fresh or continues a saved one. A log is read
 * through its snapshot: null for a void log, bytes for a recording one.
 */
class ActivePlayMoveLogTest {

    private val game = TestGames.alpha
    private val day = LocalDate.parse("2026-10-31")
    private val yesterday = day.minusDays(1)
    private val key = GameKey(game.value, day.toString())

    private val fakes = FakeGames(listOf(game))
    private val ui = LogCapturingUi(fakes[game])
    private val registry = GamePlayRegistry(listOf(ui))
    private val withReplay = GameRegistry(listOf(replayPlugin()))
    private val daily = FakeDailyRepository().apply {
        setToday(game, DailyToday.Fresh(TestGames.puzzle(game)))
        setHistory(game, listOf(DailyEntry(game, day, 7, "alpha/v1", 0L)))
    }
    private val saved = FakeSavedPlayRepository()
    private val matches = FakeMatchRepository().apply { signedInUid = CHALLENGER.uid }
    private val dispatcher = UnconfinedTestDispatcher()
    private val calendar = FakeGameCalendar(day)
    private val lane = WriteLane()

    private fun store(flags: ReleaseFlags, games: GameRegistry = withReplay) = PlaySessionStore(
        games = games,
        ui = registry,
        opening = SlotOpening(
            daily, LoadPastDay(daily, games), saved, calendar, games, lane, FakeReceivedPuzzleRepository(),
            matches,
            LoadSharedPuzzle(games, FakeSharedPuzzleRepository(), FakeReceivedPuzzleRepository(), { Budget.Unlimited }, { Instant.EPOCH }),
            testDispatchers(dispatcher),
        ),
        writers = { SlotWriter(it, saved, lane, CoroutineScope(dispatcher)) },
        completions = FakeCompletionRepository(),
        finishLeg = FinishLeg(FakeMatchResultOutbox(), FakeIdentityService()) { Instant.EPOCH },
        calendar = calendar,
        dispatchers = testDispatchers(dispatcher),
        flags = flags,
    )

    private val on = FakeReleaseFlags(ReleaseFlag.FullReplay)
    private val off = FakeReleaseFlags()

    /** The log the board's controller was created with, after one setting move. */
    private fun snapshotOf(play: ActivePlay): ByteArray? {
        val log = ui.logOf(play.controller)
        log.setting(REPLAY, TestGames.Session, MOVE, atMs = 0L)
        return log.snapshot()
    }

    @Test
    fun `flag off records nothing`() {
        val play = store(off).open(key)!!

        assertNull(snapshotOf(play))
    }

    @Test
    fun `flag on starts a fresh log on a fresh board`() {
        val play = store(on).open(key)!!

        assertNotNull(snapshotOf(play))
    }

    @Test
    fun `flag on starts a fresh log on a past day's build`() {
        daily.setCached(game, yesterday, TestGames.puzzle(game))

        val play = store(on).open(GameKey(game.value, yesterday.toString()))!!

        assertNotNull(snapshotOf(play))
    }

    @Test
    fun `flag on resumes a saved play with a void log`() {
        runBlocking {
            saved.save(PlaySlot.Daily(game, day), SessionBlob.encode(game, FakeController.encode(2, false, TestGames.Ref(game, PuzzleKey.Daily(day, 7)))), elapsedMs = 0L)
        }

        val play = store(on).open(key)!!

        assertEquals(2, (play.controller as FakeController).moves)
        assertNull(snapshotOf(play))
    }

    @Test
    fun `flag on gives a game without a replay member the off log`() {
        val play = store(on, games = fakes.games).open(key)!!

        assertNull(snapshotOf(play))
    }

    @Test
    fun `play again starts a new fresh log over the same puzzle`() {
        val play = store(on).open(key)!!
        val first = ui.logOf(play.controller)
        (play.controller as FakeController).win()

        play.playAgain()

        val again = play.controller as FakeController
        assertNotSame(first, ui.logOf(again))
        assertSame(again, play.board.value.controller)
        assertEquals(PuzzleKey.Daily(day, 7), again.ref!!.key)
        assertEquals(1, again.playAgains)
        assertTrue(again.puzzle != null)
        assertNotNull(snapshotOf(play))
    }

    @Test
    fun `play again with the flag off keeps the controller and its off log`() {
        val play = store(off).open(key)!!
        val before = play.controller
        (before as FakeController).win()

        play.playAgain()

        assertSame(before, play.controller)
        assertNull(snapshotOf(play))
    }

    @Test
    fun `flag on starts a fresh log on a duel leg's build`() {
        val code = checkNotNull(ShareCode.parse("M8T3VQ"))
        daily.setCached(game, yesterday, TestGames.puzzle(game))
        runBlocking {
            matches.create(
                MatchDraft(
                    code = code,
                    deadlineHours = 24,
                    challenger = CHALLENGER,
                    invitedUid = null,
                    legs = listOf(MatchLeg(LegRef.Daily(game, yesterday, 6), authorUid = null)),
                    includeFriends = false,
                    rematchOf = null,
                ),
            )
        }

        val play = store(on).open(MatchLegKey(code.value, 0, game.value))!!

        assertNotNull(snapshotOf(play))
    }

    @Test
    fun `play again whose puzzle the game now rejects ends unavailable`() {
        val play = store(on).open(key)!!
        (play.controller as FakeController).win()
        fakes[game].rejectWith = "untrusted"

        play.playAgain()

        assertTrue(play.board.value.status is PlayBoardStatus.Unavailable)
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
        val CHALLENGER = Player("challenger", "Ivan", photoUrl = null)
        val MOVE = ReplayMove(kind = 0, args = IntArray(0))

        /** A game whose every move is understood and changes nothing. */
        val REPLAY = object : GameReplay<TestGames.Session> {
            override fun step(session: TestGames.Session, move: ReplayMove, atMs: Long): TestGames.Session = session

            override fun factsOf(initial: TestGames.Session): (TestGames.Session) -> ReplayFacts =
                { ReplayFacts(hints = 0, correct = 0, total = 0, solved = false) }
        }
    }
}
