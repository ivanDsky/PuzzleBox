package com.ivandsky.puzzlebox.app.play

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.di.PreferencesQueensSettings
import com.ivandsky.puzzlebox.auth.GoogleIdToken
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.common.di.commonModule
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.cloud.CloudSync
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.match.MatchLocalRepository
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.match.SelfMatchRepository
import com.ivandsky.puzzlebox.data.match.MatchResultOutbox
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.data.leaderboard.SubmitStatusTracker
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import com.ivandsky.puzzlebox.data.linkedin.LinkedInDays
import com.ivandsky.puzzlebox.domain.di.domainModule
import com.ivandsky.puzzlebox.domain.ranks.ObserveFriendDay
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.today
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.queensPlugin
import com.ivandsky.puzzlebox.games.queensui.QueensPlayUi
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.games.strands.strandsPlugin
import com.ivandsky.puzzlebox.games.strandsui.StrandsPlayUi
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.sudokuPlugin
import com.ivandsky.puzzlebox.games.sudokuui.SudokuHighlightSettings
import com.ivandsky.puzzlebox.games.sudokuui.SudokuPlayUi
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.tangoPlugin
import com.ivandsky.puzzlebox.games.tangoui.TangoPlayUi
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.zipPlugin
import com.ivandsky.puzzlebox.games.zipui.ZipPlayUi
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.hint.HintMode
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import com.ivandsky.puzzlebox.play.di.playModule
import com.ivandsky.puzzlebox.play.navigation.playEntry
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.cloud.FakeCloudSync
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInDays
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeGameSettingsRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeMatchLocalRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchResultOutbox
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSelfMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import java.time.LocalDate
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
import org.robolectric.annotation.GraphicsMode

