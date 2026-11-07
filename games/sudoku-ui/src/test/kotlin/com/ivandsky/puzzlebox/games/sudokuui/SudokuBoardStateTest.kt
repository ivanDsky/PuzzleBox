package com.ivandsky.puzzlebox.games.sudokuui

import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuCommand
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzleRef
import com.ivandsky.puzzlebox.games.sudoku.SudokuScoreModel
import com.ivandsky.puzzlebox.games.sudoku.SudokuSession
import com.ivandsky.puzzlebox.games.sudoku.SudokuUnit
import com.ivandsky.puzzlebox.games.sudoku.UnitKind
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuHint
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuTechnique
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuBoardStateTest {

    private val givens = grid("..3.5. .2.4.6 1..... .....2 2.4.1. .1.3..")
    private val solution = grid("463251 521436 142563 356142 234615 615324")
    private val peanut450 = SudokuPuzzle(SudokuGames.MINI.variant, givens, solution)
    private val ref = SudokuPuzzleRef(SudokuGames.MINI.id, PuzzleKey.Daily(LocalDate.of(2026, 11, 4), 450))

    private val published = ArrayList<SudokuSession>()
    private var settled = 0

    private fun grid(text: String): List<Int> = text.filterNot { it.isWhitespace() }.map { if (it == '.') 0 else it.digitToInt() }

    private fun state(session: SudokuSession = SudokuSession(ref = null, puzzle = peanut450)) = SudokuBoardState(
        initialSession = session,
        now = { Instant.EPOCH },
        onSessionChanged = { published += it },
        settle = { settled++; it },
    )

    @Test
    fun `selecting moves the selection only`() {
        val s = state()
        s.select(7)
        assertEquals(7, s.selected)
        assertTrue(published.isEmpty())
        assertEquals(0, settled)
    }

    @Test
    fun `the notes toggle is a setting outside history and the clock`() {
        val s = state()
        s.toggleNotesMode()
        assertTrue(s.session.notesMode)
        assertFalse(s.canUndo)
        assertEquals(0, settled)
        assertEquals(1, published.size)
    }

    @Test
    fun `a digit in notes mode toggles a note, otherwise places an entry`() {
        val s = state()
        s.select(0)
        s.toggleNotesMode()
        s.input(4)
        assertEquals(SudokuCell.Notes(1 shl 3), s.session.board.cells[0])
        s.toggleNotesMode()
        s.input(4)
        assertEquals(4, s.session.board.digitAt(0))
        assertEquals(2, settled)
    }

    @Test
    fun `input on a given is refused with a caption until the next action`() {
        val s = state()
        s.select(2)
        val before = s.session
        s.input(1)
        assertEquals(before, s.session)
        assertEquals("That number is part of the puzzle.", s.rejection)
        s.erase()
        assertEquals(before, s.session)
        assertEquals("That number is part of the puzzle.", s.rejection)
        s.select(0)
        assertNull(s.rejection)
    }

    @Test
    fun `erase, clear, undo and redo route through the session`() {
        val s = state()
        s.select(0)
        s.input(4)
        s.erase()
        assertEquals(SudokuCell.Empty, s.session.board.cells[0])
        s.undo()
        assertEquals(4, s.session.board.digitAt(0))
        s.redo()
        assertEquals(SudokuCell.Empty, s.session.board.cells[0])
        s.input(6)
        s.clear()
        assertEquals(SudokuCell.Empty, s.session.board.cells[0])
        assertTrue(s.session.history.done.last() is SudokuCommand.Clear)
    }

    @Test
    fun `a hint counts once per target and stage 2 reveals it as an undoable step`() {
        val s = state()
        s.requestHint()
        val first = s.hint
        val target = checkNotNull(s.hintTarget)
        assertTrue(first is SudokuHint.Place || first is SudokuHint.Reveal)
        assertEquals(1, s.session.hintsUsed)
        assertFalse(s.hintRevealed)

        s.requestHint()
        assertTrue(s.hintRevealed)
        assertEquals(solution[target], s.session.board.digitAt(target))
        assertTrue(s.session.history.done.last() is SudokuCommand.HintReveal)
        assertEquals(1, s.session.hintsUsed)

        s.undo()
        assertEquals(0, s.session.board.digitAt(target))
        assertNull(s.hint)
    }

    @Test
    fun `asking again for a target already shown does not count it twice`() {
        val s = state()
        s.requestHint()
        s.select(0)
        assertNull(s.hint)
        s.requestHint()
        assertEquals(1, s.session.hintsUsed)
        assertFalse(s.hintRevealed)
    }

    @Test
    fun `a wrong entry hint never escalates`() {
        val s = state()
        s.select(0)
        s.input(6)
        s.requestHint()
        assertEquals(SudokuHint.Wrong(listOf(0)), s.hint)
        s.requestHint()
        assertEquals(SudokuHint.Wrong(listOf(0)), s.hint)
        assertFalse(s.hintRevealed)
        assertEquals(6, s.session.board.digitAt(0))
        assertEquals(1, s.session.hintsUsed)
        assertEquals(setOf(0), s.hintCells)
        assertEquals("The 6 in row 1, column 1 doesn't fit here — take another look at its row, column and box.", s.hintCaption)
    }

    @Test
    fun `a conflict is the standing violation and a hint that never escalates`() {
        val s = state()
        s.select(0)
        s.input(3)
        assertEquals("Two 3s in row 1", s.violation)
        s.requestHint()
        s.requestHint()
        assertTrue(s.hint is SudokuHint.Conflict)
        assertFalse(s.hintRevealed)
        assertEquals(setOf(0, 2), s.hintCells)
        assertNull(s.hintTarget)
        assertEquals(1, s.session.hintsUsed)
    }

    @Test
    fun `every action clears the active hint`() {
        val s = state()
        s.requestHint()
        s.toggleNotesMode()
        assertNull(s.hint)
        assertNull(s.hintCaption)
    }

    @Test
    fun `solving by hint reveals emits the win once`() {
        val wins = ArrayList<CompletionRecord>()
        val s = SudokuBoardState(
            initialSession = SudokuSession(ref = ref, puzzle = peanut450),
            score = SudokuScoreModel(),
            onWinRecord = { wins += it },
            now = { Instant.EPOCH },
        )
        repeat(givens.count { it == 0 } * 2) { s.requestHint() }
        assertTrue(s.session.isComplete)
        s.requestHint()
        s.undo()
        assertEquals(ref, wins.single().puzzleRef)
    }

    @Test
    fun `a technique hint maps to its target and the unit that justifies it`() {
        val s = state()
        s.requestHint()
        val place = s.hint as SudokuHint.Place
        val geometry = peanut450.geometry
        val unitCells = place.unit?.let(geometry::cellsOf) ?: geometry.peersOf(place.cell)
        assertEquals(place.cell, s.hintTarget)
        assertEquals(unitCells.filterTo(HashSet()) { it != place.cell }, s.hintCells)
        val model = s.model
        assertTrue(model.cells[place.cell].hintTarget)
        assertEquals(s.hintCells, model.cells.filter { it.hintUnit }.map { it.idx }.toSet())
    }

    @Test
    fun `struck cells tint at stage 1 only`() {
        // Norvig's grid2: singles stall on it, so playing hints out reaches a Place unlocked by eliminations.
        val hard = SudokuPuzzle(
            SudokuGames.CLASSIC.variant,
            grid("4.....8.5.3..........7......2.....6.....8.4......1.......6.3.7.5..2.....1.4......"),
            grid("417369825632158947958724316825437169791586432346912758289643571573291684164875293"),
        )
        val s = state(SudokuSession(ref = null, puzzle = hard))
        var place: SudokuHint.Place? = null
        while (place == null && !s.session.isComplete) {
            s.requestHint()
            place = (s.hint as? SudokuHint.Place)?.takeIf { it.via != null }
            if (place == null) s.requestHint()
        }
        assertTrue(place!!.struck.isNotEmpty())
        assertEquals(place.struck.toSet(), s.hintStruck)
        assertEquals(s.hintStruck, s.model.cells.filter { it.hintStruck }.map { it.idx }.toSet())

        s.requestHint()

        assertTrue(s.hintStruck.isEmpty())
        assertTrue(s.model.cells.none { it.hintStruck })
    }

    @Test
    fun `the solving move clears the selection and a solved board refuses one`() {
        val s = state()
        givens.indices.filter { givens[it] == 0 }.forEach { idx ->
            s.select(idx)
            s.input(solution[idx])
        }
        assertTrue(s.session.isComplete)
        assertNull(s.selected)

        s.select(0)

        assertNull(s.selected)
    }

    @Test
    fun `captions name 1-based positions and preface eliminations`() {
        val board = SudokuSession(ref = null, puzzle = peanut450).board
        val line = SudokuHint.Place(14, 2, SudokuTechnique.HIDDEN_SINGLE_LINE, SudokuUnit(UnitKind.COLUMN, 2), null, emptyList())
        assertEquals(
            "Column 3 still needs a 2, and every other empty cell in it already sees a 2, so it goes in row 3, column 3.",
            SudokuHintCaptions.hint(line, board),
        )
        val eight = SudokuHint.Place(14, 8, SudokuTechnique.HIDDEN_SINGLE_BOX, SudokuUnit(UnitKind.BOX, 0), null, emptyList())
        assertEquals(
            "This box still needs an 8, and row 3, column 3 is the only empty cell in it where an 8 can go.",
            SudokuHintCaptions.hint(eight, board),
        )
        val viaNaked = SudokuHint.Place(14, 2, SudokuTechnique.NAKED_SINGLE, null, SudokuTechnique.POINTING, listOf(3))
        assertEquals(
            "After ruling out some numbers, row 3, column 3 already sees every other number in its row, column and box, " +
                "so the only number left for it is 2.",
            SudokuHintCaptions.hint(viaNaked, board),
        )
        assertEquals(
            "There's no simple step from here, so here's one cell to get you going: row 6, column 6 is 4.",
            SudokuHintCaptions.hint(SudokuHint.Reveal(35, 4), board),
        )
    }
}
