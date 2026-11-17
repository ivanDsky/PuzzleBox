package com.ivandsky.puzzlebox.app.e2e

import android.os.Looper
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.designsystem.CelebrationMotion
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
 * Nav-rethink 104, through the real shell with Result moments on: the win of each of the five dailies stays on its board for about 1.5 s with no stamp, and then Result opens with nothing over it; a tap on the board during the rest opens
 * Result at once. Nav-rethink 129: a board in the solve frame pops once on the way. Nav-rethink 130: all five boards pop, and Result opens 1.5 s from the solve on every one; with the flag off no board pops or rests. The clock is held from
 * the winning move on and stepped a frame (16 ms) at a time.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w411dp-h891dp")
class SolveRestE2ETest {

    @Test fun `a Zip win rests on the board, then opens Result`() = rests(DailySolve.Zip)

    @Test fun `a Tango win rests on the board, then opens Result`() = rests(DailySolve.Tango)

    @Test fun `a Queens win rests on the board, then opens Result`() = rests(DailySolve.Queens)

    @Test fun `a Mini Sudoku win rests on the board, then opens Result`() = rests(DailySolve.MiniSudoku)

    @Test fun `a Strands win rests on the board, then opens Result`() = rests(DailySolve.Strands)

    @Test @Config(qualifiers = TABLET)
    fun `a Zip win rests on the board, then opens Result on a tablet`() = rests(DailySolve.Zip)

    @Test @Config(qualifiers = TABLET)
    fun `a Queens win rests on the board, then opens Result on a tablet`() = rests(DailySolve.Queens)

    @Test @Config(qualifiers = TABLET)
    fun `a Mini Sudoku win rests on the board, then opens Result on a tablet`() = rests(DailySolve.MiniSudoku)

    @Test @Config(qualifiers = TABLET)
    fun `a Strands win rests on the board, then opens Result on a tablet`() = rests(DailySolve.Strands)

    @Test fun `a tap on the board during the rest opens Result at once`() = tapped()

    @Test @Config(qualifiers = TABLET)
    fun `a tap on the board during the rest opens Result at once on a tablet`() = tapped()

    @Test fun `a Zip win pops the board once, then opens Result`() = pops(DailySolve.Zip)

    @Test fun `a Tango win pops the board once, then opens Result`() = pops(DailySolve.Tango)

    @Test @Config(qualifiers = TABLET)
    fun `a Zip win pops the board once, then opens Result on a tablet`() = pops(DailySolve.Zip)

    @Test fun `a Queens win pops the board once, then opens Result`() = pops(DailySolve.Queens)

    @Test fun `a Mini Sudoku win pops the grid once, then opens Result`() = pops(DailySolve.MiniSudoku, SUDOKU_GRID)

    @Test fun `a Strands win pops the board once, then opens Result`() = pops(DailySolve.Strands, STRANDS_BOARD)

    @Test @Config(qualifiers = TABLET)
    fun `a Mini Sudoku win pops the grid once, then opens Result on a tablet`() = pops(DailySolve.MiniSudoku, SUDOKU_GRID)

    @Test fun `a Zip win opens Result 1500 ms from the solve`() = prompt(DailySolve.Zip)

    @Test fun `a Tango win opens Result 1500 ms from the solve`() = prompt(DailySolve.Tango)

    @Test fun `a Queens win opens Result 1500 ms from the solve`() = prompt(DailySolve.Queens)

    @Test fun `a Mini Sudoku win opens Result 1500 ms from the solve`() = prompt(DailySolve.MiniSudoku)

    @Test fun `a Strands win opens Result 1500 ms from the solve`() = prompt(DailySolve.Strands)

    @Test fun `flag off, a Mini Sudoku win neither rests nor pops`() = still(DailySolve.MiniSudoku, SUDOKU_GRID)

    @Test fun `flag off, a Strands win neither rests nor pops`() = still(DailySolve.Strands, STRANDS_BOARD)

