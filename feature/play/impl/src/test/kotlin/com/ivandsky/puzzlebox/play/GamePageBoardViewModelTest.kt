package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardSnapshot
import com.ivandsky.puzzlebox.data.leaderboard.SubmitResult
import com.ivandsky.puzzlebox.domain.ObserveLeaderboard
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.board.DailyBoardRowModel
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState
import com.ivandsky.puzzlebox.play.friend.dismissedGoogleSignIn
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The chosen day is the state's day at once, and no other day's rows stay under it while its board loads. */
@OptIn(ExperimentalCoroutinesApi::class)
class GamePageBoardViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val game = TestGames.alpha
    private val today = LocalDate.of(2026, 11, 9)
    private val yesterday = today.minusDays(1)

    private fun entry(name: String) =
        LeaderboardEntry(uid = name, displayName = name, puzzleRef = "alpha/$today/1", timeMs = 10_000L, hints = 0, completedAtMs = 0L)

    private val repository = object : LeaderboardRepository {
        override fun observe(board: LeaderboardRef, order: RankOrder): Flow<LeaderboardSnapshot?> {
            val name = if (board.day == yesterday) "Yest1" else "Today1"
            return flowOf(LeaderboardSnapshot(board, listOf(entry(name))))
        }

        override suspend fun submit(entry: LeaderboardEntry): SubmitResult = error("never submitted")
    }

    /** Every read of the calendar after the first waits for [reads]: the board of a newly chosen day has not started. */
    private val reads = MutableSharedFlow<Unit>(replay = 1)
    private var observed = 0
    private val calendar = object : GameCalendar {
        override fun today(game: GameId) = today

        override fun dayAt(game: GameId, epochMs: Long) = today

        override fun observeToday(game: GameId): Flow<LocalDate> = flow {
            if (observed++ > 0) reads.first()
            emit(today)
            awaitCancellation()
        }
    }

    private val registry = TestGames.registry(listOf(game))
    private val identity = FakeIdentityService(Identity("me", "Me", null))

    /** Built inside the test: its scope takes the main dispatcher the rule installs when the test starts. */
    private val viewModel by lazy {
        GamePageBoardViewModel(
            game, ObserveLeaderboard(identity, repository, registry, calendar), calendar, registry, identity,
            dismissedGoogleSignIn,
        )
    }

    /** A collector standing in for the screen: [GamePageBoardViewModel.state] only runs while observed. */
    private val screen = CoroutineScope(main.testDispatcher + Job())

    @After
    fun leaveScreen() {
        screen.cancel()
    }

    @Test
    fun `selecting a day names it at once and shows no rows until its board arrives`() {
        viewModel.state.launchIn(screen)
        settle()
        assertEquals(DailyBoardState.Ranked(listOf(row("Today1"))), viewModel.state.value.board)

        viewModel.selectDay(BoardDay.Yesterday)
        settle()
        assertEquals(BoardDay.Yesterday, viewModel.state.value.day)
        assertEquals(DailyBoardState.Loading, viewModel.state.value.board)

        reads.tryEmit(Unit)
        settle()
        assertEquals(BoardDay.Yesterday, viewModel.state.value.day)
        assertEquals(DailyBoardState.Ranked(listOf(row("Yest1"))), viewModel.state.value.board)
    }

    private fun settle() = main.testDispatcher.scheduler.runCurrent()

    private fun row(name: String) =
        DailyBoardRowModel(rank = 1, name = name, timeMs = 10_000L, hints = 0, isSelf = false)
}
