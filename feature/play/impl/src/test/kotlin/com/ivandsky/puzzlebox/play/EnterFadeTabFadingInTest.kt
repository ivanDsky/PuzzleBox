package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LocalTabFadingIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A block on a tab that is fading in is that fade's entrance: it starts settled, where a first open runs its own. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class EnterFadeTabFadingInTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun redAfterFirstFrame(fadingIn: Boolean): Float {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalTabFadingIn provides fadingIn) {
                Box(Modifier.size(40.dp).background(Color.White)) {
                    EnterFade(index = 0, reduced = false) {
                        Box(Modifier.size(40.dp).background(Color.Red).testTag("block"))
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        val image = composeRule.onNodeWithTag("block").captureToImage().toPixelMap()
        val pixel = image[image.width / 2, image.height / 2]
        // White at no alpha, pure red at full: the green channel carries how much of the block shows.
        return 1f - pixel.green
    }

    @Test
    fun `a block on a tab that is fading in shows whole on its first frame`() {
        assertEquals(1f, redAfterFirstFrame(fadingIn = true), 0.02f)
    }

    @Test
    fun `a block on a first open starts hidden and fades in`() {
        assertTrue(redAfterFirstFrame(fadingIn = false) < 0.2f)
    }
}