/**
 * The fixed control layout is one layout (nav-rethink B5): all five real games
 * (Zip, Tango, Queens, Mini Sudoku, Strands) are opened in turn on the same Play stack and agree on where
 * their controls sit. Under the board a control row holds Undo, Redo, Hint and Clear left to right, as wide
 * as the board; on a landscape tablet the same buttons stack top to bottom in a column beside it. The timer sits
 * above the board, left of the top bar's buttons, at one place in every game. Strands has no Undo, Redo or
 * Clear: its counter takes their slots and its Hint the Hint slot. Mdpi windows, so a dp is a pixel.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// DrainTestApplication (plain Application + looper drain): the real PuzzleBoxApplication's startKoin would race this class's own graph.
@Config(sdk = [34], application = DrainTestApplication::class)
class ControlLayoutParityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val cadenceToday: LocalDate = TangoDefinition.cadence.today()

    private val games = listOf(ZipDefinition.id, TangoDefinition.id, QueensDefinition.id, SudokuGames.MINI.id, StrandsDefinition.id)

    private val navigator = Navigator(NavigationState(tab = PlayKey, stacks = listOf(listOf(PlayKey, GameKey(games.first().value)))))

    private var koin: KoinApplication? = null

    @Before
    fun startGraph() {
        stopKoin() // tests share the JVM-global Koin context
        koin = startKoin {
            modules(
                commonModule,
                module {
                    single<CoroutineScope> { CoroutineScope(SupervisorJob() + get<DispatcherProvider>().default) }
                    single<DailyRepository> { freshDailyForAllGames() }
                    single<CompletionRepository> { FakeCompletionRepository() }
                    single<ReleaseFlags> { FakeReleaseFlags() }
                    single { GameRegistry(listOf(zipPlugin, tangoPlugin, queensPlugin, sudokuPlugin(SudokuGames.MINI), strandsPlugin)) }
                    single {
                        GamePlayRegistry(
                            listOf(
                                ZipPlayUi(),
                                TangoPlayUi(HintMode.Stepwise(get<DispatcherProvider>().default)),
                                QueensPlayUi(PreferencesQueensSettings(FakeGameSettingsRepository(), CoroutineScope(Dispatchers.Unconfined)), HintMode.Stepwise(get<DispatcherProvider>().default)),
                                SudokuPlayUi(SudokuGames.MINI, Tints),
                                StrandsPlayUi(),
                            ),
                        )
                    }
                    single<ReceivedPuzzleRepository> { FakeReceivedPuzzleRepository() }
                    single<SharedPuzzleRepository> { FakeSharedPuzzleRepository() }
                    single<MatchRepository> { FakeMatchRepository() }
                                        single<SelfMatchRepository> { FakeSelfMatchRepository() }
                    single<SavedPlayRepository> { FakeSavedPlayRepository() }
                    single<MatchLocalRepository> { FakeMatchLocalRepository() }
                    single<MatchResultOutbox> { FakeMatchResultOutbox() }
                    single<IdentityService> { FakeIdentityService() }
                    single<GameCalendar> { FakeGameCalendar(cadenceToday) }
                    // Every tutorial seen: the boards draw straight away.
                    single<UserPreferencesRepository> { FakeUserPreferencesRepository(seen = games.toSet()) }
                    single<GoogleIdTokenProvider> { CancelledGoogleIdTokenProvider }
                    single<LeaderboardRepository> { FakeLeaderboardRepository(emptyMap()) }
                    single<LinkedInDays> { FakeLinkedInDays() }
                    single { SubmitStatusTracker() }
                },
                domainModule,
                module { single<CloudSync> { FakeCloudSync() } },
                playModule,
                module { single { ObserveFriendDay.Binding(reader = null) } }, // no Friends/Global setting
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w360dp-h800dp")
    @Test
    fun `controls sit under the board in the same slots in every game on a phone`() {
        eachGame(DpSize(360.dp, 800.dp), PlayLayout.SinglePane) { assertRowUnderBoard(it) }
    }

    @Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w800dp-h1280dp")
    @Test
    fun `controls sit under the board in the same slots in every game on a tablet in portrait`() {
        eachGame(DpSize(800.dp, 1280.dp), PlayLayout.SinglePane) { assertRowUnderBoard(it) }
    }

    @Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w1280dp-h800dp")
    @Test
    fun `controls stack beside the board in the same order in every game on a tablet in landscape`() {
        eachGame(DpSize(1280.dp, 800.dp), PlayLayout.TwoPane) { game ->
            val board = rect(game, boardTag(game))
            val hint = rect(game, hintTag(game))
            assertTrue("${game.value} hint ${hint.left} beside the board's right ${board.right}", hint.left >= board.right - 1f)
            if (game == StrandsDefinition.id) {
                val counter = rect(game, "strands.counter")
                assertTrue("strands counter above its hint", counter.bottom <= hint.top + 1f)
            } else {
                val undo = rect(game, "play.undo")
                val redo = rect(game, "play.redo")
                assertTrue("${game.value} undo above redo", undo.top < redo.top)
                assertTrue("${game.value} redo above hint", redo.top < hint.top)
                assertTrue("${game.value} undo and hint share a column", abs(undo.left - hint.left) < 1f)
                assertTrue("${game.value} hint above clear", hint.top < rect(game, "play.clear").top)
            }
            assertTopBar(game)
        }
    }

    @Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w360dp-h800dp")
    @Test
    fun `the timer and the overflow button are at one place in every game`() {
        val timers = mutableListOf<Rect>()
        val helps = mutableListOf<Rect>()
        val overflows = mutableListOf<Rect>() // only the games with settings to put behind it (Queens' Auto-cross)
        eachGame(DpSize(360.dp, 800.dp), PlayLayout.SinglePane) { game ->
            timers += rect(game, "play.timer")
            helps += rect(game, "play.how_to_play")
            if (composeRule.onAllNodesWithTag("play.overflow", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) overflows += rect(game, "play.overflow")
        }
        assertEquals("one timer top across ${games.size} games: $timers", 1, timers.map { it.top }.distinct().size)
        assertEquals("one how-to-play top across ${games.size} games: $helps", 1, helps.map { it.top }.distinct().size)
        assertEquals("one overflow left across ${overflows.size} games: $overflows", 1, overflows.map { it.left }.distinct().size)
    }

    /** Hint under the board and within its span; the slots run left to right; every game's Hint starts in the same column. */
    private fun assertRowUnderBoard(game: GameId) {
        val board = rect(game, boardTag(game))
        val hint = rect(game, hintTag(game))
        assertTrue("${game.value} hint top ${hint.top} under the board's bottom ${board.bottom}", hint.top >= board.bottom - 1f)
        assertTrue("${game.value} hint left ${hint.left} in the board's span ${board.left}..${board.right}", hint.left >= board.left - 1f && hint.right <= board.right + 1f)
        if (game == StrandsDefinition.id) {
            val counter = rect(game, "strands.counter")
            assertTrue("strands counter ${counter.right} left of its hint ${hint.left}", counter.right <= hint.left + 1f)
            assertTrue("strands counter top ${counter.top} under the board's bottom ${board.bottom}", counter.top >= board.bottom - 1f)
        } else {
            val undo = rect(game, "play.undo")
            val redo = rect(game, "play.redo")
            val clear = rect(game, "play.clear")
            assertTrue("${game.value} undo < redo < hint < clear", undo.left < redo.left && redo.left < hint.left && hint.left < clear.left)
            assertTrue("${game.value} one row", abs(undo.top - hint.top) < 1f && abs(hint.top - clear.top) < 1f)
        }
        assertTopBar(game)
        hintLefts[game] = hint.left
        if (game == games.last()) {
            // Zip, Tango, Queens and Sudoku start the Hint slot at one place; Strands' half-row pill within a few pixels of it.
            val first = hintLefts.getValue(games.first())
            hintLefts.forEach { (id, left) -> assertEquals("${id.value} hint left vs ${games.first().value}'s", first, left, 12f) }
        }
    }

    /** The timer sits above the board, left of the top bar's buttons. */
    private fun assertTopBar(game: GameId) {
        val board = rect(game, boardTag(game))
        val timer = rect(game, "play.timer")
        val help = rect(game, "play.how_to_play")
        assertTrue("${game.value} timer bottom ${timer.bottom} above the board's top ${board.top}", timer.bottom <= board.top + 1f)
        assertTrue("${game.value} timer right ${timer.right} left of how to play ${help.left}", timer.right <= help.left + 1f)
    }

    private val hintLefts = linkedMapOf<GameId, Float>()

    private fun boardTag(game: GameId) = when (game) {
        QueensDefinition.id -> "queens.board"
        SudokuGames.MINI.id -> "sudoku.board"
        StrandsDefinition.id -> "strands.board"
        else -> "play.board"
    }

    private fun hintTag(game: GameId) = if (game == StrandsDefinition.id) "strands.hint" else "play.hint"

    /** [tag]'s one node's bounds in the root, once [game]'s puzzle is ready and its screen settled. */
    private fun rect(game: GameId, tag: String): Rect {
        composeRule.waitUntil(10_000) { play(game)?.board?.value?.status is PlayBoardStatus.Ready }
        val found = runCatching {
            composeRule.waitUntil(10_000) { composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size == 1 }
        }
        check(found.isSuccess) { "${game.value}: expected one '$tag', found ${composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size}" }
        composeRule.waitForIdle()
        val node = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().single()
        assertNotNull(node)
        return node.boundsInRoot
    }

    /** Opens each game in turn on one Play stack at [size] and [layout], handing [check] each once it is on top. */
    private fun eachGame(size: DpSize, layout: PlayLayout, check: (GameId) -> Unit) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Box(Modifier.requiredSize(size)) {
                    NavDisplay(
                        backStack = navigator.state.currentStack,
                        onBack = { navigator.pop() },
                        entryProvider = entryProvider {
                            playEntry(navigator, layout, onOpenRanks = { _, _ -> }, onMakeOne = {})
                            entry<ResultKey> { Text("result") }
                        },
                    )
                }
            }
        }
        games.forEach { game ->
            if (game != games.first()) {
                composeRule.runOnIdle { navigator.replaceTop(GameKey(game.value)) }
                composeRule.waitForIdle() // let the stack transition finish: only the new game's screen is left
            }
            check(game)
        }
    }

    private fun play(game: GameId) = koin!!.koin.get<PlaySessionStore>().peek(PlaySlot.Daily(game, cadenceToday))

    private object Tints : SudokuHighlightSettings {
        override val peer: Flow<Boolean> = flowOf(true)
        override val sameDigit: Flow<Boolean> = flowOf(true)

        override suspend fun setPeer(enabled: Boolean) = Unit

        override suspend fun setSameDigit(enabled: Boolean) = Unit
    }

    private object CancelledGoogleIdTokenProvider : GoogleIdTokenProvider {
        override suspend fun idToken(activityContext: Context): GoogleIdToken =
            GoogleIdToken.Unavailable(GoogleIdToken.Unavailable.Reason.CANCELLED)
    }
}
