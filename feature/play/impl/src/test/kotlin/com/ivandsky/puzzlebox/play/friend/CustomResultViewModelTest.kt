package com.ivandsky.puzzlebox.play.friend

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import com.ivandsky.puzzlebox.common.di.commonModule
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.CustomBoardRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.CustomBoardView
import com.ivandsky.puzzlebox.domain.FinishLeg
import com.ivandsky.puzzlebox.domain.LoadCustomReplay
import com.ivandsky.puzzlebox.domain.LoadDailyReplay
import com.ivandsky.puzzlebox.domain.LoadLegReplay
import com.ivandsky.puzzlebox.domain.LoadSharedPuzzle
import com.ivandsky.puzzlebox.domain.LoadSolveReplay
import com.ivandsky.puzzlebox.domain.LoadPastDay
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.replay.ReplayFrame
import com.ivandsky.puzzlebox.model.SolveReplay
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.gameui.replay.GameReplayBoard
import com.ivandsky.puzzlebox.gameui.replay.ReplayBoardOptions
import com.ivandsky.puzzlebox.gameui.replay.ReplaySectionState
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.navigation.navKeySavedStateConfiguration
import com.ivandsky.puzzlebox.play.ActivePlay
import com.ivandsky.puzzlebox.play.FakeController
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.SlotOpening
import com.ivandsky.puzzlebox.play.SlotWriter
import com.ivandsky.puzzlebox.play.WriteLane
import com.ivandsky.puzzlebox.play.testDispatchers
import com.ivandsky.puzzlebox.play.FakePlayUi
import com.ivandsky.puzzlebox.play.FakeGames
import com.ivandsky.puzzlebox.play.loadSolveReplay
import com.ivandsky.puzzlebox.play.replayCapableGames
import com.ivandsky.puzzlebox.play.api.CustomGameKey
import com.ivandsky.puzzlebox.play.api.CustomResultKey
import com.ivandsky.puzzlebox.play.api.FriendPuzzleKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.play.api.playNavKeys
import com.ivandsky.puzzlebox.play.di.playModule
import com.ivandsky.puzzlebox.play.navigation.playEntry
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.replay.ReplayFixtures
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeCustomBoardRepository
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import kotlin.coroutines.CoroutineContext
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A friend's puzzle result over its ViewModel and the play routes, every source a fake: the
 * first solve here reads Record with its time, a slower play again reads a play-again standing with the record
 * that stands, and the screen shows no streak. Solving the board lands on the result, Done
 * returns to the card, Play again plays the finished play again, and the key survives saved state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CustomResultViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val composeRule = createComposeRule()

    private val alpha = TestGames.alpha
    private val code = checkNotNull(ShareCode.parse("K7Q2MX"))
    private val fakes = FakeGames(listOf(alpha))
    private val completions = FakeCompletionRepository()
    private val received = FakeReceivedPuzzleRepository()
    private val ref = checkNotNull(fakes.games[alpha]).refOf(PuzzleKey.Custom(code.value))

    /** Collectors standing in for the screen: the state only runs while observed. */
    private val screen = CoroutineScope(main.testDispatcher + Job())

    private var koin: KoinApplication? = null

    @Before
    fun setUp() {
        runBlocking { received.upsert(puzzle()) }
        stopKoin() // tests share the JVM-global Koin context
        koin = startKoin {
            modules(
                commonModule,
                module {
                    single<DailyRepository> { FakeDailyRepository() }
                    single<CompletionRepository> { completions }
                    single<GameRegistry> { fakes.games }
                    single<GamePlayRegistry> { fakes.registry }
                    single<SavedPlayRepository> { FakeSavedPlayRepository() }
                    single<ReceivedPuzzleRepository> { received }
                    single<SharedPuzzleRepository> { FakeSharedPuzzleRepository() }
                    single<MatchRepository> { FakeMatchRepository() }
                    factory { FinishLeg(FakeMatchResultOutbox(), FakeIdentityService()) { Instant.EPOCH } }
                    single<GameCalendar> { FakeGameCalendar(LocalDate.parse("2026-11-04")) }
                    single<UserPreferencesRepository> { FakeUserPreferencesRepository() }
                    single { GameEditorRegistry(emptyList()) }
                    single<CustomBoardRepository> { FakeCustomBoardRepository() }
                    single<IdentityService> { FakeIdentityService() }
                    single { dismissedGoogleSignIn }
                    factory { LoadPastDay(get(), get()) }
                    factory { loadSolveReplay(get(), get(), get()) }
                },
                playModule,
            )
        }
    }

    @After
    fun tearDown() {
        screen.cancel()
        koin?.close()
    }

    private fun puzzle() = ReceivedPuzzle(
        code = code,
        gameId = alpha,
        variant = TestGames.variant(alpha),
        sharedFormatVersion = 1,
        puzzleBytes = TestGames.puzzle(alpha).payload,
        title = "Crown the castle",
        authorName = "Jonas",
        note = "",
        authorTimeMs = 102_000L,
        authorHints = 2,
        authorUid = "uid-jonas",
        openedAt = Instant.ofEpochMilli(1_000L),
        unpublished = false,
        postState = PostState.Pending,
        postAttempts = 0,
        difficulty = null,
    )


    private fun key(timeMs: Long, hints: Int = 0) = CustomResultKey(alpha.value, code.value, timeMs, hints)

    private fun row(timeMs: Long, hints: Int = 0, isRecord: Boolean = false) =
        CompletionRecord(ref, timeMs, hints, RecordStats(1, "{}"), isRecord = isRecord)

    private fun observed(key: CustomResultKey): CustomResultViewModel {
        val koin = checkNotNull(koin).koin
        val viewModel = CustomResultViewModel(
            key, completions, received, fakes.games, fakes.registry, koin.get(), koin.get(), koin.get(), loadReplay(fakes.games),
        )
        screen.launch { viewModel.state.collect {} }
        main.testDispatcher.scheduler.runCurrent()
        return viewModel
    }

    private val fixture = ReplayFixtures.GAME
    private val fixtureGames = replayCapableGames()
    private val fixtureUi = FakePlayUi(fixture)
    private val fixtureRef = TestGames.Ref(fixture, PuzzleKey.Custom(code.value))
    private val replay: SolveReplay = ReplayFixtures.sealed(ReplayFixtures.SHORT_MOVES)

    /** [LoadSolveReplay] over this test's stores; the fixture game opens a real timeline. */
    private fun loadReplay(
        games: GameRegistry,
        flags: ReleaseFlags = FakeReleaseFlags(ReleaseFlag.FullReplay),
    ): LoadSolveReplay {
        val loadShared = LoadSharedPuzzle(games, FakeSharedPuzzleRepository(), received, { Budget.Unlimited }, { Instant.EPOCH })
        return LoadSolveReplay(
            flags = flags,
            games = games,
            daily = LoadDailyReplay(completions, FakeDailyRepository()),
            custom = LoadCustomReplay(completions, received, loadShared),
            leg = LoadLegReplay(FakeMatchRepository(), FakeMatchResultOutbox(), FakeIdentityService(), FakeDailyRepository(), received, loadShared),
            completions = completions,
            dispatchers = InlineDispatcherProvider,
        )
    }

    /** A solve of the replay-capable fixture game's friend puzzle, observed as the screen would. */
    private fun observedReplay(
        timeMs: Long = FIXTURE_TIME_MS,
        flags: ReleaseFlags = FakeReleaseFlags(ReleaseFlag.FullReplay),
        board: GameReplayBoard? = ReplayBoard,
        store: PlaySessionStore = checkNotNull(koin).koin.get(),
    ): CustomResultViewModel {
        storeFixturePuzzle()
        fixtureUi.replayBoard = board
        val koin = checkNotNull(koin).koin
        val viewModel = CustomResultViewModel(
            CustomResultKey(fixture.value, code.value, timeMs, 0), completions, received, fixtureGames,
            GamePlayRegistry(listOf(fixtureUi)),
            koin.get(), store, koin.get(), loadReplay(fixtureGames, flags),
        )
        screen.launch { viewModel.state.collect {} }
        main.testDispatcher.scheduler.runCurrent()
        return viewModel
    }

    private fun storeFixturePuzzle() {
        runBlocking { received.upsert(puzzle().copy(gameId = fixture, variant = TestGames.variant(fixture))) }
    }

    /** A real store over the fixture game: its play's record write seals [replay], as a recording play's would. */
    private fun liveStore(): PlaySessionStore {
        val dispatcher = main.testDispatcher
        val lane = WriteLane()
        val saved = FakeSavedPlayRepository()
        val calendar = FakeGameCalendar(LocalDate.parse("2026-11-04"))
        val sealing = object : CompletionRepository by completions {
            override suspend fun onCompleted(record: CompletionRecord, replay: SolveReplay?) =
                completions.onCompleted(record, replay ?: this@CustomResultViewModelTest.replay)
        }
        return PlaySessionStore(
            games = fixtureGames,
            ui = GamePlayRegistry(listOf(fixtureUi)),
            opening = SlotOpening(
                FakeDailyRepository(), LoadPastDay(FakeDailyRepository(), fixtureGames), saved, calendar, fixtureGames, lane, received,
                FakeMatchRepository(),
                LoadSharedPuzzle(fixtureGames, FakeSharedPuzzleRepository(), received, { Budget.Unlimited }, { Instant.EPOCH }),
                testDispatchers(dispatcher),
            ),
            writers = { SlotWriter(it, saved, lane, CoroutineScope(dispatcher)) },
            completions = sealing,
            finishLeg = FinishLeg(FakeMatchResultOutbox(), FakeIdentityService()) { Instant.EPOCH },
            calendar = calendar,
            dispatchers = testDispatchers(dispatcher),
        )
    }

    /** The friend puzzle's live play, won as the board leaves it before the result opens. */
    private fun solveLive(store: PlaySessionStore): ActivePlay {
        storeFixturePuzzle()
        fixtureUi.replayBoard = ReplayBoard
        val play = checkNotNull(store.open(CustomGameKey(fixture.value, code.value)))
        main.testDispatcher.scheduler.runCurrent()
        fixtureUi.latest.win()
        main.testDispatcher.scheduler.runCurrent()
        return play
    }

    private suspend fun writeFixtureSolve(timeMs: Long, replay: SolveReplay?) =
        completions.onCompleted(CompletionRecord(fixtureRef, timeMs, 0, RecordStats(1, "{}"), isRecord = true), replay)

    private fun solvedFixture(timeMs: Long = FIXTURE_TIME_MS, replay: SolveReplay? = this.replay) =
        runBlocking { writeFixtureSolve(timeMs, replay) }

    @Test
    fun `a stored solve with a replay shows the replay section`() {
        solvedFixture()

        val ready = observedReplay().state.value.replay as ReplaySectionState.Ready

        assertEquals(ReplayFixtures.SHORT_MOVES, ready.timeline.moveTimesMs.size)
    }

    @Test
    fun `a live solve stays hidden until its own write returns, whatever an older row with the same time and hints says`() {
        solvedFixture()
        val gate = CompletableDeferred<Unit>()
        completions.writeGate = gate
        val store = liveStore()
        val play = solveLive(store)

        val viewModel = observedReplay(store = store)

        assertFalse(play.record.value.stored)
        assertEquals(ReplaySectionState.Hidden, viewModel.state.value.replay)

        gate.complete(Unit)
        main.testDispatcher.scheduler.runCurrent()

        assertTrue(play.record.value.stored)
        assertTrue(viewModel.state.value.replay is ReplaySectionState.Ready)
    }

    @Test
    fun `a live solve whose write fails stays hidden, whatever an older row says`() {
        solvedFixture()
        completions.fail = true
        val store = liveStore()
        val play = solveLive(store)

        val viewModel = observedReplay(store = store)
        main.testDispatcher.scheduler.runCurrent()

        assertFalse(play.record.value.stored)
        assertTrue(play.record.value.writeFailed)
        assertEquals(ReplaySectionState.Hidden, viewModel.state.value.replay)
    }

    @Test
    fun `a stored solve without a replay stays hidden`() {
        solvedFixture(replay = null)

        assertEquals(ReplaySectionState.Hidden, observedReplay().state.value.replay)
    }

    @Test
    fun `the replay section is hidden while the write is pending, ready once it lands`() {
        val gate = CompletableDeferred<Unit>()
        completions.writeGate = gate
        val viewModel = observedReplay()
        screen.launch { writeFixtureSolve(FIXTURE_TIME_MS, replay) }
        main.testDispatcher.scheduler.runCurrent()
        assertEquals(ReplaySectionState.Hidden, viewModel.state.value.replay)

        gate.complete(Unit)
        main.testDispatcher.scheduler.runCurrent()

        assertTrue(viewModel.state.value.replay is ReplaySectionState.Ready)
    }

    @Test
    fun `the replay section stays hidden after a failed write`() {
        completions.fail = true
        val viewModel = observedReplay()

        runCatching { solvedFixture() }
        main.testDispatcher.scheduler.runCurrent()

        assertEquals(ReplaySectionState.Hidden, viewModel.state.value.replay)
    }

    @Test
    fun `another solve of the puzzle does not open this result's replay`() {
        solvedFixture(timeMs = FIXTURE_TIME_MS + 1)

        assertEquals(ReplaySectionState.Hidden, observedReplay().state.value.replay)
    }

    @Test
    fun `flag off keeps the replay section hidden`() {
        solvedFixture()

        assertEquals(ReplaySectionState.Hidden, observedReplay(flags = FakeReleaseFlags()).state.value.replay)
    }

    @Test
    fun `a game with no replay board never shows the replay section`() {
        solvedFixture()

        assertEquals(ReplaySectionState.Hidden, observedReplay(board = null).state.value.replay)
    }

    @Test
    fun `the first solve here reads Record with its time`() {
        completions.setRecords(listOf(row(42_000L, isRecord = true)))

        val state = observed(key(42_000L)).state.value

        assertEquals(CustomStanding.Record, state.standing)
        assertEquals(42_000L, state.timeMs)
        assertEquals(puzzle(), state.puzzle)
    }

    @Test
    fun `a slower play again reads a play-again standing with the record that stands`() {
        completions.setRecords(listOf(row(118_000L, isRecord = true), row(130_000L, hints = 1)))

        val state = observed(key(130_000L, hints = 1)).state.value

        assertEquals(CustomStanding.PlayAgain(BestScore(118_000L, 0)), state.standing)
        assertEquals(130_000L, state.timeMs)
        assertEquals(1, state.hintsUsed)
    }

    @Test
    fun `the standing waits for the solve's row, then lands`() {
        val viewModel = observed(key(42_000L))
        assertNull(viewModel.state.value.standing)
        assertEquals("the solve's own facts show at once", 42_000L, viewModel.state.value.timeMs)

        completions.setRecords(listOf(row(42_000L, isRecord = true)))
        main.testDispatcher.scheduler.runCurrent()

        assertEquals(CustomStanding.Record, viewModel.state.value.standing)
    }

    @Test
    fun `taking the puzzle down closes the result's board`() {
        val viewModel = observed(key(42_000L))
        assertFalse(viewModel.state.value.boardClosed)

        runBlocking { received.markUnpublished(code) }
        main.testDispatcher.scheduler.runCurrent()

        assertTrue(viewModel.state.value.boardClosed)
    }

    @Test
    fun `a taken-down puzzle's result reads Leaderboard closed and posts nothing`() {
        val takenDown = puzzle().copy(unpublished = true)
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                CustomResultScreen(
                    state = CustomResultState(
                        timeMs = 130_000L,
                        hintsUsed = 0,
                        title = "Alpha",
                        brand = fakes.registry.brandOf(alpha),
                        rankOrder = RankOrder.TimeThenHints,
                        puzzle = takenDown,
                        standing = CustomStanding.Record,
                        board = CustomBoardView.Ranked(top = emptyList(), mine = null, author = takenDown.authorPin()),
                    ),
                    onDone = {},
                    onShare = {},
                    onPlayAgain = {},
                    onSignIn = {},
                )
            }
        }
        composeRule.waitForIdle()

        node("custom_board.closed").assertExists()
        node("custom_board.you.local_only").assertExists()
        assertEquals("a closed board never says it is posting", 0, count("custom_board.you.pending"))
    }

    @Test
    fun `the screen stamps the record pill on a first win and names the standing record on a play again`() {
        var standing: CustomStanding by mutableStateOf(CustomStanding.Record)
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                CustomResultScreen(
                    state = CustomResultState(
                        timeMs = 130_000L,
                        hintsUsed = 0,
                        title = "Alpha",
                        brand = fakes.registry.brandOf(alpha),
                        rankOrder = RankOrder.TimeThenHints,
                        puzzle = puzzle(),
                        standing = standing,
                    ),
                    onDone = {},
                    onShare = {},
                    onPlayAgain = {},
                    onSignIn = {},
                )
            }
        }
        composeRule.waitForIdle()
        node("result.time").assertExists()
        node("custom_result.author").assertExists()
        node("result.tile.hints").assertExists()
        assertEquals("a friend's puzzle has no streak", 0, count("result.tile.streak"))
        node("result.record_badge").assertExists()
        assertEquals(0, count("result.previous_best"))

        composeRule.runOnIdle { standing = CustomStanding.PlayAgain(BestScore(118_000L, 0)) }
        composeRule.waitForIdle()

        node("result.previous_best").assertExists()
        assertEquals(0, count("result.record_badge"))
    }

    @Test
    fun `a solve lands on the result and Done returns to the card`() {
        val navigator = showBoard()

        solve()

        assertEquals(key(FakeController.TIME_MS), navigator.top)
        node("route.custom_result").assertExists()

        node("result.done").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(FriendPuzzleKey(code.value), navigator.top)
        node("route.friend").assertExists()
    }

    @Test
    fun `Play again plays the finished play again instead of bouncing back to the result`() {
        val navigator = showBoard()
        solve()
        val play = fakes[alpha].latest

        node("result.playAgain").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(CustomGameKey(alpha.value, code.value), navigator.top)
        assertEquals(1, play.playAgains)
        assertFalse(play.isComplete)
        node("fake.board").assertExists()
    }

    @Test
    fun `the key survives a saved-state round trip`() {
        val state = NavigationState(PlayKey, listOf(listOf(PlayKey, FriendPuzzleKey(code.value), key(118_000L, 2))))
        val config = navKeySavedStateConfiguration { playNavKeys() }

        val restored = decodeFromSavedState(
            NavigationState.serializer(),
            encodeToSavedState(NavigationState.serializer(), state, config),
            config,
        )

        assertEquals(state, restored)
    }

    private val Navigator.top: NavKey get() = state.currentStack.last()

    /** The friend's board on top of its card, as the card's Play leaves the stack. */
    private fun showBoard(): Navigator {
        val navigator = Navigator(
            NavigationState(PlayKey, listOf(listOf(PlayKey, FriendPuzzleKey(code.value), CustomGameKey(alpha.value, code.value)))),
        )
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                NavDisplay(
                    backStack = navigator.state.currentStack,
                    onBack = { navigator.pop() },
                    entryProvider = entryProvider { playEntry(navigator, PlayLayout.SinglePane, onOpenGameHistory = { _, _ -> }) },
                )
            }
        }
        composeRule.waitUntil(WAIT_MS) { count("fake.board") > 0 }
        return navigator
    }

    private fun solve() {
        composeRule.runOnIdle { fakes[alpha].latest.win() }
        composeRule.waitUntil(WAIT_MS) { count("route.custom_result") > 0 }
    }

    private fun node(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true)

    private fun count(tag: String) =
        composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size

    private object ReplayBoard : GameReplayBoard {
        override val pieces = "pieces"

        @Composable
        override fun Board(frame: ReplayFrame, options: ReplayBoardOptions, modifier: Modifier) = Unit
    }

    private object InlineDispatcherProvider : DispatcherProvider {
        private val inline = object : CoroutineDispatcher() {
            override fun isDispatchNeeded(context: CoroutineContext) = false
            override fun dispatch(context: CoroutineContext, block: Runnable) = block.run()
        }
        override val main: CoroutineDispatcher = inline
        override val io: CoroutineDispatcher = inline
        override val default: CoroutineDispatcher = inline
    }

    private companion object {
        const val WAIT_MS = 5_000L
        const val FIXTURE_TIME_MS = 42_000L
    }
}
