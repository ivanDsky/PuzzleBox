package com.ivandsky.puzzlebox.app.device

import android.content.pm.ActivityInfo
import androidx.activity.BackEventCompat
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.app.MainActivity
import com.ivandsky.puzzlebox.app.e2e.E2E_TODAY
import com.ivandsky.puzzlebox.app.e2e.E2eKoinGraph
import com.ivandsky.puzzlebox.app.nav.topLevelDestinations
import com.ivandsky.puzzlebox.app.play.ZipPlayFixture
import com.ivandsky.puzzlebox.gameui.DragEvent
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zipui.ZipPlayController
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.play.PlaySessionStore
import org.junit.Assert.assertTrue
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * The real [MainActivity] on a device, over [E2eKoinGraph]'s fakes: the graph starts before
 * the activity launches and stops after it finishes. The vocabulary mirrors the JVM
 * ShellRobot (tabs, taps, system and predictive back) plus what only a device has: a real
 * orientation change that recreates the activity and re-measures the window.
 */
class DeviceShell : TestRule {

    val graph = E2eKoinGraph(E2E_TODAY)
    val rule = createAndroidComposeRule<MainActivity>()

    private val todaySlot = PlaySlot.Daily(ZipDefinition.id, E2E_TODAY)

    override fun apply(base: Statement, description: Description): Statement {
        val inner = rule.apply(base, description)
        return object : Statement() {
            override fun evaluate() {
                graph.start()
                try {
                    inner.evaluate()
                } finally {
                    graph.stop()
                }
            }
        }
    }

    fun tapTab(key: NavKey) = tap(topLevelDestinations.single { it.key == key }.tag)

    fun tap(tag: String) {
        awaitTag(tag)
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    /**
     * Opens [gameId]'s page in the Play library (a phone first leaves another game's page,
     * since back from a board returns to the page it started from) and plays today once
     * today has loaded.
     */
    fun openGame(gameId: String) {
        val page = "library.page.$gameId"
        if (rule.onAllNodesWithTag(page).fetchSemanticsNodes().isEmpty()) {
            if (rule.onAllNodesWithTag("library.page.back").fetchSemanticsNodes().isNotEmpty()) tap("library.page.back")
            tap("library.row.$gameId")
        }
        awaitTag(page)
        // Playable once today has loaded: a loading card's action is disabled, an offline one retries.
        val offlineCard = hasTestTag("card.status_mark.offline") and hasAnyAncestor(hasTestTag("library.day_card"))
        rule.waitUntil(TIMEOUT_MS) {
            rule.onAllNodes(hasTestTag("library.play") and isEnabled()).fetchSemanticsNodes().isNotEmpty() &&
                rule.onAllNodes(offlineCard, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
        tap("library.play")
        awaitTag(GAME)
        rule.waitUntil(TIMEOUT_MS) { rule.runOnIdle { liveZip()?.session } != null }
    }

    /** Taps the node showing [text], scrolling its screen to it first. */
    fun tapText(text: String) {
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(text).performScrollTo().performClick()
        rule.waitForIdle()
    }

    fun awaitTag(tag: String) {
        rule.waitUntil(TIMEOUT_MS) {
            rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Traces the first three cells of today's Zip and returns the board's path. */
    fun playMidGame(): List<Cell> {
        rule.runOnIdle { MID_GAME.forEach { checkNotNull(liveZip()).extend(it) } }
        return zipPath()
    }

    /** Today's live Zip trace. */
    fun zipPath(): List<Cell> = checkNotNull(rule.runOnIdle { liveZip()?.session?.path }) { "no live zip board" }

    /** System back: one press through the activity's dispatcher. */
    fun back() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    /** A predictive back gesture from the left edge, scrubbed halfway, then [commit]ted or cancelled. */
    fun predictiveBack(commit: Boolean) {
        val dispatcher = rule.activity.onBackPressedDispatcher
        rule.runOnUiThread { dispatcher.dispatchOnBackStarted(backEvent(0f)) }
        rule.waitForIdle()
        rule.runOnUiThread { dispatcher.dispatchOnBackProgressed(backEvent(0.5f)) }
        rule.waitForIdle()
        rule.runOnUiThread { if (commit) dispatcher.onBackPressed() else dispatcher.dispatchOnBackCancelled() }
        rule.waitForIdle()
    }

    /** Requests [orientation] and waits until the recreated window has the matching shape. */
    fun rotate(orientation: Int) {
        val landscape = orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        rule.runOnUiThread { rule.activity.requestedOrientation = orientation }
        rule.waitUntil(TIMEOUT_MS) {
            val window = rule.onRoot().fetchSemanticsNode().boundsInRoot
            (window.width > window.height) == landscape
        }
        rule.waitForIdle()
    }

    fun assertRoute(tag: String) {
        awaitTag(tag)
        rule.onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed()
    }

    fun assertSelectedTab(key: NavKey) {
        rule.onNodeWithTag(topLevelDestinations.single { it.key == key }.tag).assertIsSelected()
    }

    /** Immersive play (N2): neither bar nor rail shows a tab. */
    fun assertNoNavigation() {
        topLevelDestinations.forEach { destination ->
            rule.onNodeWithTag(destination.tag).assertDoesNotExist()
        }
    }

    /** Compact: the tabs sit side by side along the window's bottom edge. */
    fun assertBottomBar() {
        val tabs = tabBounds()
        val window = rule.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("tabs share one row: $tabs", tabs.all { it.top == tabs.first().top })
        assertTrue("bar hugs the bottom: $tabs in $window", tabs.first().bottom >= window.bottom - window.height / 4)
    }

    /** Expanded: the tabs stack in one column along the window's start edge. */
    fun assertRail() {
        val tabs = tabBounds()
        val window = rule.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("tabs share one column: $tabs", tabs.all { it.left == tabs.first().left })
        assertTrue("rail hugs the start: $tabs in $window", tabs.first().right <= window.left + window.width / 4)
    }

    private fun tabBounds(): List<Rect> = topLevelDestinations.map {
        rule.onNodeWithTag(it.tag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
    }

    private fun liveZip(): ZipPlayController? =
        graph.koin.get<PlaySessionStore>().peek(todaySlot)?.controller as? ZipPlayController

    private fun ZipPlayController.extend(cell: Cell) =
        onDragEvent(DragEvent.Extend(GridCell(cell.idx / ZIP_SIZE, cell.idx % ZIP_SIZE)))

    private fun backEvent(progress: Float) = BackEventCompat(0f, 0f, progress, BackEventCompat.EDGE_LEFT)

    companion object {
        const val HOME = "route.home"
        const val HISTORY = "route.history"
        const val LIBRARY = "route.library"
        const val GAME = "route.game"
        const val ZIP = "zip"
        private const val ZIP_SIZE = 5
        private const val TIMEOUT_MS = 10_000L
        private val MID_GAME: List<Cell> = ZipPlayFixture.solution.take(3)
    }
}
