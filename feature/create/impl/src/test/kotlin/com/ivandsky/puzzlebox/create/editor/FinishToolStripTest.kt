package com.ivandsky.puzzlebox.create.editor

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Finish tool on the strip (E1, 90): absent without a slot; disabled, enabled and busy as its
 * slot says; a tap asks for a level first (four choices of at least 48dp, the slot's level checked)
 * and the pick goes to the slot; a busy tap cancels and asks nothing; and with it the strip still
 * holds all five tools inside its card on the narrowest phones.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
class FinishToolStripTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val finished = mutableListOf<Difficulty.Level>()
    private var cancels = 0

    private fun slot(enabled: Boolean = true, busy: Boolean = false, level: Difficulty.Level = Difficulty.Level.Medium) =
        FinishSlot(enabled, busy, level, onFinish = { finished += it }, onCancel = { cancels++ })

    @Test
    fun `without a slot the strip has no Finish tool`() {
        setStrip(finish = null)

        composeRule.onNodeWithTag(FINISH).assertDoesNotExist()
        composeRule.onNodeWithTag(CLEAR).assertIsEnabled()
    }

    @Test
    fun `a disabled slot shows a tool that takes no tap`() {
        setStrip(slot(enabled = false))

        composeRule.onNodeWithTag(FINISH).assertIsNotEnabled().assertTextContains("Finish")
        composeRule.onNodeWithTag(FINISH).performClick()

        composeRule.onAllNodesWithTag(LEVELS).assertCountEquals(0)
        assertTrue(finished.isEmpty())
    }

    @Test
    fun `a tap asks for a level before anything is finished, with the last choice checked`() {
        setStrip(slot(level = Difficulty.Level.Hard))

        composeRule.onNodeWithTag(FINISH).assertIsEnabled().performClick()

        assertTrue("nothing is finished until a level is picked", finished.isEmpty())
        composeRule.onNodeWithTag(LEVELS).assertExists()
        Difficulty.Level.entries.forEach { level ->
            val chip = composeRule.onNodeWithTag(chip(level))
            if (level == Difficulty.Level.Hard) chip.assertIsSelected() else chip.assertIsNotSelected()
        }
    }

    @Test
    fun `each level's chip shows that level's mark`() {
        setStrip(slot())
        composeRule.onNodeWithTag(FINISH).performClick()

        Difficulty.Level.entries.forEach { level ->
            val mark = hasTestTag("difficulty.${level.name.lowercase()}") and hasAnyAncestor(hasTestTag(chip(level)))
            composeRule.onNode(mark, useUnmergedTree = true).assertIsDisplayed()
        }
    }

    @Test
    fun `each level is a target of at least 48dp`() {
        setStrip(slot())
        composeRule.onNodeWithTag(FINISH).performClick()

        Difficulty.Level.entries.forEach { level ->
            val bounds = composeRule.onNodeWithTag(chip(level)).getUnclippedBoundsInRoot()
            assertTrue("$level is at least 48dp wide", bounds.right - bounds.left >= 48.dp - TOLERANCE)
            assertTrue("$level is at least 48dp tall", bounds.bottom - bounds.top >= 48.dp - TOLERANCE)
        }
    }

    @Test
    fun `picking a level finishes at it and closes the picker`() {
        setStrip(slot())
        composeRule.onNodeWithTag(FINISH).performClick()

        composeRule.onNodeWithTag(chip(Difficulty.Level.Expert)).performClick()

        assertEquals(listOf(Difficulty.Level.Expert), finished)
        composeRule.onAllNodesWithTag(LEVELS).assertCountEquals(0)
    }

    @Test
    fun `picking the checked level again finishes at it once more`() {
        setStrip(slot(level = Difficulty.Level.Medium))
        repeat(2) {
            composeRule.onNodeWithTag(FINISH).performClick()
            composeRule.onNodeWithTag(chip(Difficulty.Level.Medium)).performClick()
        }

        assertEquals(listOf(Difficulty.Level.Medium, Difficulty.Level.Medium), finished)
    }

    @Test
    fun `a busy slot says so and a tap cancels without asking for a level`() {
        setStrip(slot(busy = true))

        composeRule.onNodeWithTag(FINISH).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription))
        composeRule.onNodeWithTag(FINISH).assertIsEnabled().performClick()

        assertEquals(1, cancels)
        assertTrue(finished.isEmpty())
        composeRule.onAllNodesWithTag(LEVELS).assertCountEquals(0)
    }

    @Test
    fun `an idle tool carries no busy description`() {
        setStrip(slot())

        composeRule.onNodeWithTag(FINISH).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
    }

    @Test
    fun `all five tools sit inside the strip's card on a 360dp phone`() {
        setStrip(slot(), width = 328.dp)

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
        const val LEVELS = "create.finish.levels"
        val TOLERANCE = 0.5.dp

        fun chip(level: Difficulty.Level) = "create.finish.level.${level.name}"
    }
}
