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

/**
 * The step switch under the Zip grid with [ZipEditorController.modeTiles] on and off, in the three-step
 * and the combined layout: the same tags, the same step changes, tiles only when the flag is on.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class ZipEditorStepTilesTest {

    @get:Rule
    val rule = createComposeRule()

    private fun show(combined: Boolean, modeTiles: Boolean): ZipEditorController {
        val controller = ZipEditorController(
            EditorDraft.fresh(ZipEditor, ZipEditor.variants.first()),
            combined,
            modeTiles,
        ) {}
        rule.setContent { PuzzleBoxTheme { ZipEditorBoard(controller, Modifier.fillMaxSize()) } }
        return controller
    }

    private fun tileHeight(step: String) = rule.onNodeWithTag("zip.editor.step.$step").getBoundsInRoot().height

    private fun exists(tag: String) = rule.onNodeWithTag(tag).assertExists()

    @Test
    fun `tiles show one per step of the three-step layout and a tap selects like the old control`() {
        val controller = show(combined = false, modeTiles = true)

        exists("zip.editor.step")
        listOf("path", "numbers", "walls").forEach { assertTrue("$it tile", tileHeight(it) >= TILE_HEIGHT) }
        rule.onNodeWithTag("zip.editor.step.numbersandwalls").assertDoesNotExist()
        rule.onNodeWithTag("zip.editor.step.path").assertIsSelected()

        rule.onNodeWithTag("zip.editor.step.walls").performClick()
        rule.waitForIdle()

        assertEquals(ZipEditorStep.Walls, controller.step.value)
        rule.onNodeWithTag("zip.editor.step.walls").assertIsSelected()
        exists("zip.editor.clearWalls")
    }

    @Test
    fun `tiles show Path and Numbers and walls in the combined layout`() {
        val controller = show(combined = true, modeTiles = true)

        listOf("path", "numbersandwalls").forEach { assertTrue("$it tile", tileHeight(it) >= TILE_HEIGHT) }
        rule.onNodeWithTag("zip.editor.step.numbers").assertDoesNotExist()
        rule.onNodeWithTag("zip.editor.step.walls").assertDoesNotExist()

        rule.onNodeWithTag("zip.editor.step.numbersandwalls").performClick()
        rule.waitForIdle()

        assertEquals(ZipEditorStep.NumbersAndWalls, controller.step.value)
        rule.onNodeWithTag("zip.editor.step.numbersandwalls").assertIsSelected()
    }

    @Test
    fun `flag off keeps the segmented control in the three-step layout`() = segmentsStillWork(combined = false)

    @Test
    fun `flag off keeps the segmented control in the combined layout`() = segmentsStillWork(combined = true)

    private fun segmentsStillWork(combined: Boolean) {
        val controller = show(combined, modeTiles = false)
        val last = ZipEditorStep.tabs(combined).last()
        val tag = "zip.editor.step.${last.name.lowercase()}"

        exists("zip.editor.step")
        assertTrue("segmented control", tileHeight("path") < TILE_HEIGHT)
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()

        assertEquals(last, controller.step.value)
        rule.onNodeWithTag(tag).assertIsSelected()
    }

    private companion object {
        /** `ChoiceTiles`' tile floor; the old segmented row is 40 dp. */
        val TILE_HEIGHT = 52.dp
    }
}
