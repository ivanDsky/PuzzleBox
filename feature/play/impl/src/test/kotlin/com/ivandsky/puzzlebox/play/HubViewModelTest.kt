package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.LoadPastDay
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.SyncTodayPuzzles
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.gameui.BoardPreview
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * The hub's per-game state: each row's best under its own ranking and its Up-next line,
 * and the board thumbnails ([HubUiState.previews]): each game's own preview model of
 * today — an unfinished play's newest session (a live play's change still waiting to be
 * saved beats the older row on disk), the cached daily fresh when not started or solved
 * when solved, none when offline or unreadable — rebuilt when a new save lands.
 */
class HubViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val alpha = TestGames.alpha
    private val beta = TestGames.beta
    private val day = LocalDate.parse("2026-11-04")
    // beta ranks its bests hints first, as Strands does; alpha keeps the time-first default.
    private val fakes = FakeGames(listOf(alpha, beta), hintsRanked = setOf(beta))
    private val daily = FakeDailyRepository().apply {
        setToday(alpha, DailyToday.Fresh(TestGames.puzzle(alpha)))
        setToday(beta, DailyToday.Fresh(TestGames.puzzle(beta)))
    }
    private val completions = FakeCompletionRepository()
    private val dispatcher = main.testDispatcher
    private val calendar = FakeGameCalendar(day)
    private val saved = FakeSavedPlayRepository()
    private val lane = WriteLane()
    private val sync = SyncTodayPuzzles(fakes.games, daily, calendar)
    private val store = PlaySessionStore(
        games = fakes.games,
        ui = fakes.registry,
        opening = SlotOpening(daily, LoadPastDay(daily, fakes.games), saved, calendar, fakes.games, lane),
        writers = { SlotWriter(it, saved, lane, CoroutineScope(dispatcher)) },
        completions = completions,
        calendar = calendar,
        dispatchers = object : DispatcherProvider {
            override val main = dispatcher
            override val io = dispatcher
            override val default = dispatcher
        },
    )

    /** The collector standing in for the hub screen: the state only runs while observed. */
    private val screen = CoroutineScope(dispatcher + Job())

    @After
    fun leaveScreen() {
        screen.cancel()
    }

    private fun observedVm(): HubViewModel {
        val history = ObserveGameHistory(fakes.games, daily, completions)
        return HubViewModel(
            store = store,
            todaySet = ObserveTodaySet(fakes.games, saved, completions, calendar, daily, sync),
            syncTodayPuzzles = sync,
            stats = ObserveGameStats(fakes.games, history, ComputeStreak(GetPuzzleDay(fakes.games))),
            history = history,
            calendar = calendar,
            games = fakes.games,
            registry = fakes.registry,
            daily = daily,
            previewDispatcher = dispatcher,
        ).also { vm ->
            screen.launch { vm.uiState.collect {} }
            dispatcher.scheduler.runCurrent()
        }
    }

    private val HubViewModel.previews get() = uiState.value.previews

    private fun save(game: GameId, moves: Int, elapsedMs: Long) = runBlocking {
        val session = FakeController.envelope(game, moves = moves, day = day).session
        saved.save(PlaySlot(game, day), SessionBlob.encode(game, session), elapsedMs)
    }

    @Test
    fun `a hints-ranked game's row carries its fewest-hints best, and the next row its Up-next line`() {
        daily.setCached(alpha, day, TestGames.puzzle(alpha, tag = 4))
        completions.setRecords(
            listOf(
                CompletionRecord(TestGames.Ref(beta, day.minusDays(1), 6), 10_000L, 2, RecordStats(1, "{}"), isRecord = true),
                CompletionRecord(TestGames.Ref(beta, day.minusDays(2), 5), 90_000L, 0, RecordStats(1, "{}"), isRecord = true),
            ),
        )

        val state = observedVm().uiState.value
        val rows = state.rows.associateBy { it.game }

        assertEquals(RankOrder.HintsThenTime, rows.getValue(beta).rankOrder)
        assertEquals(BestScore(timeMs = 90_000L, hints = 0), rows.getValue(beta).best)
        assertEquals(RankOrder.TimeThenHints, rows.getValue(alpha).rankOrder)
        assertEquals(alpha, state.next)
        val line = fakes[alpha].hubLine(BoardPreview.Fresh(TestGames.puzzle(alpha, tag = 4)))
        assertNotNull(line)
        assertEquals(line, rows.getValue(alpha).line)
        assertEquals(HubHero.Next(alpha), state.plan.hero)
    }

    @Test
    fun `a game solved today has no Up-next line`() {
        daily.setCached(alpha, day, TestGames.puzzle(alpha, tag = 6))
        runBlocking { completions.onCompleted(CompletionRecord(TestGames.Ref(alpha, day, 7), 30_000L, 0, RecordStats(1, "{}"))) }

        val rows = observedVm().uiState.value.rows.associateBy { it.game }

        assertEquals(TodayStatus.Solved(30_000L), rows.getValue(alpha).status)
        assertNull(rows.getValue(alpha).line)
    }

    @Test
    fun `an unfinished play previews its newest moves, even before they reach disk`() {
        save(alpha, moves = 1, elapsedMs = 1_000L)
        store.open(GameKey(alpha.value))!!
        dispatcher.scheduler.runCurrent()
        repeat(2) { fakes[alpha].latest.move() }

        val vm = observedVm()

        assertEquals(TodayStatus.InProgress(1_000L), vm.uiState.value.rows.first { it.game == alpha }.status)
        assertEquals(FakePreview("moves 3"), vm.previews[alpha])
    }

    @Test
    fun `an unfinished play's preview is rebuilt when a new save lands`() {
        save(alpha, moves = 1, elapsedMs = 1_000L)
        val vm = observedVm()
        assertEquals(FakePreview("moves 1"), vm.previews[alpha])

        save(alpha, moves = 4, elapsedMs = 2_000L)
        dispatcher.scheduler.runCurrent()

        assertEquals(FakePreview("moves 4"), vm.previews[alpha])
    }

    @Test
    fun `a game not started previews today's cached puzzle fresh, and nothing when uncached`() {
        daily.setCached(alpha, day, TestGames.puzzle(alpha, tag = 4))

        val vm = observedVm()

        assertEquals(FakePreview("fresh 4"), vm.previews[alpha])
        assertNull(vm.previews[beta])
    }

    @Test
    fun `a game solved today previews today's cached puzzle solved`() {
        daily.setCached(alpha, day, TestGames.puzzle(alpha, tag = 6))
        runBlocking { completions.onCompleted(CompletionRecord(TestGames.Ref(alpha, day, 7), 30_000L, 0, RecordStats(1, "{}"))) }

        val vm = observedVm()

        assertEquals(TodayStatus.Solved(30_000L), vm.uiState.value.rows.first { it.game == alpha }.status)
        assertEquals(FakePreview("solved 6"), vm.previews[alpha])
    }

    @Test
    fun `an offline game has no preview`() {
        daily.setToday(beta, DailyToday.Unavailable(FetchError.Unavailable()))
        val vm = observedVm()

        vm.syncToday()
        dispatcher.scheduler.runCurrent()

        assertEquals(TodayStatus.Offline, vm.uiState.value.rows.first { it.game == beta }.status)
        assertNull(vm.previews[beta])
    }

    @Test
    fun `a save the game can't read has no preview`() {
        runBlocking { saved.save(PlaySlot(alpha, day), SessionBlob.encode(beta, "fake|2|false|$day|7|0".encodeToByteArray()), 1_000L) }

        val vm = observedVm()

        assertEquals(TodayStatus.InProgress(1_000L), vm.uiState.value.rows.first { it.game == alpha }.status)
        assertNull(vm.previews[alpha])
    }
}
