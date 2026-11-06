package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.domain.LoadPastDay
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.gameui.RecordVerdict
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
 * this solve) it shows the play's A10 verdict, previous best, label and write retry; once
 * the live play replays or re-solves it keeps the verdict it saw and hides retry; cold
 * (after process death) the verdict is unknown — the session-start baseline died with the
 * play. The primary action
 * is Next (the next unsolved playable daily) or AllDone on today's slot only.
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
    private val todaySet = ObserveTodaySet(fakes.games, saved, completions, calendar)

    /** Collectors standing in for the screen: [ResultViewModel.primary] only runs while observed. */
    private val screen = CoroutineScope(dispatcher + Job())

    @After
    fun leaveScreen() {
        screen.cancel()
    }

    private fun store(games: GameRegistry, ui: GamePlayRegistry) = PlaySessionStore(
        games = games,
        ui = ui,
        opening = SlotOpening(daily, LoadPastDay(daily, games), saved, calendar, games, lane),
        writers = { SlotWriter(it, saved, lane, CoroutineScope(dispatcher)) },
        completions = completions,
        calendar = calendar,
        dispatchers = object : DispatcherProvider {
            override val main = dispatcher
            override val io = dispatcher
            override val default = dispatcher
        },
    )

    private fun vm(key: ResultKey = this.key) = ResultViewModel(key, store, todaySet, calendar)

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
    fun `live result shows the play's verdict and label`() {
        val play = solveLive()
        val state = vm().state.value
        assertEquals(RecordVerdict.FirstSolve, state.verdict)
        assertNull(state.previousBest)
        assertFalse(state.writeFailed)
        assertEquals(play.record.value.label, state.label)
    }

    @Test
    fun `live result carries the best this solve beat`() {
        val slower = FakeController.TIME_MS + 10_000
        completions.setRecords(listOf(record(slower, 0, date = day.minusDays(1))))
        solveLive()
        val state = vm().state.value
        assertEquals(RecordVerdict.NewBest, state.verdict)
        assertEquals(BestScore(slower, hints = 0), state.previousBest)
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
        assertEquals(RecordVerdict.FirstSolve, vm.state.value.verdict)
    }

    @Test
    fun `a live play holding another solve never speaks for this result`() {
        val play = solveLive()
        val other = vm(key.copy(timeMs = FakeController.TIME_MS + 1))
        assertEquals(ResultUiState(label = play.record.value.label), other.state.value)
    }

    @Test
    fun `a replay of the live play keeps this result's own verdict`() {
        val play = solveLive()
        val vm = vm()
        assertEquals(RecordVerdict.FirstSolve, vm.state.value.verdict)

        play.replay()
        dispatcher.scheduler.runCurrent()
        // The replay's in-flight state (no verdict yet) is not this result's.
        assertNull(play.record.value.verdict)
        assertEquals(ResultUiState(verdict = RecordVerdict.FirstSolve, label = play.record.value.label), vm.state.value)
    }

    @Test
    fun `cold result claims no verdict even with a matching record`() {
        completions.setRecords(listOf(record(FakeController.TIME_MS, 0)))
        assertEquals(ResultUiState(), vm().state.value)
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
            ObserveTodaySet(three.games, saved, completions, calendar),
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
