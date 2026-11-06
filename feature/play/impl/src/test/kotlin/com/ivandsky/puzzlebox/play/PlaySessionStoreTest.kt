package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.domain.LoadPastDay
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The play session store's catalogue and its process lifetime over stand-in games: which
 * games are playable, that an unplayable key opens nothing, and that a mid-game play
 * resumes (A26: it no longer "never resumes") even in a brand-new store after process
 * death. Slot sharing, pinning, release and flush are [PlaySessionStoreSlotsTest]'s.
 */
class PlaySessionStoreTest {

    private val alpha = TestGames.alpha
    private val beta = TestGames.beta
    private val day = LocalDate.parse("2026-10-31")
    private val key = GameKey(alpha.value, day.toString())

    private val fakes = FakeGames(listOf(alpha, beta))
    private val daily = FakeDailyRepository().apply {
        setToday(alpha, DailyToday.Fresh(TestGames.puzzle(alpha)))
        setToday(beta, DailyToday.Fresh(TestGames.puzzle(beta)))
        setHistory(alpha, listOf(DailyEntry(alpha, day, 7, "alpha/v1", 0L)))
    }

    /** The saved-play rows outlive every store, as the database outlives the process. */
    private val saved = FakeSavedPlayRepository()

    private fun store(ui: GamePlayRegistry = fakes.registry): PlaySessionStore {
        val dispatcher = UnconfinedTestDispatcher()
        val calendar = FakeGameCalendar(day)
        val lane = WriteLane()
        return PlaySessionStore(
            games = fakes.games,
            ui = ui,
            opening = SlotOpening(daily, LoadPastDay(daily, fakes.games), saved, calendar, fakes.games, lane),
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

    @Test
    fun `playable lists every game with a play ui in registry order`() {
        assertEquals(listOf(alpha to "Alpha", beta to "Beta"), store().playable)
        // A plugin without a play registration is not playable.
        assertEquals(listOf(beta to "Beta"), store(GamePlayRegistry(listOf(fakes[beta]))).playable)
    }

    @Test
    fun `an unplayable key opens nothing and never fetches`() {
        val store = store()
        assertNull(store.open(GameKey("not-a-game")))

        val noUi = store(GamePlayRegistry(emptyList()))
        assertNull(noUi.open(key))
        assertNull(noUi.peek(PlaySlot.Daily(alpha, day)))
        assertEquals(0, daily.todayCalls(alpha))
    }

    @Test
    fun `a mid-game play resumes in a new store after process death`() {
        val before = store()
        val play = before.open(key)!!
        fakes[alpha].latest.move()
        fakes[alpha].latest.move()
        assertTrue(before.flush().isCompleted)

        // Process death: a brand-new store (Koin single reborn) over the same rows.
        val resumed = store().open(key)!!

        assertNotSame(play, resumed)
        assertEquals(PlayBoardStatus.Ready(banner = null), resumed.board.value.status)
        assertEquals(2, fakes[alpha].latest.moves)
        assertEquals("resumed from the row, not refetched", 1, daily.todayCalls(alpha))
        assertEquals("the save carries the record identity", "Alpha · #7", resumed.record.value.label)
    }
}
