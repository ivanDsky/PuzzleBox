package com.ivandsky.puzzlebox.create.editor

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.VariantId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Finish tool on the strip (E1): absent without a slot; disabled, enabled and busy as its slot
 * says; a tap goes to the slot's press (busy taps too, to cancel); and with it the strip still
 * holds all five tools inside its card on the narrowest phones.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
class FinishToolStripTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var presses = 0

    @Test
    fun `without a slot the strip has no Finish tool`() {
        setStrip(finish = null)

        composeRule.onNodeWithTag(FINISH).assertDoesNotExist()
        composeRule.onNodeWithTag(CLEAR).assertIsEnabled()
    }

    @Test
    fun `a disabled slot shows a tool that takes no tap`() {
        setStrip(FinishSlot(enabled = false, busy = false, onPress = { presses++ }))

        composeRule.onNodeWithTag(FINISH).assertIsNotEnabled().assertTextContains("Finish")
        composeRule.onNodeWithTag(FINISH).performClick()
        assertEquals(0, presses)
    }

    @Test
    fun `an enabled slot presses`() {
        setStrip(FinishSlot(enabled = true, busy = false, onPress = { presses++ }))

        composeRule.onNodeWithTag(FINISH).assertIsEnabled().performClick()

        assertEquals(1, presses)
    }

    @Test
    fun `a busy slot says so and a tap still reaches the press, to cancel`() {
        setStrip(FinishSlot(enabled = true, busy = true, onPress = { presses++ }))

        composeRule.onNodeWithTag(FINISH).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription))
        composeRule.onNodeWithTag(FINISH).assertIsEnabled().performClick()

        assertEquals(1, presses)
    }

    @Test
    fun `an idle tool carries no busy description`() {
        setStrip(FinishSlot(enabled = true, busy = false, onPress = {}))

        composeRule.onNodeWithTag(FINISH).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
    }

    @Test
    fun `all five tools sit inside the strip's card on a 360dp phone`() {
        setStrip(FinishSlot(enabled = true, busy = false, onPress = {}), width = 328.dp)

        val strip = composeRule.onNodeWithTag("create.tool.strip").getUnclippedBoundsInRoot()
        val tools = listOf("create.tool.undo", "create.tool.redo", "create.tool.size", FINISH, CLEAR)
            .map { tag -> tag to composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot() }
        tools.forEach { (tag, bounds) ->
            assertTrue("$tag starts inside the strip", bounds.left >= strip.left + Spacing.sm - TOLERANCE)
            assertTrue("$tag ends inside the strip", bounds.right <= strip.right - Spacing.sm + TOLERANCE)
        }
        tools.zipWithNext().forEach { (a, b) ->
            assertTrue("${a.first} and ${b.first} do not overlap", a.second.right <= b.second.left + TOLERANCE)
        }
    }

    private fun setStrip(finish: FinishSlot?, width: Dp = 379.dp) {
        val sizes = listOf(VariantId("fake/5"), VariantId("fake/6"))
        composeRule.setContent {
            PuzzleBoxTheme {
                ToolStrip(
                    canUndo = true,
                    canRedo = true,
                    onUndo = {},
                    onRedo = {},
                    sizes = sizes,
                    size = sizes.first(),
                    sizeLabel = { it.value },
                    onResize = {},
                    onClear = {},
                    hint = {},
                    modifier = Modifier.width(width),
                    finish = finish,
                )
            }
        }
    }

    private companion object {
        const val FINISH = "create.tool.finish"
        const val CLEAR = "create.tool.clear"
        val TOLERANCE = 0.5.dp
    }
}
