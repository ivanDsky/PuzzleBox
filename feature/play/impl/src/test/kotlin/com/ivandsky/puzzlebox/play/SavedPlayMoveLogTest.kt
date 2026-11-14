package com.ivandsky.puzzlebox.play

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
import com.ivandsky.puzzlebox.game.replay.ReplayOutcome
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.PuzzleKey
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
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import com.ivandsky.puzzlebox.testing.replay.ReplayFixtures
import java.time.Instant
import java.time.LocalDate
import java.util.IdentityHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a saved play carries (A1, A8; S1, S3): the envelope holds the in-progress log exactly when the
 * play records, and a resumed play continues that log, so finishing after a restart seals the bytes an
 * uninterrupted play would have sealed.
 */
class SavedPlayMoveLogTest {

    private val game = TestGames.alpha
    private val day = LocalDate.parse("2026-10-31")
    private val key = GameKey(game.value, day.toString())
    private val slot = PlaySlot.Daily(game, day)

    private val fakes = FakeGames(listOf(game))
    private val capture = LogCapturingUi(fakes[game])
    private val registry = GamePlayRegistry(listOf(capture))
    private val withReplay = GameRegistry(listOf(replayPlugin()))
    private val daily = FakeDailyRepository().apply {
        setToday(game, DailyToday.Fresh(TestGames.puzzle(game)))
        setHistory(game, listOf(DailyEntry(game, day, 7, "alpha/v1", 0L)))
    }

    /** The rows outlive every store, as the database outlives the process. */
    private val saved = FakeSavedPlayRepository()
    private val dispatcher = UnconfinedTestDispatcher()
    private val calendar = FakeGameCalendar(day)
    private val lane = WriteLane()

    private fun store() = PlaySessionStore(
        games = withReplay,
        ui = registry,
        opening = SlotOpening(
            daily, LoadPastDay(daily, withReplay), saved, calendar, withReplay, lane, FakeReceivedPuzzleRepository(),
            FakeMatchRepository().apply { signedInUid = CHALLENGER.uid },
            LoadSharedPuzzle(withReplay, FakeSharedPuzzleRepository(), FakeReceivedPuzzleRepository(), { Budget.Unlimited }, { Instant.EPOCH }),
            testDispatchers(dispatcher),
        ),
        writers = { SlotWriter(it, saved, lane, CoroutineScope(dispatcher)) },
        completions = FakeCompletionRepository(),
        finishLeg = FinishLeg(FakeMatchResultOutbox(), FakeIdentityService()) { Instant.EPOCH },
        calendar = calendar,
        dispatchers = testDispatchers(dispatcher),
    )

    /** One committed move of the play: the log records it, then the game publishes the session. */
    private fun ActivePlay.move(atMs: Long) {
        val log = capture.logOf(controller)
        log.play(ReplayFixtures.replay, ReplayFixtures.initial(total = 10), MOVE, atMs)
        (controller as FakeController).move()
    }

    private fun ActivePlay.sealedAt(endMs: Long): ByteArray? =
        capture.logOf(controller).seal(ReplayOutcome.Solved, endMs)?.toByteArray()

    private fun fakeSession(moves: Int) = FakeController.encode(moves, false, TestGames.Ref(game, PuzzleKey.Daily(day, 7)))

    private fun savedBlob(): SessionBlob.Decoded = SessionBlob.decode(checkNotNull(saved.rows[slot]))!!

    @Test
    fun `saves a version 2 envelope carrying the log`() {
        val store = store()
        val play = store.open(key)!!

        play.move(atMs = 1_000)
        assertTrue(store.flush().isCompleted)

        val blob = savedBlob()
        assertArrayEquals(MOVED_ONCE, blob.session)
        assertArrayEquals(capture.logOf(play.controller).snapshot(), blob.log)
    }

    @Test
    fun `save, new store, resume, finish seals the bytes an uninterrupted play seals`() {
        val uninterrupted = store().open(key)!!
        uninterrupted.move(1_000)
        uninterrupted.move(2_000)
        uninterrupted.move(3_000)
        val expected = checkNotNull(uninterrupted.sealedAt(endMs = 4_000))
        runBlocking { saved.delete(slot) }

        val before = store()
        val first = before.open(key)!!
        first.move(1_000)
        first.move(2_000)
        assertTrue(before.flush().isCompleted)

        val resumed = store().open(key)!!
        assertEquals(2, (resumed.controller as FakeController).moves)
        resumed.move(3_000)

        assertArrayEquals(expected, resumed.sealedAt(endMs = 4_000))
    }

    @Test
    fun `a resumed play saves the log it continued, version 2`() {
        val before = store()
        before.open(key)!!.move(1_000)
        assertTrue(before.flush().isCompleted)

        val after = store()
        val resumed = after.open(key)!!
        resumed.move(2_000)
        assertTrue(after.flush().isCompleted)

        assertArrayEquals(capture.logOf(resumed.controller).snapshot(), savedBlob().log)
    }

    @Test
    fun `resume of a version 1 save seals null and saves version 1`() {
        runBlocking {
            saved.save(slot, SessionBlob.encode(game, fakeSession(moves = 2)), elapsedMs = 0L)
        }
        val store = store()

        val play = store.open(key)!!
        assertEquals(2, (play.controller as FakeController).moves)
        play.move(1_000)
        assertTrue(store.flush().isCompleted)

        assertNull(play.sealedAt(endMs = 2_000))
        assertEquals(1, checkNotNull(saved.rows[slot])[0].toInt())
        assertNull(savedBlob().log)
    }

    @Test
    fun `a save with a log of another game resumes the play with a void log`() {
        val foreign = MoveLog.fresh(TestGames.beta).also { it.setting(ReplayFixtures.replay, ReplayFixtures.initial(total = 10), MOVE, atMs = 0L) }.snapshot()
        runBlocking {
            saved.save(slot, SessionBlob.encode(game, fakeSession(moves = 1), foreign), elapsedMs = 0L)
        }

        val play = store().open(key)!!

        assertEquals(1, (play.controller as FakeController).moves)
        assertNull(play.sealedAt(endMs = 1_000))
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
        val CHALLENGER = Player("challenger", "Ivan", photoUrl = null)
        val MOVE = ReplayMove(kind = 0, args = IntArray(0))

        /** The fake game's session after one move on today's puzzle, as the saved bytes held it before the log existed. */
        val MOVED_ONCE = "fake|1|false|2026-10-31|7|0".encodeToByteArray()

        /** The plugin only has to ship a replay member for the play to record. */
        val PLUGIN_REPLAY = object : GameReplay<TestGames.Session> {
            override fun step(session: TestGames.Session, move: ReplayMove, atMs: Long): TestGames.Session = session
            override fun factsOf(initial: TestGames.Session): (TestGames.Session) -> ReplayFacts =
                { ReplayFacts(hints = 0, correct = 0, total = 0, solved = false) }
        }
    }
}
