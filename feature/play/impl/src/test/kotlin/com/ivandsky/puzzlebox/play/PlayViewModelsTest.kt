package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.data.repository.KeptSession
import com.ivandsky.puzzlebox.data.repository.KeptSessionRepository
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.RecordVerdict
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeKeptSessionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    private val kept = FakeKeptSessionRepository()
    private val store = PlaySessionStore(
        games = fakes.games,
        ui = fakes.registry,
        daily = daily,
        completions = completions,
        kept = kept,
        today = { day },
        scopeFactory = { CoroutineScope(Job() + main.testDispatcher) },
        keepScope = CoroutineScope(Job() + main.testDispatcher),
    )

    private fun playVm(todaySet: ObserveTodaySet = ObserveTodaySet(fakes.games, completions, kept, today = { day })) =
        PlayViewModel(store, todaySet, fakes.games, fakes.registry, FakeUserPreferencesRepository())

    private fun row(game: GameId, name: String, status: TodayStatus?) = PlayUiState.Picker.Row(game, name, status)

    @Test
    fun `a fresh entry is the picker with today's set of every registered game`() {
        val vm = playVm()
        assertEquals(
            PlayUiState.Picker(listOf(row(alpha, "Alpha", TodayStatus.NotStarted), row(beta, "Beta", TodayStatus.NotStarted))),
            vm.state.value,
        )
    }

    @Test
    fun `before today's set emits the picker already lists every game, without a status`() {
        // Kept sessions still loading (DataStore on IO at a cold start).
        val loading = object : KeptSessionRepository {
            override val kept: Flow<Map<GameId, KeptSession>> = flow { awaitCancellation() }
            override suspend fun keep(session: KeptSession) = Unit
            override suspend fun discard(game: GameId) = Unit
        }
        val vm = playVm(ObserveTodaySet(fakes.games, completions, loading, today = { day }))
        assertEquals(PlayUiState.Picker(listOf(row(alpha, "Alpha", null), row(beta, "Beta", null))), vm.state.value)
    }

    @Test
    fun `picking starts the game in place and the board follows the fetch`() {
        val vm = playVm()
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
        val vm = playVm()
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
        val state = playVm().state.value as PlayUiState.Playing
        assertEquals(PlayBoardStatus.Ready(banner = null), state.status)
    }

    @Test
    fun `leave keeps the session, returns to the picker and its row reads in progress`() {
        val vm = playVm()
        vm.pick(alpha) {}
        vm.active!!.load()
        fakes[alpha].latest.move()
        fakes[alpha].latest.advance(42_000)

        vm.leave()

        assertEquals(
            PlayUiState.Picker(
                listOf(row(alpha, "Alpha", TodayStatus.InProgress(42_000)), row(beta, "Beta", TodayStatus.NotStarted)),
            ),
            vm.state.value,
        )
    }

    @Test
    fun `picking the kept game resumes it ready, never fetching`() {
        val vm = playVm()
        vm.pick(alpha) {}
        vm.active!!.load()
        fakes[alpha].latest.move()
        fakes[alpha].latest.advance(1_000)
        vm.leave()
        val fetches = daily.todayCalls(alpha)

        vm.pick(alpha) {}

        val resumed = vm.state.value as PlayUiState.Playing
        assertEquals(PlayBoardStatus.Ready(banner = null), resumed.status)
        assertFalse(resumed.play.needsLoad)
        assertEquals(1, fakes[alpha].latest.moves)
        assertEquals(fetches, daily.todayCalls(alpha))
    }

    @Test
    fun `background keeps the live session and the play stays live`() {
        val vm = playVm()
        vm.pick(alpha) {}
        vm.active!!.load()
        fakes[alpha].latest.move()
        fakes[alpha].latest.advance(5_000)

        vm.background()

        assertEquals(5_000L, kept.current.getValue(alpha).elapsedMs)
        assertTrue(vm.state.value is PlayUiState.Playing)
    }

    @Test
    fun `restore names its game`() {
        val vm = playVm()
        vm.restore(FakeController.envelope(alpha, moves = 4, day = day)) {}
        val state = vm.state.value as PlayUiState.Playing
        assertEquals(alpha, state.play.gameId)
        assertEquals(4, fakes[alpha].latest.moves)
    }

    @Test
    fun `result without a live play shows only the frozen facts`() {
        assertEquals(ResultUiState(verdict = null, writeFailed = false, label = null), ResultViewModel(store).state.value)
    }

    @Test
    fun `result reads the live verdict and label and replays the same puzzle`() {
        val vm = ResultViewModel(store)
        store.start(alpha) {}!!.load()
        fakes[alpha].latest.win()
        assertEquals(ResultUiState(verdict = RecordVerdict.FirstSolve, writeFailed = false, label = "Alpha · #7"), vm.state.value)

        vm.replay()
        assertEquals(1, fakes[alpha].latest.replays)
        assertEquals(ResultUiState(verdict = null, writeFailed = false, label = "Alpha · #7"), vm.state.value)

        fakes[alpha].latest.win()
        assertEquals(
            ResultUiState(
                verdict = RecordVerdict.Replay,
                previousBestMs = FakeController.TIME_MS,
                writeFailed = false,
                label = "Alpha · #7",
            ),
            vm.state.value,
        )
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
        assertEquals(ResultUiState(verdict = RecordVerdict.FirstSolve, writeFailed = false, label = "Alpha · #7"), vm.state.value)
    }
}
