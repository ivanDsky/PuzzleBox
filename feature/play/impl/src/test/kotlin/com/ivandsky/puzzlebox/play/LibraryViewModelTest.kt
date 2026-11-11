package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.linkedin.LinkState
import com.ivandsky.puzzlebox.data.linkedin.LinkedInDayData
import com.ivandsky.puzzlebox.data.linkedin.LinkedInOwnState
import com.ivandsky.puzzlebox.data.linkedin.LinkedInSync
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.domain.DeleteReceivedPuzzle
import com.ivandsky.puzzlebox.domain.FinishLeg
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.LoadPastDay
import com.ivandsky.puzzlebox.domain.LoadSharedPuzzle
import com.ivandsky.puzzlebox.domain.ObserveArchiveMonth
import com.ivandsky.puzzlebox.domain.ObserveArchiveProgress
import com.ivandsky.puzzlebox.domain.ObserveFriendPuzzles
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.SyncTodayPuzzles
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayView
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInDay
import com.ivandsky.puzzlebox.domain.linkedin.SyncLinkedInDay
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.linkedin.LinkedInDayState
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInDays
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInLink
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchResultOutbox
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The library's state: rows in registry order with today's status, number,
 * archive progress and stats; the first game selected until [LibraryViewModel.select];
 * offline when any today can't load; the lesson opens and closing it marks it seen; the
 * page's calendar month and picked day survive a month change and reset to today; a
 * removed friend's puzzle comes back with its saved play on Undo, until its window closes.
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
    private val received = FakeReceivedPuzzleRepository()
    private val lane = WriteLane()
    private val sync = SyncTodayPuzzles(fakes.games, daily, calendar)
    private val store = PlaySessionStore(
        games = fakes.games,
        ui = fakes.registry,
        opening = SlotOpening(
            daily, LoadPastDay(daily, fakes.games), saved, calendar, fakes.games, lane, FakeReceivedPuzzleRepository(),
            FakeMatchRepository(),
            LoadSharedPuzzle(fakes.games, FakeSharedPuzzleRepository(), FakeReceivedPuzzleRepository(), { Budget.Unlimited }, { Instant.EPOCH }),
            testDispatchers(dispatcher),
        ),
        writers = { SlotWriter(it, saved, lane, CoroutineScope(dispatcher)) },
        completions = completions,
        finishLeg = FinishLeg(FakeMatchResultOutbox(), FakeIdentityService()) { Instant.EPOCH },
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

    private fun observedVm(
        delete: DeleteReceivedPuzzle = DeleteReceivedPuzzle(received, saved),
        linkedIn: LinkedInDayState? = null,
    ): LibraryViewModel {
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
            friendPuzzles = ObserveFriendPuzzles(received, saved, completions, fakes.games),
            deleteReceivedPuzzle = delete,
            appScope = CoroutineScope(dispatcher + Job()),
            linkedIn = linkedIn,
        ).also { vm ->
            screen.launch { vm.uiState.collect {} }
            screen.launch { vm.page.collect {} }
            screen.launch { vm.friends.collect {} }
            dispatcher.scheduler.runCurrent()
        }
    }

    private fun LibraryViewModel.row(game: GameId) = uiState.value.rows.first { it.card.game == game }

    private val days = FakeLinkedInDays()

    private fun holder() = LinkedInDayState(ObserveLinkedInDay(days, fakes.games), SyncLinkedInDay(days, 500), FakeLinkedInLink())

    /** A linked day whose own result is not known yet, so [inFlight] and [lastSync] decide what it shows. */
    private fun data(inFlight: Boolean = false, lastSync: LinkedInSync? = null) = LinkedInDayData(
        link = LinkState.Connected("member-1"),
        delta = 3,
        boardInWindow = true,
        inFlight = inFlight,
        lastSync = lastSync,
        own = LinkedInOwnState.Unknown,
        ghosts = emptyList(),
        boardFetchedAtMs = null,
    )

    private fun viewOf(game: GameId, on: LocalDate): LinkedInDayView? =
        runBlocking { ObserveLinkedInDay(days, fakes.games)(game, on).first() }

    /** The day card's slot: the only collector of [LibraryViewModel.linkedInView]. */
    private fun showDayCard(vm: LibraryViewModel): Job {
        val card = screen.launch { vm.linkedInView.collect {} }
        dispatcher.scheduler.runCurrent()
        return card
    }

    /** Moves the clock to exactly [ms] later, work due at that instant included. */
    private fun after(ms: Long) {
        dispatcher.scheduler.advanceTimeBy(ms)
        dispatcher.scheduler.runCurrent()
    }

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

    @Test
    fun `Remove hides the card now and Undo brings it back with the puzzle untouched`() = runBlocking {
        received.upsert(friendPuzzle(A))
        saved.save(PlaySlot.Custom(alpha, A.value), byteArrayOf(7), elapsedMs = 70_000)
        val vm = observedVm()
        screen.launch { vm.friends.collect {} }
        dispatcher.scheduler.runCurrent()
        assertEquals(listOf(A), vm.friends.value.map { it.row.code })

        vm.removeFriend(A)
        dispatcher.scheduler.runCurrent()
        assertEquals(A, vm.removedFriend.value)
        assertTrue(vm.friends.value.isEmpty())
        assertNotNull(received.rows[A])
        assertEquals(1, saved.rows.size)

        vm.undoRemoveFriend()
        dispatcher.scheduler.advanceTimeBy(FriendRemoval.UNDO_WINDOW_MS + 1)
        assertNull(vm.removedFriend.value)
        assertEquals(listOf(A), vm.friends.value.map { it.row.code })
        assertNotNull(received.rows[A])
        assertEquals(mapOf<PlaySlot, Long>(PlaySlot.Custom(alpha, A.value) to 70_000), saved.observeInProgress().first())
    }

    @Test
    fun `the puzzle is deleted when the Undo window closes`() = runBlocking {
        received.upsert(friendPuzzle(A))
        val vm = observedVm()

        vm.removeFriend(A)
        dispatcher.scheduler.advanceTimeBy(FriendRemoval.UNDO_WINDOW_MS - 1)
        assertEquals(A, vm.removedFriend.value)
        assertNotNull(received.rows[A])
        dispatcher.scheduler.advanceTimeBy(2)
        dispatcher.scheduler.runCurrent()
        assertNull(vm.removedFriend.value)
        assertTrue(received.rows.isEmpty())
    }

    @Test
    fun `a newer removal deletes the older one and holds its own Undo`() = runBlocking {
        received.upsert(friendPuzzle(A))
        received.upsert(friendPuzzle(B))
        val vm = observedVm()

        vm.removeFriend(A)
        vm.removeFriend(B)
        dispatcher.scheduler.runCurrent()
        assertEquals(B, vm.removedFriend.value)
        assertEquals(setOf(B), received.rows.keys)

        vm.undoRemoveFriend()
        dispatcher.scheduler.advanceTimeBy(FriendRemoval.UNDO_WINDOW_MS + 1)
        assertEquals(setOf(B), received.rows.keys)
    }

    @Test
    fun `the card stays out of the list while its delete is still running`() = runBlocking {
        received.upsert(friendPuzzle(A))
        val gate = CompletableDeferred<Unit>()
        val gated = object : ReceivedPuzzleRepository by received {
            override suspend fun delete(code: ShareCode) {
                gate.await()
                received.delete(code)
            }
        }
        val vm = observedVm(DeleteReceivedPuzzle(gated, saved))
        screen.launch { vm.friends.collect {} }
        dispatcher.scheduler.runCurrent()

        vm.removeFriend(A)
        dispatcher.scheduler.advanceTimeBy(FriendRemoval.UNDO_WINDOW_MS + 1)
        dispatcher.scheduler.runCurrent()
        assertNull(vm.removedFriend.value)
        assertNotNull(received.rows[A])
        assertTrue(vm.friends.value.isEmpty())

        gate.complete(Unit)
        dispatcher.scheduler.runCurrent()
        assertTrue(received.rows.isEmpty())
        assertTrue(vm.friends.value.isEmpty())
    }

    @Test
    fun withoutLinkedInTheViewIsNullAndNothingIsCalled() {
        val vm = observedVm()
        showDayCard(vm)

        after(HOUR_MS)

        assertNull(vm.linkedIn)
        assertNull(vm.linkedInView.value)
        assertEquals(emptyList<Any>(), days.syncCalls)
    }

    @Test
    fun theListMakesNoLinkedInCall() {
        observedVm(linkedIn = holder())

        after(HOUR_MS)

        assertEquals(emptyList<Any>(), days.syncCalls)
    }

    @Test
    fun theViewFollowsTodayThenThePickedDay() {
        val past = day.minusDays(2)
        days.emit(alpha, day, data(inFlight = true))
        days.emit(alpha, past, data(lastSync = LinkedInSync.Offline))
        val vm = observedVm(linkedIn = holder())
        showDayCard(vm)
        assertEquals(viewOf(alpha, day), vm.linkedInView.value)

        vm.pick(past)
        dispatcher.scheduler.runCurrent()
        assertEquals(viewOf(alpha, past), vm.linkedInView.value)
        assertNotEquals(viewOf(alpha, day), vm.linkedInView.value)

        vm.backToToday()
        dispatcher.scheduler.runCurrent()
        assertEquals(viewOf(alpha, day), vm.linkedInView.value)
    }

    @Test
    fun selectingAnotherGameChangesTheView() {
        days.emit(alpha, day, data(inFlight = true))
        days.emit(beta, day, data(lastSync = LinkedInSync.OverBudget))
        val vm = observedVm(linkedIn = holder())
        showDayCard(vm)
        assertEquals(viewOf(alpha, day), vm.linkedInView.value)

        vm.select(beta)
        dispatcher.scheduler.runCurrent()

        assertEquals(viewOf(beta, day), vm.linkedInView.value)
        assertNotEquals(viewOf(alpha, day), vm.linkedInView.value)
    }

    @Test
    fun pickingFiveDaysFastSyncsOnlyTheLastOnce() {
        val vm = observedVm(linkedIn = holder())
        showDayCard(vm)

        repeat(5) { step ->
            vm.pick(day.minusDays(step + 1L))
            after(100)
        }
        // The fifth day came on screen at 400 ms, so its wait ends at 900 ms.
        after(399)
        assertEquals(emptyList<Any>(), days.syncCalls)

        after(1)
        assertEquals(listOf(alpha to day.minusDays(5)), days.syncCalls)

        after(HOUR_MS)
        assertEquals(listOf(alpha to day.minusDays(5)), days.syncCalls)
    }

    @Test
    fun leavingTheCardCancelsTheSync() {
        val vm = observedVm(linkedIn = holder())
        val card = showDayCard(vm)

        after(300)
        card.cancel()
        after(HOUR_MS)

        assertEquals(emptyList<Any>(), days.syncCalls)
    }

    private fun friendPuzzle(code: ShareCode) = ReceivedPuzzle(
        code = code,
        gameId = alpha,
        variant = TestGames.variant(alpha),
        sharedFormatVersion = 1,
        puzzleBytes = byteArrayOf(TestGames.MAGIC),
        title = "Crown",
        authorName = "Bo",
        note = "",
        authorTimeMs = 100_000,
        authorHints = 0,
        authorUid = "author",
        openedAt = Instant.ofEpochMilli(1_700_000_000_000),
        unpublished = false,
        postState = PostState.Pending,
        postAttempts = 0,
        difficulty = null,
    )

    private companion object {
        const val HOUR_MS = 3_600_000L
        val A = checkNotNull(ShareCode.parse("K7Q2MX"))
        val B = checkNotNull(ShareCode.parse("B4R9TZ"))
    }
}
