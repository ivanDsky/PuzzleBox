package com.ivandsky.puzzlebox.games.queensui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.queens.QueensVariants
import com.ivandsky.puzzlebox.games.queens.editor.QueensEditor
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Ledger ruling (fix-e2e, owned by 28): the context bar never wraps. On Robolectric's default
 * 320×470 dp window the phone Studio leaves the 8×8 board a 180 dp slot; opening a selection
 * swaps the hint for "{k} cells" and three chips on one horizontally scrolling line, so the
 * bar keeps its height and the board does not shrink.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QueensSelectionChipsTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `opening a selection keeps the board and the context bar at their height`() {
        val controller = QueensEditorController(EditorDraft.fresh(QueensEditor, QueensVariants.variant(SIZE).id)) {}
            .apply { setMode(QueensEditorMode.Paint) } // a fresh draft opens in Crown (36)
        rule.setContent {
            PuzzleBoxTheme {
                Box(Modifier.padding(top = 80.dp).size(STUDIO_SLOT)) { QueensEditorBoard(controller, Verdict.Checking, Modifier.fillMaxSize()) }
            }
        }
        val boardBefore = rule.onNodeWithTag(BOARD).getBoundsInRoot()
        val barBefore = rule.onNodeWithTag(CONTEXT).getBoundsInRoot()

        rule.onNodeWithTag("queens.editor.cell.0", useUnmergedTree = true).performClick()
        rule.onNodeWithTag("queens.editor.chip.cancel").assertExists()

        assertEquals(boardBefore, rule.onNodeWithTag(BOARD).getBoundsInRoot())
        assertEquals(barBefore.height, rule.onNodeWithTag(CONTEXT).getBoundsInRoot().height)
    }

    private companion object {
        const val SIZE = 8
        val STUDIO_SLOT = 180.dp
        const val BOARD = "queens.editor.board"
        const val CONTEXT = "queens.editor.context"
    }
}
