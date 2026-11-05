package com.ivandsky.puzzlebox.app.e2e

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.app.play.ZipPlayFixture
import com.ivandsky.puzzlebox.gameui.DragEvent
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.ZipSession
import com.ivandsky.puzzlebox.games.zipui.ZipPlayController
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import com.ivandsky.puzzlebox.profile.api.ProfileKey
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * History rows of the transition table (spec §2.6) over the real shell: N10 (a double-tapped
 * replay pushes `GameKey(zip, 30 Oct)` once), N11 (solve → Result → back to History), N17
 * (compact: back closes the day's detail before it leaves History) and N18 / SC8 (a replay
 * never resumes as today). Compact width: History is single-pane.
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application: the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = android.app.Application::class)
class HistoryE2ETest {

    @Test
    fun `N10 a double-tapped replay pushes the day's game once`() = shellRobot(history, graph = replayableGraph()) {
        openDay()

        // Both taps land in one frame, before the push recomposes the stack away.
        val replay = rule.onNodeWithTag(REPLAY).fetchSemanticsNode()
        rule.runOnUiThread { repeat(2) { replay.config[SemanticsActions.OnClick].action?.invoke() } }
        rule.waitForIdle()

        assertState(replaying)
        assertRoute(GAME)
    }

    @Test
    fun `N11 solving a replay shows its result, and back returns to History`() =
        shellRobot(history, graph = replayableGraph()) {
            openDay()
            tap(REPLAY)
            assertRoute(GAME)
            awaitZip(REPLAY_DAY)

            rule.runOnIdle { ZipPlayFixture.solution.forEach { zip(REPLAY_DAY).extend(it) } }
            assertRoute(RESULT)
            val result = state.currentStack.last()
            assertTrue("result of the replayed day: $state", result is ResultKey && result.gameId == ZIP && result.day == "$REPLAY_DAY")
            assertEquals(listOf(HomeKey, HistoryKey, result), state.currentStack)
            assertSelectedTab(HomeKey)

            back()
            assertState(history)
            assertRoute(HISTORY)
        }

    @Test
    fun `N17 compact back closes the day's detail, then leaves History`() = shellRobot(history, graph = replayableGraph()) {
        openDay()

        back()
        assertState(history)
        assertRoute(HISTORY)
        awaitTag(ROW)
        rule.onNodeWithTag(DETAIL, useUnmergedTree = true).assertDoesNotExist()

        back()
        assertState(stacks(tab = HomeKey))
        assertRoute(HOME)
    }

    @Test
    fun `N18 a replay mid-game never resumes as today's Zip`() = shellRobot(history, graph = replayableGraph()) {
        openDay()
        tap(REPLAY)
        awaitZip(REPLAY_DAY)
        rule.runOnIdle { MID_GAME.forEach { zip(REPLAY_DAY).extend(it) } }

        tapTab(PlayKey)
        openGame(ZIP)

        assertState(stacks(tab = PlayKey, home = replaying.stacks.first(), play = listOf(PlayKey, GameKey(ZIP))))
        assertRoute(GAME)
        val today = awaitZip(E2E_TODAY)
        assertEquals("today's board is a fresh session of today", E2E_TODAY, today.ref?.date)
        assertEquals("today's board starts empty", emptyList<Cell>(), today.path)
        assertEquals("the replay keeps its own trace", MID_GAME, rule.runOnIdle { zip(REPLAY_DAY).session?.path })
    }

    /** Opens the replayed day's detail from History's list. */
    private fun ShellRobot.openDay() {
        assertRoute(HISTORY)
        tap(ROW)
        awaitTag(DETAIL)
    }

    private val ShellRobot.store: PlaySessionStore get() = graph.koin.get()

    private fun ShellRobot.zip(day: LocalDate): ZipPlayController =
        checkNotNull(store.peek(PlaySlot(ZipDefinition.id, day))) { "no live zip play for $day" }.controller as ZipPlayController

    /** Waits until [day]'s live Zip has its session, and returns it. */
    private fun ShellRobot.awaitZip(day: LocalDate): ZipSession {
        rule.waitUntil(TIMEOUT_MS) { rule.runOnIdle { store.peek(PlaySlot(ZipDefinition.id, day)) }?.let { (it.controller as ZipPlayController).session } != null }
        return checkNotNull(rule.runOnIdle { zip(day).session })
    }

    private fun ZipPlayController.extend(cell: Cell) = onDragEvent(DragEvent.Extend(GridCell(cell.idx / 5, cell.idx % 5)))

    /** Today's Zip is fresh; [REPLAY_DAY] is a captured, cached day History can replay. */
    private fun replayableGraph() = E2eKoinGraph(E2E_TODAY).apply {
        daily.setHistory(ZipDefinition.id, listOf(ZipPlayFixture.entry(E2E_TODAY), ZipPlayFixture.entry(REPLAY_DAY)))
        daily.setCached(ZipDefinition.id, REPLAY_DAY, ZipPlayFixture.envelope())
    }

    private fun stacks(
        tab: NavKey,
        home: List<NavKey> = listOf(HomeKey),
        play: List<NavKey> = listOf(PlayKey),
        profile: List<NavKey> = listOf(ProfileKey),
    ) = NavigationState(tab = tab, stacks = listOf(home, play, profile))

    private val history = stacks(tab = HomeKey, home = listOf(HomeKey, HistoryKey))
    private val replaying = stacks(tab = HomeKey, home = listOf(HomeKey, HistoryKey, GameKey(ZIP, "$REPLAY_DAY")))

    private companion object {
        const val HOME = "route.home"
        const val HISTORY = "route.history"
        const val GAME = "route.game"
        const val RESULT = "route.result"
        const val REPLAY = "history.replay"
        const val ZIP = "zip"
        const val TIMEOUT_MS = 5_000L
        val REPLAY_DAY: LocalDate = LocalDate.of(2026, 10, 30)
        val ROW = "history.row.$ZIP/$REPLAY_DAY"
        val DETAIL = "history.detail.$ZIP.$REPLAY_DAY"
        val MID_GAME: List<Cell> = ZipPlayFixture.solution.take(3)
    }
}
