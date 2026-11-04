package com.ivandsky.puzzlebox.app.play

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.game.today
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.games.queens.queensPlugin
import com.ivandsky.puzzlebox.games.queensui.QueensPlayUi
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.TangoPuzzleRef
import com.ivandsky.puzzlebox.games.tango.TangoSession
import com.ivandsky.puzzlebox.games.tango.codec.TangoSessionCodec
import com.ivandsky.puzzlebox.games.tango.tangoPlugin
import com.ivandsky.puzzlebox.games.tangoui.TangoPlayController
import com.ivandsky.puzzlebox.games.tangoui.TangoPlayUi
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.codec.ZipSessionCodec
import com.ivandsky.puzzlebox.games.zip.zipPlugin
import com.ivandsky.puzzlebox.games.zipui.ZipPlayController
import com.ivandsky.puzzlebox.games.zipui.ZipPlayUi
import com.ivandsky.puzzlebox.play.PlayRoute
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.di.playModule
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.KoinApplication
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * tango-05 route wiring over the REAL games: the picker is the play tab's fresh-entry
 * state (A14) — every registered game listed, picking starts THAT game's play in place —
 * and a nav-carried [SessionBlob] replay restores through the named game's codec
 * (ADR-0005). The play feature pins the same paths over stand-in games.
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

    private fun dataModule(
        daily: DailyRepository,
        completions: CompletionRepository,
    ): org.koin.core.module.Module = module {
        single { daily }
        single { completions }
        // The picker names games from the registry (A14) — every game registered, in
        // the app's picker order.
        single { GameRegistry(listOf(zipPlugin, tangoPlugin, queensPlugin)) }
        single { GamePlayRegistry(listOf(ZipPlayUi(), TangoPlayUi(), QueensPlayUi())) }
    }

    private fun koinWith(
        daily: DailyRepository = freshDailyForAllGames(),
        completions: CompletionRepository = FakeCompletionRepository(),
    ): KoinApplication {
        stopKoin() // tests share the JVM-global Koin context
        return startKoin { modules(dataModule(daily, completions), playModule) }
    }

    private fun tango(koin: KoinApplication): TangoPlayController =
        koin.koin.get<PlaySessionStore>().active.value!!.controller as TangoPlayController

    private fun zip(koin: KoinApplication): ZipPlayController =
        koin.koin.get<PlaySessionStore>().active.value!!.controller as ZipPlayController

    private fun status(koin: KoinApplication): PlayBoardStatus =
        koin.koin.get<PlaySessionStore>().active.value!!.board.value.status

    private var handedOff: List<Pair<Long, Int>> = emptyList()

    private fun setContent(koin: KoinApplication, restoredSession: ByteArray? = null, onBack: () -> Unit = {}) {
        handedOff = emptyList()
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                PlayRoute(
                    layout = PlayLayout.SinglePane,
                    onBack = onBack,
                    onSolved = { timeMs, hintsUsed -> handedOff = handedOff + (timeMs to hintsUsed) },
                    restoredSession = restoredSession,
                )
            }
        }
    }

    @Test
    fun `fresh entry shows the picker with every registered game`() {
        val koin = koinWith()
        try {
            setContent(koin)
            composeRule.onNodeWithTag("play.picker").assertExists()
            composeRule.onNodeWithText("Pick a game").assertExists()
            composeRule.onNodeWithTag("play.picker_zip").assertExists()
            composeRule.onNodeWithTag("play.picker_tango").assertExists()
            composeRule.onNodeWithTag("play.picker_queens").assertExists()
            composeRule.onNodeWithText("Zip").assertExists()
            composeRule.onNodeWithText("Tango").assertExists()
            composeRule.onNodeWithText("Queens").assertExists()
        } finally {
            koin.close()
        }
    }

    @Test
    fun `picking tango starts the tango screen in place`() {
        val daily = freshDailyForAllGames().apply {
            setHistory(TangoDefinition.id, listOf(TangoPlayFixture.entry(cadenceToday)))
        }
        val koin = koinWith(daily)
        try {
            setContent(koin)
            composeRule.onNodeWithTag("play.picker_tango").performClick()
            composeRule.waitForIdle()

            // The tango play screen replaced the picker; today's board loaded in.
            composeRule.runOnIdle {
                assertTrue(status(koin) is PlayBoardStatus.Ready)
                assertEquals(TangoPuzzleRef(cadenceToday, 301), tango(koin).session?.ref)
            }
            composeRule.onNodeWithText("Tango").assertExists()
            composeRule.onNodeWithTag("play.side_panel").assertExists()
            composeRule.onNodeWithText("Daily · #301").assertExists()
            composeRule.onNodeWithTag("play.picker").assertDoesNotExist()
        } finally {
            koin.close()
        }
    }

    @Test
    fun `a tango replay blob starts tango and restores the archived session`() {
        val session = TangoSession(
            ref = TangoPuzzleRef(LocalDate.parse("2026-03-09"), 12),
            puzzle = TangoPlayFixture.puzzle,
            completedAt = java.time.Instant.parse("2026-03-09T12:00:00Z"),
        )
        val blob = SessionBlob.encode(
            TangoDefinition.id,
            TangoSessionCodec.serialize(session),
        )
        val koin = koinWith(
            freshDailyForAllGames().apply {
                setToday(TangoDefinition.id, DailyToday.Unavailable(FetchError.Throttled()))
            },
        )
        try {
            setContent(koin, restoredSession = blob)

            // Restored COMPLETED session → the win handoff fires into navigation (the
            // app shell renders the result route; here it's a stub) and the archive
            // was NEVER re-fetched (the blob carried the puzzle, A19).
            composeRule.waitForIdle()
            composeRule.runOnIdle {
                val controller = tango(koin)
                assertTrue(
                    "status=${status(koin)::class.simpleName} " +
                        "complete=${controller.session?.isComplete} " +
                        "ref=${controller.session?.ref} " +
                        "done=${controller.session?.completedAt != null}",
                    controller.session?.isComplete ?: false,
                )
                assertEquals(1, handedOff.size)
            }
            composeRule.onNodeWithText("Tango").assertExists()
            composeRule.onNodeWithTag("play.picker").assertDoesNotExist()
        } finally {
            koin.close()
        }
    }

    @Test
    fun `a zip replay blob still restores through the zip codec`() {
        val zipSession = com.ivandsky.puzzlebox.games.zip.ZipSession(
            ref = com.ivandsky.puzzlebox.games.zip.ZipPuzzleRef(LocalDate.parse("2026-03-09"), 12),
            puzzle = ZipPlayFixture.puzzle(),
        )
        val blob = SessionBlob.encode(
            ZipDefinition.id,
            ZipSessionCodec.serialize(zipSession),
        )
        val daily = freshDailyForAllGames()
        val koin = koinWith(daily)
        try {
            setContent(koin, restoredSession = blob)
            composeRule.waitForIdle()

            composeRule.onNodeWithText("Zip").assertExists()
            composeRule.onNodeWithTag("play.picker").assertDoesNotExist()
            assertEquals(0, daily.todayCalls(ZipDefinition.id)) // restored, not fetched
        } finally {
            koin.close()
        }
    }

    @Test
    fun `rotation keeps the live zip controller and its hint log`() {
        val koin = koinWith()
        try {
            val restoration = StateRestorationTester(composeRule)
            restoration.setContent {
                PuzzleBoxTheme(darkTheme = false) {
                    PlayRoute(layout = PlayLayout.SinglePane, onBack = {}, onSolved = { _, _ -> })
                }
            }
            composeRule.onNodeWithTag("play.picker_zip").performClick()
            composeRule.waitForIdle()
            val live = composeRule.runOnIdle { zip(koin).also { it.hint() } }
            composeRule.runOnIdle { assertTrue(live.hintCooldownActive()) }

            // Configuration change: ON_PAUSE folds the clock before the state is saved, then
            // the composition (and its saveable) is rebuilt while the store keeps the play.
            val scenario = composeRule.activityRule.scenario
            scenario.moveToState(Lifecycle.State.STARTED)
            restoration.emulateSavedInstanceStateRestore()
            scenario.moveToState(Lifecycle.State.RESUMED)
            composeRule.waitForIdle()

            composeRule.runOnIdle {
                // Same controller ⇒ same cooldown anchor; the cooldown itself runs on the wall
                // clock, which the paused-lifecycle hop can outlast under Robolectric.
                assertSame(live, zip(koin))
                assertEquals(1, live.hintHistory.size)
            }
        } finally {
            koin.close()
        }
    }

    @Test
    fun `back from a mid-game play releases the play to the picker`() {
        val koin = koinWith()
        try {
            var back = 0
            setContent(koin, onBack = { back++ })
            composeRule.onNodeWithTag("play.picker_zip").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Zip").assertExists()

            // Device/system back through PlayRoute's BackHandler: the route pops AND
            // the play releases, so the next play entry picks again (A14).
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
            composeRule.waitForIdle()
            assertEquals(1, back)
            composeRule.onNodeWithTag("play.picker").assertExists()
        } finally {
            koin.close()
        }
    }
}
