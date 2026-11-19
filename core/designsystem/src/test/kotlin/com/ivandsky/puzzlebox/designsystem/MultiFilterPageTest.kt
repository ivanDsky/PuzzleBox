package com.ivandsky.puzzlebox.designsystem

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The multi-select page (epic picker-filters, ticket 03): the Back header over the same rows as the sheet, rows toggle and the page stays,
 * "All …" clears the group, the arrow goes back, the check slot is reserved, tags and semantics.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class MultiFilterPageTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val picks = mutableStateOf(emptySet<String>())
    private val toggles = mutableListOf<String>()
    private var clears = 0
    private var backs = 0

    private val options = listOf("a" to "Alpha", "b" to "Beta", "c" to "Gamma").map { (value, label) -> FilterOption(value, label, tag = "opt.$value") }

    private fun showPage() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                MultiFilterPage(
                    title = "Game",
                    allLabel = "All games",
                    options = options,
                    selected = picks.value,
                    onToggle = { value ->
                        toggles += value
                        picks.value = if (value in picks.value) picks.value - value else picks.value + value
                    },
                    onClearGroup = { clears++; picks.value = emptySet() },
                    onBack = { backs++ },
                    tag = "x.filter.game",
                    allTag = "x.filter.game.all",
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
    }

    private fun node(tag: String) = composeRule.onNodeWithTag(tag)

    private fun checkStrength(rowTag: String): Float {
        val image = composeRule.onNode(hasTestTag(FILTER_ROW_CHECK_TAG) and hasAnyAncestor(hasTestTag(rowTag)), useUnmergedTree = true).captureToImage()
        val pixels = image.toPixelMap()
        val base = pixels[0, 0].luminance()
        return (0 until image.width).maxOf { x -> (0 until image.height).maxOf { y -> abs(pixels[x, y].luminance() - base) } }
    }

    private fun labelBounds(text: String) = composeRule.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun top(tag: String) = node(tag).getUnclippedBoundsInRoot().top

    @Test
    fun `the page has a 48dp Back arrow and the title as a heading over the rows in order`() {
        showPage()

        node("x.filter.game.page").assertExists()
        node("x.filter.game.page.back").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Back").assertExists()
        composeRule.onNodeWithText("Game").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        assertTrue(top("x.filter.game.all") < top("opt.a") && top("opt.a") < top("opt.b") && top("opt.b") < top("opt.c"))
    }

    @Test
    fun `a row toggles once and the page stays, with its check and the checkbox role`() {
        showPage()

        node("opt.b").performClick()
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)

        assertEquals(listOf("b"), toggles)
        assertEquals(0, backs)
        node("opt.b").assertIsSelected().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
        node("opt.a").assertIsNotSelected()
        node("x.filter.game.all").assertIsNotSelected()
        assertTrue("check shows: ${checkStrength("opt.b")}", checkStrength("opt.b") > 0.2f)
        assertTrue(checkStrength("opt.a") < 0.02f)
        node("x.filter.game.page").assertExists()
    }

    @Test
    fun `the all row clears the group once, stays, and is selected with its check when nothing is picked`() {
        picks.value = setOf("a", "c")
        showPage()
        node("x.filter.game.all").assertIsNotSelected()

        node("x.filter.game.all").performClick()
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)

        assertEquals(1, clears)
        assertEquals(0, backs)
        assertTrue(toggles.isEmpty())
        node("x.filter.game.all").assertIsSelected()
        assertTrue(checkStrength("x.filter.game.all") > 0.2f)
        node("x.filter.game.page").assertExists()
    }

    @Test
    fun `the check slot is reserved so a label has the same bounds selected and not`() {
        showPage()
        val before = labelBounds("Alpha")

        node("opt.a").performClick()
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        val after = labelBounds("Alpha")

        assertEquals(before.left.value, after.left.value, 0.01f)
        assertEquals(before.right.value, after.right.value, 0.01f)
    }

    @Test
    fun `the arrow calls back once and toggles nothing`() {
        showPage()

        node("x.filter.game.page.back").performClick()

        assertEquals(1, backs)
        assertTrue(toggles.isEmpty() && clears == 0)
    }

    @Test
    fun `every row is a checkbox in one selectable group`() {
        showPage()

        listOf("x.filter.game.all").plus(options.map { it.tag!! }).forEach {
            node(it).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
        }
        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup)).assertCountEquals(1)
    }

    private companion object {
        const val SETTLE_MS = 1000L
    }
}
