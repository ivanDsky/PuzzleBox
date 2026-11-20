package com.ivandsky.puzzlebox.app.e2e

import android.content.Context
import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.v2.AndroidComposeTestRule as v2AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.MainActivity
import com.ivandsky.puzzlebox.app.play.TangoPlayFixture
import com.ivandsky.puzzlebox.app.shortcut.ShortcutIntents
import com.ivandsky.puzzlebox.app.shortcut.ShortcutTarget
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.QueensPuzzleRef
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.games.strands.StrandsPuzzleRef
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzleRef
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.TangoPuzzleRef
import com.ivandsky.puzzlebox.games.tangoui.TangoPlacement
import com.ivandsky.puzzlebox.games.tangoui.TangoPlayController
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.ZipPuzzleRef
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.play.PlaySessionStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A launcher shortcut's intent over the real activity: a game's shortcut lands on its board with the Games list beneath
 * it and Back walks Games, Today, out; Up next picks the unfinished game, else the first unsolved, else Today, as the
 * Today card does at the tap; an unknown game, or an intent relaunched from Recents, opens the app as usual.
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w411dp-h891dp")
class ShortcutLaunchE2ETest {

    private lateinit var rule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

    private val graph = E2eKoinGraph(E2E_TODAY)
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val store: PlaySessionStore get() = graph.koin.get()

    @Before
    fun startGraph() = graph.start()

    @After
    fun stopGraph() = graph.stop()

    @Test
    fun `a game's shortcut lands on its board with the Games list beneath, and Back walks Games, Today, out`() =
        launching(shortcut(ZIP)) {
            assertBoardOf(ZIP)

            back()
            assertRoute("route.library")
            rule.onNodeWithTag("nav.tab.play").assertIsSelected()

            back()
            assertRoute("route.home")

            back()
            assertTrue("the last Back leaves the app", rule.activity.isFinishing)
        }

    @Test
    fun `a shortcut while the app runs on Ranks lands on the board, once however often it is tapped`() =
        launching(fromRecents(shortcut(ZIP))) {
            // Relaunched from Recents, the intent that first started the task is not applied again.
            assertRoute("route.home")
            tap("nav.tab.ranks")
            assertRoute("route.ranks")

            repeat(2) { newIntent(shortcut(TANGO)) }
            assertBoardOf(TANGO)

            back()
            assertRoute("route.library")
        }

    @Test
    fun `Up next on a fresh day opens the first game, and the next after it is solved`() = launching(shortcut(UP_NEXT)) {
        assertBoardOf(ZIP)

        back()
        solve(ZIP)
        newIntent(shortcut(UP_NEXT))
        assertBoardOf(TANGO)
    }

    @Test
    fun `Up next opens the unfinished game ahead of the first unsolved one`() = launching(shortcut(TANGO)) {
        val tango = awaitTango()
        rule.runOnIdle { with(TangoPlayFixture.placements.first()) { tango.onPlacement(TangoPlacement(first, second)) } }
        back()
        rule.waitUntil(TIMEOUT_MS) { runBlocking { graph.saved.load(TANGO_TODAY) } != null }
        assertRoute("route.library")

        newIntent(shortcut(UP_NEXT))

        assertBoardOf(TANGO)
        assertTrue("Zip, first in order but not started, stays closed", store.peek(ZIP_TODAY) == null)
    }

    @Test
    fun `Up next with every game solved opens the Today tab`() = launching(fromRecents(shortcut(UP_NEXT))) {
        listOf(ZIP, TANGO, QUEENS, MINI_SUDOKU, STRANDS).forEach(::solve)
        tap("nav.tab.play")
        assertRoute("route.library")

        newIntent(shortcut(UP_NEXT))

        assertRoute("route.home")
        rule.onNodeWithTag("nav.tab.home").assertIsSelected()
    }

    @Test
    fun `a Today intent lands on the Today tab at its root from a board, from Games and from Profile`() =
        launching(shortcut(ZIP)) {
            assertBoardOf(ZIP)
            newIntent(shortcut(ShortcutTarget.Today))
            assertTodayRoot()

            // Today leaves the Games stack as it was: the board is still there, one Back from the list.
            tap("nav.tab.play")
            assertRoute("route.game")
            back()
            assertRoute("route.library")
            newIntent(shortcut(ShortcutTarget.Today))
            assertTodayRoot()

            tap("shell.avatar")
            assertRoute("route.profile")
            newIntent(shortcut(ShortcutTarget.Today))
            assertTodayRoot()
        }

