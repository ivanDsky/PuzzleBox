package com.ivandsky.puzzlebox.games.sudokuui

import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuBoardModelTest {

    private fun grid(text: String): List<Int> = text.filterNot { it.isWhitespace() }.map { if (it == '.') 0 else it.digitToInt() }

    private val miniGivens = grid("..3.5. .2.4.6 1..... .....2 2.4.1. .1.3..")
    private val mini = SudokuSession(
        ref = null,
        puzzle = SudokuPuzzle(SudokuGames.MINI.variant, miniGivens, grid("463251 521436 142563 356142 234615 615324")),
    )

    private val classicGivens =
        grid("53..7....6..195....98....6.8...6...34..8.3..17...2...6.6....28....419..5....8..79")
    private val classic = SudokuSession(
        ref = null,
        puzzle = SudokuPuzzle(
            SudokuGames.CLASSIC.variant,
            classicGivens,
            grid("534678912672195348198342567859761423426853791713924856961537284287419635345286179"),
        ),
    )

    private fun SudokuBoardModel.where(flag: (SudokuCellModel) -> Boolean): Set<Int> =
        cells.filter(flag).map { it.idx }.toSet()

    @Test
    fun `6x6 selection marks its row, column and 2x3 box as peers`() {
        val model = sudokuBoardModel(mini, selected = 7)

        val row = setOf(6, 8, 9, 10, 11)
        val column = setOf(1, 13, 19, 25, 31)
        val box = setOf(0, 2)
        assertEquals(row + column + box, model.where { it.peer })
        assertEquals(setOf(7), model.where { it.selected })
        assertEquals(Triple(6, 2, 3), Triple(model.size, model.boxRows, model.boxCols))
    }

    @Test
    fun `9x9 selection marks its row, column and 3x3 box as peers`() {
        val model = sudokuBoardModel(classic, selected = 40)

        val row = (36..44).toSet() - 40
        val column = (4..76 step 9).toSet() - 40
        val box = setOf(30, 31, 32, 39, 41, 48, 49, 50)
        assertEquals(row + column + box, model.where { it.peer })
        assertFalse(model.cells[0].peer)
        assertFalse("row 4, column 1 sits in box 4, not the center box", model.cells[27].peer)
    }

    @Test
    fun `same digit marks every other cell holding the selected digit`() {
        val model = sudokuBoardModel(classic, selected = 0)

        val fives = classicGivens.indices.filter { classicGivens[it] == 5 }.toSet() - 0
        assertEquals(fives, model.where { it.sameDigit })
        assertFalse(model.cells[0].sameDigit)
    }

    @Test
    fun `an empty selection highlights peers but no same digit`() {
        val model = sudokuBoardModel(mini, selected = 0)

        assertTrue(model.where { it.sameDigit }.isEmpty())
        assertTrue(model.where { it.peer }.isNotEmpty())
    }

    @Test
    fun `no selection leaves every highlight off`() {
        val model = sudokuBoardModel(classic, selected = null)

        assertTrue(model.cells.none { it.selected || it.peer || it.sameDigit })
    }

    @Test
    fun `a duplicated digit flags both holders as conflicts`() {
        val session = mini.copy(board = mini.board.with(0, SudokuCell.Entry(3)))

        val model = sudokuBoardModel(session, selected = null)

        assertEquals(setOf(0, 2), model.where { it.conflict })
    }

    @Test
    fun `a 9x9 column duplicate flags its holders as conflicts`() {
        // Column 1 holds givens 5, 6, 8, 4, 7 from the top; a second 6 at row 9 duplicates row 2's.
        val session = classic.copy(board = classic.board.with(72, SudokuCell.Entry(6)))

        val model = sudokuBoardModel(session, selected = null)

        assertTrue(model.cells[9].conflict)
        assertTrue(model.cells[72].conflict)
        assertFalse(model.cells[0].conflict)
    }

    @Test
    fun `cells carry digit, given and visible notes`() {
        val board = mini.board
            .with(0, SudokuCell.Entry(4, notes = 0b11))
            .with(1, SudokuCell.Notes(0b10011))
        val model = sudokuBoardModel(mini.copy(board = board), selected = null)

        assertEquals(SudokuCellModel(0, 4, 0, false, false, false, false, false, false, false, false), model.cells[0])
        assertEquals(0b10011, model.cells[1].notes)
        assertEquals(0, model.cells[1].digit)
        assertTrue(model.cells[2].given)
        assertEquals(3, model.cells[2].digit)
    }

    @Test
    fun `hint target and hint cells pass through`() {
        val model = sudokuBoardModel(mini, selected = null, hintTarget = 4, hintCells = setOf(3, 4, 5), hintStruck = setOf(1, 7))

        assertEquals(setOf(4), model.where { it.hintTarget })
        assertEquals(setOf(3, 4, 5), model.where { it.hintUnit })
        assertEquals(setOf(1, 7), model.where { it.hintStruck })
    }
}
