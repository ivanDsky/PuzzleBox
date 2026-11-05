package com.ivandsky.puzzlebox.app.play

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.ivandsky.puzzlebox.common.di.commonModule
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.GetReplaySession
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.today
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.games.queens.queensPlugin
import com.ivandsky.puzzlebox.games.queensui.QueensPlayUi
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.TangoPuzzleRef
import com.ivandsky.puzzlebox.games.tango.tangoPlugin
import com.ivandsky.puzzlebox.games.tangoui.TangoPlayController
import com.ivandsky.puzzlebox.games.tangoui.TangoPlayUi
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.zipPlugin
import com.ivandsky.puzzlebox.games.zipui.ZipPlayController
import com.ivandsky.puzzlebox.games.zipui.ZipPlayUi
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.ActivePlay
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import com.ivandsky.puzzlebox.play.di.playModule
import com.ivandsky.puzzlebox.play.navigation.playEntry
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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
 * The Play tab over the REAL games: its root is the games hub, listing every registered
 * game (A5); picking one pushes that game's [GameKey] on the Play stack (N2), whose board
 * loads today's puzzle; back returns to the hub (N3); a configuration change keeps the
 * store's live controller. The play feature pins the same paths over stand-in games.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Plain Application: the real PuzzleBoxApplication's startKoin would race this class's own graph.
@Config(sdk = [34], application = android.app.Application::class)
class RealGamesPlayRouteTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** Production Tango today is the REAL cadence date — the history row it resolves
     *  must name that same day (the repository rows the day it captures). */
    private val cadenceToday: LocalDate = TangoDefinition.cadence.today()

    private val navigator = Navigator(NavigationState(tab = PlayKey, stacks = listOf(listOf(PlayKey))))

    private var koin: KoinApplication? = null

    @Before
    fun clearKoin() {
        stopKoin() // tests share the JVM-global Koin context
    }

    @After
    fun tearDown() {
        koin?.close()
    }

    private fun startGraph(daily: DailyRepository = freshDailyForAllGames()) {
        koin = startKoin {
            modules(
                commonModule,
                module {
                    single { daily }
                    single<CompletionRepository> { FakeCompletionRepository() }
                    // The hub names games from the registry (A5), in the app's order.
                    single { GameRegistry(listOf(zipPlugin, tangoPlugin, queensPlugin)) }
                    single { GamePlayRegistry(listOf(ZipPlayUi(), TangoPlayUi(), QueensPlayUi())) }
                    single<SavedPlayRepository> { FakeSavedPlayRepository() }
                    // Every real game shares the America/Los_Angeles cadence zone.
                    single<GameCalendar> { FakeGameCalendar(cadenceToday) }
                    factory { GetReplaySession(get(), get()) }
                },
                playModule,
            )
        }
    }

    /** The Play tab's stack as the shell draws it: the play feature's entries over [navigator]. */
    @Composable
    private fun PlayStack() {
        PuzzleBoxTheme(darkTheme = false) {
            NavDisplay(
                backStack = navigator.state.currentStack,
                onBack = { navigator.pop() },
                entryProvider = entryProvider {
                    playEntry(navigator, PlayLayout.SinglePane)
                    entry<ResultKey> { Text("result") }
                },
            )
        }
    }

    private fun play(game: GameId): ActivePlay? =
        koin!!.koin.get<PlaySessionStore>().peek(PlaySlot(game, cadenceToday))

    private fun pick(game: GameId) {
        composeRule.onNodeWithTag("hub.row.${game.value}").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("route.game").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil(10_000) { play(game)?.board?.value?.status is PlayBoardStatus.Ready }
    }

    @Test
    fun `the play tab root is the hub with every registered game`() {
        startGraph()
        composeRule.setContent { PlayStack() }

        composeRule.onNodeWithTag("route.hub").assertExists()
        composeRule.onNodeWithTag("hub.row.zip").assertExists()
        composeRule.onNodeWithTag("hub.row.tango").assertExists()
        composeRule.onNodeWithTag("hub.row.queens").assertExists()
        composeRule.onNodeWithText("Zip").assertExists()
        composeRule.onNodeWithText("Tango").assertExists()
        composeRule.onNodeWithText("Queens").assertExists()
    }

    @Test
    fun `picking tango pushes its game and loads today's board`() {
        startGraph(
            freshDailyForAllGames().apply {
                setHistory(TangoDefinition.id, listOf(TangoPlayFixture.entry(cadenceToday)))
            },
        )
        composeRule.setContent { PlayStack() }

        pick(TangoDefinition.id)

        composeRule.runOnIdle {
            assertEquals(listOf<NavKey>(PlayKey, GameKey(TangoDefinition.id.value)), navigator.state.currentStack)
            val tango = play(TangoDefinition.id)!!.controller as TangoPlayController
            assertEquals(TangoPuzzleRef(cadenceToday, 301), tango.session?.ref)
        }
        composeRule.onNodeWithTag("play.side_panel").assertExists()
        composeRule.onNodeWithText("Daily · #301").assertExists()
        composeRule.onNodeWithTag("route.hub").assertDoesNotExist()
    }

    @Test
    fun `rotation keeps the live zip controller and its hint log`() {
        startGraph()
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { PlayStack() }
        pick(ZipDefinition.id)
        val live = composeRule.runOnIdle { (play(ZipDefinition.id)!!.controller as ZipPlayController).also { it.hint() } }
        composeRule.runOnIdle { assertTrue(live.hintCooldownActive()) }

        // Configuration change: ON_PAUSE folds the clock before the state is saved, then
        // the composition is rebuilt while the store keeps the slot's play.
        val scenario = composeRule.activityRule.scenario
        scenario.moveToState(Lifecycle.State.STARTED)
        restoration.emulateSavedInstanceStateRestore()
        scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            // Same controller ⇒ same cooldown anchor; the cooldown itself runs on the wall
            // clock, which the paused-lifecycle hop can outlast under Robolectric.
            assertSame(live, play(ZipDefinition.id)!!.controller)
            assertEquals(1, live.hintHistory.size)
        }
    }

    @Test
    fun `back from a mid-game board returns to the hub`() {
        startGraph()
        composeRule.setContent { PlayStack() }
        pick(ZipDefinition.id)

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        assertEquals(listOf<NavKey>(PlayKey), composeRule.runOnIdle { navigator.state.currentStack })
        composeRule.onNodeWithTag("route.hub").assertExists()
    }
}
