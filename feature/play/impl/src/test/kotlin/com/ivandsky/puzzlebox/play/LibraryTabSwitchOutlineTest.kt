package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
 * The tablet's selected-game outline through the Duels → Dailies (and Friends → Dailies) tab
 * switch. The new body fades in through a graphicsLayer alpha, which composites into an
 * offscreen buffer cut to the body's bounds, so an outline reaching past the pane's sides was
 * sheared for the whole fade. This steps the paused clock a frame at a time and reads the
 * pixels where the outline runs, outside the card: each side edge must be as strong as the top
 * edge (which has room in the body) on every frame, and all of them drawn once settled.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h900dp")
class LibraryTabSwitchOutlineTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var background = Color.Unspecified
    private var tab by mutableStateOf(GamesTab.Duels)

    private val slot = DuelsSlot(
        content = { Text("duels body") },
        needYou = { 0 },
        onOpenDuel = {},
    )

    @Test
    fun `the outline is whole on every frame of the switch from Duels`() = assertWholeAfterSwitchFrom(GamesTab.Duels)

    @Test
    fun `the outline is whole on every frame of the switch from Friends`() = assertWholeAfterSwitchFrom(GamesTab.Friends)

    private fun assertWholeAfterSwitchFrom(from: GamesTab) {
        tab = from
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            // Settled rows: their own staggered fade is [LibraryOutlineFadeTest]'s; this isolates the tab body's.
            CompositionLocalProvider(LocalInspectionMode provides true) {
                PuzzleBoxTheme(darkTheme = false) {
                    background = MaterialTheme.colorScheme.background
                    Box(Modifier.testTag("stage").background(background).padding(24.dp)) {
                        LibraryList(
                            state = previewState(),
                            layout = PlayLayout.TwoPane,
                            scroll = rememberScrollState(),
                            onRow = {},
                            modifier = Modifier.width(400.dp),
                            duels = slot,
                            tab = tab,
                            onTab = { tab = it },
                        )
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        composeRule.runOnIdle { tab = GamesTab.Dailies }

        val ring = with(composeRule.density) { 4.5.dp.toPx() }
        var framesWithRow = 0
        repeat(FRAMES) { frame ->
            composeRule.mainClock.advanceTimeByFrame()
            if (composeRule.onAllNodesWithTag(ROW).fetchSemanticsNodes().isEmpty()) return@repeat
            framesWithRow++
            val image = composeRule.onNodeWithTag("stage").captureToImage()
            val stage = composeRule.onNodeWithTag("stage").fetchSemanticsNode().boundsInRoot
            val card = composeRule.onNodeWithTag(ROW).fetchSemanticsNode().boundsInRoot.translate(-stage.left, -stage.top)
            // The top edge sits inside the body's bounds, so its strength at this frame's fade is what the other edges must match.
            val top = strength(image, card.center.x, card.top - ring)
            assertEquals("frame $frame left edge", top, strength(image, card.left - ring, card.center.y), TOLERANCE)
            assertEquals("frame $frame right edge", top, strength(image, card.right + ring, card.center.y), TOLERANCE)
        }
        assertTrue("the Dailies rows never appeared", framesWithRow > FADE_FRAMES)

        val image = composeRule.onNodeWithTag("stage").captureToImage()
        val stage = composeRule.onNodeWithTag("stage").fetchSemanticsNode().boundsInRoot
        val card = composeRule.onNodeWithTag(ROW).fetchSemanticsNode().boundsInRoot.translate(-stage.left, -stage.top)
        assertDrawn(image, "settled left edge", card.left - ring, card.center.y)
        assertDrawn(image, "settled right edge", card.right + ring, card.center.y)
        assertDrawn(image, "settled top edge", card.center.x, card.top - ring)
    }

    private fun strength(image: ImageBitmap, x: Float, y: Float): Float {
        val pixel = image.toPixelMap()[x.toInt(), y.toInt()]
        return abs(pixel.red - background.red) + abs(pixel.green - background.green) + abs(pixel.blue - background.blue)
    }

    private fun assertDrawn(image: ImageBitmap, where: String, x: Float, y: Float) {
        assertTrue("outline missing at the $where ($x, $y)", strength(image, x, y) > MIN_OFF)
    }

    private fun Rect.translate(dx: Float, dy: Float) = Rect(left + dx, top + dy, right + dx, bottom + dy)

    private companion object {
        const val MIN_OFF = 0.05f
        const val ROW = "library.row.zip"
        const val TOLERANCE = 0.02f
        const val SETTLE_MS = 500L
        const val FRAMES = 40
        const val FADE_FRAMES = 10
    }
}
