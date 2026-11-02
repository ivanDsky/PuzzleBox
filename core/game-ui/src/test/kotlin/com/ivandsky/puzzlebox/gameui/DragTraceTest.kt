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
 * The trace gesture contract, exercised for real (Zip §10 user rulings are the spec):
 * drag extends hook-approved cells in order, dragging back retracts to earlier cells,
 * and a single tap on an extendable adjacent cell extends once.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class DragTraceTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** 5x5 board; hooks mimic Zip: 4-way adjacency + no revisit, drag-back retracts. */
    private class ZipLikeHooks(private val rows: Int = 5, private val cols: Int = 5) : DragTraceHooks {
        override fun canExtend(path: List<GridCell>, next: GridCell): Boolean {
            if (next in path) return false
            val last = path.lastOrNull() ?: return next == GridCell(0, 0) // start = waypoint 1
            return next.row in (last.row - 1)..(last.row + 1) &&
                next.col in (last.col - 1)..(last.col + 1) &&
                (next.row == last.row || next.col == last.col) &&
                next.row in 0 until rows && next.col in 0 until cols
        }

        override fun retractIndex(path: List<GridCell>, next: GridCell): Int? =
            path.indexOf(next).takeIf { it >= 0 && it < path.lastIndex }
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
    fun `drag forward extends hook-approved cells in order`() {
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
                DragEvent.Release(GridCell(1, 1)),
            ),
            events,
        )
    }

    @Test
    fun `dragging back over the path retracts (zip section-10)`() {
        setContent(path = listOf(GridCell(0, 0), GridCell(0, 1), GridCell(1, 1)))
        composeRule.onNodeWithTag("trace").performTouchInput {
            down(cellCenter(1, 1))
            moveTo(cellCenter(0, 1)) // one step back
            moveTo(cellCenter(0, 0)) // all the way back
            up()
        }
        composeRule.waitForIdle()
        assertEquals(
            listOf(
                DragEvent.Retract(1),
                DragEvent.Retract(0),
                DragEvent.Release(GridCell(0, 0)),
            ),
            events,
        )
    }

    @Test
    fun `single tap on an adjacent cell extends once (zip section-10)`() {
        setContent(path = listOf(GridCell(0, 0), GridCell(0, 1)))
        composeRule.onNodeWithTag("trace").performTouchInput {
            click(cellCenter(0, 2))
        }
        composeRule.waitForIdle()
        assertEquals(
            listOf(DragEvent.Extend(GridCell(0, 2)), DragEvent.Release(GridCell(0, 2))),
            events,
        )
    }

    @Test
    fun `tap on a non-adjacent cell emits only the release for authoritative rejection`() {
        setContent(path = listOf(GridCell(0, 0), GridCell(0, 1)))
        composeRule.onNodeWithTag("trace").performTouchInput {
            click(cellCenter(4, 4))
        }
        composeRule.waitForIdle()
        assertEquals(listOf(DragEvent.Release(GridCell(4, 4))), events)
    }

    @Test
    fun `tap on the trace tail is a no-op`() {
        setContent(path = listOf(GridCell(0, 0), GridCell(0, 1)))
        composeRule.onNodeWithTag("trace").performTouchInput {
            click(cellCenter(0, 1))
        }
        composeRule.waitForIdle()
        assertEquals(listOf(DragEvent.Release(GridCell(0, 1))), events)
    }
}
