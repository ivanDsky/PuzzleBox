package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.domain.FinishLeg
import com.ivandsky.puzzlebox.domain.LoadPastDay
import com.ivandsky.puzzlebox.domain.LoadSharedPuzzle
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayClock
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.Restored
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.play.api.CustomGameKey
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.MatchLegKey
import com.ivandsky.puzzlebox.play.api.MatchLegResultKey
import com.ivandsky.puzzlebox.play.api.ResultKey
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
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The store's live plays by slot on virtual time: one play per (game, day)
 * whichever key opens it, a null day pinned to the day it first opened, a released slot's
 * final save resumed by its reopen without the network, a solve's row deleted, and the
 * app-stop flush folding the clock before it writes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
// Robolectric: the store logs a failed records snapshot through android.util.Log.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaySessionStoreSlotsTest {

    private val alpha = TestGames.alpha
    private val beta = TestGames.beta
    private val today = LocalDate.parse("2026-11-04")
    private val slot = PlaySlot.Daily(alpha, today)
    private val key = GameKey(alpha.value, today.toString())
    private val todayKey = GameKey(alpha.value)

    private val fakes = FakeGames(listOf(alpha, beta))
    private val daily = FakeDailyRepository().apply {
        setToday(alpha, DailyToday.Fresh(TestGames.puzzle(alpha)))
        setToday(beta, DailyToday.Fresh(TestGames.puzzle(beta)))
        setHistory(alpha, listOf(DailyEntry(alpha, today, 7, "alpha/v1", 0L)))
    }
    private val saved = FakeSavedPlayRepository()
    private val calendar = FakeGameCalendar(today)
    private val completions = FakeCompletionRepository()

    private fun TestScope.store(
        ui: GamePlayRegistry = fakes.registry,
        repo: CompletionRepository = completions,
    ): PlaySessionStore {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val lane = WriteLane()
        return PlaySessionStore(
            games = fakes.games,
            ui = ui,
            opening = SlotOpening(
                daily, LoadPastDay(daily, fakes.games), saved, calendar, fakes.games, lane, FakeReceivedPuzzleRepository(),
                FakeMatchRepository(),
                LoadSharedPuzzle(fakes.games, FakeSharedPuzzleRepository(), FakeReceivedPuzzleRepository(), { Budget.Unlimited }, { Instant.EPOCH }),
                testDispatchers(dispatcher),
            ),
            writers = { SlotWriter(it, saved, lane, CoroutineScope(dispatcher)) },
            completions = repo,
            finishLeg = FinishLeg(FakeMatchResultOutbox(), FakeIdentityService()) { Instant.EPOCH },
            calendar = calendar,
            dispatchers = object : DispatcherProvider {
                override val main = dispatcher
                override val io = dispatcher
                override val default = dispatcher
            },
        )
    }

    private fun blob(game: GameId, session: ByteArray) = SessionBlob.encode(game, session)

    @Test
    fun `every key naming a slot shares its one live play`() = runTest {
        val store = store()
        val play = store.open(key)!!

        assertSame(play, store.open(key))
        assertSame("a null day resolves to the same slot", play, store.open(todayKey))
        assertSame(play, store.peek(slot))
        assertEquals(slot, play.slot)
        assertEquals(1, daily.todayCalls(alpha))
        assertNull(store.peek(PlaySlot.Daily(beta, today)))
    }

    @Test
    fun `a released slot's save resumes on reopen without the network`() = runTest {
        val store = store()
        val first = store.open(key)!!
        runCurrent()
        assertEquals(PlayBoardStatus.Ready(banner = null), first.board.value.status)
        fakes[alpha].latest.move()
        fakes[alpha].latest.move()

        store.retain(emptyList())
        runCurrent()
        assertNull(store.peek(slot))

        val reopened = store.open(key)!!
        runCurrent()
        assertEquals(1, daily.todayCalls(alpha))
        assertEquals(PlayBoardStatus.Ready(banner = null), reopened.board.value.status)
        assertEquals(2, fakes[alpha].latest.moves)
        assertEquals("the save carries the record identity", first.record.value.label, reopened.record.value.label)
    }

    @Test
    fun `a null-day key keeps its pinned day across midnight`() = runTest {
        val store = store()
        val play = store.open(todayKey)!!
        val next = today.plusDays(1)
        calendar.setAll(next)

        assertSame(play, store.open(todayKey))
        store.retain(listOf(todayKey))
        assertSame("the key keeps the slot it opened", play, store.peek(slot))

        store.retain(emptyList())
        runCurrent()
        assertEquals("a released pin resolves today again", PlaySlot.Daily(alpha, next), store.open(todayKey)!!.slot)
    }

    @Test
    fun `a solve deletes the slot's row and never saves again`() = runTest {
        val store = store()
        store.open(key)
        fakes[alpha].latest.move()
        advanceTimeBy(SAVE_WINDOW_MS)
        runCurrent()
        assertTrue(slot in saved.rows)

        fakes[alpha].latest.win()
        runCurrent()
        assertFalse(slot in saved.rows)

        store.flush()
        runCurrent()
        assertTrue(saved.rows.isEmpty())
    }

    @Test
    fun `discard drops a friend's live play without its pending save`() = runTest {
        val friend = PlaySlot.Custom(alpha, "K7Q2MX")
        val store = store()
        store.open(CustomGameKey(alpha.value, friend.puzzleId))
        runCurrent()
        fakes[alpha].latest.move()

        store.discard(friend)
        advanceTimeBy(SAVE_WINDOW_MS)
        runCurrent()
        store.flush()
        runCurrent()

        assertNull(store.peek(friend))
        assertFalse(friend in saved.rows)
    }

    @Test
    fun `retain releases an unnamed slot after writing its latest save`() = runTest {
        val store = store()
        val kept = store.open(todayKey)!!
        val betaSlot = PlaySlot.Daily(beta, today)
        val released = store.open(GameKey(beta.value, today.toString()))!!
        fakes[beta].latest.move()
        assertFalse("still inside the save window", betaSlot in saved.rows)

        store.retain(listOf(todayKey))
        runCurrent()

        assertNull(store.peek(betaSlot))
        assertSame(kept, store.peek(slot))
        assertArrayEquals(blob(beta, fakes[beta].latest.bytes()), saved.rows[betaSlot])
        assertNotSame(released, store.open(GameKey(beta.value, today.toString())))
    }

    private fun solved(time: Long, hints: Int) = CompletionRecord(
        TestGames.Ref(alpha, PuzzleKey.Daily(today, 7)),
        time,
        hints,
        RecordStats(1, "{}"),
        isRecord = true,
    )

    @Test
    fun `a day with a completion record opens its result with that record's time and hints`() = runTest {
        completions.setRecords(listOf(solved(time = 61_000L, hints = 2)))
        val store = store()

        val expected = ResultKey(alpha.value, today.toString(), 61_000L, 2)
        assertEquals(expected, store.completedResult(key))
        assertEquals("a null-day key resolves to today's slot", expected, store.completedResult(todayKey))
        assertNull("nothing is opened to decide", store.peek(slot))
        assertNull("another game's day has no record", store.completedResult(GameKey(beta.value, today.toString())))
    }

    @Test
    fun `an unsolved day opens its board`() = runTest {
        assertNull(store().completedResult(key))
    }

    @Test
    fun `a play again saved mid-way opens its board though the day has a record`() = runTest {
        completions.setRecords(listOf(solved(time = 61_000L, hints = 2)))
        saved.save(slot, blob(alpha, byteArrayOf(1)), elapsedMs = 0L)

        assertNull(store().completedResult(key))
    }

    @Test
    fun `a solved live play opens its own solve until Play again resets it`() = runTest {
        val store = store()
        store.open(key)
        runCurrent()
        fakes[alpha].latest.win()
        runCurrent()

        val result = ResultKey(alpha.value, today.toString(), FakeController.TIME_MS, 0)
        assertEquals(result, store.completedResult(key))
        val live = store.peek(slot)
        assertSame("opening hands back the same play", live, store.open(key))
        assertTrue("opening never resets it", fakes[alpha].latest.isComplete)
        assertEquals(0, fakes[alpha].latest.playAgains)

        store.playAgain(result)

        assertEquals(1, fakes[alpha].latest.playAgains)
        assertFalse(fakes[alpha].latest.isComplete)
        assertNull("the fresh attempt opens as a board", store.completedResult(key))
        assertSame(store.peek(slot), store.open(key))
    }

    @Test
    fun `Play again with no live play opens a fresh board of the solved day`() = runTest {
        completions.setRecords(listOf(solved(time = 61_000L, hints = 2)))
        val store = store()
        val result = checkNotNull(store.completedResult(key))

        store.playAgain(result)
        runCurrent()

        assertNotNull(store.peek(slot))
        assertFalse(fakes[alpha].latest.isComplete)
        assertNull("the play again is live, so its board opens", store.completedResult(key))
    }

    @Test
    fun `a failing records flow never leaves an unsolved day waiting`() = runTest {
        val broken = object : CompletionRepository by completions {
            override fun observeRecords(): Flow<List<CompletionRecord>> = flow { throw IllegalStateException("boom") }
        }
        val store = store(repo = broken)
        runCurrent()

        assertEquals(OpenDecision.Cold, store.decide(key))
        assertNull(store.completedResult(key))
    }

    @Test
    fun `a solved live play decides its result without a read, an unsolved day decides its board`() = runTest {
        val store = store()
        assertEquals(OpenDecision.Open, store.decide(key))
        store.open(key)
        runCurrent()
        fakes[alpha].latest.win()
        runCurrent()

        assertEquals(
            OpenDecision.Finished(ResultKey(alpha.value, today.toString(), FakeController.TIME_MS, 0)),
            store.decide(key),
        )
    }

    @Test
    fun `a solve released before its record lands still opens its result`() = runTest {
        val store = store()
        store.open(key)
        runCurrent()
        fakes[alpha].latest.win()
        runCurrent()

        store.retain(emptyList())
        runCurrent()

        assertEquals(
            ResultKey(alpha.value, today.toString(), FakeController.TIME_MS, 0),
            store.completedResult(key),
        )
    }

    @Test
    fun `a result key keeps its slot's play live`() = runTest {
        val store = store()
        val play = store.open(todayKey)!!
        store.open(GameKey(beta.value, today.toString()))

        store.retain(listOf(ResultKey(alpha.value, today.toString(), 1_000L, 0)))
        runCurrent()

        assertSame("the result names its game's slot", play, store.peek(slot))
        assertNull(store.peek(PlaySlot.Daily(beta, today)))
    }

    @Test
    fun `a duel puzzle's result key keeps its live leg play`() = runTest {
        val store = store()
        val leg = MatchLegKey("M8T3VQ", 1, alpha.value)
        val play = store.open(leg)!!
        store.open(MatchLegKey(leg.code, 0, alpha.value))

        store.retain(listOf(MatchLegResultKey(leg.code, leg.legIndex, 1_000L, 0, gaveUp = false)))
        runCurrent()

        assertSame("the result names its puzzle's leg", play, store.peek(play.slot))
        assertNull(store.peek(PlaySlot.Leg(alpha, leg.code, 0)))
    }

    @Test
    fun `flush folds the clock before writing`() = runTest {
        val store = store(GamePlayRegistry(listOf(ClockedPlayUi(fakes[alpha]))))
        store.open(key)
        assertTrue(saved.rows.isEmpty())

        val flushed = store.flush()
        runCurrent()

        assertTrue(flushed.isCompleted)
        assertArrayEquals("the fold's banked clock is what was written", blob(alpha, byteArrayOf(1)), saved.rows[slot])
    }

    /** The stand-in game with a clock: its session is the banked fold count. */
    private class ClockedPlayUi(base: FakePlayUi) : GamePlayUi by base {
        override fun createController(env: PlayEnvironment): GamePlayController = ClockedController(env)
    }

    /** Publishes only when a background fold banks the running window. */
    private class ClockedController(private val env: PlayEnvironment) : GamePlayController, PlayClock {
        private var folds = 0
        override val isComplete = false
        override val clockWindow: Any? = null

        override fun adoptToday(puzzle: Puzzle, ref: PuzzleRef): Adopt = Adopt.Adopted
        override fun adoptRestored(bytes: ByteArray): Restored? = null
        override fun playAgain() = Unit
        override fun tick() = Unit
        override fun onForeground() = Unit
        override fun displayedElapsedMs(): Long = 0L

        override fun onBackground() {
            folds++
            env.onSessionChanged(byteArrayOf(folds.toByte()))
        }
    }

    private companion object {
        /** SlotWriter's default throttle window. */
        const val SAVE_WINDOW_MS = 300L
    }
}
