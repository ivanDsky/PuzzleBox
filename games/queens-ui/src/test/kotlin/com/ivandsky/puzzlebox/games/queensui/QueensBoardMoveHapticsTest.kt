package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensSession
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSystemClock

/**
 * S2 on the real Queens board: each board change plays through the classifier. Each row is real gesture input on
 * the 4×4 fixture, a step slower than the shared tick gap (#1686), so every change that ticks is heard.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w840dp-h1000dp-420dpi")
class QueensBoardMoveHapticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val n = 4
    private val played = ArrayList<HapticFeedbackType>()
    private val haptics = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            played += hapticFeedbackType
        }
    }

    private fun showBoard(state: QueensBoardState) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                    QueensPlayBoard(state, Modifier.testTag(BoardTag))
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun tapCell(row: Int, col: Int) {
        ShadowSystemClock.advanceBy(PACE)
        composeRule.onNodeWithTag(BoardTag).performTouchInput {
            click(Offset(width * (col + 0.5f) / n, height * (row + 0.5f) / n))
        }
        composeRule.waitForIdle()
    }

    /** Drags across row [row] from column [fromCol] to [toCol], one cell per move. */
    private fun dragRow(row: Int, fromCol: Int, toCol: Int) {
        composeRule.onNodeWithTag(BoardTag).performTouchInput { down(Offset(width * (fromCol + 0.5f) / n, height * (row + 0.5f) / n)) }
        composeRule.waitForIdle()
        val step = if (toCol >= fromCol) 1 else -1
        for (col in fromCol + step..toCol step step) {
            // One move per frame: the effect reads the session once per frame, as a finger does.
            ShadowSystemClock.advanceBy(PACE)
            composeRule.onNodeWithTag(BoardTag).performTouchInput {
                moveTo(Offset(width * (col + 0.5f) / n, height * (row + 0.5f) / n))
            }
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithTag(BoardTag).performTouchInput { up() }
        composeRule.waitForIdle()
    }

    /** Row 0 = [cross, empty, cross, queen], auto-cross OFF so only the two manual crosses are marks. */
    private fun crossEmptyCrossQueen(): QueensSession =
        QueensUiFixtures.session().withAutoCross(false)
            .withCrossToggled(Cell(0), QueensUiFixtures.T0)
            .withCrossToggled(Cell(2), QueensUiFixtures.T0)
            .withQueenPlaced(Cell(3), QueensUiFixtures.T0)

    @Test
    fun `a tap ticks each change and no tap buzzes`() {
        val state = QueensPlayFixtures.boardState(QueensUiFixtures.session())
        showBoard(state)

        tapCell(0, 0) // empty → X
        assertEquals(listOf(HapticFeedbackType.TextHandleMove), played)

        tapCell(0, 0) // X → queen; auto-cross fills around it
        assertEquals(listOf(HapticFeedbackType.TextHandleMove, HapticFeedbackType.TextHandleMove), played)

        composeRule.runOnIdle { state.undo() }
        composeRule.waitForIdle()
        assertEquals("undo is silent", 2, played.size)
    }

    @Test
    fun `a board that opens with marks on it plays nothing`() {
        showBoard(QueensPlayFixtures.boardState(crossEmptyCrossQueen()))

        assertEquals(emptyList<HapticFeedbackType>(), played)
    }

    @Test
    fun `a paint drag over three empty cells ticks once per frame that marks cells`() {
        val state = QueensPlayFixtures.boardState(QueensUiFixtures.session())
        showBoard(state)

        dragRow(1, fromCol = 0, toCol = 2)

        assertEquals(3, state.session.marks.size)
        // The touch-slop frame marks the start cell and the next cell in one event, so they share one tick.
        assertEquals(listOf(HapticFeedbackType.TextHandleMove, HapticFeedbackType.TextHandleMove), played)
    }

    @Test
    fun `an erase swipe ticks once per cross it lifts`() {
        val state = QueensPlayFixtures.boardState(crossEmptyCrossQueen())
        showBoard(state)

        dragRow(0, fromCol = 0, toCol = 3)

        assertEquals(null, state.session.markAt(Cell(0)))
        assertEquals(null, state.session.markAt(Cell(2)))
        assertEquals(listOf(HapticFeedbackType.TextHandleMove, HapticFeedbackType.TextHandleMove), played)
    }

    private companion object {
        const val BoardTag = "queens.haptics.board"
        val PACE: Duration = Duration.ofMillis(100)
    }
}
