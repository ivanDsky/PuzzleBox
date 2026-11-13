package com.ivandsky.puzzlebox.games.tangoui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.replay.ReplayFrame
import com.ivandsky.puzzlebox.gameui.replay.ReplayBoardOptions
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoReplay
import com.ivandsky.puzzlebox.games.tango.TangoReplayMove
import com.ivandsky.puzzlebox.games.tango.TangoSession
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A forward step on the replay board: with `animate` a glyph fades in, out and between sun and moon,
 * so half a fade in the board differs from the finished one; without it the board lands at once.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class TangoReplayMotionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val initial = TangoSession(ref = null, puzzle = TangoPlayFixture.puzzle)

    private fun TangoSession.place(idx: Int, value: TangoCell?): TangoSession =
        checkNotNull(TangoReplay.step(this, TangoReplayMove.Place(idx, value).toReplayMove(), 1_000L))

    // Cell 9 breaks no rule with a sun or a moon, and the frames are past the settle window anyway.
    private val empty = initial
    private val sun = initial.place(9, TangoCell.SUN)
    private val moon = sun.place(9, TangoCell.MOON)
    private val erased = sun.place(9, null)

    private fun frame(session: TangoSession, before: TangoSession?, atMs: Long) = ReplayFrame(
        session = session,
        before = before,
        hinted = emptySet(),
        facts = TangoReplay.factsOf(initial)(session),
        atMs = atMs,
        sinceMoveMs = MotionTokens.SettleMillis,
        ended = false,
    )

    private fun pixels(): IntArray {
        val image = composeRule.onRoot().captureToImage()
        return IntArray(image.width * image.height).also { image.readPixels(it) }
    }

    /** The board half a fade after a step from [from] to [to], then once it has finished. */
    private fun midAndFinal(from: TangoSession, to: TangoSession, animate: Boolean): Pair<IntArray, IntArray> {
        val shown = mutableStateOf(frame(from, before = null, atMs = 1_000))
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PuzzleBoxTheme {
                Box { TangoReplayBoard.Board(shown.value, ReplayBoardOptions(animate = animate, showErrors = true), Modifier.size(320.dp)) }
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.runOnIdle { shown.value = frame(to, before = from, atMs = 2_000) }
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
        /** About 100 ms: half way through the glyph fade, long after a snap has landed. */
        const val MID_FRAMES = 6
        const val REST_FRAMES = 90
    }

    private fun assertFading(from: TangoSession, to: TangoSession) {
        val (mid, final) = midAndFinal(from, to, animate = true)
        assertFalse("the board was already at rest half way through the fade", mid.contentEquals(final))
    }

    private fun assertAtOnce(from: TangoSession, to: TangoSession) {
        val (mid, final) = midAndFinal(from, to, animate = false)
        assertArrayEquals("the board was still moving", final, mid)
    }

    @Test fun `with animate a glyph fades in`() = assertFading(empty, sun)
    @Test fun `with animate a sun fades to a moon`() = assertFading(sun, moon)
    @Test fun `with animate a glyph fades out`() = assertFading(sun, erased)
    @Test fun `without animate a glyph appears at once`() = assertAtOnce(empty, sun)
    @Test fun `without animate a sun turns to a moon at once`() = assertAtOnce(sun, moon)
    @Test fun `without animate a glyph vanishes at once`() = assertAtOnce(sun, erased)
}
