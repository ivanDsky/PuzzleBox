package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.gameui.TutorialHost
import com.ivandsky.puzzlebox.gameui.TutorialSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Mini tutorial keeps one layout across its three lessons on a 360 dp phone. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class SudokuTutorialLayoutTest {

    @get:Rule
    val rule = createComposeRule()

    private val ui = SudokuPlayUi(SudokuGames.MINI, InMemorySudokuHighlightSettings())

    private class Snapshot(val keypad: String, val board: String, val instructionLines: Int)

    private fun lessons(): List<Snapshot> {
        var session by mutableStateOf(TutorialSession(ui.tutorial.take(1)))
        rule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                TutorialHost(ui = ui, session = session, onFinished = {})
            }
        }
        return ui.tutorial.indices.map { index ->
            rule.runOnIdle { session = TutorialSession(ui.tutorial.drop(index).take(1)) }
            rule.waitForIdle()
            val layouts = ArrayList<TextLayoutResult>()
            rule.onNodeWithTag("tutorial.instruction").fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult]
                .action?.invoke(layouts)
            Snapshot(
                keypad = rule.onNodeWithTag("sudoku.keypad").getUnclippedBoundsInRoot().toString(),
                board = rule.onNodeWithTag("sudoku.board").getUnclippedBoundsInRoot().toString(),
                instructionLines = layouts.single().lineCount,
            )
        }
    }

    @Test
    fun `every lesson puts the keypad and board in the same place`() {
        val shots = lessons()
        assertEquals(shots.map { it.keypad }.toString(), 1, shots.map { it.keypad }.toSet().size)
        assertEquals(shots.map { it.board }.toString(), 1, shots.map { it.board }.toSet().size)
    }

    @Test
    fun `every lesson's instruction fits two lines at 360 dp`() {
        val lines = lessons().map { it.instructionLines }
        assertTrue(lines.toString(), lines.all { it in 1..2 })
    }
}
