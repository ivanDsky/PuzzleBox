package com.ivandsky.puzzlebox.app.e2e

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.app.play.ZipPlayFixture
import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.gameui.DragEvent
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.ZipPuzzleRef
import com.ivandsky.puzzlebox.games.zip.ZipSession
import com.ivandsky.puzzlebox.games.zipui.ZipPlayController
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.history.api.HistoryRootKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * History rows of the transition table (spec §2.6) over the real shell: N10 (a double-tapped
 * play-again pushes `GameKey(zip, 30 Oct)` once), N11 (solve → Result → back to History), N17
 * (compact: back closes the day's detail before it leaves History) and N18 / SC8 (a play-again
 * never resumes as today). Compact width: History is single-pane.
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application: the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = android.app.Application::class)
class HistoryE2ETest {

    @Test
    fun `N10 a double-tapped play-again pushes the day's game once`() = shellRobot(history, graph = playAgainGraph()) {
        openDay()

        // Both taps land in one frame, before the push recomposes the stack away.
        val playAgain = rule.onNodeWithTag(PLAY_AGAIN_TAG).fetchSemanticsNode()
        rule.runOnUiThread { repeat(2) { playAgain.config[SemanticsActions.OnClick].action?.invoke() } }
        rule.waitForIdle()

        assertState(playingAgain)
        assertRoute(GAME)
    }

    @Test
    fun `N11 solving a play-again shows its result, and back returns to History`() =
        shellRobot(history, graph = playAgainGraph()) {
            openDay()
            tap(PLAY_AGAIN_TAG)
            assertRoute(GAME)
            awaitZip(PLAY_AGAIN_DAY)

            rule.runOnIdle { ZipPlayFixture.solution.forEach { zip(PLAY_AGAIN_DAY).extend(it) } }
            assertRoute(RESULT)
            val result = state.currentStack.last()
            assertTrue("result of the replayed day: $state", result is ResultKey && result.gameId == ZIP && result.day == "$PLAY_AGAIN_DAY")
            assertEquals(listOf(HomeKey, HistoryKey(), result), state.currentStack)
            assertNoNavigation()

            back()
            assertState(history)
            assertRoute(HISTORY)
        }

    @Test
    fun `a solved day with only a record opens its result in place, and back returns to History`() {
        val graph = playAgainGraph().apply {
            records.setRecords(
                listOf(
                    CompletionRecord(
                        puzzleRef = ZipPuzzleRef(PuzzleKey.Daily(PLAY_AGAIN_DAY, 1)),
                        time = 61_000,
                        hints = 2,
                        stats = RecordStats(schemaVersion = 1, blob = "{}"),
                    ),
                ),
            )
        }
        shellRobot(playingAgain, graph = graph) {
            awaitTag(RESULT)

            val result = ResultKey(ZIP, "$PLAY_AGAIN_DAY", 61_000, 2)
            assertEquals(listOf(HomeKey, HistoryKey(), result), state.currentStack)

            back()
            assertState(history)
            assertRoute(HISTORY)
        }
    }

    @Test
    fun `N17 compact back closes the day's detail, then leaves History`() = shellRobot(history, graph = playAgainGraph()) {
        openDay()

        back()
        assertState(history)
        assertRoute(HISTORY)
        scrollToRow()
        rule.onNodeWithTag(DETAIL, useUnmergedTree = true).assertDoesNotExist()

        back()
        assertState(stacks(tab = HomeKey))
        assertRoute(HOME)
    }

