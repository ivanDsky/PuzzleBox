package com.ivandsky.puzzlebox.games.zipui.editor

import androidx.compose.foundation.layout.fillMaxSize
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
import com.ivandsky.puzzlebox.games.zip.editor.ZipEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The step switch under the Zip grid: one tile per step, Path and Numbers & walls, a tap selecting it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class ZipEditorStepTilesTest {

    @get:Rule
    val rule = createComposeRule()

    private fun show(): ZipEditorController {
        val controller = ZipEditorController(EditorDraft.fresh(ZipEditor, ZipEditor.variants.first())) {}
        rule.setContent { PuzzleBoxTheme { ZipEditorBoard(controller, Modifier.fillMaxSize()) } }
        return controller
    }

    private fun tileHeight(step: String) = rule.onNodeWithTag("zip.editor.step.$step").getBoundsInRoot().height

    @Test
    fun `tiles show Path and Numbers and walls and a tap selects`() {
        val controller = show()

        listOf("path", "numbersandwalls").forEach { assertTrue("$it tile", tileHeight(it) >= TILE_HEIGHT) }
        rule.onNodeWithTag("zip.editor.step.path").assertIsSelected()

        rule.onNodeWithTag("zip.editor.step.numbersandwalls").performClick()
        rule.waitForIdle()

        assertEquals(ZipEditorStep.NumbersAndWalls, controller.step.value)
        rule.onNodeWithTag("zip.editor.step.numbersandwalls").assertIsSelected()
        rule.onNodeWithTag("zip.editor.clearWalls").assertExists()
    }

    private companion object {
        /** `ChoiceTiles`' tile floor. */
        val TILE_HEIGHT = 52.dp
    }
}
