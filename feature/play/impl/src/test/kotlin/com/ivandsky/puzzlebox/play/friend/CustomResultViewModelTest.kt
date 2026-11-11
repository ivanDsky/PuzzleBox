package com.ivandsky.puzzlebox.play.friend

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import com.ivandsky.puzzlebox.domain.LoadPastDay
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.navigation.navKeySavedStateConfiguration
import com.ivandsky.puzzlebox.play.FakeController
import com.ivandsky.puzzlebox.play.FakeGames
import com.ivandsky.puzzlebox.play.api.CustomGameKey
import com.ivandsky.puzzlebox.play.api.CustomResultKey
import com.ivandsky.puzzlebox.play.api.FriendPuzzleKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.play.api.playNavKeys
import com.ivandsky.puzzlebox.play.di.playModule
import com.ivandsky.puzzlebox.play.navigation.playEntry
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
 * first solve here reads Record with its time, a slower replay reads Replay with the record
 * that stands, and the screen shows no streak. Solving the board lands on the result, Done
 * returns to the card, Play again replays the finished play, and the key survives saved state.
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
            key, completions, received, fakes.games, fakes.registry, koin.get(), koin.get(), koin.get(),
        )
        screen.launch { viewModel.state.collect {} }
        main.testDispatcher.scheduler.runCurrent()
        return viewModel
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
    fun `a slower replay reads Replay with the record that stands`() {
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
    fun `the screen stamps the record pill on a first win and names the standing record on a replay`() {
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
    fun `Play again replays the finished play instead of bouncing back to the result`() {
        val navigator = showBoard()
        solve()
        val play = fakes[alpha].latest

        node("result.playAgain").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(CustomGameKey(alpha.value, code.value), navigator.top)
        assertEquals(1, play.replays)
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

    private companion object {
        const val WAIT_MS = 5_000L
    }
}