    private fun rests(game: DailySolve) = solving(game) {
        val frames = framesUntil("$game: Result") {
            assertFalse("$game: the board played a stamp", shows(BOARD_STAMP))
            assertFalse("$game: a celebration over Result", shows(OVERLAY))
            shows("result.hero.screen")
        }
        assertTrue("$game: Result came after ${frames * FRAME_MS} ms, before the rest was over", frames * FRAME_MS >= MIN_REST_MS)
    }

    private fun tapped() = solving(DailySolve.Zip) {
        repeat(5) { frame() }
        assertTrue("the board rests", shows("route.game"))
        assertFalse("Result waits for the rest", shows("result.hero.screen"))
        rule.onNodeWithTag(REST).performSemanticsAction(SemanticsActions.OnClick)
        val frames = framesUntil("Result") { shows("result.hero.screen") }
        assertTrue("one tap opened Result in $frames frames, not after the rest", frames * FRAME_MS < MIN_REST_MS / 2)
        assertFalse(shows(OVERLAY))
    }

    /** [board] is the node the pop scales. The frame's tap layer fills its board and scales with it, so its width on screen is the board's. */
    private fun pops(game: DailySolve, board: String = REST) = solving(game) {
        framesUntil("$game: the rest") { shows(REST) }
        val atRest = width(board)
        var widest = atRest
        var last = atRest
        framesUntil("$game: Result") {
            if (shows(REST)) {
                last = width(board)
                widest = maxOf(widest, last)
            }
            shows("result.hero.screen")
        }
        assertEquals("$game: the board's pop", CelebrationMotion.BoardPopScale, widest / atRest, 0.002f)
        assertEquals("$game: the board is back at rest for the hand-off", atRest, last, 0f)
    }

    /** The moment starts at the solve: the rest is up on the next frames, and Result follows the 1.5 s with only the shell's own frames after. */
    private fun prompt(game: DailySolve) = solving(game) {
        val toRest = framesUntil("$game: the rest") { shows(REST) }
        assertTrue("$game: the rest began ${toRest * FRAME_MS} ms after the solve", toRest * FRAME_MS <= REST_START_MS)
        val toResult = toRest + framesUntil("$game: Result") { shows("result.hero.screen") }
        assertTrue("$game: Result came ${toResult * FRAME_MS} ms after the solve", toResult * FRAME_MS in MIN_REST_MS..MAX_REST_MS)
    }

    /** With Result moments off the old path runs: no rest, no pop of [board], and Result still opens. */
    private fun still(game: DailySolve, board: String) = solving(game, flags = emptyArray()) {
        val atRest = width(board)
        framesUntil("$game: Result") {
            assertFalse("$game: the board rests with the flag off", shows(REST))
            if (shows(board)) assertEquals("$game: the board moved", atRest, width(board), 0f)
            shows("result.hero.screen")
        }
    }

    private fun ShellRobot.width(tag: String): Float = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.width

    /** Plays [game] to its winning move with [flags] on (Result moments unless told otherwise), holds the clock, wins, then runs [block]. */
    private fun solving(game: DailySolve, flags: Array<ReleaseFlag> = arrayOf(ReleaseFlag.ResultMoments), block: ShellRobot.() -> Unit) =
        shellRobot(tabAt(PlayKey), graph = shellGraph(*flags)) {
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

    private companion object {
        const val TABLET = "w1280dp-h800dp"
        const val REST = "play.solve_rest"
        const val OVERLAY = "solve.celebration"
        const val SUDOKU_GRID = "sudoku.grid"
        const val STRANDS_BOARD = "strands.board"
        const val BOARD_STAMP = "play.solve_stamp"
        const val FRAME_MS = 16
        const val MIN_REST_MS = 1_300
        const val REST_START_MS = 100
        const val MAX_REST_MS = 1_700
        const val BUDGET_FRAMES = 250
    }
}
