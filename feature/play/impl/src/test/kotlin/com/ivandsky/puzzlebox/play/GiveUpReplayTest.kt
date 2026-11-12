package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.data.identity.Identity
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
import com.ivandsky.puzzlebox.game.replay.ReplayOutcome
import com.ivandsky.puzzlebox.game.replay.Timeline
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.model.SolveReplay
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
import com.ivandsky.puzzlebox.testing.replay.ReplayFixtures
import java.time.Instant
import java.time.LocalDate
import java.util.IdentityHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What giving up a duel leg stores beside the loss (A10, A11, A13, A23): the moves made before the
 * give-up, sealed "gave up" at the play time of that moment, from the live play or from a saved one.
 */
class GiveUpReplayTest {

    private val game = TestGames.alpha
    private val day = LocalDate.parse("2026-10-31")
    private val ref = TestGames.Ref(game, PuzzleKey.Daily(day, 7))
    private val legKey = MatchLegKey(LEG_CODE, 0, game.value)
    private val code = ShareCode.parse(LEG_CODE)!!
    private val legSlot = PlaySlot.Leg(game, code.value, legIndex = 0)

    private val capture = LogCapturingUi(FakeGames(listOf(game))[game])
    private val withReplay = GameRegistry(listOf(replayPlugin()))
    private val daily = FakeDailyRepository()
    private val saved = FakeSavedPlayRepository()
    private val outbox = FakeMatchResultOutbox()
    private val dispatcher = UnconfinedTestDispatcher()
    private val calendar = FakeGameCalendar(day)
    private val lane = WriteLane()
    private val me = Identity(uid = "me", displayName = "Me", photoUrl = null)

    private val on = FakeReleaseFlags(ReleaseFlag.FullReplay)
    private val off = FakeReleaseFlags()

    private fun store(flags: ReleaseFlags) = PlaySessionStore(
        games = withReplay,
        ui = GamePlayRegistry(listOf(capture)),
        opening = SlotOpening(
            daily, LoadPastDay(daily, withReplay), saved, calendar, withReplay, lane, FakeReceivedPuzzleRepository(),
            FakeMatchRepository(),
            LoadSharedPuzzle(withReplay, FakeSharedPuzzleRepository(), FakeReceivedPuzzleRepository(), { Budget.Unlimited }, { Instant.EPOCH }),
            testDispatchers(dispatcher),
        ),
        writers = { SlotWriter(it, saved, lane, CoroutineScope(dispatcher)) },
        completions = FakeCompletionRepository(),
        finishLeg = FinishLeg(outbox, FakeIdentityService(me)) { Instant.EPOCH },
        calendar = calendar,
        dispatchers = testDispatchers(dispatcher),
        flags = flags,
    )

    private fun session(placed: Int) = ReplayFixtures.initial(total = 10).copy(placed = placed)

    /** A saved leg play: [placed] stamped placements at 1s apart, the clock banked at [elapsedMs]. */
    private fun saveLeg(placed: Int, elapsedMs: Long, log: ByteArray? = savedLog(placed)) = runBlocking {
        val bytes = FakeController.encode(moves = placed, complete = false, ref = ref, elapsedMs = elapsedMs)
        saved.save(legSlot, SessionBlob.encode(game, bytes, log), elapsedMs)
    }

    private fun savedLog(placed: Int): ByteArray? {
        val log = MoveLog.fresh(game)
        repeat(placed) { log.play(ReplayFixtures.replay, session(it), PLACE, atMs = (it + 1) * 1_000L) }
        return log.snapshot()
    }

    /** The one loss in the outbox: its replay, or null when it carries none. */
    private fun queuedReplay(): SolveReplay? {
        val row = runBlocking { outbox.observe(code).first() }.single()
        check(row.result.gaveUp)
        return row.result.replay
    }

    private fun SolveReplay.timeline(): Timeline =
        checkNotNull(Timeline.open(game, ReplayFixtures.replay, session(0), this))

