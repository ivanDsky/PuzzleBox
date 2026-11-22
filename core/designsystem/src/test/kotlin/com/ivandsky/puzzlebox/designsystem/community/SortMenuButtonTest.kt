package com.ivandsky.puzzlebox.designsystem.community

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The sort menu button (epic community-browse, ticket 04): the current option beside the sort icon, the anchored menu with
 * its tick on the current option, the pick that comes back, and the role and state a screen reader hears.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SortMenuButtonTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var expanded by mutableStateOf(false)
    private var current by mutableStateOf("Most played")
    private val picks = mutableListOf<String>()

    private fun show() {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                SortMenuButton(
                    options = listOf("Most played", "Newest"),
                    selected = current,
                    optionLabel = { it },
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    onSelect = { picks += it; current = it },
                    modifier = Modifier.testTag(BUTTON),
                )
            }
        }
    }

    private fun button() = composeRule.onNodeWithTag(BUTTON)

    private fun ticks() = composeRule.onAllNodesWithTag(SORT_MENU_TICK_TAG).fetchSemanticsNodes().size

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTag(tag: String) =
        onAllNodes(hasTestTag(tag), useUnmergedTree = true)

    @Test
    fun `closed it shows the current option and no menu`() {
        show()

        composeRule.onNodeWithText("Most played", useUnmergedTree = true).assertIsDisplayed()
        assertTrue("the other option is not listed yet", composeRule.onAllNodes(hasText("Newest")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `the touch target is 48dp`() {
        show()

        button().assertTouchHeightIsEqualTo(48.dp)
    }

    @Test
    fun `a tap asks to open`() {
        show()

        button().performClick()

        assertTrue(expanded)
    }

    @Test
    fun `open lists every option with the tick on the current one only`() {
        expanded = true
        show()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Newest").assertIsDisplayed()
        assertEquals("one tick", 1, ticks())
        composeRule.onNode(hasTestTag(SORT_MENU_TICK_TAG), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `picking an option reports it and closes the menu`() {
        expanded = true
        show()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Newest").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("Newest"), picks)
        assertTrue("the menu asked to close", !expanded)
        composeRule.onNodeWithText("Newest", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `it is a dropdown list that reports collapsed then expanded`() {
        show()
        button().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList))
        button().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))

        expanded = true
        composeRule.waitForIdle()

        button().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
    }

    @Test
    fun `the items fade in over 140ms`() {
        composeRule.mainClock.autoAdvance = false
        expanded = true
        show()
        // Past the platform popup's own 30ms entrance, well inside the 140ms fade.
        composeRule.mainClock.advanceTimeBy(EARLY_MS)
        assertTrue("early in the fade the ink is still faint", !itemDrawn())

        composeRule.mainClock.advanceTimeBy(SORT_FADE_MS + FRAME_MS)

        assertTrue("after the fade: the items are drawn", itemDrawn())
    }

    @Test
    fun `reduced motion shows the items at once`() {
        animatorScale(0f)
        composeRule.mainClock.autoAdvance = false
        expanded = true
        show()
        composeRule.mainClock.advanceTimeBy(EARLY_MS)

        assertTrue("with reduced motion the ink is full at the same moment", itemDrawn())
    }

    /** Whether the "Newest" item's text has ink: it is dark on the light menu once its layer has any alpha. */
    private fun itemDrawn(): Boolean {
        val image = composeRule.onNodeWithText("Newest").captureToImage().toPixelMap()
        return (0 until image.width).any { x -> (0 until image.height).any { y -> image[x, y].luminance() < INK_LUMINANCE } }
    }

    private fun animatorScale(scale: Float) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    @After
    fun resetScale() = animatorScale(1f)

    @Test
    fun `the current option reads as selected to a screen reader`() {
        expanded = true
        show()
        composeRule.waitForIdle()

        composeRule.onNode(hasText("Most played").and(hasSelected()), useUnmergedTree = false)
            .assertExists()
        composeRule.onNode(hasText("Newest").and(hasSelected())).assertDoesNotExist()
    }

    private fun hasSelected() = SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)

    private companion object {
        const val BUTTON = "sort"
        const val SORT_FADE_MS = 140L
        const val FRAME_MS = 16L
        const val EARLY_MS = 48L
        const val INK_LUMINANCE = 0.4f
    }
}
