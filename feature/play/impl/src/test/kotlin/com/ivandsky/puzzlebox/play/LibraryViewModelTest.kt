package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.LoadPastDay
import com.ivandsky.puzzlebox.domain.ObserveArchiveMonth
import com.ivandsky.puzzlebox.domain.ObserveArchiveProgress
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.SyncTodayPuzzles
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The library's state (A25–A29): rows in registry order with today's status, number,
 * archive progress and stats; the first game selected until [LibraryViewModel.select];
 * offline when any today can't load; the lesson opens and closing it marks it seen; the
 * page's calendar month and picked day (A26) survive a month change and reset to today.
 */
class LibraryViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val alpha = TestGames.alpha
    private val beta = TestGames.beta
    private val day = LocalDate.parse("2026-11-04")
    private val fakes = FakeGames(listOf(alpha, beta))
    private val daily = FakeDailyRepository().apply {
        setToday(alpha, DailyToday.Fresh(TestGames.puzzle(alpha)))
        setToday(beta, DailyToday.Fresh(TestGames.puzzle(beta)))
    }
    private val completions = FakeCompletionRepository()
    private val dispatcher = main.testDispatcher
    private val calendar = FakeGameCalendar(day)
    private val saved = FakeSavedPlayRepository()
    private val prefs = FakeUserPreferencesRepository()
    private val lane = WriteLane()
    private val sync = SyncTodayPuzzles(fakes.games, daily, calendar)
    private val store = PlaySessionStore(
        games = fakes.games,
        ui = fakes.registry,
        opening = SlotOpening(daily, LoadPastDay(daily, fakes.games), saved, calendar, fakes.games, lane, FakeReceivedPuzzleRepository()),
        writers = { SlotWriter(it, saved, lane, CoroutineScope(dispatcher)) },
        completions = completions,
        calendar = calendar,
        dispatchers = object : DispatcherProvider {
            override val main = dispatcher
            override val io = dispatcher
            override val default = dispatcher
        },
    )

    /** The collector standing in for the library screen: the state only runs while observed. */
    private val screen = CoroutineScope(dispatcher + Job())

    @After
    fun leaveScreen() {
        screen.cancel()
    }

    private fun observedVm(): LibraryViewModel {
        val history = ObserveGameHistory(fakes.games, daily, completions)
        return LibraryViewModel(
            store = store,
            todaySet = ObserveTodaySet(fakes.games, saved, completions, calendar, daily, sync),
            syncTodayPuzzles = sync,
            stats = ObserveGameStats(fakes.games, history, ComputeStreak(GetPuzzleDay(fakes.games))),
            history = history,
            progress = ObserveArchiveProgress(daily, calendar, history),
            archiveMonth = ObserveArchiveMonth(daily, saved, completions, calendar),
            calendar = calendar,
            games = fakes.games,
            registry = fakes.registry,
            daily = daily,
            prefs = prefs,
        ).also { vm ->
            screen.launch { vm.uiState.collect {} }
            screen.launch { vm.page.collect {} }
            dispatcher.scheduler.runCurrent()
        }
    }

    private fun LibraryViewModel.row(game: GameId) = uiState.value.rows.first { it.card.game == game }

    @Test
    fun `rows follow the registry and the first game is selected`() {
        val vm = observedVm()
        val state = vm.uiState.value
        assertEquals(listOf(alpha, beta), state.rows.map { it.card.game })
        assertEquals(alpha, state.selected)
        assertFalse(state.offline)
        assertEquals(TodayStatus.NotStarted, vm.row(alpha).card.status)

        vm.select(beta)
        dispatcher.scheduler.runCurrent()
        assertEquals(beta, vm.uiState.value.selected)
    }

    @Test
    fun `a solve shows in the row's status and stats`() {
        val vm = observedVm()
        runBlocking { completions.onCompleted(CompletionRecord(TestGames.Ref(alpha, PuzzleKey.Daily(day, 7)), 30_000L, 0, RecordStats(1, "{}"))) }
        dispatcher.scheduler.runCurrent()
        assertEquals(TodayStatus.Solved(30_000L), vm.row(alpha).card.status)
        assertEquals(1, vm.row(alpha).stats.solved)
        assertEquals(0, vm.row(beta).stats.solved)
    }

    @Test
    fun `an archive gives progress, and today's number falls back to its count`() {
        daily.setArchiveRange(alpha, day.minusDays(9)..day)
        val vm = observedVm()
        val row = vm.row(alpha)
        assertNotNull(row.progress)
        assertEquals(10, row.progress!!.total)
        assertEquals(10, row.today)
        assertEquals(10, row.card.puzzleNumber)
        assertNull(vm.row(beta).progress)
    }

    @Test
    fun `a today that can't load marks the library offline`() {
        daily.setToday(beta, DailyToday.Unavailable(FetchError.Unavailable()))
        val vm = observedVm()
        vm.syncToday()
        dispatcher.scheduler.runCurrent()
        assertEquals(TodayStatus.Offline, vm.row(beta).card.status)
        assertTrue(vm.uiState.value.offline)
    }

    @Test
    fun `a lesson opens for a taught game and closing it marks it seen`() {
        fakes[alpha].teach(2)
        val vm = observedVm()
        vm.openLesson(beta)
        assertNull("a game without lessons opens none", vm.lesson.value)

        vm.openLesson(alpha)
        assertEquals(alpha, vm.lesson.value?.game)
        vm.closeLesson()
        dispatcher.scheduler.runCurrent()
        assertNull(vm.lesson.value)
        assertEquals(setOf(alpha), prefs.seen)
    }

    @Test
    fun `the page opens on today's month with today on the day card`() {
        daily.setArchiveRange(alpha, day.minusDays(60)..day)
        val vm = observedVm()
        val page = vm.page.value
        assertNotNull(page)
        assertEquals(alpha, page!!.game)
        assertEquals(YearMonth.from(day), page.month)
        assertEquals(day, page.days.last().date)
        assertNull(page.picked)
    }

    @Test
    fun `a picked day survives a month change until back to today`() {
        daily.setArchiveRange(alpha, day.minusDays(60)..day)
        val vm = observedVm()
        val past = day.minusDays(2)
        vm.pick(past)
        dispatcher.scheduler.runCurrent()
        assertEquals(past, vm.page.value?.picked?.date)

        val earlier = YearMonth.from(day).minusMonths(1)
        vm.showMonth(earlier)
        assertEquals("the asked month leads its days", earlier, vm.shownMonth.value)
        dispatcher.scheduler.runCurrent()
        assertEquals(earlier, vm.page.value?.month)
        assertEquals("the day card keeps its day", past, vm.page.value?.picked?.date)

        vm.backToToday()
        dispatcher.scheduler.runCurrent()
        assertNull(vm.shownMonth.value)
        assertEquals(YearMonth.from(day), vm.page.value?.month)
        assertNull(vm.page.value?.picked)
    }

    @Test
    fun `picking today returns to today and a game switch resets the page`() {
        daily.setArchiveRange(alpha, day.minusDays(60)..day)
        daily.setArchiveRange(beta, day.minusDays(60)..day)
        val vm = observedVm()
        vm.pick(day.minusDays(1))
        vm.pick(day)
        dispatcher.scheduler.runCurrent()
        assertNull(vm.page.value?.picked)

        vm.pick(day.minusDays(40))
        vm.showMonth(YearMonth.from(day).minusMonths(2))
        vm.select(alpha)
        dispatcher.scheduler.runCurrent()
        assertNotNull("re-selecting the open game keeps its day", vm.page.value?.picked)

        vm.select(beta)
        dispatcher.scheduler.runCurrent()
        assertEquals(beta, vm.page.value?.game)
        assertEquals(YearMonth.from(day), vm.page.value?.month)
        assertNull(vm.page.value?.picked)
    }
}
