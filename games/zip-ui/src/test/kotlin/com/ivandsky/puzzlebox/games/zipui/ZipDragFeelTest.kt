package com.ivandsky.puzzlebox.games.zipui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.ivandsky.puzzlebox.designsystem.LightBoardTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.games.zip.ZipPuzzle
import java.time.LocalDate
import kotlin.math.abs
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
 * Ticket 02b: on the play screen the drawn line end is at the finger on the frame of the
 * pointer event, at every drag speed, and a move redraws the line without recomposing a
 * cell. One pointer move a frame along row 0 of the 5x5 fixture; each frame is captured and
 * the head's position read off its pixels.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w800dp-h1280dp-mdpi")
class ZipDragFeelTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val walled get() = ZipPlayFixture.puzzle()

    // No walls: at every cell of row 0 the step down is as legal as the step along the row.
    private val open get() = walled.let { it.copy(board = it.board.copy(walls = emptySet())) }

    private var cellComposed: (Int) -> Unit = {}

    private fun show(puzzle: ZipPuzzle) {
        val controller = ZipPlayController(PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), {}, {}))
        check(controller.adoptToday(ZipPlayFixture.envelope(puzzle), ZipPlayFixture.ref(LocalDate.of(2026, 10, 31))) == Adopt.Adopted)
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                CompositionLocalProvider(LocalZipCellComposed provides { cellComposed(it) }) {
                    ZipPlayScreen(controller, PlayBoardStatus.Ready, PlayLayout.SinglePane, sidePanel = null, onRetry = {}, onSolved = { _, _ -> })
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
    }

    private fun board() = composeRule.onNodeWithTag("play.board")

    /** Draws a frame and reads the head's centre on row 0, in cells from the board's left edge; NaN with no line drawn. */
    private fun drawnHead(): Float {
        val pixels = board().captureToImage().toPixelMap()
        val cell = pixels.width / CELLS
        val y = (cell / 2).toInt()
        val right = (pixels.width - 1 downTo 0).firstOrNull { pixels[it, y] == LightBoardTokens.pathTrace } ?: return Float.NaN
        return (right + 1) / cell - HEAD_RADIUS_CELLS
    }

    private fun finger(x: Float, y: Float) = board().performTouchInput {
        moveTo(Offset(width * x / CELLS, height * y / CELLS), delayMillis = 0)
    }

    /** Lands on the start cell and drags past the touch slop to [START_X]; returns once that is drawn. */
    private fun beginDrag(y: Float) {
        board().performTouchInput { down(Offset(width * 0.5f / CELLS, height * y / CELLS)) }
        finger(START_X, y)
        repeat(SETTLE_FRAMES) {
            composeRule.mainClock.advanceTimeByFrame()
            drawnHead()
        }
        assertEquals("the drag has begun and its first lean is drawn", START_X, drawnHead(), TOLERANCE_CELLS)
    }

    /** Moves the finger to [x] and draws the frame of that event: how far the head is from the finger, in cells. */
    private fun lagAfterMoveTo(x: Float, y: Float): Float {
        finger(x, y)
        composeRule.mainClock.advanceTimeByFrame()
        return abs(x - drawnHead())
    }

    /** Drags right from [START_X] for [moves] frames of [step] cells each, [y] cells below the board's top. */
    private fun assertHeadKeepsUp(puzzle: ZipPuzzle, step: Float, y: Float, moves: Int) {
        show(puzzle)
        beginDrag(y)
        val lags = (1..moves).map { lagAfterMoveTo(START_X + it * step, y) }
        val late = lags.count { !(it <= TOLERANCE_CELLS) }
        assertEquals(
            "frames with the head off the finger, of $moves moves (worst ${"%.2f".format(lags.max())} cell): " +
                lags.joinToString(" ") { "%.2f".format(it) },
            0,
            late,
        )
    }

    @Test
    fun `a slow drag along the centre line draws the head at the finger on every frame`() =
        assertHeadKeepsUp(walled, step = 0.02f, y = 0.5f, moves = 135)

    @Test
    fun `a fast drag draws the head at the finger on every frame`() =
        assertHeadKeepsUp(walled, step = 0.47f, y = 0.5f, moves = 5)

    @Test
    fun `a flick across three cells in one event draws the head at the finger on that frame`() =
        assertHeadKeepsUp(walled, step = 2.9f, y = 0.5f, moves = 1)

    @Test
    fun `a drag off the centre line of a walled row draws the head level with the finger on every frame`() =
        assertHeadKeepsUp(walled, step = 0.02f, y = 0.7f, moves = 135)

    @Test
    fun `a drag off the centre line past open side cells draws the head level with the finger on every frame`() =
        assertHeadKeepsUp(open, step = 0.02f, y = 0.7f, moves = 135)

    @Test
    fun `a drag recomposes each cell it changes once and no cell per frame`() {
        show(walled)
        val recomposed = mutableListOf<Int>()
        cellComposed = { recomposed += it }

        board().performTouchInput { down(Offset(width * 0.5f / CELLS, height * 0.5f / CELLS)) }
        for (tenth in 7..25) {
            finger(tenth / 10f, 0.5f)
            composeRule.mainClock.advanceTimeByFrame()
        }

        // The trace takes cells 0, 1 and 2; cell 12 is the waypoint that becomes the next
        // target (its flash ran in composition, recomposing it on every frame of the drag).
        assertEquals(listOf(0, 1, 2, 12), recomposed.sorted())
    }

    private companion object {
        const val CELLS = 5f
        const val START_X = 0.8f

        /** Longer than any trace blend, so the first lean is at rest before a frame is measured. */
        const val SETTLE_FRAMES = 12

        /** The head's radius: half the line width, which is 0.54 of a cell widened by 1.2. */
        const val HEAD_RADIUS_CELLS = 0.324f

        /** Three px on the 110 px cell: under it the head reads as on the finger. */
        const val TOLERANCE_CELLS = 0.03f
    }
}
