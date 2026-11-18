package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The skeleton's sheen sweeps symmetrically over the element: the band starts fully before the start edge, ends fully
 * past the end edge, and at the midpoint of the sweep is centred on the element.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SkeletonSheenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the band's offsets at the start, the middle and the end of the sweep`() {
        assertEquals("starts fully before the start edge", -100f, sheenLeft(0f, width = 200f, rtl = false), 0.001f)
        assertEquals("is centred at the middle", 50f, sheenLeft(0.5f, width = 200f, rtl = false), 0.001f)
        assertEquals("ends fully past the end edge", 200f, sheenLeft(1f, width = 200f, rtl = false), 0.001f)
    }

    @Test
    fun `the band's centre passes the element's centre at the midpoint, whatever the width`() {
        listOf(8f, 96f, 200f, 760f).forEach { width ->
            val band = width / 2f
            assertEquals("width $width", width / 2f, sheenLeft(0.5f, width, rtl = false) + band / 2f, 0.001f)
        }
    }

    @Test
    fun `the travel before the midpoint equals the travel after it, in both directions`() {
        listOf(false, true).forEach { rtl ->
            listOf(0.1f, 0.25f, 0.4f).forEach { early ->
                val centreEarly = sheenLeft(early, 200f, rtl) + 50f
                val centreLate = sheenLeft(1f - early, 200f, rtl) + 50f
                assertEquals("rtl=$rtl at $early", 200f, centreEarly + centreLate, 0.001f)
            }
        }
    }

    @Test
    fun `right to left the sweep starts at the right edge`() {
        assertEquals(200f, sheenLeft(0f, width = 200f, rtl = true), 0.001f)
        assertEquals(50f, sheenLeft(0.5f, width = 200f, rtl = true), 0.001f)
        assertEquals(-100f, sheenLeft(1f, width = 200f, rtl = true), 0.001f)
    }

    // The drawn pixels, not just the offsets: halfway through the sweep the brightest column is the element's middle one.
    @Test
    fun `halfway through the sweep the brightest column is the element's middle`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                Box(Modifier.testTag(SKELETON).width(200.dp).height(20.dp).skeleton(Color(0xFF808080), RectangleShape))
            }
        }
        composeRule.mainClock.advanceTimeBy(MotionTokens.ShimmerDuration / 2L)

        val pixels = composeRule.onNodeWithTag(SKELETON).captureToImage().toPixelMap()
        val row = (0 until pixels.width).map { pixels[it, pixels.height / 2].red }
        val brightest = row.indices.maxBy { row[it] }
        assertEquals("brightest column of ${pixels.width}", pixels.width / 2f, brightest.toFloat(), pixels.width * 0.04f)
    }

    private companion object {
        const val SKELETON = "skeleton"
    }
}
