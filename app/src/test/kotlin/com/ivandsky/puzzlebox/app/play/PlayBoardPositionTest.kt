package com.ivandsky.puzzlebox.app.play

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.ivandsky.puzzlebox.common.di.commonModule
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.LoadPastDay
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.SyncTodayPuzzles
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.today
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.queensPlugin
import com.ivandsky.puzzlebox.games.queensui.QueensPlayUi
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.tangoPlugin
import com.ivandsky.puzzlebox.games.tangoui.TangoPlayUi
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.zipPlugin
import com.ivandsky.puzzlebox.games.zipui.ZipPlayUi
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
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
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
 * A14: the shared play chrome puts every game's board at the same top y. Each real game
 * is opened on the same Play stack in turn, at one window size, and its board's top edge
 * is read once the board is ready — a phone (one pane) and a tablet (two panes).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Plain Application: the real PuzzleBoxApplication's startKoin would race this class's own graph.
@Config(sdk = [34], application = android.app.Application::class)
class PlayBoardPositionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** Every real game shares the America/Los_Angeles cadence zone. */
    private val cadenceToday: LocalDate = TangoDefinition.cadence.today()

    private val games = listOf(ZipDefinition.id, TangoDefinition.id, QueensDefinition.id)

    private val navigator = Navigator(
        NavigationState(tab = PlayKey, stacks = listOf(listOf(PlayKey, GameKey(games.first().value)))),
    )

    private var koin: KoinApplication? = null

    @Before
    fun startGraph() {
        stopKoin() // tests share the JVM-global Koin context
        koin = startKoin {
            modules(
                commonModule,
                module {
                    single<DailyRepository> { freshDailyForAllGames() }
                    single<CompletionRepository> { FakeCompletionRepository() }
                    single { GameRegistry(listOf(zipPlugin, tangoPlugin, queensPlugin)) }
                    single { GamePlayRegistry(listOf(ZipPlayUi(), TangoPlayUi(), QueensPlayUi())) }
                    single<SavedPlayRepository> { FakeSavedPlayRepository() }
                    single<GameCalendar> { FakeGameCalendar(cadenceToday) }
                    factory { LoadPastDay(get(), get()) }
                    single { SyncTodayPuzzles(get(), get(), get()) }
                    factory { ObserveTodaySet(get(), get(), get(), get(), get(), get()) }
                    // The hub's best times and puzzle numbers.
                    factory { ObserveGameHistory(get(), get(), get()) }
                    factory { ObserveGameStats(get(), get(), ComputeStreak(GetPuzzleDay(get()))) }
                    // Every tutorial seen: the boards draw straight away.
                    single<UserPreferencesRepository> { FakeUserPreferencesRepository(seen = games.toSet()) }
                },
                playModule,
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h800dp")
    @Test
    fun `every board starts at the same height on a phone`() {
        assertSameBoardTop(DpSize(360.dp, 800.dp), PlayLayout.SinglePane)
    }

    @Config(sdk = [34], application = android.app.Application::class, qualifiers = "w1280dp-h800dp")
    @Test
    fun `every board starts at the same height on a tablet`() {
        assertSameBoardTop(DpSize(1280.dp, 800.dp), PlayLayout.TwoPane)
    }

    private fun assertSameBoardTop(size: DpSize, layout: PlayLayout) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Box(Modifier.requiredSize(size)) {
                    NavDisplay(
                        backStack = navigator.state.currentStack,
                        onBack = { navigator.pop() },
                        entryProvider = entryProvider {
                            playEntry(navigator, layout, onOpenHistory = {})
                            entry<ResultKey> { Text("result") }
                        },
                    )
                }
            }
        }
        val tops = games.associateWith { game ->
            if (game != games.first()) {
                composeRule.runOnIdle { navigator.replaceTop(GameKey(game.value)) }
                // Let the stack transition finish: only the new game's board is left.
                composeRule.waitForIdle()
            }
            boardTop(game)
        }
        val zipTop = tops.getValue(ZipDefinition.id)
        tops.forEach { (game, top) -> assertEquals("${game.value} board top (px) vs Zip's", zipTop, top, 0.5f) }
    }

    /** The top edge of [game]'s board, in root pixels, once its puzzle is ready. */
    private fun boardTop(game: GameId): Float {
        composeRule.waitUntil(10_000) { play(game)?.board?.value?.status is PlayBoardStatus.Ready }
        val tag = if (game == QueensDefinition.id) "queens.board" else "play.board"
        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().size == 1 }
        return composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().single().positionInRoot.y
    }

    private fun play(game: GameId) = koin!!.koin.get<PlaySessionStore>().peek(PlaySlot(game, cadenceToday))
}
