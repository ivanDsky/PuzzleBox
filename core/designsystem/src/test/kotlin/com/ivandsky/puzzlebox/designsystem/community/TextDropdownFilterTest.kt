package com.ivandsky.puzzlebox.designsystem.community

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AppTypography
import com.ivandsky.puzzlebox.designsystem.LightColorScheme
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The text dropdown filter (epic community-browse, ticket 04): its label or value, the underline that marks a set filter,
 * the 48dp target and the role and state a screen reader hears.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TextDropdownFilterTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val value = mutableStateOf<String?>(null)
    private val open = mutableStateOf(false)
    private var clicks = 0

    private var quiet = false

    private fun show(enabled: Boolean = true) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Box(Modifier.background(Color.White)) {
                    TextDropdownFilter(
                        label = "Any game",
                        value = value.value,
                        open = open.value,
                        onClick = { clicks++ },
                        modifier = Modifier.testTag(FILTER),
                        enabled = enabled,
                        colors = if (quiet) TextDropdownFilterDefaults.quietColors() else TextDropdownFilterDefaults.colors(),
                        textStyle = if (quiet) TextDropdownFilterDefaults.quietTextStyle() else TextDropdownFilterDefaults.textStyle(),
                    )
                }
            }
        }
    }

    private fun filter() = composeRule.onNodeWithTag(FILTER)

    private fun hasUnderline() = composeRule.onAllNodes(hasTestTag(TEXT_DROPDOWN_UNDERLINE_TAG), useUnmergedTree = true)
        .fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `unset shows the label and no underline`() {
        show()

        composeRule.onNodeWithText("Any game", useUnmergedTree = true).assertIsDisplayed()
        assertTrue("no underline when nothing is picked", !hasUnderline())
    }

    @Test
    fun `set shows the value instead of the label with a 2dp secondary underline`() {
        value.value = "Zip"
        show()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Zip", useUnmergedTree = true).assertIsDisplayed()
        assertTrue("the label is replaced", composeRule.onAllNodes(androidx.compose.ui.test.hasText("Any game"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        val underline = composeRule.onNodeWithTag(TEXT_DROPDOWN_UNDERLINE_TAG, useUnmergedTree = true)
        underline.assertIsDisplayed()
        underline.assertHeightIsEqualTo(2.dp)
        val image = underline.captureToImage()
        val pixel = image.toPixelMap()[image.width / 2, image.height / 2]
        assertTrue("the underline is secondary: $pixel", close(LightColorScheme.secondary, pixel))
    }

    @Test
    fun `the touch target is 48dp`() {
        show()

        filter().assertTouchHeightIsEqualTo(48.dp)
    }

    @Test
    fun `it is a dropdown list that reports collapsed then expanded`() {
        show()
        filter().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList))
        filter().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))

        composeRule.runOnUiThread { open.value = true }
        composeRule.waitForIdle()

        filter().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
    }

    @Test
    fun `a tap reports the click`() {
        show()

        filter().performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun `disabled ignores a tap`() {
        show(enabled = false)

        filter().assertIsNotEnabled()
        filter().performClick()

        assertEquals(0, clicks)
    }

    /** The text's resolved style, read from its layout. */
    private fun textStyleOf(text: String): TextStyle {
        val results = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single().layoutInput.style
    }

    @Test
    fun `the standard look is onSurfaceVariant unset and onSurface set, in the label style`() {
        show()
        composeRule.waitForIdle()
        assertSame(LightColorScheme.onSurfaceVariant, textStyleOf("Any game").color, "unset text")
        assertEquals(AppTypography.labelLarge.fontWeight, textStyleOf("Any game").fontWeight)

        composeRule.runOnUiThread { value.value = "Zip" }
        composeRule.waitForIdle()

        assertSame(LightColorScheme.onSurface, textStyleOf("Zip").color, "set text")
        assertEquals(AppTypography.labelLarge.fontWeight, textStyleOf("Zip").fontWeight)
    }

    @Test
    fun `the quiet look is medium onSurfaceVariant with no underline even when set`() {
        quiet = true
        value.value = "Last 7 days"
        show()
        composeRule.waitForIdle()

        val style = textStyleOf("Last 7 days")
        assertSame(LightColorScheme.onSurfaceVariant, style.color, "quiet set text")
        assertEquals(FontWeight.Medium, style.fontWeight)
        assertTrue("no underline when set", !hasUnderline())
        filter().assertTouchHeightIsEqualTo(48.dp)
    }

    private fun assertSame(expected: Color, actual: Color, message: String) = assertTrue("$message: expected $expected, was $actual", close(expected, actual))

    private fun close(expected: Color, actual: Color) =
        listOf(expected.red - actual.red, expected.green - actual.green, expected.blue - actual.blue).all { kotlin.math.abs(it) < 0.02f }

    private companion object {
        const val FILTER = "filter"
    }
}
