package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The trace gesture contract (user gate feedback R1 — ticket 26): drawing never cancels —
 * hook-approved cells extend, invalid cells are ignored, release keeps the trace as
 * drawn; only the immediately-previous cell retracts by draw-back; a deliberate tap on a
 * traced cell retracts to it (the first traced cell empties); a tap never extends and a
 * tap elsewhere is a no-op.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class DragTraceTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** 5x5 board; hooks mimic Zip: 4-way adjacency + no revisit, first cell = waypoint 1. */
    private class ZipLikeHooks(private val rows: Int = 5, private val cols: Int = 5) : DragTraceHooks {
        override fun canExtend(path: List<GridCell>, next: GridCell): Boolean {
            if (next in path) return false
            val last = path.lastOrNull() ?: return next == GridCell(0, 0) // start = waypoint 1
            return next.row in (last.row - 1)..(last.row + 1) &&
                next.col in (last.col - 1)..(last.col + 1) &&
                (next.row == last.row || next.col == last.col) &&
                next.row in 0 until rows && next.col in 0 until cols
        }
    }

    private val events = mutableListOf<DragEvent>()

    /** 5x5 board (see [setContent]); cell centers from the board's MEASURED size — no
     *  requiredSize crutch, so a sizing regression (e.g. lost aspectRatio collapsing
     *  the board to zero height) moves the cells and fails the gestures. */
    private fun cellCenter(row: Int, col: Int): Offset {
        val size = composeRule.onNodeWithTag("trace").fetchSemanticsNode().size
        return Offset(
            x = (col + 0.5f) * size.width / 5f,
            y = (row + 0.5f) * size.height / 5f,
        )
    }

    private fun setContent(path: List<GridCell> = emptyList()) {
        events.clear()
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                // Column + fillMaxWidth only: the board must derive its own height from
                // aspectRatio — an unsized board would collapse and fail the gestures.
                Column(Modifier.fillMaxWidth()) {
                    DragTraceBoard(
                        rows = 5,
                        cols = 5,
                        path = path,
                        hooks = ZipLikeHooks(),
                        onEvent = { events += it },
                        modifier = Modifier.fillMaxWidth().testTag("trace"),
                    )
                }
            }
        }
    }

    @Test
    fun `drag forward extends hook-approved cells in order and release keeps the trace`() {
        setContent()
        composeRule.onNodeWithTag("trace").performTouchInput {
            down(cellCenter(0, 0))
            moveTo(cellCenter(0, 1))
            moveTo(cellCenter(1, 1))
            up()
        }
        composeRule.waitForIdle()
        assertEquals(
            listOf(
                DragEvent.Extend(GridCell(0, 0)),
                DragEvent.Extend(GridCell(0, 1)),
                DragEvent.Extend(GridCell(1, 1)),
            ),
            events,
        )
    }

    @Test
    fun `wandering over invalid cells never extends or cancels, only reports refusals`() {
        setContent()
        composeRule.onNodeWithTag("trace").performTouchInput {
            down(cellCenter(3, 3))
            moveTo(cellCenter(3, 4))
            moveTo(cellCenter(4, 4))
            moveTo(cellCenter(2, 2))
            up()
        }
        composeRule.waitForIdle()
        assertEquals(
            listOf(GridCell(3, 3), GridCell(3, 4), GridCell(4, 4), GridCell(2, 2)).map(DragEvent::Refused),
            events,
        )
    }

    @Test
    fun `drawing onto the previous cell retracts by one`() {
        setContent(path = listOf(GridCell(0, 0), GridCell(0, 1), GridCell(1, 1)))
        composeRule.onNodeWithTag("trace").performTouchInput {
            down(cellCenter(1, 1)) // the tail
            moveTo(cellCenter(0, 1)) // one step back
            up()
        }
        composeRule.waitForIdle()
        assertEquals(listOf(DragEvent.Retract(1)), events)
    }

    @Test
    fun `drawing across an earlier traced cell never slashes the path`() {
        setContent(path = listOf(GridCell(0, 0), GridCell(0, 1), GridCell(0, 2), GridCell(1, 2)))
        composeRule.onNodeWithTag("trace").performTouchInput {
            down(cellCenter(1, 0)) // untraced, non-adjacent to the tail → refused
            moveTo(cellCenter(1, 1)) // adjacent to the tail → the only extend of the stroke
            moveTo(cellCenter(0, 1)) // traced, but not the previous cell → ignored (R1)
            moveTo(cellCenter(1, 1)) // tail re-entry → no-op
            up()
        }
        composeRule.waitForIdle()
        assertEquals(listOf(DragEvent.Refused(GridCell(1, 0)), DragEvent.Extend(GridCell(1, 1))), events)
    }

    @Test
    fun `a stroke starting on an earlier traced cell retracts only through previous cells`() {
        setContent(path = listOf(GridCell(2, 1), GridCell(2, 2), GridCell(1, 2), GridCell(0, 2)))
        composeRule.onNodeWithTag("trace").performTouchInput {
            down(cellCenter(2, 2)) // traced, NOT the previous cell — touch-down retracts nothing
            moveTo(cellCenter(1, 1)) // untraced, hook-rejected → refused, trace untouched
            moveTo(cellCenter(1, 2)) // previous → retract one
            moveTo(cellCenter(2, 2)) // previous again → retract one
            up()
        }
        composeRule.waitForIdle()
        assertEquals(listOf(DragEvent.Refused(GridCell(1, 1)), DragEvent.Retract(2), DragEvent.Retract(1)), events)
    }

    @Test
    fun `tap on a mid-trace cell retracts to it`() {
        setContent(path = listOf(GridCell(0, 0), GridCell(0, 1), GridCell(1, 1)))
        composeRule.onNodeWithTag("trace").performTouchInput {
            click(cellCenter(0, 1))
        }
        composeRule.waitForIdle()
        assertEquals(listOf(DragEvent.Retract(1)), events)
    }

    @Test
    fun `tap on the start cell empties the trace to the start state`() {
        setContent(path = listOf(GridCell(0, 0), GridCell(0, 1)))
        composeRule.onNodeWithTag("trace").performTouchInput {
            click(cellCenter(0, 0))
        }
        composeRule.waitForIdle()
        assertEquals(listOf(DragEvent.Clear), events)
    }

    @Test
    fun `tap on a non-traced cell is a no-op even when the hooks would allow extending`() {
        setContent(path = listOf(GridCell(0, 0), GridCell(0, 1)))
        composeRule.onNodeWithTag("trace").performTouchInput {
            click(cellCenter(0, 2)) // adjacent to the tail — the old contract extended here
            click(cellCenter(4, 4)) // nowhere near the trace
        }
        composeRule.waitForIdle()
        assertEquals(emptyList<DragEvent>(), events)
    }

    @Test
    fun `tap on the trace tail is a no-op`() {
        setContent(path = listOf(GridCell(0, 0), GridCell(0, 1)))
        composeRule.onNodeWithTag("trace").performTouchInput {
            click(cellCenter(0, 1))
        }
        composeRule.waitForIdle()
        assertEquals(emptyList<DragEvent>(), events)
    }

    @Test
    fun `movement within the touch slop still counts as a tap`() {
        setContent(path = listOf(GridCell(0, 0), GridCell(0, 1), GridCell(1, 1)))
        composeRule.onNodeWithTag("trace").performTouchInput {
            down(cellCenter(0, 1))
            moveBy(Offset(3f, 2f)) // under the slop — still a deliberate tap
            up()
        }
        composeRule.waitForIdle()
        assertEquals(listOf(DragEvent.Retract(1)), events)
    }
}
