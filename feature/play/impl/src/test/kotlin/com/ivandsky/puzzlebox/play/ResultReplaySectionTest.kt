package com.ivandsky.puzzlebox.play

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.domain.FinishLeg
import com.ivandsky.puzzlebox.domain.GetNextRollover
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.LoadPastDay
import com.ivandsky.puzzlebox.domain.LoadSharedPuzzle
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveLeaderboard
import com.ivandsky.puzzlebox.domain.ObserveResultInsights
import com.ivandsky.puzzlebox.domain.ObserveResultRank
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.SyncTodayPuzzles
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.CompletionSource
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.replay.ReplayFrame
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.replay.GameReplayBoard
import com.ivandsky.puzzlebox.gameui.replay.ReplayBoardOptions
import com.ivandsky.puzzlebox.gameui.replay.ReplaySectionState
import com.ivandsky.puzzlebox.gameui.replay.ReplayUnavailable
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.SolveReplay
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import com.ivandsky.puzzlebox.play.friend.signedOutBoard
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.replay.ReplayFixtures
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchResultOutbox
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The own result's replay section (ticket 48): it follows the stored completion, never the live play. Hidden while the
 * write is pending or failed, Ready once the row with this solve's time and hints lands with a replay, Unavailable for a
 * stored solve without one. A cold result (the row is already stored) is Ready at once.
 */
class ResultReplaySectionTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val game = ReplayFixtures.GAME
    private val day = LocalDate.parse("2026-10-31")
    private val ref = TestGames.Ref(game, PuzzleKey.Daily(day, 7))
    private val games = replayCapableGames()
    private val daily = FakeDailyRepository().apply {
        setHistory(game, listOf(DailyEntry(game, day, 7, "fixture/v1", 0L)))
        setCached(game, day, TestGames.puzzle(game))
        setToday(game, DailyToday.Fresh(TestGames.puzzle(game)))
    }
    private val completions = FakeCompletionRepository()
    private val saved = FakeSavedPlayRepository()
    private val dispatcher = main.testDispatcher
    private val calendar = FakeGameCalendar(day)
    private val clock = Clock.fixed(day.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val replay: SolveReplay = ReplayFixtures.sealed(ReplayFixtures.SHORT_MOVES)

    /** Stands in for the screen: the state only runs while observed. */
    private val screen = CoroutineScope(dispatcher + Job())

    @After
    fun leaveScreen() {
        screen.cancel()
    }

    private val fakeUi = FakePlayUi(game)
    private val uis = GamePlayRegistry(listOf(fakeUi))
    private val history = ObserveGameHistory(games, daily, completions)
    private val lane = WriteLane()
    private val store = PlaySessionStore(
        games = games,
        ui = uis,
        opening = SlotOpening(
            daily, LoadPastDay(daily, games), saved, calendar, games, lane, FakeReceivedPuzzleRepository(),
            FakeMatchRepository(),
            LoadSharedPuzzle(games, FakeSharedPuzzleRepository(), FakeReceivedPuzzleRepository(), { Budget.Unlimited }, { Instant.EPOCH }),
            testDispatchers(dispatcher),
        ),
        writers = { SlotWriter(it, saved, lane, CoroutineScope(dispatcher)) },
        completions = completions,
        finishLeg = FinishLeg(FakeMatchResultOutbox(), FakeIdentityService()) { Instant.EPOCH },
        calendar = calendar,
        dispatchers = testDispatchers(dispatcher),
    )

    private fun vm(
        timeMs: Long = TIME_MS,
        board: GameReplayBoard? = ReplayBoard,
    ): ResultViewModel {
        fakeUi.replayBoard = board
        return ResultViewModel(
            key = ResultKey(game.value, day.toString(), timeMs, 0),
            store = store,
            todaySet = ObserveTodaySet(games, saved, completions, calendar, daily, SyncTodayPuzzles(games, daily, calendar)),
            calendar = calendar,
            history = history,
            rollover = GetNextRollover(games, clock),
            ui = uis,
            games = games,
            insights = ObserveResultInsights(games, history, ComputeStreak(GetPuzzleDay(games, clock)), GetPuzzleDay(games, clock)),
            rank = ObserveResultRank(ObserveLeaderboard(FakeIdentityService(), FakeLeaderboardRepository(), games, calendar), games),
            linkedIn = null,
            board = signedOutBoard(),
            completions = completions,
            loadReplay = loadSolveReplay(games, daily, completions),
        ).also { viewModel ->
            screen.launch { viewModel.state.collect {} }
            dispatcher.scheduler.runCurrent()
        }
    }

    /** A live win of the slot, as the play screen leaves it: the result opens on this play. */
    private fun solveLive(): ActivePlay {
        val play = store.open(GameKey(game.value, day.toString()))!!
        dispatcher.scheduler.runCurrent()
        fakeUi.latest.win()
        dispatcher.scheduler.runCurrent()
        return play
    }

    private fun solve(timeMs: Long = TIME_MS, source: CompletionSource = CompletionSource.App) =
        CompletionRecord(ref, timeMs, 0, RecordStats(1, "{}"), isRecord = true, source = source)

    private val ResultViewModel.replaySection get() = state.value.replay

    @Test
    fun `hidden before this solve is stored`() {
        assertEquals(ReplaySectionState.Hidden, vm().replaySection)
    }

    @Test
    fun `hidden while the write is pending, ready once it lands`() {
        val gate = CompletableDeferred<Unit>()
        completions.writeGate = gate
        val viewModel = vm()
        screen.launch { completions.onCompleted(solve(), replay) }
        dispatcher.scheduler.runCurrent()
        assertEquals(ReplaySectionState.Hidden, viewModel.replaySection)

        gate.complete(Unit)
        dispatcher.scheduler.runCurrent()

        val ready = viewModel.replaySection as ReplaySectionState.Ready
        assertEquals(ReplayFixtures.SHORT_MOVES, ready.timeline.moveTimesMs.size)
    }

    @Test
    fun `hidden after a failed write`() {
        completions.fail = true
        val viewModel = vm()

        runCatching { kotlinx.coroutines.runBlocking { completions.onCompleted(solve(), replay) } }
        dispatcher.scheduler.runCurrent()

        assertEquals(ReplaySectionState.Hidden, viewModel.replaySection)
    }

    @Test
    fun `a cold result with its row already stored is ready`() {
        kotlinx.coroutines.runBlocking { completions.onCompleted(solve(), replay) }

        assertTrue(vm().replaySection is ReplaySectionState.Ready)
    }

    @Test
    fun `a stored solve without a replay says there is none`() {
        kotlinx.coroutines.runBlocking { completions.onCompleted(solve(), replay = null) }

        assertEquals(ReplaySectionState.Unavailable(ReplayUnavailable.NotRecorded), vm().replaySection)
    }

    @Test
    fun `a stored LinkedIn import says it was imported`() {
        kotlinx.coroutines.runBlocking { completions.onCompleted(solve(source = CompletionSource.LinkedIn), replay = null) }

        assertEquals(ReplaySectionState.Unavailable(ReplayUnavailable.ImportedFromLinkedIn), vm().replaySection)
    }

    @Test
    fun `another solve of the puzzle does not open this result's replay`() {
        kotlinx.coroutines.runBlocking { completions.onCompleted(solve(timeMs = TIME_MS + 1), replay) }

        assertEquals(ReplaySectionState.Hidden, vm().replaySection)
    }

    @Test
    fun `a live solve stays hidden until its write returns, whatever an older row with the same time and hints says`() {
        kotlinx.coroutines.runBlocking { completions.onCompleted(solve(), replay) }
        val gate = CompletableDeferred<Unit>()
        completions.writeGate = gate
        val play = solveLive()

        val viewModel = vm()

        assertEquals(ReplaySectionState.Hidden, viewModel.replaySection)
        assertFalse(play.record.value.stored)

        gate.complete(Unit)
        dispatcher.scheduler.runCurrent()

        // The play's own write carries no replay (the fake game seals none): once stored it is the newest solve, so
        // the older row's replay is not this solve's.
        assertTrue(play.record.value.stored)
        assertEquals(ReplaySectionState.Unavailable(ReplayUnavailable.NotRecorded), viewModel.replaySection)
    }

    @Test
    fun `a game with no replay board never loads the replay`() {
        kotlinx.coroutines.runBlocking { completions.onCompleted(solve(), replay) }

        assertEquals(ReplaySectionState.Hidden, vm(board = null).replaySection)
    }

    private object ReplayBoard : GameReplayBoard {
        override val pieces = "pieces"

        @Composable
        override fun Board(frame: ReplayFrame, options: ReplayBoardOptions, modifier: Modifier) = Unit
    }

    private companion object {
        const val TIME_MS = FakeController.TIME_MS
    }
}
