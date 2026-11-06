package com.ivandsky.puzzlebox.app.e2e

import androidx.compose.ui.test.onAllNodesWithTag
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.app.play.ZipPlayFixture
import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.gameui.DragEvent
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zipui.ZipPlayController
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import com.ivandsky.puzzlebox.profile.api.ProfileKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A Result keeps its own solve's verdict (A9, F2): today's Zip solved from Home leaves a
 * Result on Home's stack; the same slot reopened on Play replays the shared live play and
 * solves it again, slower. Home's Result still shows its first solve's First solve badge —
 * neither the replay's in-flight state nor the second solve's verdict leaks into it.
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application: the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = android.app.Application::class)
class ResultIsolationE2ETest {

    @Test
    fun `a Result on Home keeps its badge while Play replays and re-solves the same slot`() = shellRobot(graph = numberedGraph()) {
        tap(HOME_PLAY)
        solveZip()
        val first = state.currentStack.last()
        assertTrue("Home's top is the first solve's Result: $state", first is ResultKey)
        awaitBadge()

        switchTab(PlayKey)
        openGame(ZIP)
        assertRoute(GAME)
        switchTab(HomeKey)
        assertState(stacks(tab = HomeKey, home = listOf(HomeKey, first), play = listOf(PlayKey, GameKey(ZIP))))
        assertRoute(RESULT)
        awaitBadge()

        switchTab(PlayKey)
        solveZip(takingMs = SLOWER_MS) // slower than the first, so a distinct solve and no record
        val second = state.currentStack.last()
        assertTrue("Play's top is the second solve's Result: $state", second is ResultKey)
        assertNotEquals("two distinct solves", (first as ResultKey).timeMs, (second as ResultKey).timeMs)

        switchTab(HomeKey)
        assertEquals(listOf(HomeKey, first), state.stacks.first())
        assertRoute(RESULT)
        awaitBadge()
    }

    /** A numbered daily (#592): an unnumbered one is practice and never earns a verdict badge. */
    private fun numberedGraph() = E2eKoinGraph(E2E_TODAY).apply {
        daily.setHistory(ZipDefinition.id, listOf(ZipPlayFixture.entry(E2E_TODAY)))
    }

    /**
     * Waits for a fresh board on today's live Zip (a replay starts empty), then solves it with
     * at least [takingMs] on Zip's wall clock between the first move (which starts it) and the last.
     */
    private fun ShellRobot.solveZip(takingMs: Long = 0L) {
        rule.waitUntil(TIMEOUT_MS) {
            rule.runOnIdle { store.peek(todaySlot)?.let { zip().session?.path } }.let { it != null && it != ZipPlayFixture.solution }
        }
        val startedAt = System.currentTimeMillis()
        rule.runOnIdle { zip().extend(ZipPlayFixture.solution.first()) }
        rule.waitUntil(TIMEOUT_MS) { System.currentTimeMillis() - startedAt > takingMs }
        rule.runOnIdle { ZipPlayFixture.solution.drop(1).forEach { zip().extend(it) } }
        awaitTag(RESULT)
    }

    private fun ShellRobot.awaitBadge() {
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodesWithTag(BADGE).fetchSemanticsNodes().isNotEmpty() }
    }

    private val ShellRobot.store: PlaySessionStore get() = graph.koin.get()

    private fun ShellRobot.zip(): ZipPlayController =
        checkNotNull(store.peek(todaySlot)) { "no live zip play for $E2E_TODAY" }.controller as ZipPlayController

    private fun ZipPlayController.extend(cell: Cell) = onDragEvent(DragEvent.Extend(GridCell(cell.idx / 5, cell.idx % 5)))

    private fun stacks(
        tab: NavKey,
        home: List<NavKey> = listOf(HomeKey),
        play: List<NavKey> = listOf(PlayKey),
        create: List<NavKey> = listOf(CreateKey),
        profile: List<NavKey> = listOf(ProfileKey),
    ) = NavigationState(tab = tab, stacks = listOf(home, play, create, profile))

    private val todaySlot = PlaySlot.Daily(ZipDefinition.id, E2E_TODAY)

    private companion object {
        const val GAME = "route.game"
        const val RESULT = "route.result"
        const val BADGE = "result.record_badge"
        const val HOME_PLAY = "home.today_play"
        const val ZIP = "zip"
        const val SLOWER_MS = 100L
        const val TIMEOUT_MS = 5_000L
    }
}
