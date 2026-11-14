package com.ivandsky.puzzlebox.play

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
import com.ivandsky.puzzlebox.game.replay.ReplayOutcome
import com.ivandsky.puzzlebox.game.replay.Timeline
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.SolveReplay
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.MatchLegKey
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a win stores beside the completion (A1, A7, A11; S1, S4, S5): the play's log sealed at the record's
 * time, written in the same call as the record, and nothing but the record when the play kept no log.
 */
class WinSealsReplayTest {

    private val game = TestGames.alpha
    private val day = LocalDate.parse("2026-10-31")
    private val key = GameKey(game.value, day.toString())
    private val slot = PlaySlot.Daily(game, day)
    private val ref = TestGames.Ref(game, PuzzleKey.Daily(day, 7))

    private val capture = LogCapturingUi(FakeGames(listOf(game))[game])
    private val withReplay = GameRegistry(listOf(replayPlugin()))
    private val daily = FakeDailyRepository().apply {
        setToday(game, DailyToday.Fresh(TestGames.puzzle(game)))
        setHistory(game, listOf(DailyEntry(game, day, 7, "alpha/v1", 0L)))
    }
    private val saved = FakeSavedPlayRepository()
    private val dispatcher = UnconfinedTestDispatcher()
    private val calendar = FakeGameCalendar(day)
    private val lane = WriteLane()

    private fun store(completions: FakeCompletionRepository) = PlaySessionStore(
        games = withReplay,
        ui = GamePlayRegistry(listOf(capture)),
        opening = SlotOpening(
            daily, LoadPastDay(daily, withReplay), saved, calendar, withReplay, lane, FakeReceivedPuzzleRepository(),
            FakeMatchRepository(),
            LoadSharedPuzzle(withReplay, FakeSharedPuzzleRepository(), FakeReceivedPuzzleRepository(), { Budget.Unlimited }, { Instant.EPOCH }),
            testDispatchers(dispatcher),
        ),
        writers = { SlotWriter(it, saved, lane, CoroutineScope(dispatcher)) },
        completions = completions,
        finishLeg = FinishLeg(FakeMatchResultOutbox(), FakeIdentityService()) { Instant.EPOCH },
        calendar = calendar,
        dispatchers = testDispatchers(dispatcher),
    )

    /** One placement the log records, then the game publishes the session. */
    private fun ActivePlay.place(atMs: Long) {
        capture.logOf(controller).play(ReplayFixtures.replay, session, PLACE, atMs)
        session = session.copy(placed = session.placed + 1)
        (controller as FakeController).move()
    }

    private var session = ReplayFixtures.initial(total = 2)

    private fun FakeCompletionRepository.records() = runBlocking { observeRecords().first() }

    private fun FakeCompletionRepository.latest(): SolveReplay? = runBlocking { latestReplay(ref) }

    private fun SolveReplay.timeline(): Timeline =
        checkNotNull(Timeline.open(game, ReplayFixtures.replay, ReplayFixtures.initial(total = 2), this))

    /** A two-placement solve ending on the fake game's record time. */
    private fun ActivePlay.solve() {
        place(10_000)
        place(20_000)
        (controller as FakeController).win()
    }

    @Test
    fun `stores a replay that ends solved at the record's time`() {
        val completions = FakeCompletionRepository()

        store(completions).open(key)!!.solve()

        val replay = checkNotNull(completions.latest())
        val timeline = replay.timeline()
        assertEquals(FakeController.TIME_MS, timeline.endMs)
        assertEquals(ReplayOutcome.Solved, timeline.outcome)
        assertTrue(timeline.at(timeline.endMs).facts.solved)
        assertEquals(replay, runBlocking { completions.recordReplay(ref) })
    }

    @Test
    fun `leaves the record as the fake game reported it`() {
        val completions = FakeCompletionRepository()

        store(completions).open(key)!!.solve()

        assertEquals(listOf(FakeController.record(ref)), completions.records())
    }

    @Test
    fun `a failed write retried stores the replay sealed at the win`() {
        val completions = FakeCompletionRepository().apply { fail = true }
        val play = store(completions).open(key)!!
        play.solve()
        assertTrue(play.record.value.writeFailed)
        assertNull(completions.latest())
        val sealed = checkNotNull(capture.logOf(play.controller).seal(ReplayOutcome.Solved, FakeController.TIME_MS))

        // A late move after the win must not reach the retry.
        capture.logOf(play.controller).play(ReplayFixtures.replay, session, PLACE, atMs = 30_000)
        completions.fail = false
        play.retryRecordWrite()

        assertFalse(play.record.value.writeFailed)
        assertEquals(sealed, completions.latest())
        assertEquals(listOf(FakeController.record(ref)), completions.records())
    }

    @Test
    fun `a resumed version 1 save stores the record alone`() {
        runBlocking { saved.save(slot, SessionBlob.encode(game, FakeController.encode(moves = 1, complete = false, ref = ref)), elapsedMs = 0L) }
        val completions = FakeCompletionRepository()

        val play = store(completions).open(key)!!
        (play.controller as FakeController).win()

        assertNull(completions.latest())
        assertEquals(listOf(FakeController.record(ref)), completions.records())
    }

    @Test
    fun `play again after a win records its own replay`() {
        val completions = FakeCompletionRepository()
        val play = store(completions).open(key)!!
        play.solve()
        val first = checkNotNull(completions.latest())

        play.playAgain()
        session = ReplayFixtures.initial(total = 2)
        play.place(5_000)
        play.place(6_000)
        (play.controller as FakeController).win()

        val second = checkNotNull(completions.latest())
        assertEquals(listOf(5_000L, 6_000L), second.timeline().moveTimesMs.toList())
        assertEquals(listOf(10_000L, 20_000L), first.timeline().moveTimesMs.toList())
    }

    @Test
    fun `a duel leg's win reaches the inner store with its replay`() {
        val legSlot = PlaySlot.Leg(game, LEG_CODE, legIndex = 0)
        val log = MoveLog.fresh(game).also { it.play(ReplayFixtures.replay, ReplayFixtures.initial(total = 2), PLACE, atMs = 5_000) }
        runBlocking {
            saved.save(legSlot, SessionBlob.encode(game, FakeController.encode(moves = 1, complete = false, ref = ref), log.snapshot()), elapsedMs = 0L)
        }
        session = ReplayFixtures.initial(total = 2).copy(placed = 1)
        val completions = FakeCompletionRepository()

        val play = store(completions).open(MatchLegKey(LEG_CODE, 0, game.value))!!
        play.place(10_000)
        (play.controller as FakeController).win()

        val replay = checkNotNull(completions.latest())
        assertEquals(listOf(5_000L, 10_000L), replay.timeline().moveTimesMs.toList())
        assertEquals(FakeController.TIME_MS, replay.timeline().endMs)
        assertEquals(LEG_CODE, completions.records().single().matchCode)
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
