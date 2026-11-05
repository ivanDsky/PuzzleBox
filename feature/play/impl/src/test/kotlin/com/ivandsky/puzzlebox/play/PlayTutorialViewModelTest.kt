package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.gameui.TutorialState
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeKeptSessionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The play VM's tutorial (spec A11): a first pick of a game with unseen lessons opens
 * them before any play exists, closing marks the game seen and starts it, and How to
 * play opens them over the live play with its clock paused. Nothing is kept or recorded.
 */
class PlayTutorialViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val alpha = TestGames.alpha
    private val beta = TestGames.beta
    private val day = LocalDate.parse("2026-10-31")
    private val fakes = FakeGames(listOf(alpha, beta)).apply { this[alpha].teach(2) }
    private val daily = FakeDailyRepository().apply {
        setToday(alpha, DailyToday.Fresh(TestGames.puzzle(alpha)))
        setToday(beta, DailyToday.Fresh(TestGames.puzzle(beta)))
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

    private fun playVm(prefs: FakeUserPreferencesRepository) = PlayViewModel(
        store,
        ObserveTodaySet(fakes.games, completions, kept, today = { day }),
        fakes.games,
        fakes.registry,
        prefs,
    )

    private fun records() = runBlocking { completions.observeRecords().first() }

    /** A seen alpha, live and ready, with [moves] moves and [elapsedMs] on its clock. */
    private fun livePlay(vm: PlayViewModel, moves: Int, elapsedMs: Long): FakeController {
        vm.pick(alpha) {}
        vm.active!!.load()
        val live = fakes[alpha].latest
        repeat(moves) { live.move() }
        live.advance(elapsedMs)
        return live
    }

    @Test
    fun `a first pick of a game with unseen lessons opens them before any play`() {
        val vm = playVm(FakeUserPreferencesRepository())
        vm.pick(alpha) {}

        val open = vm.state.value as PlayUiState.Tutorial
        assertSame(fakes[alpha], open.ui)
        assertNull(open.over)
        assertEquals(TutorialState.Playing(0, 2, fakes[alpha].tutorial[0]), open.session.state.value)
        assertNull(store.active.value)
        assertEquals(0, daily.todayCalls(alpha))
    }

    @Test
    fun `closing the first-play lessons marks the game seen and starts it`() {
        val prefs = FakeUserPreferencesRepository()
        val vm = playVm(prefs)
        vm.pick(alpha) {}
        (vm.state.value as PlayUiState.Tutorial).session.skip()

        vm.closeTutorial {}

        assertEquals(setOf(alpha), prefs.seen)
        val playing = vm.state.value as PlayUiState.Playing
        assertSame(store.active.value, playing.play)
        assertEquals(alpha, playing.play.gameId)
    }

    @Test
    fun `a seen game and a game without lessons both start at once`() {
        val vm = playVm(FakeUserPreferencesRepository(seen = setOf(alpha)))
        vm.pick(alpha) {}
        assertEquals(alpha, (vm.state.value as PlayUiState.Playing).play.gameId)

        vm.leave()
        vm.pick(beta) {}
        assertEquals(beta, (vm.state.value as PlayUiState.Playing).play.gameId)
    }

    @Test
    fun `how to play opens over the live play and pauses its clock until it closes`() {
        val prefs = FakeUserPreferencesRepository(seen = setOf(alpha))
        val vm = playVm(prefs)
        val live = livePlay(vm, moves = 2, elapsedMs = 5_000L)
        val play = store.active.value!!

        vm.howToPlay()

        val open = vm.state.value as PlayUiState.Tutorial
        assertSame(play, open.over)
        assertNull(live.clockWindow)
        live.advance(10_000L)
        assertEquals(5_000L, live.displayedElapsedMs())

        open.session.skip()
        vm.closeTutorial {}

        val playing = vm.state.value as PlayUiState.Playing
        assertSame(play, playing.play)
        assertSame(live, playing.controller)
        assertNotNull(live.clockWindow)
        live.advance(1_000L)
        assertEquals(6_000L, live.displayedElapsedMs())
        assertEquals(2, live.moves)
        assertTrue(kept.current.isEmpty())
        assertTrue(records().isEmpty())
    }

    @Test
    fun `how to play does nothing for a game without lessons or without a play`() {
        val vm = playVm(FakeUserPreferencesRepository())
        vm.howToPlay()
        assertTrue(vm.state.value is PlayUiState.Picker)

        vm.pick(beta) {}
        vm.active!!.load()
        vm.howToPlay()
        assertTrue(vm.state.value is PlayUiState.Playing)
    }

    @Test
    fun `back during the lessons leaves them unseen and shows the picker`() {
        val prefs = FakeUserPreferencesRepository()
        val vm = playVm(prefs)
        vm.pick(alpha) {}

        vm.leave()

        assertTrue(vm.state.value is PlayUiState.Picker)
        assertTrue(prefs.seen.isEmpty())
        vm.pick(alpha) {}
        assertTrue(vm.state.value is PlayUiState.Tutorial)
    }

    @Test
    fun `a replay of another game arriving closes the lessons over the old play`() {
        val vm = playVm(FakeUserPreferencesRepository(seen = setOf(alpha)))
        livePlay(vm, moves = 1, elapsedMs = 0L)
        vm.howToPlay()

        vm.restore(FakeController.envelope(beta, moves = 0, day = day)) {}

        val playing = vm.state.value as PlayUiState.Playing
        assertEquals(beta, playing.play.gameId)
    }

    @Test
    fun `a rotation restore of the same play keeps the lessons and their step`() {
        val vm = playVm(FakeUserPreferencesRepository(seen = setOf(alpha)))
        livePlay(vm, moves = 1, elapsedMs = 0L)
        vm.howToPlay()
        val open = vm.state.value as PlayUiState.Tutorial
        open.session.advance()

        vm.restore(FakeController.envelope(alpha, moves = 1, day = day)) {}

        val after = vm.state.value as PlayUiState.Tutorial
        assertSame(open.session, after.session)
        assertEquals(1, (after.session.state.value as TutorialState.Playing).index)
    }
}
