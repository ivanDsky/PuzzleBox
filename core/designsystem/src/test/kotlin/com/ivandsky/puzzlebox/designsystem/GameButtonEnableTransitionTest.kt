package com.ivandsky.puzzlebox.designsystem

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

// The disabled-to-enabled fade: on a paused clock the face fill eases
// from the muted wash to the accent, every frame between the two with no jump, and under
// reduced motion the first frame after the flip is already the accent.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class GameButtonEnableTransitionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val scheme = lightColorScheme()
    private val accent = GameButtonColors(fill = Color(0xFF2EB5A6), content = Color.White, lip = Color(0xFF1A7F74))
    private var enabled by mutableStateOf(false)

    // The face is sampled over an opaque white backdrop, so the accent is an opaque pixel
    // and the muted wash is read back from the first frame rather than recomputed.
    private val accentPixel = accent.fill

    private fun show(reducedMotion: Boolean) {
        if (reducedMotion) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        }
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MaterialTheme(colorScheme = scheme) {
                Box(Modifier.background(Color.White)) {
                    GameButtonFace(
                        colors = accent,
                        enabled = enabled,
                        interactionSource = MutableInteractionSource(),
                        label = "Hint",
                        describe = true,
                        size = GameButtonSize.Tall,
                        modifier = Modifier.testTag(FACE),
                    ) {}
                }
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
    }

    /** The face's centre pixel: the fill, since the content slot is empty. */
    private fun fill(): Color {
        val image = composeRule.onNodeWithTag(FACE).captureToImage()
        return image.toPixelMap()[image.width / 2, image.height / 2]
    }

    private fun enable() {
        composeRule.runOnUiThread { enabled = true }
    }

    /** How far [c] has travelled from [from] to [to], on the red channel (the widest gap). */
    private fun progress(c: Color, from: Color, to: Color) = (c.red - from.red) / (to.red - from.red)

    @Test
    fun `enabling fades the fill between its two colours with no flash`() {
        show(reducedMotion = false)
        val disabledPixel = fill()
        assertTrue("the wash is not the accent", abs(disabledPixel.red - accentPixel.red) > 0.3f)

        enable()
        val frames = List(FRAMES) {
            composeRule.mainClock.advanceTimeByFrame()
            fill()
        }
        val steps = frames.map { progress(it, disabledPixel, accentPixel) }

        // No frame flashes past either endpoint. Colour lerps in Oklab, so green and blue bow off the
        // straight RGB line; red, the channel with the widest gap, is the measure.
        steps.forEach { assertTrue("$steps", it >= -TOLERANCE && it <= 1f + TOLERANCE) }
        // It moves one way only, starts short of the accent, passes through the middle, and lands on it.
        steps.zipWithNext().forEach { (before, after) -> assertTrue("$steps", after >= before - TOLERANCE) }
        assertTrue("first frame already at the accent: $steps", steps.first() < 0.9f)
        assertTrue("never in between: $steps", steps.any { it in 0.1f..0.9f })
        assertEquals(1f, steps.last(), TOLERANCE)
    }

    @Test
    fun `under reduced motion the fill jumps straight to the accent`() {
        show(reducedMotion = true)
        val disabledPixel = fill()
        assertTrue("the wash is not the accent", abs(disabledPixel.red - accentPixel.red) > 0.3f)

        enable()
        // The flip lands on the frame that recomposes it; every frame is the wash or the accent, never between.
        val steps = List(REDUCED_FRAMES) {
            composeRule.mainClock.advanceTimeByFrame()
            progress(fill(), disabledPixel, accentPixel)
        }
        steps.forEach { assertTrue("$steps", abs(it) < TOLERANCE || abs(it - 1f) < TOLERANCE) }
        assertEquals(1f, steps.last(), TOLERANCE)
    }

    private companion object {
        const val FACE = "face"
        const val FRAMES = 40
        const val REDUCED_FRAMES = 3
        const val TOLERANCE = 0.02f
    }
}
