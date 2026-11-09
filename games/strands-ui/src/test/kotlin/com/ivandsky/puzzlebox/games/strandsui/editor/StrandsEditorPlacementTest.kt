package com.ivandsky.puzzlebox.games.strandsui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.games.strands.editor.StrandsEditor
import com.ivandsky.puzzlebox.gameui.PlayLayout
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Where the authoring controls sit (user F8b): on a phone the word field follows the board and a
 * tap on the board selects a path; on a tablet the board is alone and the side pane's
 * [StrandsEditorUi.PaneTools] hold the word field and the path rows. The selected path's
 * spangram toggle sits in [StrandsEditorUi.ToolHint] on both. The clue and the credit are on
 * neither: they wait for the publish details.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class StrandsEditorPlacementTest {

    @get:Rule
    val rule = createComposeRule()

    private val controller = StrandsEditorController(EditorDraft.fresh(StrandsEditor, StrandsDefinition.VARIANT)) {}

    private fun path(vararg cells: Int) {
        cells.forEach(controller::traceTo)
        controller.endTrace()
    }

    private fun board(layout: PlayLayout) = rule.setContent {
        PuzzleBoxTheme {
            Box(Modifier.size(PANE_WIDTH, PANE_HEIGHT).testTag(PANE)) {
                StrandsEditorUi.Board(controller, Verdict.Checking, layout, Modifier)
            }
        }
    }

    private fun assertAbsent(tag: String) =
        rule.onNode(hasTestTag(tag), useUnmergedTree = true).assertDoesNotExist()

    private fun assertPresent(tag: String) =
        rule.onNode(hasTestTag(tag), useUnmergedTree = true).assertExists()

    @Test
    fun `the phone board has the word field but no paths, no clue and no credit`() {
        path(0, 1)
        controller.selectPath(0)
        board(PlayLayout.SinglePane)
        assertPresent(BOARD)
        assertPresent(WORD)
        assertAbsent(PATH_0)
        assertAbsent(SPANGRAM_0)
        assertAbsent(CLUE)
        assertAbsent(CREDIT)
    }

    @Test
    fun `the tablet board holds only the board`() {
        path(0, 1)
        controller.selectPath(0)
        board(PlayLayout.TwoPane)
        assertPresent(BOARD)
        assertAbsent(WORD)
        assertAbsent(PATH_0)
        assertAbsent(SPANGRAM_0)
        assertAbsent(CLUE)
        assertAbsent(CREDIT)
    }

    @Test
    fun `the tablet board fits an 876 by 720 pane without scrolling`() {
        board(PlayLayout.TwoPane)
        val pane = rule.onNodeWithTag(PANE).fetchSemanticsNode().size
        // The grid's own size, not its clipped bounds: a scrolled-off grid would still "fit".
        val grid = rule.onNodeWithTag(BOARD).fetchSemanticsNode().size
        assertTrue("grid $grid inside pane $pane", grid.height <= pane.height && grid.width <= pane.width)
        rule.onNode(hasScrollAction()).assertDoesNotExist()
    }

    @Test
    fun `the pane tools hold the word field and the paths`() {
        path(0, 1)
        controller.selectPath(0)
        rule.setContent { PuzzleBoxTheme { StrandsEditorUi.PaneTools(controller, Modifier) } }
        assertPresent(WORD)
        assertPresent(PATH_0)
        assertAbsent(SPANGRAM_0)
        assertAbsent(BOARD)
        assertAbsent(CLUE)
        assertAbsent(CREDIT)
    }

    @Test
    fun `the tool hint holds the selected path's spangram toggle`() {
        path(0, 1)
        controller.selectPath(null)
        rule.setContent { PuzzleBoxTheme { StrandsEditorUi.ToolHint(controller, Modifier) } }
        assertAbsent(SPANGRAM_0)
        controller.selectPath(0)
        assertPresent(SPANGRAM_0)
        assertPresent(REMOVE)
    }

    private companion object {
        val PANE_WIDTH = 876.dp
        val PANE_HEIGHT = 720.dp
        const val PANE = "test.pane"
        const val BOARD = "strands.editor.board"
        const val WORD = "strands.editor.word"
        const val PATH_0 = "strands.editor.path.0"
        const val SPANGRAM_0 = "strands.editor.spangram.0"
        const val CLUE = "strands.editor.clue"
        const val CREDIT = "strands.editor.credit"
        const val REMOVE = "strands.editor.remove"
    }
}
