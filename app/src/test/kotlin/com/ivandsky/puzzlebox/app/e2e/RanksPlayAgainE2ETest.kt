package com.ivandsky.puzzlebox.app.e2e

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.play.ZipPlayFixture
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
import com.ivandsky.puzzlebox.play.api.CommunityKey
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
 * The Ranks hub's day "Your solve" play-again (spec §2.6 N10, N11, N18 / SC8) over the real shell, the hub being
 * where the former History detail's play-again lives: N10 (a double-tapped play-again pushes
 * `GameKey(zip, 30 Oct)` once), N11 (solving the replayed day shows its result, and back returns to the hub) and N18 (a
 * play-again never resumes as today). The hub opens on Zip's board at the played-again day, which holds a solve, so
 * its game opens on that solve's result and the result's Play again starts the fresh attempt.
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class)
class RanksPlayAgainE2ETest {

    @Test
    fun `N10 a double-tapped play-again pushes the day's game once`() = shellRobot(ranks, graph = playAgainGraph()) {
        awaitTag(PLAY_AGAIN_TAG)

        // Both taps land in one frame, before the push recomposes the stack away.
        val playAgain = rule.onNodeWithTag(PLAY_AGAIN_TAG).fetchSemanticsNode()
        rule.runOnUiThread { repeat(2) { playAgain.config[SemanticsActions.OnClick].action?.invoke() } }
        rule.waitForIdle()

        // The day holds a solve, so its game opens on that solve's result, in place of the pushed game.
        assertState(solved)
        assertRoute(RESULT)
    }

    @Test
    fun `N11 playing a day again shows its result, and back returns to the hub`() = shellRobot(ranks, graph = playAgainGraph()) {
        awaitTag(PLAY_AGAIN_TAG)
        tap(PLAY_AGAIN_TAG)
        assertRoute(RESULT)
        playAgainFromResult()
        assertRoute(GAME)
        awaitZip(PLAY_AGAIN_DAY)

        rule.runOnIdle { ZipPlayFixture.solution.forEach { zip(PLAY_AGAIN_DAY).extend(it) } }
        assertRoute(RESULT)
        val result = state.currentStack.last()
        assertTrue("result of the replayed day: $state", result is ResultKey && result.gameId == ZIP && result.day == "$PLAY_AGAIN_DAY")
        assertEquals(listOf(HistoryRootKey, HistoryKey(ZIP, "$PLAY_AGAIN_DAY"), result), state.currentStack)
        assertNoNavigation()

        back()
        assertState(ranks)
        assertRoute(RANKS)
    }

    @Test
    fun `N18 a play-again mid-game never resumes as today's Zip`() = shellRobot(ranks, graph = playAgainGraph()) {
        awaitTag(PLAY_AGAIN_TAG)
        tap(PLAY_AGAIN_TAG)
        awaitTag(RESULT)
        playAgainFromResult()
        awaitZip(PLAY_AGAIN_DAY)
        rule.runOnIdle { MID_GAME.forEach { zip(PLAY_AGAIN_DAY).extend(it) } }

        switchTab(PlayKey)
        openGame(ZIP)

        assertState(stacks(tab = PlayKey, history = playingAgain.stackOf(HistoryRootKey), play = listOf(PlayKey, GameKey(ZIP))))
        assertRoute(GAME)
        val today = awaitZip(E2E_TODAY)
        assertEquals("today's board is a fresh session of today", E2E_TODAY, (today.ref?.key as? PuzzleKey.Daily)?.date)
        assertEquals("today's board starts empty", emptyList<Cell>(), today.path)
        assertEquals("the replay keeps its own trace", MID_GAME, rule.runOnIdle { zip(PLAY_AGAIN_DAY).session?.path })
    }

    /** The result's own Play again starts the fresh attempt; it sits below the fold of the test window. */
    private fun ShellRobot.playAgainFromResult() {
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodesWithTag(RESULT_PLAY_AGAIN).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag(RESULT_PLAY_AGAIN).performScrollTo().performClick()
        rule.waitForIdle()
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

    /** Today's Zip is fresh; [PLAY_AGAIN_DAY] is a captured, cached day with a kept solve the hub's Your solve row can play again. */
    private fun playAgainGraph() = E2eKoinGraph(E2E_TODAY).apply {
        daily.setHistory(ZipDefinition.id, listOf(ZipPlayFixture.entry(E2E_TODAY), ZipPlayFixture.entry(PLAY_AGAIN_DAY)))
        daily.setCached(ZipDefinition.id, PLAY_AGAIN_DAY, ZipPlayFixture.envelope())
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

    private fun stacks(
        tab: NavKey,
        home: List<NavKey> = listOf(HomeKey),
        play: List<NavKey> = listOf(PlayKey),
        history: List<NavKey> = listOf(HistoryRootKey),
    ) = NavigationState(tab = tab, stacks = listOf(home, play, listOf(CommunityKey), history))

    private val opening = HistoryKey(ZIP, "$PLAY_AGAIN_DAY")
    private val ranks = stacks(tab = HistoryRootKey, history = listOf(HistoryRootKey, opening))
    private val solved = stacks(tab = HistoryRootKey, history = listOf(HistoryRootKey, opening, ResultKey(ZIP, "$PLAY_AGAIN_DAY", 61_000, 2)))
    private val playingAgain = stacks(tab = HistoryRootKey, history = listOf(HistoryRootKey, opening, GameKey(ZIP, "$PLAY_AGAIN_DAY")))

    private companion object {
        const val RANKS = "route.ranks"
        const val GAME = "route.game"
        const val RESULT = "route.result"
        const val PLAY_AGAIN_TAG = "ranks.yoursolve.playagain"
        const val RESULT_PLAY_AGAIN = "result.playAgain"
        const val ZIP = "zip"
        const val TIMEOUT_MS = 5_000L
        val PLAY_AGAIN_DAY: LocalDate = LocalDate.of(2026, 10, 30)
        val MID_GAME: List<Cell> = ZipPlayFixture.solution.take(3)
    }
}
