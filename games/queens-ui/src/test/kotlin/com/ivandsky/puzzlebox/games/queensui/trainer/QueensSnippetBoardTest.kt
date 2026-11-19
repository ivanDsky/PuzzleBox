package com.ivandsky.puzzlebox.games.queensui.trainer

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.MarkKind
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStep
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

/** The snippet board's tap, judged by the move check and reported once, on the 7×7 daily 916. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class QueensSnippetBoardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val reports = mutableListOf<TrainerVerdict>()

    private fun show(snippet: QueensSnippet, compute: CoroutineDispatcher = Dispatchers.Default) {
        reports.clear()
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                QueensSnippetBoard(snippet, compute, { reports += it }, Modifier.fillMaxWidth())
            }
        }
        composeRule.waitForIdle()
    }

    /** Tap the center of [cell] on the board node itself. */
    private fun tap(snippet: QueensSnippet, cell: Int) {
        val n = snippet.puzzle.size
        composeRule.onNodeWithTag(QUEENS_SNIPPET_BOARD_TAG).performTouchInput {
            click(Offset(width * (cell % n + 0.5f) / n, height * (cell / n + 0.5f) / n))
        }
        composeRule.waitForIdle()
    }

    private fun awaitReports(count: Int) = composeRule.waitUntil(timeoutMillis = 10_000) { reports.size >= count }

    private fun targetCell(snippet: QueensSnippet): Int = when (val result = snippet.cut.step.result) {
        is QueensStep.Deduction.Result.LastCell -> result.cell.idx
        is QueensStep.Deduction.Result.Cross -> result.cells.minOf { it.idx }
    }

    private fun assertTargetReportsRight(snippet: QueensSnippet) {
        show(snippet)

        tap(snippet, targetCell(snippet))
        awaitReports(1)

        assertEquals(listOf(TrainerVerdict.Right), reports)
    }

    @Test
    fun `tapping a queen step's target square reports Right once`() =
        assertTargetReportsRight(QueensTrainerFixtures.snippets.first { it.placesQueen })

    @Test
    fun `tapping a cross step's target square reports Right once`() =
        assertTargetReportsRight(QueensTrainerFixtures.snippets.first { !it.placesQueen })

    @Test
    fun `a fitting square the technique does not prove reports AnotherWay`() {
        val (snippet, cell) = checkNotNull(QueensTrainerFixtures.snippetWith(TrainerVerdict.AnotherWay)) { "daily 916 has a snippet with another fitting move" }
        show(snippet)

        tap(snippet, cell)
        awaitReports(1)

        assertEquals(listOf(TrainerVerdict.AnotherWay), reports)
    }

    @Test
    fun `a square off the solution reports Wrong`() {
        val (snippet, cell) = checkNotNull(QueensTrainerFixtures.snippetWith(TrainerVerdict.Wrong)) { "daily 916 has a snippet with a wrong move" }
        show(snippet)

        tap(snippet, cell)
        awaitReports(1)

        assertEquals(listOf(TrainerVerdict.Wrong), reports)
    }

    @Test
    fun `a second tap reports nothing`() {
        val snippet = QueensTrainerFixtures.snippets.first()
        val open = QueensTrainerFixtures.openSquares(snippet).keys.toList()
        show(snippet)

        tap(snippet, open[0])
        awaitReports(1)
        tap(snippet, open[1])
        tap(snippet, open[0])
        composeRule.waitForIdle()

        assertEquals(1, reports.size)
    }

    @Test
    fun `a tap on a square a queen crossed out does nothing, and the next tap on an open square still counts`() {
        val snippet = QueensTrainerFixtures.snippets.first { QueensSnippetBoardState(it).session.marks.values.any { mark -> mark.kind == MarkKind.AUTO } }
        val session = QueensSnippetBoardState(snippet).session
        val autoCrossed = session.marks.filterValues { it.kind == MarkKind.AUTO }.keys.first().idx
        val open = QueensTrainerFixtures.openSquares(snippet).keys.first()
        show(snippet)

        tap(snippet, autoCrossed)
        composeRule.waitForIdle()
        assertTrue("an auto cross takes no tap", reports.isEmpty())

        tap(snippet, open)
        awaitReports(1)
        assertEquals(1, reports.size)
        assertTrue(Cell(autoCrossed) !in QueensSnippetBoardState(snippet).session.queens)
    }

    @Test
    fun `the move check runs on the dispatcher it is given`() {
        val dispatcher = CountingDispatcher()
        val snippet = QueensTrainerFixtures.snippets.first()
        show(snippet, dispatcher)

        tap(snippet, QueensTrainerFixtures.openSquares(snippet).keys.first())
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
