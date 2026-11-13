package com.ivandsky.puzzlebox.create.editor

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.VariantId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Size tool exists only when the game has a size to pick: a one-variant editor's strip
 * has no Size tool (nothing to choose, so nothing to open), a multi-variant one does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ToolStripSizeToolTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `a one-variant editor's strip has no Size tool`() {
        setStrip(listOf(VariantId("solo/only")))

        composeRule.onNodeWithTag("create.tool.undo").assertExists()
        composeRule.onNodeWithTag("create.tool.redo").assertExists()
        composeRule.onNodeWithTag("create.tool.clear").assertExists()
        composeRule.onNodeWithTag("create.tool.size").assertDoesNotExist()
    }

    @Test
    fun `a multi-variant editor's strip has the Size tool, opening every size`() {
        val sizes = listOf(VariantId("duo/a"), VariantId("duo/b"))
        setStrip(sizes)

        composeRule.onNodeWithTag("create.tool.size").assertExists().performClick()
        sizes.forEach { composeRule.onNodeWithTag("create.size.chip.${it.value}").assertExists() }
    }

    private fun setStrip(sizes: List<VariantId>) {
        composeRule.setContent {
            PuzzleBoxTheme {
                ToolStrip(
                    canUndo = false,
                    canRedo = false,
                    onUndo = {},
                    onRedo = {},
                    sizes = sizes,
                    size = sizes.first(),
                    sizeLabel = { it.value },
                    onResize = {},
                    onClear = {},
                    hint = {},
                )
            }
        }
    }
}
