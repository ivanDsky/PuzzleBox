package com.ivandsky.puzzlebox.games.sudokuui

import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzleRef
import com.ivandsky.puzzlebox.games.sudoku.codec.SudokuCodec
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The ⋮ menu's two highlight switches drive the live board model and outlive the controller. */
class SudokuHighlightToggleTest {

    private fun grid(text: String): List<Int> = text.filterNot { it.isWhitespace() }.map { if (it == '.') 0 else it.digitToInt() }

    private val puzzle = SudokuPuzzle(
        SudokuGames.MINI.variant,
        grid("..3.5. .2.4.6 1..... .....2 2.4.1. .1.3.."),
        grid("463251 521436 142563 356142 234615 615324"),
    )
    private val ref = SudokuPuzzleRef(SudokuGames.MINI.id, PuzzleKey.Daily(LocalDate.of(2026, 11, 4), 450))
    private val settings = InMemorySudokuHighlightSettings()
    private val ui = SudokuPlayUi(SudokuGames.MINI, settings)

    private fun opened(): SudokuPlayController {
        val controller = ui.createController(PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), {}, {})) as SudokuPlayController
        controller.adoptToday(Puzzle(ui.gameId, puzzle.variant.id, SudokuCodec.VERSION, SudokuCodec.encode(puzzle)), ref)
        // Cell 2 holds a given 3 and cell 6 is empty: both tints have something to paint.
        controller.board!!.select(2)
        return controller
    }

    private fun labels(controller: SudokuPlayController) = ui.overflowFor(controller).map { it.label to it.checked }

    private fun toggle(controller: SudokuPlayController, label: String) = ui.overflowFor(controller).single { it.label == label }.onClick()

    @Test
    fun `both switches start on and tint the board`() {
        val c = opened()

        assertEquals(listOf("Highlight row, column & box" to true, "Highlight same numbers" to true), labels(c))
        assertTrue(c.board!!.model.cells.any { it.peer })
    }

    @Test
    fun `switching row column and box off repaints the board and leaves the other switch on`() {
        val c = opened()

        toggle(c, "Highlight row, column & box")

        assertEquals(listOf("Highlight row, column & box" to false, "Highlight same numbers" to true), labels(c))
        assertTrue(c.board!!.model.cells.none { it.peer })
        assertTrue(c.board!!.model.cells[2].selected)
    }

    @Test
    fun `switching same numbers off drops the digit tint and switching back restores it`() {
        val c = opened()
        assertTrue(c.board!!.model.cells.any { it.sameDigit })

        toggle(c, "Highlight same numbers")
        assertTrue(c.board!!.model.cells.none { it.sameDigit })
        assertTrue(c.board!!.model.cells.any { it.peer })

        toggle(c, "Highlight same numbers")
        assertTrue(c.board!!.model.cells.any { it.sameDigit })
    }

    @Test
    fun `a choice persists into the next game`() {
        toggle(opened(), "Highlight row, column & box")

        val next = opened()

        assertEquals(listOf("Highlight row, column & box" to false, "Highlight same numbers" to true), labels(next))
        assertTrue(next.board!!.model.cells.none { it.peer })
    }
}
