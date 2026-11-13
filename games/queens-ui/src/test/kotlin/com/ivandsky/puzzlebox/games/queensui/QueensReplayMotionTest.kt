package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.replay.ReplayFrame
import com.ivandsky.puzzlebox.gameui.replay.ReplayBoardOptions
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensReplay
import com.ivandsky.puzzlebox.games.queens.QueensReplayMove
import com.ivandsky.puzzlebox.games.queens.QueensSession
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A forward step on the replay board: with `animate` a placed queen pops in and its crosses grow in,
 * so a board a few frames after the step differs from the finished one; without it the board lands at once.
 * A backward step (an earlier `atMs`, as a seek back while playing) lands at once even with `animate` (D1).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class QueensReplayMotionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val initial = QueensUiFixtures.session()

    private fun QueensSession.after(move: QueensReplayMove, atMs: Long): QueensSession =
        checkNotNull(QueensReplay.step(this, move.toReplayMove(), atMs))

    private val queen = initial.after(QueensReplayMove.Place(Cell(1)), 2_000)
    private val cross = initial.after(QueensReplayMove.Cross(Cell(15)), 2_000)

    private fun frame(session: QueensSession, before: QueensSession?, atMs: Long) = ReplayFrame(
        session = session,
        before = before,
        moves = emptyList(),
        hinted = emptySet(),
        facts = QueensReplay.factsOf(initial)(session),
        atMs = atMs,
        sinceMoveMs = 0L,
        ended = false,
    )

    private fun pixels(): IntArray {
        val image = composeRule.onRoot().captureToImage()
        return IntArray(image.width * image.height).also { image.readPixels(it) }
    }

    /** The board a few frames after a forward step to [to], then once it has come to rest. */
    private fun midAndFinal(
        to: QueensSession,
        animate: Boolean,
        from: QueensSession = initial,
        fromAtMs: Long = 1_000,
        toAtMs: Long = 2_000,
    ): Pair<IntArray, IntArray> {
        val shown = mutableStateOf(frame(from, before = null, atMs = fromAtMs))
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Box { QueensReplayBoard.Board(shown.value, ReplayBoardOptions(animate = animate, showErrors = true), Modifier.size(320.dp)) }
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.runOnIdle { shown.value = frame(to, before = initial, atMs = toAtMs) }
        // Frame by frame: an animation, even a snap, starts on one frame and ends on a later one.
        // A capture draws the board as of the frame before it, so each frame is captured and the last one kept.
        var mid = IntArray(0)
        repeat(MID_FRAMES) {
            composeRule.mainClock.advanceTimeByFrame()
            mid = pixels()
        }
        repeat(REST_FRAMES) { composeRule.mainClock.advanceTimeByFrame() }
        pixels()
        return mid to pixels()
    }

    private companion object {
        const val MID_FRAMES = 4
        const val REST_FRAMES = 90
    }

    @Test
    fun `with animate a placed queen is still popping in a few frames after the step`() {
        val (mid, final) = midAndFinal(queen, animate = true)

        assertFalse("the board was already at rest", mid.contentEquals(final))
    }

    @Test
    fun `without animate a placed queen lands at once`() {
        val (mid, final) = midAndFinal(queen, animate = false)

        assertArrayEquals("the board was still moving", final, mid)
    }

    @Test
    fun `with animate a cross is still growing in a few frames after the step`() {
        val (mid, final) = midAndFinal(cross, animate = true)

        assertFalse("the board was already at rest", mid.contentEquals(final))
    }

    @Test
    fun `without animate a cross lands at once`() {
        val (mid, final) = midAndFinal(cross, animate = false)

        assertArrayEquals("the board was still moving", final, mid)
    }

    @Test
    fun `with animate a queen that appears on a backward step lands at once`() {
        val (mid, final) = midAndFinal(queen, animate = true, from = initial, fromAtMs = 2_000, toAtMs = 1_000)

        assertArrayEquals("the board was still moving", final, mid)
    }

    @Test
    fun `with animate a cross that appears on a backward step lands at once`() {
        val (mid, final) = midAndFinal(cross, animate = true, from = initial, fromAtMs = 2_000, toAtMs = 1_000)

        assertArrayEquals("the board was still moving", final, mid)
    }
}
