package com.ivandsky.puzzlebox.create.editor

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.editor.EditorOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The A | B choice with `modeTiles` on and off: the same tags and the same overlay changes, quiet
 * 52 dp tiles when on, the old 40 dp segmented row when off.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class VerdictWitnessTilesTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var overlay by mutableStateOf(EditorOverlay(EditorOverlay.Witness.A, showOnBoard = true))

    private fun show(modeTiles: Boolean) {
        composeRule.setContent {
            PuzzleBoxTheme {
                VerdictWitness(overlay, autoShow = true, onChange = { overlay = it }, onAutoShow = {}, legend = "legend", modeTiles = modeTiles)
            }
        }
    }

    private fun rowHeight(tag: String) = composeRule.onNodeWithTag(tag).getBoundsInRoot().height

    @Test
    fun `with tiles on, a tap on B picks it and keeps show on board`() {
        show(modeTiles = true)
        composeRule.onNodeWithTag(WITNESS_A).assertIsSelected()
        composeRule.onNodeWithTag(WITNESS_B).assertIsNotSelected()

        composeRule.onNodeWithTag(WITNESS_B).performClick()

        assertEquals(EditorOverlay(EditorOverlay.Witness.B, showOnBoard = true), overlay)
        composeRule.onNodeWithTag(WITNESS_B).assertIsSelected()
        composeRule.onNodeWithTag(WITNESS_A).assertIsNotSelected()
    }

    @Test
    fun `with tiles on, both choices are 52 dp tiles`() {
        show(modeTiles = true)

        listOf(WITNESS_A, WITNESS_B).forEach { assertTrue("$it tile ${rowHeight(it)}", rowHeight(it) >= TILE_HEIGHT) }
    }

    @Test
    fun `with tiles off, the segmented row stays under the tile height`() {
        show(modeTiles = false)

        listOf(WITNESS_A, WITNESS_B).forEach { assertTrue("$it segment ${rowHeight(it)}", rowHeight(it) < TILE_HEIGHT) }
        composeRule.onNodeWithTag(WITNESS_B).performClick()

        assertEquals(EditorOverlay.Witness.B, overlay.witness)
        composeRule.onNodeWithTag(WITNESS_B).assertIsSelected()
    }

    private companion object {
        const val WITNESS_A = "create.verdict.witness.a"
        const val WITNESS_B = "create.verdict.witness.b"

        /** `ChoiceTiles`' tile floor; the old segmented row is 40 dp. */
        val TILE_HEIGHT = 52.dp
    }
}
