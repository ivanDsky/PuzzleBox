package com.ivandsky.puzzlebox.gameui

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.CelebrationMotion
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import java.time.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Nav-rethink 129, the solve moment B with the rest on: after the sweep the board pops to 1.03 and settles, the accent
 * frame line laps the board clockwise from the top edge and is closed 1300 ms after the solve, and nothing moves from
 * then to the hand-off after [CelebrationMotion.SolveMomentMs]. Reduced motion shows the whole outline from the start, with no pop. The clock is
 * held; a 200 dp white board sits 24 dp inside a white pane, at one pixel per dp.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PlaySolveMomentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var solved by mutableStateOf<Instant?>(null)
    private var handOffs = 0

    @After
    fun restoreMotion() = setAnimatorScale(1f)

    private fun setAnimatorScale(scale: Float) {
        val resolver = RuntimeEnvironment.getApplication().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    private fun setFrame() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalSolveRestOnBoard provides true) {
                PuzzleBoxTheme(darkTheme = false) {
                    Box(Modifier.testTag(PANE).background(Color.White).padding(MARGIN.dp)) {
                        PlaySolveFrame(brand = GameBrand.Neutral, solved = solved, onHandOff = { handOffs++ }) {
                            Box(Modifier.size(BOARD.dp).background(Color.White))
                        }
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(FRAME_MS)
    }

    /** With the clock held, a test-thread write reaches the composition only once it is applied. */
    private fun solve(identity: Instant?) {
        solved = identity
        Snapshot.sendApplyNotifications()
    }

    /** Solves and steps to [ms] after the solve. */
    private fun solveAndRunTo(ms: Long) {
        solve(Instant.ofEpochSecond(1))
        composeRule.mainClock.advanceTimeBy(ms)
    }

    private fun pane(): PixelMap = composeRule.onNodeWithTag(PANE).captureToImage().toPixelMap()

    /** The board's drawn width: the tap layer fills the board and scales with it. */
    private fun boardWidth(): Float = composeRule.onNodeWithTag(REST).fetchSemanticsNode().boundsInRoot.width

    @Test
    fun `the board pops to 1,03 as the sweep ends and is back at rest while the frame draws`() {
        setFrame()
        solveAndRunTo(400)
        assertEquals("the board is still while the band sweeps", BOARD.toFloat(), boardWidth(), 0f)

        var widest = 0f
        var widestAt = 0L
        var at = 400L
        while (at < 720) {
            composeRule.mainClock.advanceTimeBy(FRAME_MS)
            at += FRAME_MS
            val width = boardWidth()
            if (width > widest) {
                widest = width
                widestAt = at
            }
        }
        assertEquals("the pop peaks at ${CelebrationMotion.BoardPopScale}", BOARD * CelebrationMotion.BoardPopScale, widest, 0.2f)
        assertTrue("the peak came at $widestAt ms, not as the 120 ms rise ends near 560 ms", widestAt in 540L..620L)

        composeRule.mainClock.advanceTimeBy(960 - at)
        assertEquals("the spring has settled to exactly full size", BOARD.toFloat(), boardWidth(), 0f)
        assertEquals(0, handOffs)
    }

    @Test
    fun `the frame line laps the board from the top edge and closes 1300 ms after the solve`() {
        setFrame()
        solveAndRunTo(460)
        assertEquals("no frame line while the band sweeps", Color.White, pane()[TOP_MIDDLE.first, TOP_MIDDLE.second])

        composeRule.mainClock.advanceTimeBy(300)
        val lapping = pane()
        assertNotEquals("the line has passed the middle of the top edge", Color.White, lapping[TOP_MIDDLE.first, TOP_MIDDLE.second])
        assertEquals("the lap is clockwise: the left edge comes last", Color.White, lapping[LEFT_UPPER.first, LEFT_UPPER.second])

        composeRule.mainClock.advanceTimeBy(400)
        assertEquals("at 1160 ms the line has not reached the top of the left edge", Color.White, pane()[LEFT_UPPER.first, LEFT_UPPER.second])

        composeRule.mainClock.advanceTimeBy(220)
        val closed = pane()
        assertEquals("the closed frame is the accent outline", closed[TOP_MIDDLE.first, TOP_MIDDLE.second], closed[LEFT_UPPER.first, LEFT_UPPER.second])
        assertNotEquals(Color.White, closed[LEFT_UPPER.first, LEFT_UPPER.second])
    }

    @Test
    fun `nothing moves from the closed frame to the hand-off after the solve moment`() {
        setFrame()
        solveAndRunTo(1_380)
        val closed = pane()
        assertEquals(BOARD.toFloat(), boardWidth(), 0f)

        composeRule.mainClock.advanceTimeBy(100)
        assertEquals("still resting", 0, handOffs)
        assertTrue("the board changed after the frame closed", closed.buffer.contentEquals(pane().buffer))

        composeRule.mainClock.advanceTimeBy(CelebrationMotion.SolveMomentMs - CLOCK_MARGIN_MS - 1_480)
        assertEquals("still resting before the moment ends", 0, handOffs)
        assertTrue("the board changed after the frame closed", closed.buffer.contentEquals(pane().buffer))

        composeRule.mainClock.advanceTimeBy(2 * CLOCK_MARGIN_MS)
        assertEquals("the hand-off follows the solve moment", 1, handOffs)
    }

    @Test
    fun `reduced motion shows the whole outline from the start, with no pop, for the same rest`() {
        setAnimatorScale(0f)
        setFrame()
        solveAndRunTo(2 * FRAME_MS)
        val first = pane()
        assertNotEquals("the outline is whole at once", Color.White, first[LEFT_UPPER.first, LEFT_UPPER.second])
        assertEquals(first[TOP_MIDDLE.first, TOP_MIDDLE.second], first[LEFT_UPPER.first, LEFT_UPPER.second])

        var at = 2 * FRAME_MS
        while (at < 720) {
            composeRule.mainClock.advanceTimeBy(FRAME_MS)
            at += FRAME_MS
            assertEquals("no pop at $at ms", BOARD.toFloat(), boardWidth(), 0f)
        }
        assertTrue("no band, no comet: the board changed", first.buffer.contentEquals(pane().buffer))

        composeRule.mainClock.advanceTimeBy(CelebrationMotion.SolveMomentMs - CLOCK_MARGIN_MS - at)
        assertEquals("the rest is a pause, so reduced motion keeps it", 0, handOffs)
        composeRule.mainClock.advanceTimeBy(2 * CLOCK_MARGIN_MS)
        assertEquals(1, handOffs)
    }

    @Test
    fun `a fresh board after the solve has no frame, and its solve plays the moment again`() {
        setFrame()
        solveAndRunTo(2_000)
        assertEquals(1, handOffs)
        assertNotEquals(Color.White, pane()[LEFT_UPPER.first, LEFT_UPPER.second])

        solve(null)
        composeRule.mainClock.advanceTimeBy(2 * FRAME_MS)
        assertEquals("play again: the frame is gone", Color.White, pane()[LEFT_UPPER.first, LEFT_UPPER.second])

        solve(Instant.ofEpochSecond(2))
        composeRule.mainClock.advanceTimeBy(760)
        val lapping = pane()
        assertNotEquals(Color.White, lapping[TOP_MIDDLE.first, TOP_MIDDLE.second])
        assertEquals("the second lap starts from nothing", Color.White, lapping[LEFT_UPPER.first, LEFT_UPPER.second])
        composeRule.mainClock.advanceTimeBy(CelebrationMotion.SolveMomentMs - CLOCK_MARGIN_MS - 760)
        assertEquals("the second rest is still on", 1, handOffs)
        composeRule.mainClock.advanceTimeBy(2 * CLOCK_MARGIN_MS)
        assertEquals(2, handOffs)
    }

    private companion object {
        /** Clock margin around [CelebrationMotion.SolveMomentMs]: whole frames, so the clock's steps can't straddle it. */
        const val CLOCK_MARGIN_MS = 50L
        const val PANE = "pane"
        const val REST = "play.solve_rest"
        const val FRAME_MS = 16L
        const val MARGIN = 24
        const val BOARD = 200

        /** On the frame line at the middle of the top edge: the lap reaches it 600 ms after the solve. */
        val TOP_MIDDLE = MARGIN + BOARD / 2 to MARGIN + 1

        /** On the frame line a quarter of the way down the left edge: the lap reaches it 1250 ms after the solve. */
        val LEFT_UPPER = MARGIN + 1 to MARGIN + BOARD / 4
    }
}
