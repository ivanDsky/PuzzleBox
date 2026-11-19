package com.ivandsky.puzzlebox.games.tangoui.trainer

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.games.tango.TangoCell
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The snippet board's tap, judged by the move check and reported once, on the 6×6 daily byid-1. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class TangoSnippetBoardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val reports = mutableListOf<TrainerVerdict>()

    private fun show(snippet: TangoSnippet, compute: CoroutineDispatcher = Dispatchers.Default) {
        reports.clear()
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                TangoSnippetBoard(snippet, compute, { reports += it }, Modifier.fillMaxWidth())
            }
        }
        composeRule.waitForIdle()
    }

    /** Tap the center of cell [idx] on the board node itself. */
    private fun tap(snippet: TangoSnippet, idx: Int) {
        val n = snippet.puzzle.n
        composeRule.onNodeWithTag(TANGO_SNIPPET_BOARD_TAG).performTouchInput {
            click(Offset(width * (idx % n + 0.5f) / n, height * (idx / n + 0.5f) / n))
        }
        composeRule.waitForIdle()
    }

    private fun awaitReports(count: Int) = composeRule.waitUntil(timeoutMillis = 10_000) { reports.size >= count }

    private fun assertTargetReportsRight(snippet: TangoSnippet) {
        show(snippet)

        tap(snippet, snippet.cut.step.idx)
        awaitReports(1)

        assertEquals(listOf(TrainerVerdict.Right), reports)
    }

    @Test
    fun `tapping a sun step's target cell reports Right once`() =
        assertTargetReportsRight(TangoTrainerFixtures.snippets.first { it.value == TangoCell.SUN })

    @Test
    fun `tapping a moon step's target cell reports Right once`() =
        assertTargetReportsRight(TangoTrainerFixtures.snippets.first { it.value == TangoCell.MOON })

    @Test
    fun `a fitting cell the technique does not prove reports AnotherWay`() {
        val (snippet, idx) = checkNotNull(TangoTrainerFixtures.snippetWith(TrainerVerdict.AnotherWay)) { "byid-1 has a snippet with another fitting move" }
        show(snippet)

        tap(snippet, idx)
        awaitReports(1)

        assertEquals(listOf(TrainerVerdict.AnotherWay), reports)
    }

    @Test
    fun `a cell off the solution reports Wrong`() {
        val (snippet, idx) = checkNotNull(TangoTrainerFixtures.snippetWith(TrainerVerdict.Wrong)) { "byid-1 has a snippet with a wrong move" }
        show(snippet)

        tap(snippet, idx)
        awaitReports(1)

        assertEquals(listOf(TrainerVerdict.Wrong), reports)
    }

    @Test
    fun `a second tap reports nothing`() {
        val snippet = TangoTrainerFixtures.snippets.first()
        val open = TangoTrainerFixtures.openCells(snippet).keys.toList()
        show(snippet)

        tap(snippet, open[0])
        awaitReports(1)
        tap(snippet, open[1])
        tap(snippet, open[0])
        composeRule.waitForIdle()

        assertEquals(1, reports.size)
    }

    @Test
    fun `a tap on a given does nothing, and the next tap on an open cell still counts`() {
        val snippet = TangoTrainerFixtures.snippets.first()
        val given = snippet.puzzle.givens.keys.first()
        val open = TangoTrainerFixtures.openCells(snippet).keys.first()
        show(snippet)

        tap(snippet, given)
        composeRule.waitForIdle()
        assertTrue("a given takes no tap", reports.isEmpty())

        tap(snippet, open)
        awaitReports(1)
        assertEquals(1, reports.size)
    }

    @Test
    fun `the move check runs on the dispatcher it is given`() {
        val dispatcher = CountingDispatcher()
        val snippet = TangoTrainerFixtures.snippets.first()
        show(snippet, dispatcher)

        tap(snippet, TangoTrainerFixtures.openCells(snippet).keys.first())
        awaitReports(1)

        assertTrue("the move check was dispatched", dispatcher.dispatched > 0)
    }

    private class CountingDispatcher : CoroutineDispatcher() {
        @Volatile var dispatched = 0

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatched++
            block.run()
        }
    }
}
