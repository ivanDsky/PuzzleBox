package com.ivandsky.puzzlebox.games.zipui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LightBoardTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.zip.engine.ZipValidation
import com.ivandsky.puzzlebox.games.zip.render.ZipRendererModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Snap trace on Zip (flag SnapTrace), on the Compose test clock over a 3 x 3 board of
 * 100 dp cells: the line ends on the last cell centre once the 70 ms grow has run, its head
 * never grows past rest (radius 32.4 dp on a 54 dp line), and a rejected cell does not scale.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ZipSnapTraceTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun model(path: List<Int>, rejectedCell: Int? = null) = ZipRendererModel(
        n = 3,
        path = path,
        cells = (0 until 9).map { idx ->
            ZipRendererModel.CellRender(label = null, isWaypoint = false, isStart = false, isOnPath = idx in path, isNextTarget = false)
        },
        walls = emptyList(),
        hintHighlight = null,
        rejection = rejectedCell?.let { ZipRendererModel.RejectedMove(it, ZipValidation.Reason.NotAdjacent) },
        status = ZipRendererModel.Status.PLAYING,
    )

    private fun px(dp: Int) = with(composeRule.density) { dp.dp.toPx() }.toInt()

    @Test
    fun `the line grows to the last cell centre in the snap grow and the head stays at rest`() {
        var shown by mutableStateOf(model(listOf(0)))
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Box(Modifier.size(300.dp).testTag("pipes")) { ZipPipes(shown, animate = true, snap = true) }
            }
        }
        // The test harness draws on a capture only: draw the one-cell line, swap the path, then
        // draw again so the grow starts, and let it play out on the virtual clock.
        composeRule.onNodeWithTag("pipes").captureToImage()
        composeRule.runOnIdle { shown = model(listOf(0, 1, 2)) }
        composeRule.onNodeWithTag("pipes").captureToImage()
        composeRule.mainClock.advanceTimeBy(70 + 3 * 16L)
        val settled = composeRule.onNodeWithTag("pipes").captureToImage().toPixelMap()
        assertEquals("the line end is on the last cell centre", LightBoardTokens.pathTrace, settled[px(250), px(50)])
        assertEquals("the head covers its resting radius", LightBoardTokens.pathTrace, settled[px(280), px(50)])
        assertNotEquals("no head larger than rest", LightBoardTokens.pathTrace, settled[px(284), px(50)])
        assertNotEquals("no head larger than rest, above", LightBoardTokens.pathTrace, settled[px(250), px(16)])
    }

    /** The pixel at the cell's top-edge middle and the bare board corner, after the rejection settles. */
    private fun rejectedCellEdge(snap: Boolean): Pair<Color, Color> {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Box(Modifier.size(100.dp).testTag("cell")) {
                    ZipCellDecor(model(emptyList(), rejectedCell = 4), 1, 1, animated = true, snap = snap)
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(2000)
        val image = composeRule.onNodeWithTag("cell").captureToImage().toPixelMap()
        return image[px(50), px(2)] to image[0, 0]
    }

    // Scaled to 0.86 the wash leaves a 7 dp margin; unscaled it reaches the cell edge.
    @Test
    fun `under snap a rejected cell does not shrink, its wash reaches the cell edge`() {
        val (edge, bare) = rejectedCellEdge(snap = true)
        assertNotEquals(bare, edge)
    }

    @Test
    fun `with the flag off a rejected cell still shrinks`() {
        val (edge, bare) = rejectedCellEdge(snap = false)
        assertEquals(bare, edge)
    }
}
