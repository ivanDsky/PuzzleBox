package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.domain.GetReplaySession
import com.ivandsky.puzzlebox.domain.ObservePlayProgress
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.ResultPrimaryAction
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The result VM for a [ResultKey]'s slot: live (the slot's play in the store, holding
 * this solve) it shows the play's badge, label and write retry; cold (after process
 * death, or once the live play replays or re-solves) the badge is a (time, hints) match
 * against the slot's completion records and retry is hidden. The primary action is Next
 * (the next unsolved playable daily) or AllDone on today's slot only.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ResultViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val alpha = TestGames.alpha
    private val beta = TestGames.beta
    private val gamma = GameId("gamma")
    private val day = LocalDate.parse("2026-10-31")
    private val key = ResultKey(alpha.value, day.toString(), FakeController.TIME_MS, 0)
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
    private val store = store(fakes.games, fakes.registry)
    private val progress = ObservePlayProgress(fakes.games, saved, completions, calendar)

    /** Collectors standing in for the screen: [ResultViewModel.primary] only runs while observed. */
    private val screen = CoroutineScope(dispatcher + Job())

    @After
    fun leaveScreen() {
        screen.cancel()
    }

    private fun store(games: GameRegistry, ui: GamePlayRegistry) = PlaySessionStore(
        games = games,
        ui = ui,
        opening = SlotOpening(daily, GetReplaySession(daily, games), saved, calendar, games, lane),
        writers = { SlotWriter(it, saved, lane, CoroutineScope(dispatcher)) },
        completions = completions,
        calendar = calendar,
        dispatchers = object : DispatcherProvider {
            override val main = dispatcher
            override val io = dispatcher
            override val default = dispatcher
        },
    )

    private fun vm(key: ResultKey = this.key) = ResultViewModel(key, store, completions, progress, fakes.games, calendar)

    private fun ResultViewModel.observed() = apply {
        screen.launch { primary.collect {} }
        dispatcher.scheduler.runCurrent()
    }

    private fun solveLive(): ActivePlay {
        val play = store.open(GameKey(alpha.value, day.toString()))!!
        dispatcher.scheduler.runCurrent()
        fakes[alpha].latest.win()
        dispatcher.scheduler.runCurrent()
        return play
    }

    private fun record(time: Long, hints: Int, date: LocalDate = day, game: GameId = alpha) =
        CompletionRecord(TestGames.Ref(game, date, 7), time, hints, RecordStats(1, "{}"), isRecord = true)

    @Test
    fun `live result shows the play's badge and label`() {
        val play = solveLive()
        val state = vm().state.value
        assertEquals(true, state.isRecord)
        assertFalse(state.writeFailed)
        assertEquals(play.record.value.label, state.label)
    }

    @Test
    fun `live result surfaces a failed write and retries it`() {
        completions.fail = true
        solveLive()
        val vm = vm()
        assertTrue(vm.state.value.writeFailed)

        completions.fail = false
        vm.retryRecordWrite()
        dispatcher.scheduler.runCurrent()
        assertFalse(vm.state.value.writeFailed)
        assertEquals(true, vm.state.value.isRecord)
    }

    @Test
    fun `a live play holding another solve never speaks for this result`() {
        val play = solveLive()
        val other = vm(key.copy(timeMs = FakeController.TIME_MS + 1))
        assertEquals(ResultUiState(isRecord = null, writeFailed = false, label = play.record.value.label), other.state.value)
    }

    @Test
    fun `a replay of the live play keeps this result's verdict from the records`() {
        val play = solveLive()
        val vm = vm()
        assertEquals(true, vm.state.value.isRecord)

        play.replay()
        dispatcher.scheduler.runCurrent()

        // The replay's in-flight state (no badge yet) is not this result's; the record row is.
        assertEquals(true, vm.state.value.isRecord)
        assertFalse(vm.state.value.writeFailed)
    }

    @Test
    fun `cold result with a matching record shows the badge`() {
        completions.setRecords(listOf(record(FakeController.TIME_MS, 0)))
        assertEquals(ResultUiState(isRecord = true, writeFailed = false, label = null), vm().state.value)
    }

    @Test
    fun `cold result without a matching record claims no badge`() {
        completions.setRecords(
            listOf(
                record(FakeController.TIME_MS + 1, 0),
                record(FakeController.TIME_MS, 1),
                record(FakeController.TIME_MS, 0, date = day.minusDays(1)),
            ),
        )
        assertNull(vm().state.value.isRecord)
    }

    @Test
    fun `cold result hides the write retry`() {
        completions.fail = true
        val vm = vm()
        vm.retryRecordWrite()
        assertEquals(ResultUiState(), vm.state.value)
    }

    @Test
    fun `today's result offers the next unsolved game`() {
        val vm = vm().observed()
        assertEquals(ResultPrimaryAction.Next(beta, fakes.games.displayName(beta)), vm.primary.value)
    }

    @Test
    fun `today's result with every other game solved is all done`() {
        completions.setRecords(listOf(record(FakeController.TIME_MS, 0, game = beta)))
        val vm = vm().observed()
        assertEquals(ResultPrimaryAction.AllDone, vm.primary.value)
    }

    @Test
    fun `Next skips a registered game the store cannot play`() {
        // beta has a plugin but no play UI: the hub never lists it, so Next must not name it.
        val three = FakeGames(listOf(alpha, beta, gamma))
        val playable = GamePlayRegistry(listOf(three[alpha], three[gamma]))
        val vm = ResultViewModel(
            key,
            store(three.games, playable),
            completions,
            ObservePlayProgress(three.games, saved, completions, calendar),
            three.games,
            calendar,
        ).observed()
        assertEquals(ResultPrimaryAction.Next(gamma, three.games.displayName(gamma)), vm.primary.value)
    }

    @Test
    fun `a past day's result offers done, never next`() {
        val vm = vm(key.copy(day = day.minusDays(1).toString())).observed()
        assertEquals(ResultPrimaryAction.Done, vm.primary.value)
    }
}
