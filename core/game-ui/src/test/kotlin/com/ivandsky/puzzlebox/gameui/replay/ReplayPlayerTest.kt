package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.replay.ReplayFrame
import com.ivandsky.puzzlebox.game.replay.Timeline
import com.ivandsky.puzzlebox.testing.replay.ReplayFixtures
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Ticket 44: every control of the single-board player, driven by the test clock. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ReplayPlayerTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** 12 moves, one a second, the last at 12 000; each places one piece. */
    private val timeline = ReplayFixtures.timeline(ReplayFixtures.SHORT_MOVES)

    private val board = object : GameReplayBoard {
        override val pieces = "pieces"

        @Composable
        override fun Board(frame: ReplayFrame, options: ReplayBoardOptions, modifier: Modifier) {
            Text("facts ${frame.facts.correct}", modifier.testTag(BOARD))
        }
    }

    private fun show(timeline: Timeline = this.timeline) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { PuzzleBoxTheme(darkTheme = false) { ReplayPlayer(timeline, board) } }
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun tap(tag: String) {
        composeRule.onNodeWithTag(tag).performClick()
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun playFor(ms: Long) = composeRule.mainClock.advanceTimeBy(ms + FIRST_FRAME_SLACK_MS)

    private fun seekTo(ms: Long) {
        composeRule.onNodeWithTag(ReplayTags.SEEK).performSemanticsAction(SemanticsActions.SetProgress) { it(ms.toFloat()) }
        composeRule.mainClock.advanceTimeByFrame()
    }

    @Test
    fun `opens paused on the finished board`() {
        show()

        composeRule.onNodeWithTag(BOARD).assertTextEquals("facts 12")
        composeRule.onNodeWithTag(ReplayTags.PLAY).assertContentDescriptionEquals("Play")
        composeRule.onNodeWithTag(ReplayTags.POSITION).assertTextEquals("0:12 / 0:12")
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.onNodeWithTag(BOARD).assertTextEquals("facts 12")
    }

    @Test
    fun `play advances the position with the test clock and pause stops it`() {
        show()
        seekTo(0)

        tap(ReplayTags.PLAY)
        composeRule.onNodeWithTag(ReplayTags.PLAY).assertContentDescriptionEquals("Pause")
        playFor(3_000)
        composeRule.onNodeWithTag(BOARD).assertTextEquals("facts 3")

        tap(ReplayTags.PLAY)
        composeRule.onNodeWithTag(ReplayTags.PLAY).assertContentDescriptionEquals("Play")
        composeRule.mainClock.advanceTimeBy(3_000)
        composeRule.onNodeWithTag(BOARD).assertTextEquals("facts 3")
    }

    @Test
    fun `play from the end starts over and stops at the end again`() {
        show()

        tap(ReplayTags.PLAY)
        playFor(2_000)
        composeRule.onNodeWithTag(BOARD).assertTextEquals("facts 2")

        composeRule.mainClock.advanceTimeBy(11_000)
        composeRule.onNodeWithTag(BOARD).assertTextEquals("facts 12")
        composeRule.onNodeWithTag(ReplayTags.PLAY).assertContentDescriptionEquals("Play")
    }

    @Test
    fun `next lands on the next tick and previous on the one before`() {
        show()
        seekTo(3_400)
        composeRule.onNodeWithTag(BOARD).assertTextEquals("facts 3")

        tap(ReplayTags.NEXT)
        composeRule.onNodeWithTag(ReplayTags.POSITION).assertTextEquals("0:04 / 0:12")
        composeRule.onNodeWithTag(BOARD).assertTextEquals("facts 4")

        tap(ReplayTags.PREVIOUS)
        tap(ReplayTags.PREVIOUS)
        composeRule.onNodeWithTag(ReplayTags.POSITION).assertTextEquals("0:02 / 0:12")
        composeRule.onNodeWithTag(BOARD).assertTextEquals("facts 2")
    }

    @Test
    fun `seek shows the frame at that time`() {
        show()

        seekTo(7_000)

        composeRule.onNodeWithTag(BOARD).assertTextEquals("facts 7")
        composeRule.onNodeWithTag(ReplayTags.POSITION).assertTextEquals("0:07 / 0:12")
    }

    @Test
    fun `speed cycles 1 2 4 8 and back, and a faster speed advances faster`() {
        show()
        composeRule.onNodeWithTag(ReplayTags.SPEED).assertContentDescriptionEquals("Speed 1×")
        for (expected in listOf("2×", "4×", "8×", "1×", "2×")) {
            tap(ReplayTags.SPEED)
            composeRule.onNodeWithTag(ReplayTags.SPEED).assertContentDescriptionEquals("Speed $expected")
        }

        seekTo(0)
        tap(ReplayTags.PLAY)
        playFor(2_000)
        composeRule.onNodeWithTag(BOARD).assertTextEquals("facts 4")
    }

    @Test
    fun `skip idle toggles and jumps an idle gap`() {
        val slow = ReplayFixtures.timeline(moves = 3, stepMs = 20_000)
        show(slow)
        seekTo(0)

        tap(ReplayTags.PLAY)
        composeRule.mainClock.advanceTimeBy(500)
        // Skip idle is on at the start: the 20 s gap before the first move is cut to its 1 s lead-in.
        composeRule.onNodeWithTag(BOARD).assertTextEquals("facts 0")
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.onNodeWithTag(BOARD).assertTextEquals("facts 1")

        tap(ReplayTags.PLAY)
        tap(ReplayTags.SKIP_IDLE)
        seekTo(0)
        tap(ReplayTags.PLAY)
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.onNodeWithTag(BOARD).assertTextEquals("facts 0")
    }

    private companion object {
        const val BOARD = "fake-board"

        /** The first frame after play only sets the clock's baseline, so it moves nothing. */
        const val FIRST_FRAME_SLACK_MS = 50L
    }
}