    @Test
    fun `a live give-up after three moves queues a replay that ends gave up at the give-up time`() {
        saveLeg(placed = 0, elapsedMs = 0L)
        val store = store(on)
        val play = store.open(legKey)!!
        repeat(3) {
            capture.logOf(play.controller).play(ReplayFixtures.replay, session(it), PLACE, atMs = (it + 1) * 1_000L)
            (play.controller as FakeController).move()
        }
        (play.controller as FakeController).advance(5_000)

        runBlocking { store.giveUp(legKey) }

        val timeline = checkNotNull(queuedReplay()).timeline()
        assertEquals(listOf(1_000L, 2_000L, 3_000L), timeline.moveTimesMs.toList())
        assertEquals(5_000L, timeline.endMs)
        assertEquals(ReplayOutcome.GaveUp, timeline.outcome)
        assertEquals(false, timeline.at(timeline.endMs).facts.solved)
    }

    @Test
    fun `a live give-up with zero moves queues a replay of the empty board that ends gave up`() {
        saveLeg(placed = 0, elapsedMs = 0L)
        val store = store(on)
        val play = store.open(legKey)!!
        (play.controller as FakeController).advance(4_000)

        runBlocking { store.giveUp(legKey) }

        val timeline = checkNotNull(queuedReplay()).timeline()
        assertEquals(0, timeline.moveTimesMs.size)
        assertEquals(4_000L, timeline.endMs)
        assertEquals(ReplayOutcome.GaveUp, timeline.outcome)
    }

    @Test
    fun `a give-up of a saved play that is not open seals its saved log at the banked play time`() {
        saveLeg(placed = 2, elapsedMs = 30_000L)
        val store = store(on)

        runBlocking { store.giveUp(legKey) }

        val timeline = checkNotNull(queuedReplay()).timeline()
        assertEquals(listOf(1_000L, 2_000L), timeline.moveTimesMs.toList())
        assertEquals(30_000L, timeline.endMs)
        assertEquals(ReplayOutcome.GaveUp, timeline.outcome)
        assertNull(runBlocking { saved.load(legSlot) })
    }

    @Test
    fun `a leg that was never opened queues the loss without a replay`() {
        runBlocking { store(on).giveUp(legKey) }

        assertNull(queuedReplay())
    }

    @Test
    fun `a saved version 1 play queues the loss without a replay`() {
        saveLeg(placed = 2, elapsedMs = 30_000L, log = null)

        runBlocking { store(on).giveUp(legKey) }

        assertNull(queuedReplay())
    }

    @Test
    fun `flag off queues the loss without a replay, live or saved`() {
        saveLeg(placed = 2, elapsedMs = 30_000L)
        runBlocking { store(off).giveUp(legKey) }
        assertNull(queuedReplay())

        val other = MatchLegKey(LEG_CODE, 1, game.value)
        val otherSlot = PlaySlot.Leg(game, code.value, legIndex = 1)
        runBlocking { saved.save(otherSlot, SessionBlob.encode(game, FakeController.encode(1, false, ref), savedLog(1)), elapsedMs = 0L) }
        val live = store(off)
        live.open(other)!!
        runBlocking { live.giveUp(other) }
        val row = runBlocking { outbox.observe(code).first() }.single { it.result.legIndex == 1 }
        assertNull(row.result.replay)
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
            replay = PLUGIN_REPLAY,
        )
    }

    private companion object {
        const val LEG_CODE = "M8T3VQ"
        val PLACE = ReplayMove(kind = 0, args = IntArray(0))
        val PLUGIN_REPLAY = object : GameReplay<TestGames.Session> {
            override fun step(session: TestGames.Session, move: ReplayMove, atMs: Long): TestGames.Session = session
            override fun factsOf(initial: TestGames.Session): (TestGames.Session) -> ReplayFacts =
                { ReplayFacts(hints = 0, correct = 0, total = 0, solved = false) }
        }
    }
}
