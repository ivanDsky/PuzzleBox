package com.ivandsky.puzzlebox.app.e2e

import android.os.Looper
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.play.api.PlayKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Nav-rethink 36b, through the real shell with Result moments on: the win of each of the five dailies hands off to
 * Result with the celebration over it (the board never plays its own stamp), and the page under the celebration is
 * the finished page, so neither its end nor a tap that skips it shows a Result still filling in. The clock is held
 * from the winning move on and stepped a frame (16 ms) at a time.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w411dp-h891dp")
class SolveCelebrationE2ETest {

    @Test fun `a Zip win plays the celebration over Result`() = celebrates(DailySolve.Zip)

    @Test fun `a Tango win plays the celebration over Result`() = celebrates(DailySolve.Tango)

    @Test fun `a Queens win plays the celebration over Result`() = celebrates(DailySolve.Queens)

    @Test fun `a Mini Sudoku win plays the celebration over Result`() = celebrates(DailySolve.MiniSudoku)

    @Test fun `a Strands win plays the celebration over Result`() = celebrates(DailySolve.Strands)

    @Test @Config(qualifiers = TABLET)
    fun `a Zip win plays the celebration over Result on a tablet`() = celebrates(DailySolve.Zip)

    @Test @Config(qualifiers = TABLET)
    fun `a Tango win plays the celebration over Result on a tablet`() = celebrates(DailySolve.Tango)

    @Test @Config(qualifiers = TABLET)
    fun `a Queens win plays the celebration over Result on a tablet`() = celebrates(DailySolve.Queens)

    @Test @Config(qualifiers = TABLET)
    fun `a Mini Sudoku win plays the celebration over Result on a tablet`() = celebrates(DailySolve.MiniSudoku)

    @Test @Config(qualifiers = TABLET)
    fun `a Strands win plays the celebration over Result on a tablet`() = celebrates(DailySolve.Strands)

    @Test fun `Result is the finished page at the frame the celebration ends`() = finishedWhenItEnds()

    @Test @Config(qualifiers = TABLET)
    fun `Result is the finished page at the frame the celebration ends on a tablet`() = finishedWhenItEnds()

    @Test fun `a tap on the celebration's first frame shows the finished page once it is in place`() = finishedWhenSkipped()

    @Test @Config(qualifiers = TABLET)
    fun `a tap on the celebration's first frame shows the finished page once it is in place on a tablet`() = finishedWhenSkipped()

    private fun celebrates(game: DailySolve) = solving(game) {
        framesUntil("$game: the celebration over Result") {
            assertFalse("$game: the board played its own stamp before handing off", shows(BOARD_STAMP))
            shows(CELEBRATION)
        }
        assertTrue("$game: Result is drawn under the celebration", shows("result.hero.screen"))
        framesUntil("$game: the celebration's stamp") { shows(CELEBRATION_STAMP) }
    }

    private fun finishedWhenItEnds() = solving(DailySolve.Zip) {
        val toResult = framesUntil("the celebration") { shows(CELEBRATION) }
        val played = framesUntil("the celebration's end") { !shows(CELEBRATION) }
        val late = framesUntilFilled()
        assertEquals(
            "Result went on filling in for ${late * FRAME_MS} ms after the celebration ended " +
                "(solve to filled Result: ${(toResult + played + late) * FRAME_MS} ms)",
            0,
            late,
        )
    }

    private fun finishedWhenSkipped() = solving(DailySolve.Zip) {
        val toResult = framesUntil("the celebration") { shows(CELEBRATION) }
        // The page is still travelling in, so the tap goes through the overlay's click action, not a screen point.
        rule.onNodeWithTag(CELEBRATION).performSemanticsAction(SemanticsActions.OnClick)
        frame()
        assertFalse("one tap ends the celebration", shows(CELEBRATION))
        val toPlace = 1 + framesUntil("the board's exit") { !shows("route.game") }
        val late = framesUntilFilled()
        assertEquals(
            "Result went on filling in for ${late * FRAME_MS} ms after it was in place " +
                "(solve to filled Result, skipped at once: ${(toResult + toPlace + late) * FRAME_MS} ms)",
            0,
            late,
        )
    }

    /** Plays [game] to its winning move with Result moments on, holds the clock, wins, then runs [block]. */
    private fun solving(game: DailySolve, block: ShellRobot.() -> Unit) =
        shellRobot(tabAt(PlayKey), graph = shellGraph(ReleaseFlag.ResultMoments)) {
            val win = with(game) { playToTheLastMove() }
            rule.mainClock.autoAdvance = false
            rule.runOnIdle(win)
            block()
        }

    /** One frame of the held clock, with the work Result's loads posted to the main looper. */
    private fun ShellRobot.frame() {
        rule.mainClock.advanceTimeByFrame()
        shadowOf(Looper.getMainLooper()).idle()
        rule.waitForIdle()
    }

    /** The frames it takes until [done] holds. */
    private fun ShellRobot.framesUntil(what: String, done: () -> Boolean): Int {
        var frames = 0
        while (!done()) {
            if (frames >= BUDGET_FRAMES) fail("$what did not show within ${BUDGET_FRAMES * FRAME_MS} ms")
            frame()
            frames++
        }
        return frames
    }

    /** The frames from now until Result's page stops changing: 0 when it already draws what it draws at rest. */
    private fun ShellRobot.framesUntilFilled(): Int {
        val drawn = List(FILL_FRAMES) { page().also { frame() } }
        repeat(REST_FRAMES) { frame() }
        val rest = page()
        val filled = drawn.indexOfLast { it != rest } + 1
        if (filled == FILL_FRAMES) fail("Result was still changing ${FILL_FRAMES * FRAME_MS} ms on")
        return filled
    }

    /** What Result's page draws this frame, as a hash of its pixels. */
    private fun ShellRobot.page(): Int =
        rule.onNodeWithTag("result.hero.screen").captureToImage().toPixelMap().buffer.contentHashCode()

    private companion object {
        const val TABLET = "w1280dp-h800dp"
        const val CELEBRATION = "solve.celebration"
        const val CELEBRATION_STAMP = "solve.celebration_stamp"
        const val BOARD_STAMP = "play.solve_stamp"
        const val FRAME_MS = 16
        const val BUDGET_FRAMES = 250
        const val FILL_FRAMES = 100
        const val REST_FRAMES = 190
    }
}
