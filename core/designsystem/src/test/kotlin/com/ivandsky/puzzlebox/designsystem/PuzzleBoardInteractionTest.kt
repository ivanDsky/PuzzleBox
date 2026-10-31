package com.ivandsky.puzzlebox.designsystem

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// The gesture seam of the board, exercised for real: tap commits on release, drag
// reports its start cell, every newly entered cell in order, and end vs cancel.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PuzzleBoardInteractionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val taps = mutableListOf<Pair<Int, Int>>()
    private val started = mutableListOf<Pair<Int, Int>>()
    private val entered = mutableListOf<Pair<Int, Int>>()
    private var ended = 0
    private var cancelled = 0

    private fun setContent() {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                PuzzleBoard(
                    cellsPerSide = 4,
                    modifier = Modifier
                        .testTag("board")
                        .requiredSize(400.dp),
                    onCellTap = { row, col -> taps += row to col },
                    onCellDragStart = { row, col -> started += row to col },
                    onCellDrag = { row, col -> entered += row to col },
                    onDragEnd = { ended++ },
                    onDragCancel = { cancelled++ },
                )
            }
        }
    }

    @Test
    fun `tap reports the tapped cell`() {
        setContent()
        composeRule.onNodeWithTag("board").performTouchInput {
            click(Offset(width * 0.125f, height * 0.125f))
        }
        composeRule.waitForIdle()
        assertEquals(listOf(0 to 0), taps)
        assertEquals(0, started.size)
    }

    @Test
    fun `drag reports start cell, entered cells in order, and end`() {
        setContent()
        composeRule.onNodeWithTag("board").performTouchInput {
            // bottom-left cell -> two cells to the right
            down(Offset(width * 0.125f, height * 0.875f))
            moveTo(Offset(width * 0.375f, height * 0.875f))
            moveTo(Offset(width * 0.625f, height * 0.875f))
            up()
        }
        composeRule.waitForIdle()
        assertEquals(listOf(3 to 0), started)
        assertEquals(listOf(3 to 1, 3 to 2), entered)
        assertEquals(1, ended)
        assertEquals(0, cancelled)
    }

    @Test
    fun `cancelling a drag reports cancel, not end`() {
        setContent()
        composeRule.onNodeWithTag("board").performTouchInput {
            down(Offset(width * 0.125f, height * 0.125f))
            moveTo(Offset(width * 0.375f, height * 0.125f))
            cancel()
        }
        composeRule.waitForIdle()
        assertEquals(listOf(0 to 0), started)
        assertEquals(1, cancelled)
        assertEquals(0, ended)
    }
}
