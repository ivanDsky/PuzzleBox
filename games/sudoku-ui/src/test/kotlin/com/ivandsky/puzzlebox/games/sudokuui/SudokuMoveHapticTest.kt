package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.sudoku.SudokuCommand
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzleRef
import com.ivandsky.puzzlebox.games.sudoku.SudokuSession
import com.ivandsky.puzzlebox.gameui.MoveHaptic
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class SudokuMoveHapticTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val givens = grid("..3.5. .2.4.6 1..... .....2 2.4.1. .1.3..")
    private val solution = grid("463251 521436 142563 356142 234615 615324")
    private val peanut450 = SudokuPuzzle(SudokuGames.MINI.variant, givens, solution)
    private val ref = SudokuPuzzleRef(SudokuGames.MINI.id, PuzzleKey.Daily(LocalDate.of(2026, 11, 4), 450))
    private val openCells = givens.indices.filter { givens[it] == 0 }

    private fun grid(text: String): List<Int> = text.filterNot { it.isWhitespace() }.map { if (it == '.') 0 else it.digitToInt() }

    private fun fresh() = SudokuBoardState(SudokuSession(ref = ref, puzzle = peanut450))

    private fun SudokuBoardState.place(idx: Int, digit: Int = solution[idx]) {
        select(idx)
        input(digit)
    }

    private fun SudokuBoardState.snap() = SudokuMoveSnapshot(session, rejection)

    /** Runs [action] on [state] and classifies the step the way the effect does. */
    private fun step(state: SudokuBoardState, action: SudokuBoardState.() -> Unit): MoveHaptic? {
        val before = state.snap()
        state.action()
        return sudokuMoveHaptic(before, state.snap())
    }

    @Test
    fun `a right or wrong entry ticks`() {
        val state = fresh()
        assertEquals(MoveHaptic.Placed, step(state) { place(0) })
        assertEquals(MoveHaptic.Placed, step(state) { place(1, digit = 1) })
    }

    @Test
    fun `an entry that closes a unit ticks as well`() {
        val state = fresh()
        state.place(0)
        state.place(1)
        state.place(3)
        assertEquals(MoveHaptic.Placed, step(state) { place(5) })
    }

    @Test
    fun `a note entered or removed ticks, the notes toggle does not`() {
        val state = fresh()
        state.select(0)
        assertNull(step(state) { toggleNotesMode() })
        assertEquals(MoveHaptic.Placed, step(state) { input(4) })
        assertEquals(MoveHaptic.Placed, step(state) { input(4) })
        assertNull(step(state) { toggleNotesMode() })
    }

    @Test
    fun `an erase is silent, of a digit or of only its notes`() {
        val state = fresh()
        state.place(0)
        assertNull(step(state) { erase() })
        state.select(1)
        state.toggleNotesMode()
        state.input(2)
        state.toggleNotesMode()
        state.select(1)
        assertNull(step(state) { erase() })
    }

    @Test
    fun `two redos in one step are silent`() {
        val state = fresh()
        state.place(0)
        state.place(1)
        state.undo()
        state.undo()
        assertNull(step(state) { redo(); redo() })
    }

    @Test
    fun `undo then the same digit again ticks`() {
        val state = fresh()
        state.place(0)
        state.undo()
        assertEquals(MoveHaptic.Placed, step(state) { place(0) })
    }

    @Test
    fun `a tap that tries to change a given is rejected, every time`() {
        val state = fresh()
        state.select(2)
        assertEquals(MoveHaptic.Rejected, step(state) { input(5) })
        assertEquals("same message, second refusal", MoveHaptic.Rejected, step(state) { input(5) })
        assertEquals(MoveHaptic.Rejected, step(state) { erase() })
    }

    @Test
    fun `undo and redo are silent`() {
        val state = fresh()
        state.place(0)
        assertNull(step(state) { undo() })
        assertNull(step(state) { redo() })
    }

    @Test
    fun `clear is silent`() {
        val state = fresh()
        state.place(0)
        assertNull(step(state) { clear() })
    }

    @Test
    fun `a hint reveal is silent`() {
        val state = fresh()
        state.requestHint()
        assertNull(step(state) { requestHint() })
        assertTrue(state.session.history.done.last() is SudokuCommand.HintReveal)
    }

    @Test
    fun `the solving entry is silent, the solve confirm owns it`() {
        val state = fresh()
        openCells.dropLast(1).forEach { state.place(it) }
        assertNull(step(state) { place(openCells.last()) })
    }

    @Test
    fun `a step that changes nothing is silent`() {
        val state = fresh()
        assertNull(step(state) { select(3) })
    }

    @Test
    fun `each move plays once through the effect, and a refused tap buzzes once`() {
        val played = ArrayList<HapticFeedbackType>()
        val haptics = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                played += hapticFeedbackType
            }
        }
        val state = fresh()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PuzzleBoxTheme {
                CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                    rememberSudokuMotion(state)
                }
            }
        }
        composeRule.runOnIdle { state.place(0) }
        composeRule.runOnIdle { assertEquals(listOf(HapticFeedbackType.TextHandleMove), played) }
        composeRule.runOnIdle {
            state.select(2)
            state.input(5)
        }
        composeRule.runOnIdle { assertEquals(listOf(HapticFeedbackType.TextHandleMove, HapticFeedbackType.Reject), played) }
    }
}
