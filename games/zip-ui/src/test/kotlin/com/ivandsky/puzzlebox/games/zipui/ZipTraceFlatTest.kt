package com.ivandsky.puzzlebox.games.zipui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.game.replay.ReplayFrame
import com.ivandsky.puzzlebox.designsystem.LightBoardTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.replay.ReplayBoardOptions
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipReplay
import com.ivandsky.puzzlebox.games.zip.render.ZipRendererModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Flat surfaces (nav-rethink B1, F1 "3d path distracting"): with the flat fact on, the Zip trace is one
 * stroke in `pathTrace` — no lip band and no offset — in play and in replay; with it off the lip stays.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ZipTraceFlatTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val lip = LightBoardTokens.pathTraceLip
    private val trace = LightBoardTokens.pathTrace

    private fun PixelMap.contains(color: Color): Boolean =
        (0 until height).any { y -> (0 until width).any { x -> this[x, y] == color } }

    private fun px(dp: Int): Int = with(composeRule.density) { dp.dp.toPx() }.toInt()

    private fun capture(flat: Boolean, content: @Composable () -> Unit): PixelMap {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false, flatSurfaces = flat) {
                Box(Modifier.size(300.dp).testTag("board")) { content() }
            }
        }
        composeRule.waitForIdle()
        return composeRule.onNodeWithTag("board").captureToImage().toPixelMap()
    }

    private fun tracedModel(): ZipRendererModel {
        val path = listOf(0, 1, 2)
        return ZipRendererModel(
            n = 3,
            path = path,
            cells = (0 until 9).map { idx ->
                ZipRendererModel.CellRender(
                    label = null,
                    isWaypoint = false,
                    isStart = false,
                    isOnPath = idx in path,
                    isNextTarget = false,
                )
            },
            walls = emptyList(),
            hintHighlight = null,
            rejection = null,
            status = ZipRendererModel.Status.PLAYING,
        )
    }

    @Test
    fun `flat play trace has no lip pixel and no offset`() {
        val image = capture(flat = true) { ZipPipes(tracedModel()) }
        // A 100dp pitch: the 54dp line spans y 23–77dp along row 0. Flat, nothing sits below it.
        assertEquals(trace, image[px(150), px(50)])
        assertFalse("a lip pixel under the flat line", image.contains(lip))
        assertTrue("the flat line ends at 77dp", image[px(150), px(82)] != trace)
        assertEquals(trace, image[px(150), px(76)])
    }

    @Test
    fun `flat replay trace has no lip pixel`() {
        val session = ZipPlayFixture.session().copy(path = (0..9).map(::Cell).take(5))
        val frame = ReplayFrame(
            session = session,
            before = null,
            moves = emptyList(),
            hinted = emptySet(),
            facts = ZipReplay.factsOf(ZipPlayFixture.session())(session),
            atMs = 5_000L,
            sinceMoveMs = 0L,
            ended = false,
        )
        val image = capture(flat = true) {
            ZipReplayBoard.Board(frame, ReplayBoardOptions(animate = false, showErrors = true), Modifier.size(300.dp))
        }
        assertTrue("the replay shows the trace", image.contains(trace))
        assertFalse("a lip pixel in the flat replay", image.contains(lip))
    }

    @Test
    fun `flat off keeps the lip`() {
        val image = capture(flat = false) { ZipPipes(tracedModel()) }
        assertEquals(trace, image[px(150), px(50)])
        assertEquals(lip, image[px(150), px(82)])
    }
}
