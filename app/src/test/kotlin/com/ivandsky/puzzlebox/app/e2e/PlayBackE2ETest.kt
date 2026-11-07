package com.ivandsky.puzzlebox.app.e2e

import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.app.play.ZipPlayFixture
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.gameui.DragEvent
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.gameui.todayStatusLine
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zipui.ZipPlayController
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.profile.api.ProfileKey
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * N3 over the real shell: every way back from a game mid-play (system back, Up, predictive
 * back) returns to the hub, the left board's save lands, and the hub marks Zip in progress
 * (A10, A13). Every row asserts the navigation state (with SC2's selected tab) and the
 * visible `route.*` tag.
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application: the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = android.app.Application::class)
class PlayBackE2ETest {

    @Test
    fun `N3 system back from a game returns to the hub`() = shellRobot(playHub) {
        openZipMidGame()

        back()

        assertState(playHub)
        assertRoute(HUB)
        assertSavedAndInProgress()
    }

    @Test
    fun `N3 Up from a game returns to the hub`() = shellRobot(playHub) {
        openZipMidGame()

        tap(GAME_UP)

        assertState(playHub)
        assertRoute(HUB)
        assertSavedAndInProgress()
    }

    @Test
    fun `N3 predictive back cancelled stays on the game, committed returns to the hub`() = shellRobot(playHub) {
        openZipMidGame()

        predictiveBack(commit = false)
        assertState(inZip)
        assertRoute(GAME)

        predictiveBack(commit = true)
        assertState(playHub)
        assertRoute(HUB)
        assertSavedAndInProgress()
    }

    private fun ShellRobot.openZipMidGame() {
        openGame(ZIP)
        rule.waitUntil(TIMEOUT_MS) { rule.runOnIdle { store.peek(todaySlot)?.let { zip().session } } != null }
        rule.runOnIdle { MID_GAME.forEach { zip().extend(it) } }
    }

    /** The left board is on "disk", and the library's Zip row reads in progress (back from the board lands on Zip's page; its back shows the list). */
    private fun ShellRobot.assertSavedAndInProgress() {
        rule.waitUntil(TIMEOUT_MS) { runBlocking { graph.saved.load(todaySlot) } != null }
        tap("library.page.back")
        val inProgressRow = hasTestTag(ZIP_ROW) and (hasText(IN_PROGRESS, substring = true) or hasAnyDescendant(hasText(IN_PROGRESS, substring = true)))
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodes(inProgressRow).fetchSemanticsNodes().isNotEmpty() }
    }

    private val ShellRobot.store: PlaySessionStore get() = graph.koin.get()

    private fun ShellRobot.zip(): ZipPlayController =
        checkNotNull(store.peek(todaySlot)) { "no live zip play for $E2E_TODAY" }.controller as ZipPlayController

    private fun ZipPlayController.extend(cell: Cell) = onDragEvent(DragEvent.Extend(GridCell(cell.idx / 5, cell.idx % 5)))

    private fun stacks(
        tab: NavKey,
        home: List<NavKey> = listOf(HomeKey),
        play: List<NavKey> = listOf(PlayKey),
        profile: List<NavKey> = listOf(ProfileKey),
    ) = NavigationState(tab = tab, stacks = listOf(home, play, profile))

    private val playHub = stacks(tab = PlayKey)
    private val inZip = stacks(tab = PlayKey, play = listOf(PlayKey, GameKey(ZIP)))
    private val todaySlot = PlaySlot.Daily(ZipDefinition.id, E2E_TODAY)

    private companion object {
        const val HUB = "route.library"
        const val GAME = "route.game"
        const val GAME_UP = "game.up"
        const val ZIP = "zip"
        const val ZIP_ROW = "library.row.zip"
        // The saved-play status line's words, without its clock.
        val IN_PROGRESS = todayStatusLine(TodayStatus.InProgress(0L)).substringBefore(" ·")
        const val TIMEOUT_MS = 5_000L
        val MID_GAME: List<Cell> = ZipPlayFixture.solution.take(3)
    }
}
