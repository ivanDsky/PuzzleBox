package com.ivandsky.puzzlebox.games.zipui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import com.ivandsky.puzzlebox.designsystem.LightBoardTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The live trace line on the real play screen, mid-gesture (the finger stays down): every
 * cell the finger joins shows in the rendered board without anything else poking a redraw.
 * Pins that the line's draw observes the state that moves it, so a drag with no other
 * invalidation still paints.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ZipTraceDragRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val controller = ZipPlayController(PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), {}, {})).also {
        check(it.adoptToday(ZipPlayFixture.envelope(), ZipPlayFixture.ref(LocalDate.of(2026, 10, 31))) == Adopt.Adopted)
    }

    private fun show() = composeRule.setContent {
        PuzzleBoxTheme(darkTheme = false) {
            ZipPlayScreen(
                controller = controller,
                status = PlayBoardStatus.Ready(null),
                layout = PlayLayout.SinglePane,
                sidePanel = null,
                onRetry = {},
                onSolved = { _, _ -> },
            )
        }
    }

    /**
     * The board's pixel at the centre of cell ([row], [col]) of the 5 × 5 board, once the line
     * has had time to grow: a frame is drawn every 16 ms for a quarter second, as a device would.
     */
    private fun cellPixel(row: Int, col: Int): Color {
        lateinit var image: androidx.compose.ui.graphics.ImageBitmap
        repeat(16) {
            composeRule.mainClock.advanceTimeBy(16)
            image = composeRule.onNodeWithTag("play.board").captureToImage()
        }
        val pixels = image.toPixelMap()
        return pixels[(pixels.width * (col + 0.5f) / 5).toInt(), (pixels.height * (row + 0.5f) / 5).toInt()]
    }

    private fun cellAt(width: Float, height: Float, row: Int, col: Int) =
        Offset(width * (col + 0.5f) / 5, height * (row + 0.5f) / 5)

    @Test
    fun `a held drag paints each cell it joins`() {
        show()
        composeRule.waitForIdle()
        val bare = cellPixel(0, 2)
        assertNotEquals(LightBoardTokens.pathTrace, bare)

        composeRule.onNodeWithTag("play.board").performTouchInput {
            down(cellAt(width.toFloat(), height.toFloat(), 0, 0))
            moveTo(cellAt(width.toFloat(), height.toFloat(), 0, 1))
        }
        composeRule.waitForIdle()
        assertEquals(LightBoardTokens.pathTrace, cellPixel(0, 1))

        composeRule.onNodeWithTag("play.board").performTouchInput {
            moveTo(cellAt(width.toFloat(), height.toFloat(), 0, 2))
            moveTo(cellAt(width.toFloat(), height.toFloat(), 0, 3))
        }
        composeRule.waitForIdle()
        assertEquals(LightBoardTokens.pathTrace, cellPixel(0, 2))
        assertEquals(LightBoardTokens.pathTrace, cellPixel(0, 3))

        composeRule.onNodeWithTag("play.board").performTouchInput { up() }
    }

    @Test
    fun `a held backtrack retracts the painted line`() {
        show()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("play.board").performTouchInput {
            down(cellAt(width.toFloat(), height.toFloat(), 0, 0))
            (1..3).forEach { moveTo(cellAt(width.toFloat(), height.toFloat(), 0, it)) }
        }
        composeRule.waitForIdle()
        assertEquals(LightBoardTokens.pathTrace, cellPixel(0, 3))

        composeRule.onNodeWithTag("play.board").performTouchInput {
            moveTo(cellAt(width.toFloat(), height.toFloat(), 0, 2))
            moveTo(cellAt(width.toFloat(), height.toFloat(), 0, 1))
        }
        composeRule.waitForIdle()
        assertEquals(LightBoardTokens.pathTrace, cellPixel(0, 1))
        assertNotEquals(LightBoardTokens.pathTrace, cellPixel(0, 3))

        composeRule.onNodeWithTag("play.board").performTouchInput { up() }
    }
}
