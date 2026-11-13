package com.ivandsky.puzzlebox.games.zipui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LightBoardTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.replay.ReplayFrame
import com.ivandsky.puzzlebox.gameui.replay.ReplayBoardOptions
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipReplay
import com.ivandsky.puzzlebox.games.zip.ZipReplayMove
import com.ivandsky.puzzlebox.games.zip.ZipSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F1 / F14: a forward step on the replay board plays Zip's motion only while [ReplayBoardOptions.animate]; paused,
 * scrubbing or under Reduce motion (animate off) the frame is drawn at once. The clock is paused and stepped one frame
 * at a time, as on a device, and the rendered board is sampled. A backward step (an earlier `atMs`, as a seek back while
 * playing) is drawn at once even with `animate` (D1).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class ZipReplayMotionTest {

    @get:Rule
    val rule = createComposeRule()

    private val initial = ZipPlayFixture.session()
    private val solution = ZipPlayFixture.solution

    private var frame by mutableStateOf(frameAfter(0))
    private var options by mutableStateOf(ReplayBoardOptions(animate = true, showErrors = true))

    private fun sessionAfter(cells: Int): ZipSession {
        var session = initial
        solution.take(cells).forEachIndexed { i, cell ->
            session = checkNotNull(ZipReplay.step(session, ZipReplayMove.Extend(cell).toReplayMove(), i * 1_000L + 1_000L)) { "extend $i" }
        }
        return session
    }

    private fun frameAfter(cells: Int, atMs: Long = cells * 1_000L): ReplayFrame {
        val session = sessionAfter(cells)
        return ReplayFrame(
            session = session,
            before = if (cells == 0) null else sessionAfter(cells - 1),
            moves = emptyList(),
            hinted = emptySet(),
            facts = ZipReplay.factsOf(initial)(session),
            atMs = atMs,
            sinceMoveMs = 0L,
            ended = false,
        )
    }

    /** Shows the board at [from] cells, then steps it to [to] cells and returns the board drawn on each of the 16 frames after the step is composed. */
    private fun stepFrames(
        animate: Boolean,
        from: Int,
        to: Int,
        fromAtMs: Long = from * 1_000L,
        toAtMs: Long = to * 1_000L,
    ): List<ImageBitmap> {
        frame = frameAfter(from, fromAtMs)
        options = ReplayBoardOptions(animate = animate, showErrors = true)
        rule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Box { ZipReplayBoard.Board(frame, options, Modifier.size(320.dp).testTag(BOARD)) }
            }
        }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        frame = frameAfter(to, toAtMs)
        // The first frame recomposes the board with the step; a capture shows the frame before it, so the first is dropped.
        rule.mainClock.advanceTimeByFrame()
        return (0..16).map {
            rule.onNodeWithTag(BOARD).captureToImage().also { rule.mainClock.advanceTimeBy(16) }
        }.drop(1)
    }

    /** The board pixel [col] × [row] cell units from its top-left corner (a 5 × 5 board). */
    private fun ImageBitmap.at(col: Float, row: Float): Color = toPixelMap()[(width * col / 5).toInt(), (height * row / 5).toInt()]

    @Test
    fun `playing, a step grows the path in from the old end`() {
        // [0, 1, 2] -> [0, 1, 2, 3]: cell 3 is in row 0, column 3.
        val frames = stepFrames(animate = true, from = 3, to = 4)
        assertNotEquals("the first frame has not reached the new cell", LightBoardTokens.pathTrace, frames.first().at(3.5f, 0.5f))
        assertEquals("the path has grown into the new cell", LightBoardTokens.pathTrace, frames.last().at(3.5f, 0.5f))
    }

    @Test
    fun `paused or scrubbing, the same step draws the whole path at once`() {
        val frames = stepFrames(animate = false, from = 3, to = 4)
        assertEquals(LightBoardTokens.pathTrace, frames.first().at(3.5f, 0.5f))
        assertEquals(LightBoardTokens.pathTrace, frames.last().at(3.5f, 0.5f))
    }

    // Cells 0..13 run through the waypoint at cell 12 (row 2, column 2); the end sits one cell past it. The ring's
    // rim sits just outside the resting dot, above its centre and clear of the horizontal pipe, so only the pop reaches it.
    private val rimAboveDot = 2.5f - ZipBoardSpec.waypointDotSizeFraction / 2 - 0.01f

    @Test
    fun `playing, a dot the path reaches pops`() {
        val frames = stepFrames(animate = true, from = 12, to = 14)
        val rest = frames.last().at(2.5f, rimAboveDot)
        assertTrue("some frame draws the dot larger than at rest", frames.any { it.at(2.5f, rimAboveDot) != rest })
    }

    @Test
    fun `paused or scrubbing, a dot the path reaches does not pop`() {
        val frames = stepFrames(animate = false, from = 12, to = 14)
        val rest = frames.last().at(2.5f, rimAboveDot)
        assertTrue("no frame draws the dot larger than at rest", frames.all { it.at(2.5f, rimAboveDot) == rest })
    }

    // A seek back while playing: the frame shown is at an earlier atMs than the one before it. Here the path is longer
    // on the earlier frame (the undo came after it), so the board grows.
    @Test
    fun `playing, a backward step that lengthens the path draws it at once`() {
        val frames = stepFrames(animate = true, from = 3, to = 4, fromAtMs = 2_000, toAtMs = 1_000)
        assertEquals(LightBoardTokens.pathTrace, frames.first().at(3.5f, 0.5f))
        assertEquals(LightBoardTokens.pathTrace, frames.last().at(3.5f, 0.5f))
    }

    @Test
    fun `playing, a backward step that shortens the path leaves at once`() {
        val frames = stepFrames(animate = true, from = 4, to = 3, fromAtMs = 2_000, toAtMs = 1_000)
        assertNotEquals("the old end is still drawn", LightBoardTokens.pathTrace, frames.first().at(3.5f, 0.5f))
    }

    @Test
    fun `playing, a backward step onto a dot does not pop it`() {
        val frames = stepFrames(animate = true, from = 12, to = 14, fromAtMs = 2_000, toAtMs = 1_000)
        val rest = frames.last().at(2.5f, rimAboveDot)
        assertTrue("no frame draws the dot larger than at rest", frames.all { it.at(2.5f, rimAboveDot) == rest })
    }

    private companion object {
        const val BOARD = "zip.replay.board"
    }
}
