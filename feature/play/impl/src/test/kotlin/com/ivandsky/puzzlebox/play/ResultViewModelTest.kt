package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.domain.GetReplaySession
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The result VM is a thin view over the [PlaySessionStore]'s live play. */
class ResultViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val alpha = TestGames.alpha
    private val beta = TestGames.beta
    private val day = LocalDate.parse("2026-10-31")
    private val fakes = FakeGames(listOf(alpha, beta))
    private val daily = FakeDailyRepository().apply {
        setToday(alpha, DailyToday.Fresh(TestGames.puzzle(alpha)))
        setHistory(alpha, listOf(DailyEntry(alpha, day, 7, "alpha/v1", 0L)))
    }
    private val completions = FakeCompletionRepository()
    private val dispatcher = main.testDispatcher
    private val calendar = FakeGameCalendar(day)
    private val saved = FakeSavedPlayRepository()
    private val lane = WriteLane()
    private val store = PlaySessionStore(
        games = fakes.games,
        ui = fakes.registry,
        opening = SlotOpening(daily, GetReplaySession(daily, fakes.games), saved, calendar, fakes.games, lane),
        writers = { SlotWriter(it, saved, lane, CoroutineScope(dispatcher)) },
        completions = completions,
        calendar = calendar,
        dispatchers = object : DispatcherProvider {
            override val main = dispatcher
            override val io = dispatcher
            override val default = dispatcher
        },
    )

    @Test
    fun `result without a live play shows only the frozen facts`() {
        assertEquals(ResultUiState(isRecord = null, writeFailed = false, label = null), ResultViewModel(store).state.value)
    }

    @Test
    fun `result reads the live verdict and label and replays the same puzzle`() {
        val vm = ResultViewModel(store)
        store.start(alpha) {}!!.load()
        fakes[alpha].latest.win()
        assertEquals(ResultUiState(isRecord = true, writeFailed = false, label = "Alpha · #7"), vm.state.value)

        vm.replay()
        assertEquals(1, fakes[alpha].latest.replays)
        assertEquals(ResultUiState(isRecord = null, writeFailed = false, label = "Alpha · #7"), vm.state.value)
    }

    @Test
    fun `result surfaces a failed write and retries it`() {
        completions.fail = true
        val vm = ResultViewModel(store)
        store.start(alpha) {}!!.load()
        fakes[alpha].latest.win()
        assertTrue(vm.state.value.writeFailed)

        completions.fail = false
        vm.retryRecordWrite()
        assertEquals(ResultUiState(isRecord = true, writeFailed = false, label = "Alpha · #7"), vm.state.value)
    }
}
