package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.PlayLayout
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The tablet's selected-game outline while the row is still fading in. The fade is a
 * graphicsLayer alpha, which composites into an offscreen buffer cut to the layer's bounds,
 * so an outline painted outside the row's bounds is clipped to the card rectangle until the
 * fade ends. This holds the clock at a partial alpha and reads the pixels where the outline
 * runs: the middle of each straight edge (outside the card) and its rounded corner.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h900dp")
class LibraryOutlineFadeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var background = Color.Unspecified

    @Test
    fun `the selected outline is whole while its row is still fading in`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                background = MaterialTheme.colorScheme.background
                Box(Modifier.testTag("stage").background(background).padding(24.dp)) {
                    LibraryList(
                        state = previewState(),
                        layout = PlayLayout.TwoPane,
                        scroll = rememberScrollState(),
                        onRow = {},
                        modifier = Modifier.width(400.dp),
                    )
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(50)

        val image = composeRule.onNodeWithTag("stage").captureToImage()
        val stage = composeRule.onNodeWithTag("stage").fetchSemanticsNode().boundsInRoot
        val card = composeRule.onNodeWithTag("library.row.zip").fetchSemanticsNode().boundsInRoot
            .translate(-stage.left, -stage.top)

        // The stroke's centre line: OutlineGap + half OutlineWidth (4.5dp) outside the card.
        val ring = with(composeRule.density) { 4.5.dp.toPx() }
        // The rounded corner's 45° point: 28.5dp radius about the card's corner-radius centre.
        val corner = with(composeRule.density) { (24.dp - 28.5.dp * 0.70710678f).toPx() }
        assertDrawn(image, "top edge", card.center.x, card.top - ring)
        assertDrawn(image, "left edge", card.left - ring, card.center.y)
        assertDrawn(image, "top-left corner", card.left + corner, card.top + corner)
    }

    @Test
    fun `a bleed leaves the block's size and place in the layout unchanged`() {
        composeRule.setContent {
            Column(Modifier.width(200.dp)) {
                EnterFade(0, reduced = true, bleed = 6.dp) { Box(Modifier.fillMaxWidth().height(40.dp).testTag("bled")) }
                Box(Modifier.fillMaxWidth().height(10.dp).testTag("next"))
                EnterFade(0, reduced = true) { Box(Modifier.fillMaxWidth().height(40.dp).testTag("plain")) }
            }
        }
        val bled = composeRule.onNodeWithTag("bled").fetchSemanticsNode().boundsInRoot
        val next = composeRule.onNodeWithTag("next").fetchSemanticsNode().boundsInRoot
        val plain = composeRule.onNodeWithTag("plain").fetchSemanticsNode().boundsInRoot
        val dp = { v: Float -> with(composeRule.density) { v.dp.toPx() } }
        assertEquals(Rect(0f, 0f, dp(200f), dp(40f)), bled)
        assertEquals(dp(40f), next.top, 0.5f)
        assertEquals(dp(50f), plain.top, 0.5f)
        assertEquals(dp(200f), plain.width, 0.5f)
    }

    private fun assertDrawn(image: ImageBitmap, where: String, x: Float, y: Float) {
        val pixel = image.toPixelMap()[x.toInt(), y.toInt()]
        val off = abs(pixel.red - background.red) + abs(pixel.green - background.green) + abs(pixel.blue - background.blue)
        assertTrue("outline missing at the $where ($x, $y): pixel $pixel is the background", off > MIN_OFF)
    }

    private fun Rect.translate(dx: Float, dy: Float) = Rect(left + dx, top + dy, right + dx, bottom + dy)

    private companion object {
        const val MIN_OFF = 0.05f
    }
}
