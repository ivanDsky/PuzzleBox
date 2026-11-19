package com.ivandsky.puzzlebox.games.sudokuui.trainer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuTrainerMove
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The snippet board's two-tap input (select an empty cell, pick a digit), judged by the move check and reported once.
 * The picker is a popup, so its keys are found by their tags from the root.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class SudokuSnippetBoardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val reports = mutableListOf<TrainerVerdict>()

    private fun show(snippet: SudokuSnippet, compute: CoroutineDispatcher = Dispatchers.Default) {
        reports.clear()
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Box(Modifier.padding(top = 16.dp)) {
                    SudokuSnippetBoard(snippet, compute, { reports += it }, Modifier.fillMaxWidth())
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun select(idx: Int) {
        composeRule.onNodeWithTag("sudoku.cell.$idx", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
    }

    private fun pick(digit: Int) {
        composeRule.onNodeWithTag(sudokuPickerKeyTag(digit), useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
    }

    private fun awaitReports(count: Int) = composeRule.waitUntil(timeoutMillis = 10_000) { reports.size >= count }

    private fun SudokuSnippet.emptyCells() = SudokuTrainerFixtures.emptyCells(this)

    private fun SudokuSnippet.moveFor(verdict: TrainerVerdict): SudokuTrainerMove? = SudokuTrainerFixtures.moveFor(this, verdict)

    private fun Pair<SudokuSnippet, SudokuTrainerMove>.play() {
        val (snippet, move) = this
        show(snippet)
        select(SudokuTrainerFixtures.cellOf(move))
        pick(SudokuTrainerFixtures.digitOf(move))
    }

    private fun withMove(verdict: TrainerVerdict, wantSingle: Boolean? = null): Pair<SudokuSnippet, SudokuTrainerMove> =
        SudokuTrainerFixtures.snippets().filter { wantSingle == null || it.placesDigit == wantSingle }
            .firstNotNullOf { snippet -> snippet.moveFor(verdict)?.let { snippet to it } }

    @Test
    fun `selecting a cell opens the picker with every digit for a single, and no verdict yet`() {
        val snippet = SudokuTrainerFixtures.snippets().first { it.placesDigit }
        show(snippet)

        composeRule.onAllNodesWithTag(SUDOKU_PICKER_TAG, useUnmergedTree = true).assertCountEquals(0)
        select(snippet.emptyCells().first())

        composeRule.onAllNodesWithTag(SUDOKU_PICKER_TAG, useUnmergedTree = true).assertCountEquals(1)
        for (digit in 1..snippet.puzzle.geometry.size) {
            composeRule.onAllNodesWithTag(sudokuPickerKeyTag(digit), useUnmergedTree = true).assertCountEquals(1)
        }
        assertTrue("selecting is no move", reports.isEmpty())
    }

    @Test
    fun `the right digit on a single's cell reports Right once, and closes the picker`() {
        val (snippet, move) = withMove(TrainerVerdict.Right, wantSingle = true)
        (snippet to move).play()
        awaitReports(1)

        assertEquals(listOf(TrainerVerdict.Right), reports)
        composeRule.onAllNodesWithTag(SUDOKU_PICKER_TAG, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `a digit that fits but another technique proves reports AnotherWay`() {
        val (snippet, move) = withMove(TrainerVerdict.AnotherWay)
        (snippet to move).play()
        awaitReports(1)

        assertEquals(listOf(TrainerVerdict.AnotherWay), reports)
    }

    @Test
    fun `a wrong digit on a single's cell reports Wrong`() {
        val (snippet, move) = withMove(TrainerVerdict.Wrong, wantSingle = true)
        (snippet to move).play()
        awaitReports(1)

        assertEquals(listOf(TrainerVerdict.Wrong), reports)
    }

    @Test
    fun `striking an elimination's note reports Right`() {
        val (snippet, move) = withMove(TrainerVerdict.Right, wantSingle = false)
        (snippet to move).play()
        awaitReports(1)

        assertEquals(listOf(TrainerVerdict.Right), reports)
    }

    @Test
    fun `striking the note of the digit that belongs reports Wrong`() {
        val (snippet, move) = withMove(TrainerVerdict.Wrong, wantSingle = false)
        (snippet to move).play()
        awaitReports(1)

        assertEquals(listOf(TrainerVerdict.Wrong), reports)
    }

    @Test
    fun `an elimination cell's picker offers only its own notes`() {
        val snippet = SudokuTrainerFixtures.snippets().first { !it.placesDigit }
        val state = SudokuSnippetBoardState(snippet)
        val idx = snippet.emptyCells().first { state.keysAt(it).size < snippet.puzzle.geometry.size }
        show(snippet)

        select(idx)

        for (digit in 1..snippet.puzzle.geometry.size) {
            val count = if (digit in state.keysAt(idx)) 1 else 0
            composeRule.onAllNodesWithTag(sudokuPickerKeyTag(digit), useUnmergedTree = true).assertCountEquals(count)
        }
    }

    @Test
    fun `a second pick reports nothing`() {
        val (snippet, move) = withMove(TrainerVerdict.Right, wantSingle = true)
        (snippet to move).play()
        awaitReports(1)

        val other = snippet.emptyCells().first { it != (move as SudokuTrainerMove.Place).idx }
        select(other)
        composeRule.onAllNodesWithTag(SUDOKU_PICKER_TAG, useUnmergedTree = true).assertCountEquals(0)
        composeRule.waitForIdle()

        assertEquals(1, reports.size)
    }

    @Test
    fun `a tap on a given opens nothing, and the next tap on an empty cell still counts`() {
        val snippet = SudokuTrainerFixtures.snippets().first { it.placesDigit }
        val given = (0 until snippet.puzzle.geometry.cellCount).first { snippet.cut.boardBefore.digitAt(it) != 0 }
        show(snippet)

        select(given)
        composeRule.onAllNodesWithTag(SUDOKU_PICKER_TAG, useUnmergedTree = true).assertCountEquals(0)

        select(snippet.emptyCells().first())
        composeRule.onAllNodesWithTag(SUDOKU_PICKER_TAG, useUnmergedTree = true).assertCountEquals(1)
        assertTrue(reports.isEmpty())
    }

    @Test
    fun `tapping another cell moves the picker, and no verdict is reported`() {
        val snippet = SudokuTrainerFixtures.snippets().first { it.placesDigit }
        val open = snippet.emptyCells()
        show(snippet)

        select(open[0])
        select(open[1])

        composeRule.onAllNodesWithTag(SUDOKU_PICKER_TAG, useUnmergedTree = true).assertCountEquals(1)
        assertTrue(reports.isEmpty())
    }

    @Test
    fun `the move check runs on the dispatcher it is given`() {
        val dispatcher = CountingDispatcher()
        val (snippet, move) = withMove(TrainerVerdict.Right, wantSingle = true)
        show(snippet, dispatcher)
        move as SudokuTrainerMove.Place

        select(move.idx)
        pick(move.digit)
        awaitReports(1)

        assertTrue("the move check was dispatched", dispatcher.dispatched > 0)
    }

    @Test
    fun `the board publishes its digit look for the reveal`() {
        val snippet = SudokuTrainerFixtures.snippets().first()
        show(snippet)

        assertNotNull(snippet.digits.digits)
    }

    private class CountingDispatcher : CoroutineDispatcher() {
        @Volatile var dispatched = 0

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatched++
            block.run()
        }
    }
}
