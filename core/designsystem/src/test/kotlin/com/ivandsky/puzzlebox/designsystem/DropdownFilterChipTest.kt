package com.ivandsky.puzzlebox.designsystem

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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
 * The dropdown chip (epic picker-filters, ticket 01): anatomy, sizes, the colour change on the clock, the open arrow,
 * disabled, the scrolling row and the contrast floors. Sampled on a paused test clock over a white page.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class DropdownFilterChipTest {

    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun resetScale() = animatorScale(1f)

    private fun animatorScale(scale: Float) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    private val picked = mutableStateOf(false)
    private val open = mutableStateOf(false)
    private var clicks = 0

    private fun show(enabled: Boolean = true) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Box(Modifier.background(Color.White)) {
                    DropdownFilterChip(
                        label = "Source",
                        picked = picked.value,
                        open = open.value,
                        onClick = { clicks++ },
                        modifier = Modifier.testTag(CHIP),
                        enabled = enabled,
                    )
                }
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
    }

    /** Turns a state on and lets the composition see it, which a paused clock does not do alone. */
    private fun flip(state: MutableState<Boolean>) {
        composeRule.runOnUiThread { state.value = true }
        Snapshot.sendApplyNotifications()
    }

    private fun chip() = composeRule.onNodeWithTag(CHIP)

    private fun tagged(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true)

    private fun hasTag(tag: String) = composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** The chip face's fill, read a few pixels under its top edge, clear of the border and the label. */
    private fun fill(): Color {
        val image = chip().captureToImage()
        return image.toPixelMap()[image.width / 2, (FILL_PROBE_DP * composeRule.density.density).toInt()]
    }

    private fun assertSame(expected: Color, actual: Color, message: String) {
        val close = listOf(expected.red - actual.red, expected.green - actual.green, expected.blue - actual.blue).all { kotlin.math.abs(it) < COLOUR_TOLERANCE }
        assertTrue("$message: expected $expected, was $actual", close)
    }

    private fun assertDiffers(left: Color, right: Color, message: String) {
        val apart = listOf(left.red - right.red, left.green - right.green, left.blue - right.blue).any { kotlin.math.abs(it) > COLOUR_TOLERANCE }
        assertTrue("$message: both $left", apart)
    }

    private fun ImageBitmap.inkAt(yFraction: Float): Boolean {
        val pixel = toPixelMap()[width / 2, (height * yFraction).toInt()]
        return pixel.luminance() < INK_LUMINANCE
    }

    @Test
    fun `nothing picked is outlined with no check and the arrow`() {
        show()

        assertTrue("no check", !hasTag(FILTER_CHIP_CHECK_TAG))
        tagged(FILTER_CHIP_ARROW_TAG).assertIsDisplayed()
        assertSame(Color.White, fill(), "an outlined chip has no fill")
    }

    @Test
    fun `picked shows the check and fills with secondaryContainer`() {
        picked.value = true
        show()
        composeRule.mainClock.advanceTimeBy(MotionTokens.DurationShort.toLong() + FRAME_MS)

        tagged(FILTER_CHIP_CHECK_TAG).assertIsDisplayed()
        tagged(FILTER_CHIP_ARROW_TAG).assertIsDisplayed()
        assertSame(LightColorScheme.secondaryContainer, fill(), "a picked chip's container")
    }

    @Test
    fun `the face is 32dp and the touch target is 48dp`() {
        show()

        chip().assertHeightIsEqualTo(32.dp)
        chip().assertTouchHeightIsEqualTo(48.dp)
    }

    @Test
    fun `the fill changes over the short duration`() {
        show()
        val start = fill()

        flip(picked)
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(MotionTokens.DurationShort / 2L)
        val middle = fill()
        composeRule.mainClock.advanceTimeBy(MotionTokens.DurationShort / 2L)
        val end = fill()

        assertSame(Color.White, start, "at 0 ms")
        assertDiffers(start, middle, "at 60 ms the fill has left the start")
        assertDiffers(end, middle, "at 60 ms the fill has not reached the end")
        assertSame(LightColorScheme.secondaryContainer, end, "at 120 ms")
    }

    @Test
    fun `open flips the state description and turns the arrow`() {
        show()
        chip().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList))
        chip().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
        val closed = tagged(FILTER_CHIP_ARROW_TAG).captureToImage()
        assertTrue("the vertex of the closed arrow is low", closed.inkAt(LOW) && !closed.inkAt(HIGH))

        flip(open)
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(MotionTokens.DurationShort.toLong() + FRAME_MS)

        chip().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
        val turned = tagged(FILTER_CHIP_ARROW_TAG).captureToImage()
        assertTrue("the vertex of the open arrow is high", turned.inkAt(HIGH) && !turned.inkAt(LOW))
    }

    @Test
    fun `reduced motion keeps the arrow still`() {
        animatorScale(0f)
        show()

        flip(open)
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(MotionTokens.DurationShort.toLong() + FRAME_MS)

        chip().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
        val still = tagged(FILTER_CHIP_ARROW_TAG).captureToImage()
        assertTrue("the arrow did not turn", still.inkAt(LOW) && !still.inkAt(HIGH))
    }

    @Test
    fun `disabled is not tappable and draws at 38 percent`() {
        picked.value = true
        show(enabled = false)
        composeRule.mainClock.advanceTimeBy(MotionTokens.DurationShort.toLong() + FRAME_MS)

        chip().assertIsNotEnabled()
        chip().performClick()
        assertEquals("a disabled chip ignores a click", 0, clicks)
        assertSame(lerp(Color.White, LightColorScheme.secondaryContainer, 0.38f), fill(), "38% of the picked fill over white")
    }

    @Test
    fun `an enabled chip reports its click`() {
        show()

        chip().performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun `the row scrolls to its last chip at font scale 1 point 3 and spaces chips by sm`() {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                    Box(Modifier.width(RowWidth.dp).testTag(ROW)) {
                        FilterChipRow {
                            listOf("Not played", "From friends", "Mini Sudoku", "Last one").forEach {
                                DropdownFilterChip(label = it, picked = true, open = false, onClick = {}, modifier = Modifier.testTag(it))
                            }
                        }
                    }
                }
            }
        }
        val gap = composeRule.onNodeWithTag("From friends").getUnclippedBoundsInRoot().left -
            composeRule.onNodeWithTag("Not played").getUnclippedBoundsInRoot().right
        val before = composeRule.onNodeWithTag("Last one").getUnclippedBoundsInRoot().left

        composeRule.onNodeWithTag(ROW).performTouchInput { swipeLeft() }

        assertEquals("chips sit Spacing.sm apart", Spacing.sm.value, gap.value, 0.5f)
        assertTrue("the last chip moved left", composeRule.onNodeWithTag("Last one").getUnclippedBoundsInRoot().left < before)
        composeRule.onNodeWithTag("Last one").assertIsDisplayed()
    }

    @Test
    fun `a picked chip meets the contrast floors in light and dark`() {
        listOf("light" to LightColorScheme, "dark" to DarkColorScheme).forEach { (name, scheme) ->
            val text = contrast(scheme.onSecondaryContainer, scheme.secondaryContainer)
            assertTrue("$name label on its fill = $text", text >= 4.5f)
            listOf(scheme.surface, scheme.surfaceContainerLow).forEach { surface ->
                val mark = contrast(scheme.onSecondaryContainer, surface)
                assertTrue("$name check on $surface = $mark", mark >= 3f)
            }
        }
    }

    private fun contrast(foreground: Color, background: Color): Float {
        val top = foreground.luminance() + 0.05f
        val bottom = background.luminance() + 0.05f
        return if (top > bottom) top / bottom else bottom / top
    }

    private companion object {
        const val CHIP = "chip"
        const val ROW = "row"
        const val RowWidth = 360
        const val FRAME_MS = 16L
        const val FILL_PROBE_DP = 3f
        const val COLOUR_TOLERANCE = 0.02f
        const val INK_LUMINANCE = 0.8f

        /** Rows of the chevron's centre column: the vertex sits at 0.6 of the icon, and at 0.4 once turned. */
        const val LOW = 0.6f
        const val HIGH = 0.4f
    }
}
