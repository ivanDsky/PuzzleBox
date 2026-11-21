package com.ivandsky.puzzlebox.games.queensui.editor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.games.queens.QueensVariants
import com.ivandsky.puzzlebox.games.queens.editor.QueensEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Queen | Paint switch in the hint slot: Queen and Paint tiles, a tap switching the mode. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class QueensModeTilesTest {

    @get:Rule
    val rule = createComposeRule()

    private fun show(): QueensEditorController {
        val controller = QueensEditorController(EditorDraft.fresh(QueensEditor, QueensVariants.variant(SIZE).id)) {}
        rule.setContent { PuzzleBoxTheme { QueensEditorUi().ToolHint(controller, Modifier.fillMaxWidth()) } }
        return controller
    }

    private fun height(tag: String) = rule.onNodeWithTag(tag).getBoundsInRoot().height

    @Test
    fun `tiles show a Queen and a Paint tile and a tap switches the mode`() {
        val controller = show()

        rule.onNodeWithTag(GROUP).assertExists()
        listOf(CROWN, PAINT).forEach { assertTrue("$it tile", height(it) >= TILE_HEIGHT) }
        rule.onNodeWithTag(CROWN).assertIsSelected()

        rule.onNodeWithTag(PAINT).performClick()
        rule.waitForIdle()

        assertEquals(QueensEditorMode.Paint, controller.mode.value)
        rule.onNodeWithTag(PAINT).assertIsSelected()

        rule.onNodeWithTag(CROWN).performClick()
        rule.waitForIdle()

        assertEquals(QueensEditorMode.Crown, controller.mode.value)
        rule.onNodeWithTag(CROWN).assertIsSelected()
    }

    private companion object {
        const val SIZE = 6
        const val GROUP = "queens.editor.mode"
        const val CROWN = "queens.editor.mode.crown"
        const val PAINT = "queens.editor.mode.paint"

        /** `ChoiceTiles`' tile floor. */
        val TILE_HEIGHT = 52.dp
    }
}
