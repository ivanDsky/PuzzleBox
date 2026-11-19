package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The single-select page (epic picker-filters, ticket 11): the Back header and rows, the pick and its 150 ms return, the ignore window,
 * the re-tap, sizes and the cap, tags and semantics.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class SingleFilterPageTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val picked = mutableStateOf<String?>(null)
    private val selects = mutableListOf<String?>()
    private var backs = 0
    private var windowHeight = 0.dp

    private fun option(value: String, label: String = value.uppercase()) =
        FilterOption(value, label, icon = { tint -> Box(Modifier.size(24.dp).background(tint)) }, tag = "opt.$value")

    private val options = listOf(option("a", "Alpha"), option("b", "Beta"), option("c", "Gamma"))

    private fun show(rows: List<FilterOption<String>> = options, fontScale: Float = 1f, followCaller: Boolean = false) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
                    SingleFilterPage(
                        title = "Game",
                        allLabel = "All games",
                        options = rows,
                        selected = picked.value,
                        onSelect = { selects += it; if (followCaller) picked.value = it },
                        onBack = { backs++ },
                        tag = "x.filter.game",
                        allTag = "x.filter.game.all",
                    )
                }
            }
        }
        composeRule.mainClock.autoAdvance = false
        advance(FRAME_MS)
    }

    private fun advance(ms: Long) = composeRule.mainClock.advanceTimeBy(ms)

    private fun node(tag: String) = composeRule.onNodeWithTag(tag)

    private fun count(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun top(tag: String) = node(tag).getUnclippedBoundsInRoot().top

    @Test
    fun `the page has a Back header, then the all row with no icon, then the options in order`() {
        show()

        node("x.filter.game.page").assertIsDisplayed()
        node("x.filter.game.page.back").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Back").assertIsDisplayed()
        composeRule.onNodeWithText("Game").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        assertTrue(top("x.filter.game.page.back") < top("x.filter.game.all"))
        assertTrue(top("x.filter.game.all") < top("opt.a") && top("opt.a") < top("opt.b") && top("opt.b") < top("opt.c"))
        val allLeft = node("x.filter.game.all").getUnclippedBoundsInRoot().left
        assertEquals("the all row's text starts at the row's 16dp padding", 16f, (composeRule.onNodeWithText("All games", useUnmergedTree = true).getUnclippedBoundsInRoot().left - allLeft).value, 0.5f)
        assertEquals("a page is not a sheet", 0, count("x.filter.game.sheet") + count("x.filter.game.sheet.close"))
    }

    @Test
    fun `the picked row is the only selected one, and all is selected only when nothing is picked`() {
        picked.value = "b"
        show()

        node("opt.b").assertIsSelected()
        node("opt.a").assertIsNotSelected()
        node("x.filter.game.all").assertIsNotSelected()
    }

    @Test
    fun `nothing picked selects the all row alone`() {
        show()

        node("x.filter.game.all").assertIsSelected()
        options.forEach { node(it.tag!!).assertIsNotSelected() }
    }

    @Test
    fun `a tap moves the highlight at once, reports once and goes back at 150 ms while a tap in between does nothing`() {
        show()

        node("opt.b").performClick()
        advance(FRAME_MS)
        node("opt.b").assertIsSelected()
        advance(100 - FRAME_MS)
        assertEquals("not back at 100 ms", 0, backs)
        node("opt.c").performClick()

        assertEquals(listOf<String?>("b"), selects)
        node("opt.c").assertIsNotSelected()
        advance(50)
        assertEquals("back at 150 ms", 1, backs)
        assertEquals(listOf<String?>("b"), selects)
    }

    @Test
    fun `the all row reports null`() {
        picked.value = "a"
        show(followCaller = true)

        node("x.filter.game.all").performClick()
        advance(MotionTokens.DurationExit.toLong())

        assertEquals(listOf<String?>(null), selects)
        node("x.filter.game.all").assertIsSelected()
        assertEquals(1, backs)
    }

    @Test
    fun `a re-tap of the picked row reports it again and goes back`() {
        picked.value = "b"
        show(followCaller = true)

        node("opt.b").performClick()
        advance(MotionTokens.DurationExit.toLong())

        assertEquals(listOf<String?>("b"), selects)
        node("opt.b").assertIsSelected()
        assertEquals(1, backs)
    }

    @Test
    fun `the arrow goes back at once and reports nothing`() {
        show()

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertEquals(1, backs)
        assertTrue(selects.isEmpty())
    }

    @Test
    fun `rows are at least 52dp with a 48dp touch height and a long label wraps and grows the row at font scale 1 point 3`() {
        val wordy = option("w", "A long source label that has to wrap onto several lines in a narrow page")
        show(options + wordy, fontScale = 1.3f)

        options.forEach {
            node(it.tag!!).assertHeightIsAtLeast(52.dp)
            assertTrue(node(it.tag!!).fetchSemanticsNode().touchBoundsInRoot.height >= 48 * composeRule.density.density)
        }
        assertTrue(node("opt.w").getUnclippedBoundsInRoot().height > node("opt.a").getUnclippedBoundsInRoot().height)
    }

    @Test
    fun `a long list scrolls inside the cap and the page is no taller than 92 percent of the window`() {
        show(List(30) { option("r$it", "Row $it") })

        assertTrue(node("x.filter.game.page").getUnclippedBoundsInRoot().height <= windowHeight * 0.92f + 0.5.dp)
        node("opt.r29").assertIsNotDisplayed()
        composeRule.mainClock.autoAdvance = true
        node("opt.r29").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `the rows are radios in one selectable group`() {
        show()

        listOf("x.filter.game.all").plus(options.map { it.tag!! }).forEach {
            node(it).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        }
        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup)).assertCountEquals(1)
    }

    private companion object {
        const val FRAME_MS = 16L
    }
}
