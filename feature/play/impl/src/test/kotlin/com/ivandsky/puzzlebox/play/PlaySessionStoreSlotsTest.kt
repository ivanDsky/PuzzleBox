package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.domain.GetReplaySession
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.SessionBlob
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
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The store's live plays by slot on virtual time (A9–A11, N21): one play per (game, day)
 * whichever key opens it, a null day pinned to the day it first opened, a released slot's
 * final save resumed by its reopen without the network, a solve's row deleted, and the
 * app-stop flush folding the clock before it writes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaySessionStoreSlotsTest {

    private val alpha = TestGames.alpha
    private val beta = TestGames.beta
    private val today = LocalDate.parse("2026-11-04")
    private val slot = PlaySlot(alpha, today)
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

    private fun TestScope.store(ui: GamePlayRegistry = fakes.registry): PlaySessionStore {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val lane = WriteLane()
        return PlaySessionStore(
            games = fakes.games,
            ui = ui,
            opening = SlotOpening(daily, GetReplaySession(daily, fakes.games), saved, calendar, fakes.games, lane),
            writers = { SlotWriter(it, saved, lane, CoroutineScope(dispatcher)) },
            completions = FakeCompletionRepository(),
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
        assertNull(store.peek(PlaySlot(beta, today)))
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
        assertEquals("a released pin resolves today again", PlaySlot(alpha, next), store.open(todayKey)!!.slot)
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
    fun `retain releases an unnamed slot after writing its latest save`() = runTest {
        val store = store()
        val kept = store.open(todayKey)!!
        val betaSlot = PlaySlot(beta, today)
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

    @Test
    fun `a result key keeps its slot's play live`() = runTest {
        val store = store()
        val play = store.open(todayKey)!!
        store.open(GameKey(beta.value, today.toString()))

        store.retain(listOf(ResultKey(alpha.value, today.toString(), 1_000L, 0)))
        runCurrent()

        assertSame("the result names its game's slot", play, store.peek(slot))
        assertNull(store.peek(PlaySlot(beta, today)))
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

    /** Publishes only when a background fold banks the running window (F2 §2). */
    private class ClockedController(private val env: PlayEnvironment) : GamePlayController, PlayClock {
        private var folds = 0
        override val isComplete = false
        override val clockWindow: Any? = null

        override fun adoptToday(puzzle: Puzzle, ref: PuzzleRef): Adopt = Adopt.Adopted
        override fun adoptRestored(bytes: ByteArray): Restored? = null
        override fun replay() = Unit
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