    @Test
    fun `N18 a play-again mid-game never resumes as today's Zip`() = shellRobot(history, graph = playAgainGraph()) {
        openDay()
        tap(PLAY_AGAIN_TAG)
        awaitZip(PLAY_AGAIN_DAY)
        rule.runOnIdle { MID_GAME.forEach { zip(PLAY_AGAIN_DAY).extend(it) } }

        switchTab(PlayKey)
        openGame(ZIP)

        assertState(stacks(tab = PlayKey, home = playingAgain.stacks.first(), play = listOf(PlayKey, GameKey(ZIP))))
        assertRoute(GAME)
        val today = awaitZip(E2E_TODAY)
        assertEquals("today's board is a fresh session of today", E2E_TODAY, (today.ref?.key as? PuzzleKey.Daily)?.date)
        assertEquals("today's board starts empty", emptyList<Cell>(), today.path)
        assertEquals("the replay keeps its own trace", MID_GAME, rule.runOnIdle { zip(PLAY_AGAIN_DAY).session?.path })
    }

    /** Opens the played-again day's detail from History's list. */
    private fun ShellRobot.openDay() {
        assertRoute(HISTORY)
        scrollToRow()
        tap(ROW)
        awaitTag(DETAIL)
    }

    /** Scrolls History's list until [ROW] composes: the stats and weeks cards sit above the rows. */
    private fun ShellRobot.scrollToRow() {
        rule.waitUntil(TIMEOUT_MS) {
            runCatching { rule.onNodeWithTag(LIST).performScrollToNode(hasTestTag(ROW)) }.isSuccess
        }
    }

    private val ShellRobot.store: PlaySessionStore get() = graph.koin.get()

    private fun ShellRobot.zip(day: LocalDate): ZipPlayController =
        checkNotNull(store.peek(PlaySlot.Daily(ZipDefinition.id, day))) { "no live zip play for $day" }.controller as ZipPlayController

    /** Waits until [day]'s live Zip has its session, and returns it. */
    private fun ShellRobot.awaitZip(day: LocalDate): ZipSession {
        rule.waitUntil(TIMEOUT_MS) { rule.runOnIdle { store.peek(PlaySlot.Daily(ZipDefinition.id, day)) }?.let { (it.controller as ZipPlayController).session } != null }
        return checkNotNull(rule.runOnIdle { zip(day).session })
    }

    private fun ZipPlayController.extend(cell: Cell) = onDragEvent(DragEvent.Extend(GridCell(cell.idx / 5, cell.idx % 5)))

    /** Today's Zip is fresh; [PLAY_AGAIN_DAY] is a captured, cached day History can play again. */
    private fun playAgainGraph() = E2eKoinGraph(E2E_TODAY).apply {
        daily.setHistory(ZipDefinition.id, listOf(ZipPlayFixture.entry(E2E_TODAY), ZipPlayFixture.entry(PLAY_AGAIN_DAY)))
        daily.setCached(ZipDefinition.id, PLAY_AGAIN_DAY, ZipPlayFixture.envelope())
    }

    private fun stacks(
        tab: NavKey,
        home: List<NavKey> = listOf(HomeKey),
        play: List<NavKey> = listOf(PlayKey),
        history: List<NavKey> = listOf(HistoryRootKey),
        create: List<NavKey> = listOf(CreateKey),
    ) = NavigationState(tab = tab, stacks = listOf(home, play, history, create))

    private val history = stacks(tab = HomeKey, home = listOf(HomeKey, HistoryKey()))
    private val playingAgain = stacks(tab = HomeKey, home = listOf(HomeKey, HistoryKey(), GameKey(ZIP, "$PLAY_AGAIN_DAY")))

    private companion object {
        const val HOME = "route.home"
        const val HISTORY = "route.history"
        const val GAME = "route.game"
        const val RESULT = "route.result"
        // PLAY_AGAIN_DAY is unfinished, so its detail offers "Keep going".
        const val PLAY_AGAIN_TAG = "history.keep_going"
        const val LIST = "history.list"
        const val ZIP = "zip"
        const val TIMEOUT_MS = 5_000L
        val PLAY_AGAIN_DAY: LocalDate = LocalDate.of(2026, 10, 30)
        val ROW = "history.row.$ZIP/$PLAY_AGAIN_DAY"
        val DETAIL = "history.detail.$ZIP.$PLAY_AGAIN_DAY"
        val MID_GAME: List<Cell> = ZipPlayFixture.solution.take(3)
    }
}
