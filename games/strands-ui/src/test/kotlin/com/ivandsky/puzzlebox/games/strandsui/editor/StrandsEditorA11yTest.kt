package com.ivandsky.puzzlebox.games.strandsui.editor

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraft
import com.ivandsky.puzzlebox.games.strands.editor.StrandsEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The editor's accessibility floor: a blank draft still shows a grid, the word and clue fields
 * are distinguishable without sight, and the path rows meet the touch-target floor.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class StrandsEditorA11yTest {

    @get:Rule
    val rule = createComposeRule()

    private val controller = StrandsEditorController(EditorDraft.fresh(StrandsEditor, StrandsDefinition.VARIANT)) {}

    private fun show() = rule.setContent {
        PuzzleBoxTheme { StrandsEditorUi.Board(controller, Verdict.Checking, Modifier) }
    }

    private fun path(vararg cells: Int) {
        cells.forEach(controller::traceTo)
        controller.endTrace()
    }

    @Test
    fun `a blank draft draws forty eight visible empty cells`() {
        show()
        val empty = rule.onAllNodesWithTag("strands.editor.cell.empty", useUnmergedTree = true)
        assertEquals(StrandsDraft.ROWS * StrandsDraft.COLS, empty.fetchSemanticsNodes().size)
    }

    @Test
    fun `the word and clue fields announce distinct labels`() {
        show()
        rule.onNodeWithTag("strands.editor.word.label", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("strands.editor.clue.label", useUnmergedTree = true).assertExists()
        val word = rule.onNodeWithTag("strands.editor.word.label", useUnmergedTree = true).fetchSemanticsNode()
        val clue = rule.onNodeWithTag("strands.editor.clue.label", useUnmergedTree = true).fetchSemanticsNode()
        assertTrue(word != clue)
    }

    @Test
    fun `every path row is a full touch target`() {
        path(0, 1)
        path(6, 7, 8)
        show()
        val floor = with(rule.density) { 48.dp.toPx() }
        for (row in 0..1) {
            val bounds = rule.onNodeWithTag("strands.editor.path.$row").performScrollTo()
                .fetchSemanticsNode().boundsInRoot
            assertTrue("row $row is ${bounds.height}px, floor $floor", bounds.height >= floor)
        }
    }
}
