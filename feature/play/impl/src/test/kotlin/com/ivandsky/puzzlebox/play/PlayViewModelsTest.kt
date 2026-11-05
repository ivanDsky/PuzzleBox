package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.domain.GetReplaySession
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The route VMs are thin views over the [PlaySessionStore]'s live play. */
class PlayViewModelsTest {

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
    fun `a fresh entry is the picker with every playable game`() {
        val vm = PlayViewModel(store)
        assertEquals(PlayUiState.Picker(listOf(alpha to "Alpha", beta to "Beta")), vm.state.value)
    }

    @Test
    fun `picking starts the game in place and the board follows the fetch`() {
        val vm = PlayViewModel(store)
        vm.pick(alpha) {}

        val loading = vm.state.value as PlayUiState.Playing
        assertSame(fakes[alpha], loading.ui)
        assertEquals(PlayBoardStatus.Loading, loading.status)

        vm.active!!.load()
        val ready = vm.state.value as PlayUiState.Playing
        assertEquals(PlayBoardStatus.Ready(banner = null), ready.status)
        assertSame(fakes[alpha].latest, ready.controller)
    }

    @Test
    fun `an unavailable fetch reaches the screen and retry refetches`() {
        daily.setToday(alpha, DailyToday.Unavailable(FetchError.Unavailable("offline")))
        val vm = PlayViewModel(store)
        vm.pick(alpha) {}
        vm.active!!.load()
        assertTrue((vm.state.value as PlayUiState.Playing).status is PlayBoardStatus.Unavailable)

        daily.setToday(alpha, DailyToday.Fresh(TestGames.puzzle(alpha)))
        (vm.state.value as PlayUiState.Playing).play.load()
        assertEquals(PlayBoardStatus.Ready(banner = null), (vm.state.value as PlayUiState.Playing).status)
    }

    @Test
    fun `a VM created over a live play starts on it`() {
        store.start(alpha) {}!!.load()
        val state = PlayViewModel(store).state.value as PlayUiState.Playing
        assertEquals(PlayBoardStatus.Ready(banner = null), state.status)
    }

    @Test
    fun `end returns to the picker`() {
        val vm = PlayViewModel(store)
        vm.pick(alpha) {}
        vm.end()
        assertTrue(vm.state.value is PlayUiState.Picker)
    }

    @Test
    fun `restore names its game`() {
        val vm = PlayViewModel(store)
        vm.restore(FakeController.envelope(alpha, moves = 4, day = day)) {}
        val state = vm.state.value as PlayUiState.Playing
        assertEquals(alpha, state.play.gameId)
        assertEquals(4, fakes[alpha].latest.moves)
    }

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
