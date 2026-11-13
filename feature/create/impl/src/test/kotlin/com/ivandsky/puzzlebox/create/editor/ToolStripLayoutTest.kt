package com.ivandsky.puzzlebox.create.editor

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.VariantId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The tool row spans the strip at every width: Undo at its start edge, Clear at its end edge. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ToolStripLayoutTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `the tools spread across a phone-wide strip`() {
        setStrip(width = 379.dp)

        val strip = composeRule.onNodeWithTag("create.tool.strip").getUnclippedBoundsInRoot()
        val undo = composeRule.onNodeWithTag("create.tool.undo").getUnclippedBoundsInRoot()
        val clear = composeRule.onNodeWithTag("create.tool.clear").getUnclippedBoundsInRoot()
        assertDpEquals(strip.left + Spacing.sm, undo.left)
        assertDpEquals(strip.right - Spacing.sm, clear.right)
    }

    private fun setStrip(width: Dp) {
        val sizes = listOf(VariantId("fake/5"), VariantId("fake/6"))
        composeRule.setContent {
            PuzzleBoxTheme {
                ToolStrip(
                    canUndo = true,
                    canRedo = false,
                    onUndo = {},
                    onRedo = {},
                    sizes = sizes,
                    size = sizes.first(),
                    sizeLabel = { it.value },
                    onResize = {},
                    onClear = {},
                    hint = {},
                    modifier = Modifier.width(width),
                )
            }
        }
    }

    private fun assertDpEquals(expected: Dp, actual: Dp) = assertEquals(expected.value, actual.value, 0.5f)
}
