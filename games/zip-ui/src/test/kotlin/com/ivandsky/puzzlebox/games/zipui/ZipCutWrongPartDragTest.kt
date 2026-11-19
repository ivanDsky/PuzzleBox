package com.ivandsky.puzzlebox.games.zipui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.DragEvent
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.games.zip.ZipPuzzle
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Backlog #1585 on the play screen, with the CutWrongPart flag on: the finger sliding back onto the wrong
 * part of the line cuts it there, onto the correct part it does not. Fingers are in cell pitches from the
 * middle of the top-left cell, as (column, row); the line is preloaded and one stroke starts on its end.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w800dp-h1280dp-mdpi")
class ZipCutWrongPartDragTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var controller: ZipPlayController
    private var n = 0

    private fun show(puzzle: ZipPuzzle, line: List<Int>, cutWrongPart: Boolean = true) {
        n = puzzle.board.n
        controller = ZipPlayController(PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), {}, {}), cutWrongPart = cutWrongPart)
        check(controller.adoptToday(ZipPlayFixture.envelope(puzzle), ZipPlayFixture.ref(LocalDate.of(2026, 10, 31))) == Adopt.Adopted)
        for (idx in line) controller.onDragEvent(DragEvent.Extend(GridCell(idx / n, idx % n)))
        check(controller.session?.path == ZipPlayFixture.path(*line.toIntArray())) { "the line did not preload" }
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                ZipPlayScreen(controller, PlayBoardStatus.Ready, PlayLayout.SinglePane, sidePanel = null, onRetry = {}, onSolved = { _, _ -> })
            }
        }
        composeRule.waitForIdle()
    }

    private fun at(x: Float, y: Float, width: Float, height: Float) = Offset(width * (x + 0.5f) / n, height * (y + 0.5f) / n)

    private fun down(x: Float, y: Float) = composeRule.onNodeWithTag("play.board").performTouchInput { down(at(x, y, width.toFloat(), height.toFloat())) }

    private fun drag(vararg points: Pair<Float, Float>) = composeRule.onNodeWithTag("play.board").performTouchInput {
        for ((x, y) in points) moveTo(at(x, y, width.toFloat(), height.toFloat()), delayMillis = 0)
    }

    private fun pathIdx() = controller.session?.path?.map { it.idx }

    @Test
    fun `sliding back onto the wrong part cuts the line there, and drawing goes on from it in the same stroke`() {
        // Bug it catches: the detour of daily 527 can only be undone cell by cell, a slide over its earlier cell is refused.
        show(ZipCutWrongPartFixture.daily527(), ZipCutWrongPartFixture.detour527)
        down(2f, 1f)
        drag(1.6f to 1f, 1.05f to 1f)
        composeRule.waitForIdle()
        val last = ZipCutWrongPartFixture.LAST_RIGHT_527
        assertEquals("cut at (1,1)", ZipCutWrongPartFixture.detour527.take(last + 2), pathIdx())

        // Back over the cell before it, which steps back as always, and on along the solution.
        drag(1f to 0.6f, 0.6f to 0.5f, 0.1f to 0.6f)
        composeRule.waitForIdle()
        assertEquals("stepped back to (0,1), the last right cell", ZipCutWrongPartFixture.detour527.take(last + 1), pathIdx())
        drag(0f to -0.1f)
        composeRule.onNodeWithTag("play.board").performTouchInput { up() }
        composeRule.waitForIdle()
        assertEquals("on to (0,0)", ZipCutWrongPartFixture.solution527Cells.take(last + 2), pathIdx())
    }

    @Test
    fun `sliding back onto the correct part cuts nothing`() {
        // Bug it catches: the cut reaching cells that agree with the solution, undoing the player's right work.
        show(ZipCutWrongPartFixture.phone3(), ZipCutWrongPartFixture.right3)
        down(1f, 0f)
        drag(1f to 0.6f, 1f to 1f)
        composeRule.onNodeWithTag("play.board").performTouchInput { up() }
        composeRule.waitForIdle()
        assertEquals(ZipCutWrongPartFixture.right3, pathIdx())
    }

    @Test
    fun `sliding back onto the cell before the end still steps back by one`() {
        // Bug it catches: the cut taking over the step back, cutting the line to the wrong cell or not at all.
        show(ZipCutWrongPartFixture.phone3(), ZipCutWrongPartFixture.right3)
        down(1f, 0f)
        drag(1.6f to 0f, 2f to 0f)
        composeRule.onNodeWithTag("play.board").performTouchInput { up() }
        composeRule.waitForIdle()
        assertEquals(ZipCutWrongPartFixture.right3.dropLast(1), pathIdx())
    }

    @Test
    fun `with the flag off the same slide over the detour changes nothing`() {
        // Bug it catches: the cut live for every player before the flag is turned on.
        show(ZipCutWrongPartFixture.daily527(), ZipCutWrongPartFixture.detour527, cutWrongPart = false)
        down(2f, 1f)
        drag(1.6f to 1f, 1.05f to 1f)
        composeRule.onNodeWithTag("play.board").performTouchInput { up() }
        composeRule.waitForIdle()
        assertEquals(ZipCutWrongPartFixture.detour527, pathIdx())
    }
}
