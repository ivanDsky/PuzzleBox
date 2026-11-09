package com.ivandsky.puzzlebox.games.strandsui.editor

import androidx.compose.foundation.layout.Column
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
import com.ivandsky.puzzlebox.gameui.PlayLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The editor's accessibility floor: a blank draft still shows a grid, the word field and the
 * publish details' clue field are distinguishable without sight, and the path rows meet the
 * touch-target floor.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class StrandsEditorA11yTest {

    @get:Rule
    val rule = createComposeRule()

    private val controller = StrandsEditorController(EditorDraft.fresh(StrandsEditor, StrandsDefinition.VARIANT)) {}

    private fun show() = rule.setContent {
        PuzzleBoxTheme { StrandsEditorUi.Board(controller, Verdict.Checking, PlayLayout.SinglePane, Modifier) }
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
        rule.setContent {
            PuzzleBoxTheme {
                Column {
                    StrandsEditorUi.Board(controller, Verdict.Checking, PlayLayout.SinglePane, Modifier.weight(1f))
                    StrandsEditorUi.PublishFields(controller, enabled = true, modifier = Modifier)
                }
            }
        }
        rule.onNodeWithTag("strands.editor.word.label", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("strands.editor.clue.label", useUnmergedTree = true).assertExists()
        val word = rule.onNodeWithTag("strands.editor.word.label", useUnmergedTree = true).fetchSemanticsNode()
        val clue = rule.onNodeWithTag("strands.editor.clue.label", useUnmergedTree = true).fetchSemanticsNode()
        assertTrue(word != clue)
    }

    @Test
    fun `a cell being traced loses its idle tile`() {
        controller.traceTo(0)
        show()
        val traced = rule.onAllNodesWithTag("strands.editor.cell.empty", useUnmergedTree = true)
            .fetchSemanticsNodes().count { node -> node.boundsInRoot.center == rule.onNodeWithTag("strands.editor.cell.0").fetchSemanticsNode().boundsInRoot.center }
        assertEquals(0, traced)
        assertEquals(StrandsDraft.ROWS * StrandsDraft.COLS - 1, rule.onAllNodesWithTag("strands.editor.cell.empty", useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun `every path chip is a full touch target`() {
        path(0, 1)
        path(6, 7, 8)
        show()
        val floor = with(rule.density) { 48.dp.toPx() }
        for (chip in 0..1) {
            // The chip's laid-out size: its 48 dp touch floor around the drawn pill.
            val height = rule.onNodeWithTag("strands.editor.path.$chip").performScrollTo()
                .fetchSemanticsNode().layoutInfo.height
            assertTrue("chip $chip is ${height}px, floor $floor", height >= floor)
        }
    }
}
