package com.ivandsky.puzzlebox.games.tangoui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.editor.TangoDraft
import com.ivandsky.puzzlebox.games.tango.editor.TangoEditor
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** In a pane wider and taller than the board, the board and every overlay share one square. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h1000dp-mdpi")
class TangoEditorBoardBoundsTest {

    @get:Rule
    val rule = createComposeRule()

    private val draft = TangoDraft(6, (0..2).associateWith { TangoCell.SUN }, emptyList())
    private val controller = TangoEditorController(EditorDraft(TangoEditor, DraftState(draft))) {}

    private fun show(shown: TangoEditorController = controller) {
        rule.setContent {
            PuzzleBoxTheme {
                // The Studio's SquareBoardSlot: the smaller of the pane's width and height, filled.
                Box(Modifier.size(PaneWidth, PaneHeight)) {
                    Box(Modifier.size(minOf(PaneWidth, PaneHeight))) {
                        val verdict = Verdict.None(setOf(0, 1, 2))
                        TangoEditorBoard(shown, Modifier.fillMaxSize(), verdict)
                    }
                }
            }
        }
    }

    @Test
    fun `the board fills the square slot of a wide pane`() {
        show()

        val board = rule.onNodeWithTag("tango.editor.board")
        board.assertWidthIsEqualTo(minOf(PaneWidth, PaneHeight))
        board.assertHeightIsEqualTo(minOf(PaneWidth, PaneHeight))
    }

    @Test
    fun `an overlay cell is a sixth of the board it sits on`() {
        show()

        val board = rule.onNodeWithTag("tango.editor.board").getUnclippedBoundsInRoot()
        val cell = rule.onNodeWithTag("tango.editor.cell.0.clash").getUnclippedBoundsInRoot()
        val boardWidth = (board.right - board.left).value
        assertEquals(boardWidth / 6, (cell.right - cell.left).value, 0.5f)
    }

    @Test
    fun `tap zones leave the board its own height`() {
        controller.toggleTapZones()
        show()

        val board = rule.onNodeWithTag("tango.editor.board")
        board.assertHeightIsEqualTo(board.getUnclippedBoundsInRoot().let { it.right - it.left })
    }

    @Test
    fun `at n4 the board stays square, fills the slot, and a cell is a quarter of it`() {
        assertSquareAt(4)
    }

    @Test
    fun `at n8 the board stays square, fills the slot, and a cell is an eighth of it`() {
        assertSquareAt(8)
    }

    @Test
    fun `at n10 the board stays square, fills the slot, and a cell is a tenth of it`() {
        assertSquareAt(10)
    }

    private fun assertSquareAt(n: Int) {
        show(TangoEditorController(EditorDraft(TangoEditor, DraftState(TangoDraft(n, emptyMap(), emptyList())))) {})

        val slot = minOf(PaneWidth, PaneHeight)
        rule.onNodeWithTag("tango.editor.board").assertWidthIsEqualTo(slot).assertHeightIsEqualTo(slot)
        val board = rule.onNodeWithTag("tango.editor.board").getUnclippedBoundsInRoot()
        val cell = rule.onNodeWithTag("tango.editor.cell.2.clash").getUnclippedBoundsInRoot()
        val pitch = (board.right - board.left).value / n
        assertEquals(pitch, (cell.right - cell.left).value, 0.5f)
        assertEquals(pitch, (cell.bottom - cell.top).value, 0.5f)
        assertEquals(board.left.value + 2 * pitch, cell.left.value, 0.5f)
    }

    private companion object {
        val PaneWidth = 876.dp
        val PaneHeight = 720.dp
    }
}
