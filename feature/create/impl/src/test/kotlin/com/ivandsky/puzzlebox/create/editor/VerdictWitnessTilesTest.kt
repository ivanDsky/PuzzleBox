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
 * The A | B choice: the same tags and the same overlay changes as ever, quiet 52 dp tiles.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class VerdictWitnessTilesTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var overlay by mutableStateOf(EditorOverlay(EditorOverlay.Witness.A, showOnBoard = true))

    private fun show() {
        composeRule.setContent {
            PuzzleBoxTheme {
                VerdictWitness(overlay, autoShow = true, onChange = { overlay = it }, onAutoShow = {}, legend = "legend")
            }
        }
    }

    private fun rowHeight(tag: String) = composeRule.onNodeWithTag(tag).getBoundsInRoot().height

    @Test
    fun `a tap on B picks it and keeps show on board`() {
        show()
        composeRule.onNodeWithTag(WITNESS_A).assertIsSelected()
        composeRule.onNodeWithTag(WITNESS_B).assertIsNotSelected()

        composeRule.onNodeWithTag(WITNESS_B).performClick()

        assertEquals(EditorOverlay(EditorOverlay.Witness.B, showOnBoard = true), overlay)
        composeRule.onNodeWithTag(WITNESS_B).assertIsSelected()
        composeRule.onNodeWithTag(WITNESS_A).assertIsNotSelected()
    }

    @Test
    fun `both choices are 52 dp tiles`() {
        show()

        listOf(WITNESS_A, WITNESS_B).forEach { assertTrue("$it tile ${rowHeight(it)}", rowHeight(it) >= TILE_HEIGHT) }
    }

    private companion object {
        const val WITNESS_A = "create.verdict.witness.a"
        const val WITNESS_B = "create.verdict.witness.b"

        /** `ChoiceTiles`' tile floor. */
        val TILE_HEIGHT = 52.dp
    }
}
