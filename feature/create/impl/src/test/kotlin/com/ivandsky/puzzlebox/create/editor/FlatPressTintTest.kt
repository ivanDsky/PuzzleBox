package com.ivandsky.puzzlebox.create.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.game.editor.Verdict
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Press tint (nav-rethink B1, M2 flat): the Studio's ripple-less pressables, the title block and the verdict pill,
 * show the shared wash while flat is on (ticket 16); flat off draws nothing. The probe pixel sits on bare face,
 * clear of any label and of the 0.97 scale's edge.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class FlatPressTintTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `flat on, a held press on the Studio title block tints it within the press duration`() {
        show(flat = true) { TitleBar() }
        assertTints("create.studio.title", dy = -28f)
    }

    @Test
    fun `flat off, a held press on the Studio title block leaves its pixels alone`() {
        show(flat = false) { TitleBar() }
        assertUnchanged("create.studio.title", dy = -28f)
    }

    @Test
    fun `flat on, a held press on the verdict pill tints it within the press duration`() {
        show(flat = true) { Pill() }
        assertTints("create.editor.pill", fx = 0f, dx = 8f)
    }

    @Test
    fun `flat off, a held press on the verdict pill leaves its pixels alone`() {
        show(flat = false) { Pill() }
        assertUnchanged("create.editor.pill", fx = 0f, dx = 8f)
    }

    @Composable
    private fun TitleBar() = StudioTopBar(
        title = "Castle",
        subtitle = "Queens 7×7",
        saving = false,
        onBack = {},
        onRename = {},
        onDuplicate = {},
        onDelete = {},
        pill = {},
    )

    @Composable
    private fun Pill() = VerdictPill(verdict = Verdict.Incomplete(IncompleteReason("queens.unpainted", 49)), onClick = {})


    private fun show(flat: Boolean, content: @Composable () -> Unit) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false, flatSurfaces = flat) {
                Box(Modifier.background(MaterialTheme.colorScheme.surface)) { content() }
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
    }

    /** The pixel [dx]/[dy] dp from the node's [fx] across and vertical middle: bare face, clear of any label. */
    private fun pixel(tag: String, fx: Float, dx: Float, dy: Float): Color {
        val image = composeRule.onNodeWithTag(tag).captureToImage()
        val density = composeRule.density.density
        return image.toPixelMap()[(image.width * fx + dx * density).roundToInt(), (image.height / 2 + dy * density).roundToInt()]
    }

    /** How far the probe pixel moves in the first PressDuration of a held press. */
    private fun pressShift(tag: String, fx: Float, dx: Float, dy: Float): Float {
        val before = pixel(tag, fx, dx, dy)
        composeRule.onNodeWithTag(tag).performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(MotionTokens.PressDuration.toLong())
        val after = pixel(tag, fx, dx, dy)
        return maxOf(abs(after.red - before.red), abs(after.green - before.green), abs(after.blue - before.blue))
    }

    private fun assertTints(tag: String, fx: Float = 0.5f, dx: Float = 0f, dy: Float = 0f) {
        val shift = pressShift(tag, fx, dx, dy)
        assertTrue("no wash within the press duration: moved $shift", shift > WASH_MIN)
    }

    private fun assertUnchanged(tag: String, fx: Float = 0.5f, dx: Float = 0f, dy: Float = 0f) {
        assertEquals(0f, pressShift(tag, fx, dx, dy), WASH_NONE)
    }

    private companion object {
        // The wash is 12 % of the ink over the face: well over 3 % on any pair the theme draws.
        const val WASH_MIN = 0.03f
        const val WASH_NONE = 0.005f
    }
}
