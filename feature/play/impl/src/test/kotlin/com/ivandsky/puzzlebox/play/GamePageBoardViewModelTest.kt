package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.linkedin.LinkState
import com.ivandsky.puzzlebox.data.linkedin.LinkedInDayData
import com.ivandsky.puzzlebox.data.linkedin.LinkedInGhost
import com.ivandsky.puzzlebox.data.linkedin.LinkedInOwnState
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardSnapshot
import com.ivandsky.puzzlebox.data.leaderboard.SubmitResult
import com.ivandsky.puzzlebox.domain.ObserveLeaderboard
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInBoardView
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayView
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInDay
import com.ivandsky.puzzlebox.domain.linkedin.SyncLinkedInDay
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.board.DailyBoardRowModel
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState
import com.ivandsky.puzzlebox.gameui.board.GhostRowModel
import com.ivandsky.puzzlebox.play.friend.dismissedGoogleSignIn
import com.ivandsky.puzzlebox.play.linkedin.LinkedInDayState
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInDays
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInLink
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
import kotlinx.coroutines.flow.onEach
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    /** Today's board as these names in order, instead of the one row of the day's own name. */
    private var standings: List<String>? = null

    private val repository = object : LeaderboardRepository {
        override fun observe(board: LeaderboardRef, order: RankOrder): Flow<LeaderboardSnapshot?> {
            val names = standings ?: listOf(if (board.day == yesterday) "Yest1" else "Today1")
            return flowOf(LeaderboardSnapshot(board, names.map { entry(it) }))
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
            dismissedGoogleSignIn, linkedIn = null,
        )
    }

    private val days = FakeLinkedInDays()
    private val syncLinkedIn = SyncLinkedInDay(days, 500)
    private val observeLinkedIn = ObserveLinkedInDay(days, registry)

    /** A holder like the page's: its collection is the only thing that syncs. */
    private fun holder() = LinkedInDayState(observeLinkedIn, syncLinkedIn, FakeLinkedInLink())

    /** A board with LinkedIn on; its calendar never gates a read, so only the ghosts' timing is under test. */
    private fun linkedInModel(): GamePageBoardViewModel {
        val open = FakeGameCalendar(today)
        return GamePageBoardViewModel(
            game, ObserveLeaderboard(identity, repository, registry, open), open, registry, identity,
            dismissedGoogleSignIn, linkedIn = holder(),
        )
    }

    private fun ghost(name: String, timeMs: Long?, hintFree: Boolean = true) =
        LinkedInGhost("ref-$name", name, timeMs, hintFree, flawless = false)

    /** A connected day whose connections board was read, [fetchedAtMs] null when it has not been. */
    private fun dayData(ghosts: List<LinkedInGhost>, inWindow: Boolean = true, fetchedAtMs: Long? = 1L) = LinkedInDayData(
        link = LinkState.Connected("member-1"),
        delta = 0,
        boardInWindow = inWindow,
        inFlight = false,
        lastSync = null,
        own = LinkedInOwnState.Unknown,
        ghosts = ghosts,
        boardFetchedAtMs = fetchedAtMs,
    )

    private fun after(ms: Long) {
        main.testDispatcher.scheduler.advanceTimeBy(ms)
        settle()
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

    @Test
    fun `a board past the top five pins the players own row under them`() {
        standings = listOf("A", "B", "C", "D", "E", "F", "me")
        viewModel.state.launchIn(screen)
        settle()

        val board = viewModel.state.value.board as DailyBoardState.Ranked
        assertEquals(listOf("A", "B", "C", "D", "E"), board.rows.map { it.name })
        assertEquals(7, board.pinned?.rank)
        assertTrue(board.pinned?.isSelf == true)
    }

    @Test
    fun `a player inside the top five is not pinned`() {
        standings = listOf("A", "me", "C")
        viewModel.state.launchIn(screen)
        settle()

        val board = viewModel.state.value.board as DailyBoardState.Ranked
        assertEquals(listOf("A", "me", "C"), board.rows.map { it.name })
        assertEquals(null, board.pinned)
    }

    @Test
    fun `todays ghosts appear on the board state`() {
        days.emit(game, today, dayData(listOf(ghost("Ada", 61_000L), ghost("Bo", null, hintFree = false))))
        val model = linkedInModel()
        model.state.launchIn(screen)
        settle()

        assertEquals(BoardDay.Today, model.state.value.day)
        assertEquals(listOf(GhostRowModel("Ada", 61_000L, true), GhostRowModel("Bo", null, false)), model.state.value.ghosts)
        assertEquals(DailyBoardState.Ranked(listOf(row("Today1"))), model.state.value.board)
    }

    @Test
    fun `yesterday shows yesterdays ghosts and none of todays`() {
        days.emit(game, today, dayData(listOf(ghost("Ada", 61_000L))))
        val model = linkedInModel()
        model.state.launchIn(screen)
        settle()

        model.selectDay(BoardDay.Yesterday)
        settle()
        assertEquals(BoardDay.Yesterday, model.state.value.day)
        assertEquals(emptyList<GhostRowModel>(), model.state.value.ghosts)

        days.emit(game, yesterday, dayData(listOf(ghost("Cy", 70_000L, hintFree = false))))
        settle()
        assertEquals(listOf(GhostRowModel("Cy", 70_000L, false)), model.state.value.ghosts)
    }

    @Test
    fun `a hidden or out of window board has no ghosts`() {
        val model = linkedInModel()
        model.state.launchIn(screen)

        days.emit(game, today, dayData(listOf(ghost("Ada", 61_000L)), fetchedAtMs = null))
        settle()
        assertEquals(emptyList<GhostRowModel>(), model.state.value.ghosts)

        days.emit(game, today, dayData(listOf(ghost("Ada", 61_000L)), inWindow = false))
        settle()
        assertEquals(emptyList<GhostRowModel>(), model.state.value.ghosts)
    }

    @Test
    fun `the page syncs today once after the debounce and yesterday only when chosen`() {
        val model = linkedInModel()
        model.state.launchIn(screen)
        settle()

        after(499)
        assertEquals(emptyList<Any>(), days.syncCalls)
        after(1)
        assertEquals(listOf(game to today), days.syncCalls)
        after(HOUR_MS)
        assertEquals(listOf(game to today), days.syncCalls)

        model.selectDay(BoardDay.Yesterday)
        after(499)
        assertEquals(listOf(game to today), days.syncCalls)
        after(1)
        assertEquals(listOf(game to today, game to yesterday), days.syncCalls)
        after(HOUR_MS)
        assertEquals(2, days.syncCalls.size)
    }

    @Test
    fun `leaving the board cancels the debounced sync`() {
        val model = linkedInModel()
        val collector = model.state.launchIn(screen)
        settle()

        after(300)
        collector.cancel()
        after(HOUR_MS)

        assertEquals(emptyList<Any>(), days.syncCalls)
    }

    @Test
    fun `without LinkedIn nothing is called and ghosts are empty`() {
        days.emit(game, today, dayData(listOf(ghost("Ada", 61_000L))))
        viewModel.state.launchIn(screen)
        settle()

        after(HOUR_MS)
        assertEquals(emptyList<GhostRowModel>(), viewModel.state.value.ghosts)
        assertEquals(emptyList<Any>(), days.syncCalls)
        assertEquals(DailyBoardState.Ranked(listOf(row("Today1"))), viewModel.state.value.board)
    }

    @Test
    fun `the card holder and the board holder each ask once for today`() {
        days.emit(game, today, dayData(listOf(ghost("Ada", 61_000L))))
        val model = linkedInModel()
        val cardViews = mutableListOf<LinkedInDayView?>()
        holder().view(flowOf(LinkedInDayState.Target(game, today))).onEach { cardViews += it }.launchIn(screen)
        model.state.launchIn(screen)
        settle()

        after(500)
        after(HOUR_MS)

        // The fake does not dedupe: neither holder suppresses, delays or doubles the other.
        assertEquals(listOf(game to today, game to today), days.syncCalls)
        val shown = cardViews.last()?.board as LinkedInBoardView.Shown
        assertEquals(listOf("Ada"), shown.ghosts.map { it.name })
        assertTrue(model.state.value.ghosts.isNotEmpty())
    }

    private fun settle() = main.testDispatcher.scheduler.runCurrent()

    private fun row(name: String) =
        DailyBoardRowModel(rank = 1, name = name, timeMs = 10_000L, hints = 0, isSelf = false)

    private companion object {
        const val HOUR_MS = 3_600_000L
    }
}