    @Test
    fun `a shortcut for a game this build lacks opens the app as usual`() =
        launching(Intent(ShortcutIntents.ACTION).setClass(context, MainActivity::class.java).putExtra(ShortcutIntents.EXTRA_GAME_ID, "gone")) {
            assertRoute("route.home")

            back()
            assertTrue("Back leaves the app: nothing was stacked", rule.activity.isFinishing)
        }

    @Test
    fun `a consumed shortcut is not applied again when the activity is recreated`() = launching(shortcut(ZIP)) {
        assertBoardOf(ZIP)

        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        assertRoute("route.game")

        back()
        assertRoute("route.library")
        back()
        assertRoute("route.home")
    }

    private fun assertTodayRoot() {
        assertRoute("route.home")
        rule.onNodeWithTag("nav.tab.home").assertIsSelected()
    }

    private fun assertBoardOf(game: GameId) {
        assertRoute("route.game")
        rule.waitUntil(TIMEOUT_MS) { rule.runOnIdle { store.peek(PlaySlot.Daily(game, E2E_TODAY)) } != null }
    }

    private fun awaitTango(): TangoPlayController {
        rule.waitUntil(TIMEOUT_MS) { rule.runOnIdle { (store.peek(TANGO_TODAY)?.controller as? TangoPlayController)?.session } != null }
        return rule.runOnIdle { store.peek(TANGO_TODAY)?.controller as TangoPlayController }
    }

    /** [game]'s today, solved on the books (a completion record, as the Today card reads it). */
    private fun solve(game: GameId) {
        val ref = refOf(game)
        runBlocking { graph.records.onCompleted(CompletionRecord(ref, time = 60_000, hints = 0, stats = RecordStats(1, "{}"))) }
        rule.waitForIdle()
    }

    private fun refOf(game: GameId): PuzzleRef {
        val key = PuzzleKey.Daily(E2E_TODAY, 1)
        return when (game) {
            ZIP -> ZipPuzzleRef(key)
            TANGO -> TangoPuzzleRef(key)
            QUEENS -> QueensPuzzleRef(key)
            MINI_SUDOKU -> SudokuPuzzleRef(game, key)
            STRANDS -> StrandsPuzzleRef(key)
            else -> error("no daily ref for $game")
        }
    }

    /** Runs [block] over [MainActivity] started by [intent], torn down by the compose rule. */
    private fun launching(intent: Intent, block: () -> Unit) {
        rule = v2AndroidComposeTestRule(ActivityScenarioRule(intent), activityProvider = ::activityOf)
        val run = object : Statement() {
            override fun evaluate() = block()
        }
        rule.apply(run, Description.createTestDescription(ShortcutLaunchE2ETest::class.java, "launching")).evaluate()
    }

    private fun activityOf(scenario: ActivityScenarioRule<MainActivity>): MainActivity {
        var activity: MainActivity? = null
        scenario.scenario.onActivity { activity = it }
        return checkNotNull(activity) { "MainActivity is not running" }
    }

    private fun newIntent(intent: Intent) {
        rule.runOnUiThread { InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(rule.activity, intent) }
        rule.waitForIdle()
    }

    private fun back() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    // ActivityScenario tracks its activity by intent, and onNewIntent makes the new one current:
    // the warm rows start on the same intent (flags don't count) so their teardown still finds it.
    private fun fromRecents(intent: Intent) = intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)

    private fun shortcut(target: ShortcutTarget) = ShortcutIntents.intent(context, target)

    private fun shortcut(game: GameId) = shortcut(ShortcutTarget.Game(game))

    /** [tag] is the only `route.*` on screen once the transition settles. */
    private fun assertRoute(tag: String) {
        rule.waitUntil(TIMEOUT_MS) { routes() == listOf(tag) }
    }

    private fun routes(): List<String> = rule.onAllNodes(isRoute, useUnmergedTree = true).fetchSemanticsNodes()
        .map { it.config[SemanticsProperties.TestTag] }

    private companion object {
        val UP_NEXT = ShortcutTarget.UpNext
        val ZIP = ZipDefinition.id
        val TANGO = TangoDefinition.id
        val QUEENS = QueensDefinition.id
        val MINI_SUDOKU = SudokuGames.MINI.id
        val STRANDS = StrandsDefinition.id
        val ZIP_TODAY = PlaySlot.Daily(ZIP, E2E_TODAY)
        val TANGO_TODAY = PlaySlot.Daily(TANGO, E2E_TODAY)
        val isRoute = SemanticsMatcher("test tag route.*") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("route.") == true
        }
        const val TIMEOUT_MS = 10_000L
    }
}
