package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.data.cloud.CloudSyncState
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.linkedin.LinkState
import com.ivandsky.puzzlebox.data.linkedin.LinkedInDayData
import com.ivandsky.puzzlebox.data.linkedin.LinkedInDays
import com.ivandsky.puzzlebox.data.linkedin.LinkedInGhost
import com.ivandsky.puzzlebox.data.linkedin.LinkedInOwner
import com.ivandsky.puzzlebox.data.linkedin.LinkedInOwnState
import com.ivandsky.puzzlebox.data.leaderboard.BoardPage
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardSnapshot
import com.ivandsky.puzzlebox.data.leaderboard.SubmitResult
import com.ivandsky.puzzlebox.domain.ObserveLeaderboard
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInDay
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInDayView
import com.ivandsky.puzzlebox.domain.linkedin.SavedBoardState
import com.ivandsky.puzzlebox.domain.linkedin.SyncLinkedInDay
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.CompletionSource
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.gameui.board.DailyBoardLine
import com.ivandsky.puzzlebox.gameui.board.DailyBoardRowModel
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState
import com.ivandsky.puzzlebox.gameui.board.GhostRowModel
import com.ivandsky.puzzlebox.play.friend.dismissedGoogleSignIn
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.cloud.FakeCloudSync
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInDays
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInLink
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The board shows the day card's day, or the picked one over it; with LinkedIn the title and the rows change together, once the new day is ready. */
class GamePageBoardViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val game = TestGames.alpha
    private val today = LocalDate.of(2026, 11, 9)
    private val yesterday = today.minusDays(1)
    private val past = today.minusDays(4)

    private fun entry(name: String, timeMs: Long) =
        LeaderboardEntry(uid = name, displayName = name, puzzleRef = "alpha/$today/1", timeMs = timeMs, hints = 0, completedAtMs = 0L)

    /** Today's board as these names in order, instead of the one row of the day's own name. */
    private var standings: List<String>? = null

    /** When set, the board of [past] has not started, so a test sees the state while it loads. */
    private var pastBoardWaits = false
    private val pastBoardReady = MutableSharedFlow<Unit>(replay = 1)

    private val repository = object : LeaderboardRepository {
        override fun observe(board: LeaderboardRef, order: RankOrder): Flow<LeaderboardSnapshot?> = flow {
            if (board.day == past && pastBoardWaits) pastBoardReady.first()
            val names = standings ?: listOf(
                when (board.day) {
                    yesterday -> "Yest1"
                    past -> "Past1"
                    else -> "Today1"
                },
            )
            // One second apart in order, so a ghost can be placed between two rows.
            emit(LeaderboardSnapshot(board, names.mapIndexed { i, name -> entry(name, 10_000L + i * 1_000L) }))
        }

        override suspend fun fetch(board: LeaderboardRef, order: RankOrder): BoardPage? = error("unused")
        override suspend fun count(board: LeaderboardRef): Int? = error("unused")
        override suspend fun submit(entry: LeaderboardEntry): SubmitResult = error("never submitted")
    }

    private val calendar = FakeGameCalendar(today)

    private val registry = TestGames.registry(listOf(game))
    private val identity = FakeIdentityService(Identity("me", "Me", null))

    /** Built inside the test: its scope takes the main dispatcher the rule installs when the test starts. */
    private val viewModel by lazy {
        GamePageBoardViewModel(
            game, ObserveLeaderboard(identity, repository, registry, calendar), calendar, registry, identity,
            dismissedGoogleSignIn, linkedIn = null, friendDay = null, // hub off
        )
    }

    private val days = FakeLinkedInDays()
    private val syncLinkedIn = SyncLinkedInDay(days, 500)

    /** When set, the phone has not yet read [past]'s LinkedIn day, so a test sees the state while that read is awaited. */
    private var pastReadWaits = false
    private val pastRead = MutableSharedFlow<Unit>(replay = 1)

    private val phone = object : LinkedInDays by days {
        override fun observe(game: GameId, day: LocalDate) = flow {
            if (day == past && pastReadWaits) pastRead.first()
            emitAll(days.observe(game, day))
        }
    }
    private val observeLinkedIn = ObserveLinkedInDay(phone, registry)

    /** A holder like the page's; the board only observes it. */
    private fun holder() = ObserveLinkedInDayView(observeLinkedIn, syncLinkedIn, FakeLinkedInLink(), phone)

    /** A board with LinkedIn on. */
    private fun linkedInModel() = GamePageBoardViewModel(
        game, ObserveLeaderboard(identity, repository, registry, calendar), calendar, registry, identity,
        dismissedGoogleSignIn, linkedIn = holder(), friendDay = null, // hub off
    )

    private fun ghost(name: String, timeMs: Long?, hintFree: Boolean = true, photoPath: String? = null) =
        LinkedInGhost("ref-$name", name, timeMs, hintFree, flawless = false, photoPath = photoPath)

    /** A connected day whose connections board was read, [fetchedAtMs] null when it has not been. */
    private fun dayData(
        ghosts: List<LinkedInGhost>,
        inWindow: Boolean = true,
        fetchedAtMs: Long? = 1L,
        own: LinkedInOwnState = LinkedInOwnState.Unknown,
        owner: LinkedInOwner? = null,
        complete: Boolean = false,
    ) = LinkedInDayData(
        link = LinkState.Connected("member-1"),
        delta = 0,
        boardInWindow = inWindow,
        inFlight = false,
        lastSync = null,
        own = own,
        ghosts = ghosts,
        boardFetchedAtMs = fetchedAtMs,
        owner = owner,
        boardComplete = complete,
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
    fun `with no pick the board reads the day card's day and says it is not today`() {
        viewModel.show(picked = null, cardDay = yesterday)
        viewModel.state.launchIn(screen)
        settle()

        assertEquals(yesterday, viewModel.state.value.day)
        assertFalse(viewModel.state.value.isToday)
        assertEquals(DailyBoardState.Ranked(listOf(row("Yest1"))), viewModel.state.value.board)
    }

    @Test
    fun `the board follows the cloud's first restore and is never restoring without one`() {
        val cloud = FakeCloudSync(CloudSyncState.Syncing(firstRestore = true))
        val restoring = GamePageBoardViewModel(
            game, ObserveLeaderboard(identity, repository, registry, calendar), calendar, registry, identity,
            dismissedGoogleSignIn, linkedIn = null, cloudSync = cloud, friendDay = null, // hub off
        )
        restoring.state.launchIn(screen)
        viewModel.state.launchIn(screen)
        settle()

        assertTrue(restoring.state.value.restoring)
        assertFalse(viewModel.state.value.restoring)

        cloud.state.value = CloudSyncState.Syncing(firstRestore = false)
        settle()
        assertFalse(restoring.state.value.restoring)

        cloud.state.value = CloudSyncState.Syncing(firstRestore = true)
        settle()
        assertTrue(restoring.state.value.restoring)

        cloud.state.value = CloudSyncState.Synced(atMs = 1L)
        settle()
        assertFalse(restoring.state.value.restoring)
    }

    @Test
    fun `a day card on today's day is today's board`() {
        viewModel.show(picked = null, cardDay = today)
        viewModel.state.launchIn(screen)
        settle()

        assertEquals(today, viewModel.state.value.day)
        assertTrue(viewModel.state.value.isToday)
        assertEquals(DailyBoardState.Ranked(listOf(row("Today1"))), viewModel.state.value.board)
    }

    @Test
    fun `before the day card loads the board is the calendar's today, and follows its midnight`() {
        viewModel.state.launchIn(screen)
        settle()
        assertEquals(today, viewModel.state.value.day)
        assertTrue(viewModel.state.value.isToday)

        calendar.setAll(today.plusDays(1))
        settle()
        assertEquals(today.plusDays(1), viewModel.state.value.day)
        assertTrue(viewModel.state.value.isToday)
    }

    @Test
    fun `a pick switches the board and unpicking returns to the day card's day`() {
        viewModel.state.launchIn(screen)
        viewModel.show(picked = null, cardDay = yesterday)
        settle()
        assertEquals(DailyBoardState.Ranked(listOf(row("Yest1"))), viewModel.state.value.board)

        viewModel.show(picked = past, cardDay = yesterday)
        settle()
        assertEquals(past, viewModel.state.value.day)
        assertFalse(viewModel.state.value.isToday)
        assertEquals(DailyBoardState.Ranked(listOf(row("Past1"))), viewModel.state.value.board)

        viewModel.show(picked = null, cardDay = yesterday)
        settle()
        assertEquals(yesterday, viewModel.state.value.day)
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

        assertEquals(today, model.state.value.day)
        assertEquals(
            listOf(GhostRowModel("ref-Ada", "Ada", 61_000L, true), GhostRowModel("ref-Bo", "Bo", null, false)),
            model.state.value.ghosts,
        )
        assertEquals(listOf(row("Today1")), rowsOf(model))
    }

    @Test
    fun `a ghosts cached photo path reaches its board row`() {
        days.emit(game, today, dayData(listOf(ghost("Ada", 61_000L, photoPath = "/cache/ada.jpg"), ghost("Bo", null))))
        val model = linkedInModel()
        model.state.launchIn(screen)
        settle()

        assertEquals(listOf("/cache/ada.jpg", null), model.state.value.ghosts.map { it.photo })
    }

    @Test
    fun `the day cards day shows its own ghosts and none of todays`() {
        days.emit(game, today, dayData(listOf(ghost("Ada", 61_000L))))
        val model = linkedInModel()
        model.state.launchIn(screen)
        settle()

        model.show(picked = null, cardDay = yesterday)
        settle()
        assertEquals(yesterday, model.state.value.day)
        assertEquals(emptyList<GhostRowModel>(), model.state.value.ghosts)

        days.emit(game, yesterday, dayData(listOf(ghost("Cy", 70_000L, hintFree = false))))
        settle()
        assertEquals(listOf(GhostRowModel("ref-Cy", "Cy", 70_000L, false)), model.state.value.ghosts)
    }

    @Test
    fun `a hidden board has no ghosts and a saved one shows past the window`() {
        val model = linkedInModel()
        model.state.launchIn(screen)

        days.emit(game, today, dayData(listOf(ghost("Ada", 61_000L)), fetchedAtMs = null))
        settle()
        assertEquals(emptyList<GhostRowModel>(), model.state.value.ghosts)

        // A saved board needs no recent day (A24): the window only says whether a read can still make one.
        days.emit(game, today, dayData(listOf(ghost("Ada", 61_000L)), inWindow = false))
        settle()
        assertEquals(listOf("Ada"), model.state.value.ghosts.map { it.name })
    }

    @Test
    fun `the board says whether its day is saved, never saved or not yet read - A36`() {
        val model = linkedInModel()
        model.state.launchIn(screen)

        days.emit(game, today, dayData(emptyList(), fetchedAtMs = null))
        settle()
        assertEquals(SavedBoardState.NotYetRead, model.state.value.saved)

        days.emit(game, today, dayData(emptyList(), inWindow = false, fetchedAtMs = null))
        settle()
        assertEquals(SavedBoardState.NeverSaved, model.state.value.saved)

        days.emit(game, today, dayData(listOf(ghost("Ada", 61_000L)), fetchedAtMs = 7_000L))
        settle()
        assertEquals(SavedBoardState.Saved(7_000L, final = false), model.state.value.saved)

        days.emit(game, today, dayData(listOf(ghost("Ada", 61_000L)), inWindow = false, fetchedAtMs = 9_000L, complete = true))
        settle()
        assertEquals(SavedBoardState.Saved(9_000L, final = true), model.state.value.saved)
    }

    @Test
    fun `without LinkedIn the board is never saved`() {
        viewModel.state.launchIn(screen)
        settle()

        assertEquals(SavedBoardState.NotYetRead, viewModel.state.value.saved)
    }

    @Test
    fun `the board never syncs LinkedIn for any day`() {
        days.emit(game, today, dayData(listOf(ghost("Ada", 61_000L))))
        days.emit(game, yesterday, dayData(listOf(ghost("Bo", 62_000L))))
        days.emit(game, past, dayData(listOf(ghost("Cy", 70_000L))))
        val model = linkedInModel()
        model.state.launchIn(screen)
        settle()

        after(HOUR_MS)
        model.show(picked = null, cardDay = yesterday)
        after(HOUR_MS)
        model.show(picked = past, cardDay = yesterday)
        after(HOUR_MS)
        model.show(picked = null, cardDay = null)
        after(HOUR_MS)

        assertEquals(emptyList<Any>(), days.syncCalls)
        assertEquals(listOf("Ada"), model.state.value.ghosts.map { it.name })
    }

    @Test
    fun `a picked day shows that days board and ghosts, and unpicking returns to the day card's day`() {
        days.emit(game, yesterday, dayData(listOf(ghost("Ada", 61_000L))))
        days.emit(game, past, dayData(listOf(ghost("Cy", 70_000L, hintFree = false))))
        val model = linkedInModel()
        model.state.launchIn(screen)
        model.show(picked = null, cardDay = yesterday)
        settle()

        model.show(picked = past, cardDay = yesterday)
        settle()
        assertEquals(past, model.state.value.day)
        assertEquals(listOf(row("Past1")), rowsOf(model))
        assertEquals(listOf(GhostRowModel("ref-Cy", "Cy", 70_000L, false)), model.state.value.ghosts)

        model.show(picked = null, cardDay = yesterday)
        settle()
        assertEquals(yesterday, model.state.value.day)
        assertEquals(listOf(row("Yest1")), rowsOf(model))
        assertEquals(listOf(GhostRowModel("ref-Ada", "Ada", 61_000L, true)), model.state.value.ghosts)
    }

    /** Every state the board reaches once collected, in order, as the screen would have drawn them. */
    private fun drawn(model: GamePageBoardViewModel): MutableList<GamePageBoardState> {
        val seen = mutableListOf<GamePageBoardState>()
        model.state.onEach { seen += it }.launchIn(screen)
        return seen
    }

    @Test
    fun `a day switch keeps the drawn day until the new day's rows and ghosts are both in, then changes once`() {
        pastBoardWaits = true
        pastReadWaits = true
        days.emit(game, today, dayData(listOf(ghost("Ada", 61_000L))))
        days.emit(game, past, dayData(listOf(ghost("Cy", 70_000L))))
        val model = linkedInModel()
        val seen = drawn(model)
        settle()
        val before = model.state.value
        assertEquals(today, before.day)
        assertEquals(listOf(row("Today1")), rowsOf(model))
        seen.clear()

        model.show(picked = past, cardDay = null)
        settle()
        assertEquals("while the new board loads", before, model.state.value)

        pastBoardReady.tryEmit(Unit)
        settle()
        assertEquals("the rows are in, the phone's read of the ghosts is not", before, model.state.value)

        pastRead.tryEmit(Unit)
        settle()
        val arrived = model.state.value
        assertEquals(past, arrived.day)
        assertEquals(listOf(row("Past1")), rowsOf(model))
        assertEquals(listOf("Cy"), arrived.ghosts.map { it.name })
        assertEquals("one change, no Loading or ghost-less day in between", listOf(arrived), seen)
    }

    @Test
    fun `within the new day a later ghost change still flows through`() {
        days.emit(game, today, dayData(listOf(ghost("Ada", 61_000L))))
        val model = linkedInModel()
        model.state.launchIn(screen)
        model.show(picked = past, cardDay = null)
        settle()
        assertEquals(past, model.state.value.day)

        days.emit(game, past, dayData(listOf(ghost("Cy", 70_000L))))
        settle()
        assertEquals(listOf("Cy"), model.state.value.ghosts.map { it.name })
    }

    @Test
    fun `a new day that never arrives is named and shows Loading after the hold's cap`() {
        pastBoardWaits = true
        days.emit(game, today, dayData(listOf(ghost("Ada", 61_000L))))
        val model = linkedInModel()
        model.state.launchIn(screen)
        settle()
        val before = model.state.value

        model.show(picked = past, cardDay = null)
        after(HOLD_CAP_MS - 1)
        assertEquals(before, model.state.value)

        after(1)
        assertEquals(past, model.state.value.day)
        assertEquals(DailyBoardState.Loading, model.state.value.board)
        assertEquals(emptyList<GhostRowModel>(), model.state.value.ghosts)

        pastBoardReady.tryEmit(Unit)
        settle()
        assertEquals(listOf(row("Past1")), rowsOf(model))
    }

    @Test
    fun `after the cap gave up on a day, the next switch names its day at once`() {
        pastBoardWaits = true
        val model = linkedInModel()
        model.state.launchIn(screen)
        model.show(picked = past, cardDay = null)
        after(HOLD_CAP_MS)
        assertEquals(DailyBoardState.Loading, model.state.value.board)

        model.show(picked = yesterday, cardDay = null)
        settle()
        assertEquals(yesterday, model.state.value.day)
        assertEquals(listOf(row("Yest1")), rowsOf(model))
    }

    @Test
    fun `the first load names its day and shows Loading first`() {
        pastBoardWaits = true
        pastReadWaits = true
        val model = linkedInModel()
        model.show(picked = past, cardDay = null)
        val seen = drawn(model)
        settle()

        assertEquals(GamePageBoardState(day = past, isToday = false), model.state.value)
        assertEquals(listOf(GamePageBoardState(), GamePageBoardState(day = past, isToday = false)), seen)
    }

    @Test
    fun `without LinkedIn a day switch names the new day at once and shows Loading while it loads`() {
        pastBoardWaits = true
        viewModel.state.launchIn(screen)
        settle()
        assertEquals(DailyBoardState.Ranked(listOf(row("Today1"))), viewModel.state.value.board)

        viewModel.show(picked = past, cardDay = null)
        settle()
        assertEquals(past, viewModel.state.value.day)
        assertEquals(DailyBoardState.Loading, viewModel.state.value.board)
    }

    @Test
    fun `without LinkedIn the board follows the day card's day with no ghosts and calls nothing`() {
        days.emit(game, yesterday, dayData(listOf(ghost("Ada", 61_000L))))
        viewModel.show(picked = null, cardDay = yesterday)
        viewModel.state.launchIn(screen)
        settle()

        after(HOUR_MS)
        assertEquals(yesterday, viewModel.state.value.day)
        assertEquals(emptyList<GhostRowModel>(), viewModel.state.value.ghosts)
        assertEquals(emptyList<Any>(), days.syncCalls)
        assertEquals(DailyBoardState.Ranked(listOf(row("Yest1"))), viewModel.state.value.board)
    }

    @Test
    fun `two faster connections put the player third`() {
        standings = listOf("me")
        days.emit(game, today, dayData(listOf(ghost("Ada", 5_000L), ghost("Bo", 4_000L))))
        val model = linkedInModel()
        model.state.launchIn(screen)
        settle()

        val board = model.state.value.board as DailyBoardState.Ranked
        assertEquals(listOf("Bo" to 1, "Ada" to 2, "me" to 3), board.lines.orEmpty().map { it.name() to it.place() })
        assertEquals(1, board.rows.single().rank)
    }

    @Test
    fun `a pinned players place counts connections beyond the top rows`() {
        standings = listOf("a", "b", "c", "d", "e", "f", "me")
        // 12.5s sits among the top five; 15.5s only ahead of the pinned row, outside the rows drawn.
        days.emit(game, today, dayData(listOf(ghost("Near", 12_500L), ghost("Far", 15_500L))))
        val model = linkedInModel()
        model.state.launchIn(screen)
        settle()

        val board = model.state.value.board as DailyBoardState.Ranked
        assertEquals(7, board.pinned?.rank)
        assertEquals(9, board.pinnedPlace)
        assertEquals(listOf("a", "b", "c", "Near", "d", "e", "Far"), board.lines.orEmpty().map { it.name() })
    }

    private fun imported(timeMs: Long) = LinkedInOwnState.Imported(
        CompletionRecord(TestGames.Ref(game, PuzzleKey.Daily(today, 1)), timeMs, 0, RecordStats(1, "{}"), source = CompletionSource.LinkedIn),
    )

    @Test
    fun `the imported LinkedIn time replaces the player's own, so a pinned player moves into the top rows`() {
        standings = listOf("a", "b", "c", "d", "e", "f", "me")
        days.emit(game, today, dayData(emptyList(), own = imported(10_500L)))
        val model = linkedInModel()
        model.state.launchIn(screen)
        settle()

        val board = model.state.value.board as DailyBoardState.Ranked
        assertEquals(listOf("a" to 1, "me" to 2, "b" to 3, "c" to 4, "d" to 5), board.lines.orEmpty().map { it.name() to it.place() })
        assertEquals(null, board.pinned)
        assertEquals(7, board.lines.orEmpty().filterIsInstance<DailyBoardLine.Real>().single { it.row.isSelf }.row.rank)
    }

    private val owner = LinkedInOwner("Owner Name", photoPath = "/cache/owner.jpg")

    private fun youOf(board: DailyBoardState.Ranked) =
        board.lines.orEmpty().filterIsInstance<DailyBoardLine.Real>().single { it.row.isSelf }

    @Test
    fun `with no row of this profile, the row named like the owner is the you row at the imported time with the LinkedIn photo`() {
        standings = listOf("a", " owner  name ", "c")
        days.emit(game, today, dayData(emptyList(), own = imported(10_500L), owner = owner))
        val model = linkedInModel()
        model.state.launchIn(screen)
        settle()

        val board = model.state.value.board as DailyBoardState.Ranked
        assertEquals(listOf("a" to 1, " owner  name " to 2, "c" to 3), board.lines.orEmpty().map { it.name() to it.place() })
        assertEquals(10_500L, youOf(board).row.timeMs)
        assertEquals("/cache/owner.jpg", youOf(board).row.photo)
        assertFalse(youOf(board).row.fromLinkedIn)
    }

    @Test
    fun `with no row of this profile and no name match the owner's LinkedIn row is added as the you row`() {
        standings = listOf("a", "b")
        days.emit(game, today, dayData(emptyList(), own = imported(10_500L), owner = owner))
        val model = linkedInModel()
        model.state.launchIn(screen)
        settle()

        val board = model.state.value.board as DailyBoardState.Ranked
        assertEquals(listOf("a" to 1, "Owner Name" to 2, "b" to 3), board.lines.orEmpty().map { it.name() to it.place() })
        assertEquals(10_500L, youOf(board).row.timeMs)
        assertEquals("/cache/owner.jpg", youOf(board).row.photo)
        assertTrue(youOf(board).row.fromLinkedIn)
    }

    @Test
    fun `a row of this profile stays the you row with the LinkedIn time and photo, and a same-name row is left alone`() {
        standings = listOf("a", "Owner Name", "me")
        days.emit(game, today, dayData(emptyList(), own = imported(10_500L), owner = owner))
        val model = linkedInModel()
        model.state.launchIn(screen)
        settle()

        val board = model.state.value.board as DailyBoardState.Ranked
        assertEquals(listOf("a" to 1, "me" to 2, "Owner Name" to 3), board.lines.orEmpty().map { it.name() to it.place() })
        assertEquals("/cache/owner.jpg", youOf(board).row.photo)
        assertFalse(board.lines.orEmpty().filterIsInstance<DailyBoardLine.Real>().single { it.row.name == "Owner Name" }.row.isSelf)
    }

    @Test
    fun `a pinned you row follows the same choice`() {
        standings = listOf("a", "b", "c", "d", "e", "f", "Owner Name")
        days.emit(game, today, dayData(emptyList(), own = imported(18_000L), owner = owner))
        val model = linkedInModel()
        model.state.launchIn(screen)
        settle()

        val board = model.state.value.board as DailyBoardState.Ranked
        assertEquals("Owner Name", board.pinned?.name)
        assertEquals("/cache/owner.jpg", board.pinned?.photo)
        assertEquals(18_000L, board.pinned?.timeMs)
        assertEquals(7, board.pinnedPlace)
    }

    @Test
    fun `a connection named like a player is that player at the LinkedIn time and is drawn once`() {
        standings = listOf("a", "me", "Bea")
        days.emit(game, today, dayData(listOf(ghost(" bea ", 5_000L), ghost("Cy", 10_500L))))
        val model = linkedInModel()
        model.state.launchIn(screen)
        settle()

        val board = model.state.value.board as DailyBoardState.Ranked
        assertEquals(listOf("Bea" to 1, "a" to 2, "Cy" to 3, "me" to 4), board.lines.orEmpty().map { it.name() to it.place() })
    }

    @Test
    fun `without connections the board is today's rows alone`() {
        standings = listOf("a", "me")
        val model = linkedInModel()
        model.state.launchIn(screen)
        settle()

        val board = model.state.value.board as DailyBoardState.Ranked
        assertEquals(null, board.lines)
        assertEquals(null, board.pinnedPlace)
    }

    private fun DailyBoardLine.name() = when (this) {
        is DailyBoardLine.Real -> row.name
        is DailyBoardLine.Ghost -> row.name
    }

    private fun DailyBoardLine.place() = when (this) {
        is DailyBoardLine.Real -> place
        is DailyBoardLine.Ghost -> place
    }

    private fun rowsOf(model: GamePageBoardViewModel) = (model.state.value.board as DailyBoardState.Ranked).rows

    private fun settle() = main.testDispatcher.scheduler.runCurrent()

    private fun row(name: String) =
        DailyBoardRowModel(rank = 1, name = name, timeMs = 10_000L, hints = 0, isSelf = false, playerId = name)

    private companion object {
        const val HOUR_MS = 3_600_000L
        const val HOLD_CAP_MS = 1_500L
    }
}
