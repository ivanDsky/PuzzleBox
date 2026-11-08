package com.ivandsky.puzzlebox.games.tangoui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
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

    private val draft = TangoDraft((0..2).associateWith { TangoCell.SUN }, emptyList())
    private val controller = TangoEditorController(EditorDraft(TangoEditor, DraftState(draft))) {}

    private fun show() {
        rule.setContent {
            PuzzleBoxTheme {
                Box(Modifier.size(PaneWidth, PaneHeight)) {
                    TangoEditorBoard(controller, Modifier.fillMaxWidth(), Verdict.None(setOf(0, 1, 2)))
                }
            }
        }
    }

    @Test
    fun `a wide pane does not stretch the board past its cap`() {
        show()

        rule.onNodeWithTag("tango.editor.board").assertWidthIsEqualTo(480.dp)
    }

    @Test
    fun `an overlay cell is a sixth of the board it sits on`() {
        show()

        val board = rule.onNodeWithTag("tango.editor.board").getUnclippedBoundsInRoot()
        val cell = rule.onNodeWithTag("tango.editor.cell.0.clash").getUnclippedBoundsInRoot()
        assertEquals((board.right - board.left).value / TangoDraft.N, (cell.right - cell.left).value, 0.5f)
    }

    @Test
    fun `tap zones leave the board its own height`() {
        controller.toggleTapZones()
        show()

        val board = rule.onNodeWithTag("tango.editor.board")
        board.assertHeightIsEqualTo(board.getUnclippedBoundsInRoot().let { it.right - it.left })
    }

    private companion object {
        val PaneWidth = 876.dp
        val PaneHeight = 900.dp
    }
}
