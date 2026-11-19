package com.ivandsky.puzzlebox.games.sudokuui.trainer

import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuCandidates
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuTrainerMove
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuSnippetBoardStateTest {

    private val snippets = SudokuTrainerFixtures.snippets()
    private val single = snippets.first { it.placesDigit }
    private val elimination = snippets.first { !it.placesDigit }

    private fun SudokuSnippet.emptyCell(): Int = (0 until puzzle.geometry.cellCount).first { cut.boardBefore.digitAt(it) == 0 }

    private fun SudokuSnippet.givenCell(): Int = (0 until puzzle.geometry.cellCount).first { cut.boardBefore.cells[it] is SudokuCell.Given }

    /** What an elimination board must show: the engine's candidates for the cut, less the notes the walk already struck. */
    private fun SudokuSnippet.shownNotes(): IntArray {
        val candidates = SudokuCandidates.of(puzzle, cut.boardBefore)
        for ((cell, mask) in cut.struckBefore) candidates[cell] = candidates[cell] and mask.inv()
        return candidates
    }

    @Test
    fun `selecting an empty cell opens the picker and makes no move`() {
        val state = SudokuSnippetBoardState(single)
        val idx = single.emptyCell()

        state.select(idx)

        assertEquals(idx, state.selected)
        assertNull(state.played)
        assertEquals((1..single.puzzle.geometry.size).toList(), state.keysAt(idx))
    }

    @Test
    fun `a digit on a single's cell is a Place, drawn at once, and closes the picker`() {
        val state = SudokuSnippetBoardState(single)
        val idx = single.emptyCell()
        state.select(idx)

        val move = state.input(3)

        assertEquals(SudokuTrainerMove.Place(idx, 3), move)
        assertEquals(move, state.played)
        assertEquals(3, state.digits[idx])
        assertNull(state.selected)
        assertEquals("the other cells keep their digits", single.cut.boardBefore.digitAt(single.givenCell()), state.digits[single.givenCell()])
    }

    @Test
    fun `a single's board draws no notes`() {
        val state = SudokuSnippetBoardState(single)

        assertTrue(state.notes.all { it == 0 })
        assertTrue(state.model().cells.all { it.notes == 0 })
    }

    @Test
    fun `an elimination board draws the engine's candidates, and its keys are the cell's own`() {
        val state = SudokuSnippetBoardState(elimination)
        val shown = elimination.shownNotes()
        val idx = shown.indices.first { shown[it] != 0 }
        val digits = (1..elimination.puzzle.geometry.size).filter { shown[idx] and (1 shl (it - 1)) != 0 }

        state.select(idx)

        assertEquals(digits, state.keysAt(idx))
        assertEquals(shown.toList(), state.notes)
    }

    @Test
    fun `a digit on an elimination's cell is a RemoveNote that strikes the note and closes the picker`() {
        val state = SudokuSnippetBoardState(elimination)
        val shown = elimination.shownNotes()
        val idx = shown.indices.first { shown[it] != 0 }
        val digit = state.keysAt(idx).first()
        state.select(idx)

        val move = state.input(digit)

        assertEquals(SudokuTrainerMove.RemoveNote(idx, digit), move)
        assertEquals(move, state.played)
        assertEquals("the note is struck", 0, state.notes[idx] and (1 shl (digit - 1)))
        assertEquals("the other notes stay", shown[idx] and (1 shl (digit - 1)).inv(), state.notes[idx])
        assertEquals("no digit is placed", elimination.cut.boardBefore.digitAt(idx), state.digits[idx])
        assertNull(state.selected)
    }

    @Test
    fun `a digit that is not one of an elimination cell's notes does nothing`() {
        val state = SudokuSnippetBoardState(elimination)
        val shown = elimination.shownNotes()
        val idx = shown.indices.first { shown[it] != 0 }
        val absent = (1..elimination.puzzle.geometry.size).firstOrNull { it !in state.keysAt(idx) }
        state.select(idx)

        if (absent != null) assertNull(state.input(absent))
        assertNull(state.input(0))
        assertNull(state.input(elimination.puzzle.geometry.size + 1))

        assertNull(state.played)
        assertEquals(idx, state.selected)
        assertEquals(shown.toList(), state.notes)
    }

    @Test
    fun `a given, a filled cell and a cell off the board cannot be selected`() {
        val state = SudokuSnippetBoardState(single)

        state.select(single.givenCell())
        assertNull(state.selected)
        state.select(-1)
        assertNull(state.selected)
        state.select(single.puzzle.geometry.cellCount)
        assertNull(state.selected)
    }

    @Test
    fun `an elimination cell with no note to strike cannot be selected`() {
        val state = SudokuSnippetBoardState(elimination)
        val shown = elimination.shownNotes()
        val filled = (0 until shown.size).first { elimination.cut.boardBefore.digitAt(it) != 0 }

        state.select(filled)

        assertNull(state.selected)
    }

    @Test
    fun `tapping another cell moves the selection, and dismissing closes the picker`() {
        val state = SudokuSnippetBoardState(single)
        val open = (0 until single.puzzle.geometry.cellCount).filter { single.cut.boardBefore.digitAt(it) == 0 }
        state.select(open[0])
        state.select(open[1])
        assertEquals(open[1], state.selected)

        state.dismiss()

        assertNull(state.selected)
        assertNull(state.played)
    }

    @Test
    fun `input with no selected cell makes no move`() {
        val state = SudokuSnippetBoardState(single)

        assertNull(state.input(1))
        assertNull(state.played)
    }

    @Test
    fun `after the move nothing selects and nothing inputs, so a second move never happens`() {
        val state = SudokuSnippetBoardState(single)
        val open = (0 until single.puzzle.geometry.cellCount).filter { single.cut.boardBefore.digitAt(it) == 0 }
        state.select(open[0])
        val first = state.input(2)

        state.select(open[1])
        assertNull("no selection after the move", state.selected)
        assertNull(state.input(5))

        assertEquals(first, state.played)
        assertEquals(0, state.digits[open[1]])
    }

    @Test
    fun `the grid model shows only the selected cell and the givens, with no peer, same-digit or conflict tint`() {
        val state = SudokuSnippetBoardState(single)
        val idx = single.emptyCell()
        state.select(idx)

        val cells = state.model().cells

        assertTrue(cells[idx].selected)
        assertEquals(1, cells.count { it.selected })
        assertFalse(cells.any { it.peer || it.sameDigit || it.conflict || it.hintTarget || it.hintUnit || it.hintStruck })
        assertTrue(cells.filter { it.given }.all { it.digit != 0 })
    }
}
