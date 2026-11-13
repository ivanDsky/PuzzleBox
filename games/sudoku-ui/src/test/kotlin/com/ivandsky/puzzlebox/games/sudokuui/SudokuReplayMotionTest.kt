package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.replay.ReplayFrame
import com.ivandsky.puzzlebox.gameui.replay.ReplayBoardOptions
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuReplay
import com.ivandsky.puzzlebox.games.sudoku.SudokuReplayMove
import com.ivandsky.puzzlebox.games.sudoku.SudokuSession
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F14: a forward step on the Sudoku replay board plays live's motion (the placed digit's pop, the erased digit's fade)
 * only while [ReplayBoardOptions.animate]; paused, scrubbing or under Reduce motion the board snaps to the frame. The
 * clock is paused and stepped one frame at a time, and the whole rendered board is compared with its settled frame.
 * A backward step (an earlier `atMs`, as a seek back while playing) snaps even with `animate` (D1).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class SudokuReplayMotionTest {

    @get:Rule
    val rule = createComposeRule()

    // #450 "Peanut" (spec A2).
    private val givens = grid("..3.5. .2.4.6 1..... .....2 2.4.1. .1.3..")
    private val solution = grid("463251 521436 142563 356142 234615 615324")
    private val puzzle = SudokuPuzzle(SudokuGames.MINI.variant, givens, solution)
    private val initial = SudokuSession(ref = null, puzzle = puzzle)

    private fun grid(text: String): List<Int> =
        text.filterNot { it.isWhitespace() }.map { if (it == '.') 0 else it.digitToInt() }

    private var frame by mutableStateOf(frameAfter(emptyList()))
    private var options by mutableStateOf(ReplayBoardOptions(animate = true, showErrors = true))

    private fun sessionAfter(moves: List<SudokuReplayMove>): SudokuSession {
        var session = initial
        moves.forEachIndexed { i, move ->
            session = checkNotNull(SudokuReplay.step(session, move.toReplayMove(), i * 1_000L + 1_000L)) { "move $i" }
        }
        return session
    }

    private fun frameAfter(moves: List<SudokuReplayMove>, atMs: Long = moves.size * 1_000L): ReplayFrame {
        val session = sessionAfter(moves)
        return ReplayFrame(
            session = session,
            before = if (moves.isEmpty()) null else sessionAfter(moves.dropLast(1)),
            hinted = emptySet(),
            facts = SudokuReplay.factsOf(initial)(session),
            atMs = atMs,
            sinceMoveMs = 0L,
            ended = false,
        )
    }

    /** Shows the board after [before], then steps it with [step] and returns the board drawn on each of the 16 frames after the step is composed. */
    private fun stepFrames(
        animate: Boolean,
        before: List<SudokuReplayMove>,
        step: SudokuReplayMove,
        beforeAtMs: Long = before.size * 1_000L,
        stepAtMs: Long = (before.size + 1) * 1_000L,
    ): List<ImageBitmap> {
        frame = frameAfter(before, beforeAtMs)
        options = ReplayBoardOptions(animate = animate, showErrors = true)
        rule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Box { SudokuReplayBoard.Board(frame, options, Modifier.size(320.dp).testTag(BOARD)) }
            }
        }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        frame = frameAfter(before + step, stepAtMs)
        // The first frame recomposes the board with the step; a capture shows the frame before it, so the first is dropped.
        rule.mainClock.advanceTimeByFrame()
        return (0..16).map {
            rule.onNodeWithTag(BOARD).captureToImage().also { rule.mainClock.advanceTimeBy(16) }
        }.drop(1)
    }

    private fun ImageBitmap.sameAs(other: ImageBitmap) = toPixelMap().buffer.contentEquals(other.toPixelMap().buffer)

    private val placed = SudokuReplayMove.Place(0, solution[0])
    private val erased = SudokuReplayMove.Erase(0)

    @Test
    fun `playing, a placed digit pops`() {
        val frames = stepFrames(animate = true, before = emptyList(), step = placed)
        assertTrue("a frame mid-pop differs from the settled board", frames.any { !it.sameAs(frames.last()) })
    }

    @Test
    fun `paused or scrubbing, a placed digit shows at once`() {
        val frames = stepFrames(animate = false, before = emptyList(), step = placed)
        assertTrue("every frame is the settled board", frames.all { it.sameAs(frames.last()) })
    }

    @Test
    fun `playing, an erased digit fades out`() {
        val frames = stepFrames(animate = true, before = listOf(placed), step = erased)
        assertTrue("a frame mid-fade still shows the digit", frames.any { !it.sameAs(frames.last()) })
    }

    @Test
    fun `paused or scrubbing, an erased digit is gone at once`() {
        val frames = stepFrames(animate = false, before = listOf(placed), step = erased)
        assertTrue("every frame is the settled board", frames.all { it.sameAs(frames.last()) })
    }

    // A seek back while playing: the frame shown is at an earlier atMs than the one before it.
    @Test
    fun `playing, a digit that appears on a backward step shows at once`() {
        val frames = stepFrames(animate = true, before = emptyList(), step = placed, beforeAtMs = 2_000, stepAtMs = 1_000)
        assertTrue("every frame is the settled board", frames.all { it.sameAs(frames.last()) })
    }

    @Test
    fun `playing, a digit that vanishes on a backward step is gone at once`() {
        val frames = stepFrames(animate = true, before = listOf(placed), step = erased, beforeAtMs = 2_000, stepAtMs = 1_000)
        assertTrue("every frame is the settled board", frames.all { it.sameAs(frames.last()) })
    }

    private companion object {
        const val BOARD = "sudoku.replay.board"
    }
}
