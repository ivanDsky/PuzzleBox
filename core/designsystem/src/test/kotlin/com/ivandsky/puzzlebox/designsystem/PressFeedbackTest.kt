package com.ivandsky.puzzlebox.designsystem

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.down
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// The one press language (nav-rethink B1): a held press reaches the 0.97
// scale and the 12 % ink wash inside PressDuration, and both fade back on release;
// reduced motion never scales but still tints.
// Sampled on a paused test clock, with a white face and black ink so the wash is a grey step.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PressFeedbackTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun show(reducedMotion: Boolean = false) {
        if (reducedMotion) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        }
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PuzzleBoxTheme {
                CompositionLocalProvider(LocalContentColor provides Color.Black) {
                    val interaction = remember { MutableInteractionSource() }
                    Box(Modifier.background(Color.White)) {
                        Box(
                            Modifier
                                .testTag(SURFACE)
                                .pressScale(interaction, reduced = rememberReducedMotion())
                                .size(SIZE.dp)
                                .background(Color.White, SHAPE)
                                .pressTint(interaction, SHAPE)
                                .clickable(interaction, indication = null) {},
                        )
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun press() {
        composeRule.onNodeWithTag(SURFACE).performTouchInput { down(center) }
    }

    private fun release() {
        composeRule.onNodeWithTag(SURFACE).performTouchInput { up() }
    }

    private fun advance(ms: Long) = composeRule.mainClock.advanceTimeBy(ms)

    /** The surface's centre pixel: white at rest, grey once the black ink washes over it. */
    private fun wash(): Float {
        val image = composeRule.onNodeWithTag(SURFACE).captureToImage()
        return 1f - image.toPixelMap()[image.width / 2, image.height / 2].red
    }

    /** The surface's width as a share of its rest size: the layer scale shows in its root bounds. */
    private fun scale(): Float = composeRule.onNodeWithTag(SURFACE).getBoundsInRoot().width.value / SIZE

    @Test
    fun `a held press tints and scales to 0_97 within the press duration`() {
        show()
        assertEquals(0f, wash(), TOLERANCE)

        press()
        advance(MotionTokens.PressDuration.toLong())

        assertEquals(MotionTokens.PressTintAlpha, wash(), TOLERANCE)
        assertEquals(MotionTokens.PressScale, scale(), SCALE_TOLERANCE)
    }

    @Test
    fun `release fades the tint and the scale back within the press duration`() {
        show()
        press()
        advance(MotionTokens.PressDuration.toLong())
        release()
        advance(MotionTokens.PressDuration.toLong())

        assertEquals(0f, wash(), TOLERANCE)
        assertEquals(1f, scale(), SCALE_TOLERANCE)
    }

    @Test
    fun `reduced motion tints at once and never scales`() {
        show(reducedMotion = true)

        press()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()

        assertEquals(MotionTokens.PressTintAlpha, wash(), TOLERANCE)
        assertEquals(1f, scale(), SCALE_TOLERANCE)
    }

    private companion object {
        const val SURFACE = "surface"
        const val SIZE = 200f
        val SHAPE = RoundedCornerShape(24.dp)
        const val TOLERANCE = 0.02f
        const val SCALE_TOLERANCE = 0.003f
    }
}
