package com.ivandsky.puzzlebox.designsystem

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// nav-rethink M12-1 (ticket 120): the shared underline tab strip, as a unit.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class UnderlineTabsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val three = listOf(
        UnderlineTab("New today", "t.new", count = 8),
        UnderlineTab("Friends", "t.friends", count = 120),
        UnderlineTab("Yours", "t.yours"),
    )

    private fun animatorScale(scale: Float) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    private fun indicatorX() = composeRule.onNodeWithTag("underline.tabs.indicator").getUnclippedBoundsInRoot().left.value

    private fun stripWidth() = composeRule.onNodeWithTag("underline.tabs").getUnclippedBoundsInRoot().width.value

    @Test
    fun `each tab is a Role Tab with its tag and exactly the picked one is selected`() {
        composeRule.setContent { PuzzleBoxTheme { UnderlineTabs(three, selected = 1, onSelect = {}) } }

        composeRule.onNodeWithTag("t.new").assertIsNotSelected().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        composeRule.onNodeWithTag("t.friends").assertIsSelected().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        composeRule.onNodeWithTag("t.yours").assertIsNotSelected()
    }

    @Test
    fun `a tap reports the index of the tapped tab`() {
        var picked = -1
        composeRule.setContent { PuzzleBoxTheme { UnderlineTabs(three, selected = 0, onSelect = { picked = it }) } }

        composeRule.onNodeWithTag("t.yours").performClick()

        assertEquals(2, picked)
    }

    @Test
    fun `tabs are equal width, 48dp tall, with a 1dp divider and a 3dp indicator`() {
        composeRule.setContent { PuzzleBoxTheme { UnderlineTabs(three, selected = 0, onSelect = {}) } }

        val bounds = three.map { composeRule.onNodeWithTag(it.tag).getUnclippedBoundsInRoot() }
        bounds.forEach { assertEquals(48f, it.height.value, 0.5f) }
        assertEquals(bounds[0].width.value, bounds[1].width.value, 0.5f)
        assertEquals(bounds[1].width.value, bounds[2].width.value, 0.5f)
        assertEquals(1f, composeRule.onNodeWithTag("underline.tabs.divider").getUnclippedBoundsInRoot().height.value, 0.5f)
        val indicator = composeRule.onNodeWithTag("underline.tabs.indicator").getUnclippedBoundsInRoot()
        assertEquals(3f, indicator.height.value, 0.5f)
        assertEquals(bounds[0].width.value, indicator.width.value, 0.5f)
        assertEquals(48.dp, TabHeight)
        assertEquals(3.dp, TabIndicatorHeight)
    }

    @Test
    fun `a count of 120 reads 99+, a count of none or zero draws no count, a tab description is its name`() {
        val tabs = listOf(
            UnderlineTab("Dailies", "t.d", count = 0),
            UnderlineTab("Duels", "t.duels", description = "Duels, 2 need you", trailing = { Box(Modifier.size(18.dp).testTag("t.badge")) }),
            UnderlineTab("Custom", "t.c", count = 120),
        )
        composeRule.setContent { PuzzleBoxTheme { UnderlineTabs(tabs, selected = 0, onSelect = {}) } }

        composeRule.onNodeWithTag("t.d.count").assertDoesNotExist()
        composeRule.onNodeWithTag("t.c.count", useUnmergedTree = true).assertTextEquals("99+")
        composeRule.onNodeWithTag("t.duels").assertContentDescriptionEquals("Duels, 2 need you")
        composeRule.onNodeWithTag("t.badge", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `a fractional position puts the indicator that far between the tabs`() {
        composeRule.setContent { PuzzleBoxTheme { UnderlineTabsAt(three, position = { 1.5f }, onSelect = {}) } }

        val tabWidth = stripWidth() / 3f
        assertEquals(1.5f * tabWidth, indicatorX(), 1f)
        composeRule.onNodeWithTag("t.yours").assertIsSelected() // 1.5 rounds up
    }

    @Test
    fun `the fractional position follows the pager value without a transition`() {
        var position by mutableFloatStateOf(0f)
        composeRule.setContent { PuzzleBoxTheme { UnderlineTabsAt(three, position = { position }, onSelect = {}) } }
        val tabWidth = stripWidth() / 3f

        position = 0.25f
        composeRule.waitForIdle()
        assertEquals(0.25f * tabWidth, indicatorX(), 1f)
        position = 2f
        composeRule.waitForIdle()
        assertEquals(2f * tabWidth, indicatorX(), 1f)
    }

    @Test
    fun `a whole selection slides the indicator over 240ms`() {
        animatorScale(1f)
        composeRule.mainClock.autoAdvance = false
        var selected by mutableIntStateOf(0)
        composeRule.setContent { PuzzleBoxTheme { UnderlineTabs(three, selected = selected, onSelect = {}) } }
        composeRule.mainClock.advanceTimeByFrame()
        val tabWidth = stripWidth() / 3f

        composeRule.runOnUiThread { selected = 2 }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(100)
        val midway = indicatorX()
        assertTrue("indicator at $midway should be mid-slide", midway > 0.1f * tabWidth && midway < 1.9f * tabWidth)
        composeRule.mainClock.advanceTimeBy(300)
        assertEquals(2f * tabWidth, indicatorX(), 1f)
    }

    @Test
    fun `under reduced motion a whole selection moves the indicator at once`() {
        animatorScale(0f)
        composeRule.mainClock.autoAdvance = false
        var selected by mutableIntStateOf(0)
        composeRule.setContent { PuzzleBoxTheme { UnderlineTabs(three, selected = selected, onSelect = {}) } }
        composeRule.mainClock.advanceTimeByFrame()
        val tabWidth = stripWidth() / 3f

        composeRule.runOnUiThread { selected = 2 }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()

        assertEquals(2f * tabWidth, indicatorX(), 1f)
    }

    @Test
    fun `four tabs with counts at 320dp and font scale 1 point 3 keep every count inside its tab`() {
        val four = listOf(
            UnderlineTab("Dailies", "t.d"),
            UnderlineTab("Friends", "t.f", count = 12),
            UnderlineTab("Duels", "t.duels", trailing = { Box(Modifier.size(18.dp).testTag("t.badge")) }),
            UnderlineTab("Custom", "t.c", count = 3),
        )
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.3f)) {
                PuzzleBoxTheme { Box(Modifier.size(width = 320.dp, height = 100.dp)) { UnderlineTabs(four, selected = 3, onSelect = {}) } }
            }
        }

        listOf("t.f" to "t.f.count", "t.c" to "t.c.count").forEach { (tab, count) ->
            val cell = composeRule.onNodeWithTag(tab).getUnclippedBoundsInRoot()
            val number = composeRule.onNodeWithTag(count, useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertTrue("$count $number leaves $tab $cell", number.left >= cell.left && number.right <= cell.right && number.width.value > 0f)
        }
        val badge = composeRule.onNodeWithTag("t.badge", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val duels = composeRule.onNodeWithTag("t.duels").getUnclippedBoundsInRoot()
        assertTrue("badge $badge leaves $duels", badge.right <= duels.right)
    }

    private fun showAt320WithLargeText(tabs: List<UnderlineTab>, position: () -> Float = { 0f }) {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.3f)) {
                PuzzleBoxTheme { Box(Modifier.size(width = 320.dp, height = 100.dp)) { UnderlineTabsAt(tabs, position, onSelect = {}) } }
            }
        }
        composeRule.waitForIdle()
    }

    private fun labelIsCut(label: String): Boolean {
        val node = composeRule.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return results.single().isLineEllipsized(0)
    }

    @Test
    fun `New today 8 is shown in full at 320dp and font scale 1 point 3 with three tabs`() {
        val community = listOf(
            UnderlineTab("New today", "t.new", count = 8),
            UnderlineTab("Friends", "t.friends", count = 5),
            UnderlineTab("Yours", "t.yours", count = 120),
        )
        showAt320WithLargeText(community)

        val widths = community.map { composeRule.onNodeWithTag(it.tag).getUnclippedBoundsInRoot().width.value }
        assertTrue("New today is cut in widths $widths", !labelIsCut("New today"))
        assertTrue("Friends is cut", !labelIsCut("Friends"))
        assertTrue("Yours is cut", !labelIsCut("Yours"))
        val cell = composeRule.onNodeWithTag("t.new").getUnclippedBoundsInRoot()
        val count = composeRule.onNodeWithTag("t.new.count", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("count $count leaves $cell", count.right <= cell.right)
    }

    @Test
    fun `when the tabs do not all fit an equal share they split the strip by their natural width`() {
        showAt320WithLargeText(three)

        val widths = three.map { composeRule.onNodeWithTag(it.tag).getUnclippedBoundsInRoot().width.value }
        assertTrue("widths $widths are still equal", widths.max() - widths.min() > 2f)
        assertEquals(320f, widths.sum(), 1.5f)
    }

    @Test
    fun `the indicator follows the real tab bounds between two unequal tabs`() {
        showAt320WithLargeText(three, position = { 0.5f })

        val first = composeRule.onNodeWithTag("t.new").getUnclippedBoundsInRoot()
        val second = composeRule.onNodeWithTag("t.friends").getUnclippedBoundsInRoot()
        val indicator = composeRule.onNodeWithTag("underline.tabs.indicator").getUnclippedBoundsInRoot()
        assertEquals((first.left.value + second.left.value) / 2f, indicator.left.value, 1f)
        assertEquals((first.width.value + second.width.value) / 2f, indicator.width.value, 1f)
    }

    @Test
    fun `labels are cut only when the natural widths together exceed the strip`() {
        val four = listOf(
            UnderlineTab("Dailies", "t.d"),
            UnderlineTab("Friends", "t.f", count = 12),
            UnderlineTab("Duels", "t.duels", trailing = { Box(Modifier.size(18.dp)) }),
            UnderlineTab("Custom", "t.c", count = 3),
        )
        showAt320WithLargeText(four)

        assertTrue("a label is cut", listOf("Dailies", "Friends", "Duels", "Custom").any { labelIsCut(it) })
        val widths = four.map { composeRule.onNodeWithTag(it.tag).getUnclippedBoundsInRoot().width.value }
        assertEquals(320f, widths.sum(), 1.5f)
    }
}
