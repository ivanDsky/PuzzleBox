package com.ivandsky.puzzlebox.games.sudoku.engine.hints

import com.ivandsky.puzzlebox.games.sudoku.KnownSolutions
import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuGeometry
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuUnit
import com.ivandsky.puzzlebox.games.sudoku.SudokuVariants
import com.ivandsky.puzzlebox.games.sudoku.UnitKind
import com.ivandsky.puzzlebox.games.sudoku.engine.rules.SudokuConflict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuHintsTest {

    private val mini = KnownSolutions.miniSample
    private val classic = KnownSolutions.classicSample

    /** Norvig's grid2 (research 04): singles stall on it, so it exercises eliminations and the reveal. */
    private val hard = SudokuPuzzle(
        SudokuVariants.CLASSIC,
        KnownSolutions.grid("4.....8.5.3..........7......2.....6.....8.4......1.......6.3.7.5..2.....1.4......"),
        KnownSolutions.grid("417369825632158947958724316825437169791586432346912758289643571573291684164875293"),
    )

    private val singles = setOf(
        SudokuTechnique.HIDDEN_SINGLE_BOX,
        SudokuTechnique.HIDDEN_SINGLE_LINE,
        SudokuTechnique.NAKED_SINGLE,
    )

    private fun SudokuBoard.put(idx: Int, digit: Int): SudokuBoard = with(idx, SudokuCell.Entry(digit))

    /** Asserts the hint's unit matches its technique and holds the cell: a box, a line, or none for a naked single. */
    private fun assertUnitShape(geometry: SudokuGeometry, place: SudokuHint.Place) {
        when (place.technique) {
            SudokuTechnique.HIDDEN_SINGLE_BOX -> assertEquals(UnitKind.BOX, place.unit?.kind)
            SudokuTechnique.HIDDEN_SINGLE_LINE -> assertTrue(place.unit?.kind in setOf(UnitKind.ROW, UnitKind.COLUMN))
            else -> assertNull(place.unit)
        }
        place.unit?.let { assertTrue(place.cell in geometry.cellsOf(it)) }
    }

    /** The true digit of every open cell is still among the engine's candidates. */
    private fun assertCandidatesSound(puzzle: SudokuPuzzle, board: SudokuBoard) {
        val candidates = SudokuCandidates.of(puzzle, board)
        for (idx in candidates.indices) {
            if (board.digitAt(idx) != 0) continue
            assertTrue("cell $idx lost its solution digit", candidates[idx] and (1 shl (puzzle.solution[idx] - 1)) != 0)
        }
    }

    /** Plays every hinted digit until the board is solved; returns the hints in order. */
    private fun playOut(puzzle: SudokuPuzzle, hints: SudokuHints = SudokuHints()): List<SudokuHint> {
        var board = SudokuBoard.of(puzzle)
        val seen = mutableListOf<SudokuHint>()
        while (true) {
            assertCandidatesSound(puzzle, board)
            val hint = hints.hint(puzzle, board)
            seen += hint
            board = when (hint) {
                is SudokuHint.Place -> {
                    assertEquals("hinted digit at ${hint.cell}", puzzle.solution[hint.cell], hint.digit)
                    assertUnitShape(puzzle.geometry, hint)
                    board.put(hint.cell, hint.digit)
                }
                is SudokuHint.Reveal -> {
                    assertEquals("revealed digit at ${hint.cell}", puzzle.solution[hint.cell], hint.digit)
                    board.put(hint.cell, hint.digit)
                }
                SudokuHint.None -> return seen
                else -> throw AssertionError("unexpected $hint on a clean board")
            }
        }
    }

    /** The first board, reached by playing singles, where no single applies. */
    private fun stalled(puzzle: SudokuPuzzle): SudokuBoard {
        var board = SudokuBoard.of(puzzle)
        while (true) {
            when (val hint = SudokuHints(emptyList()).hint(puzzle, board)) {
                is SudokuHint.Place -> board = board.put(hint.cell, hint.digit)
                is SudokuHint.Reveal -> return board
                else -> throw AssertionError("expected singles to stall, got $hint")
            }
        }
    }

    @Test
    fun `both samples solve by applying hinted singles only`() {
        for (puzzle in listOf(mini, classic)) {
            val hints = playOut(puzzle)
            val places = hints.filterIsInstance<SudokuHint.Place>()
            assertEquals(puzzle.givens.count { it == 0 }, places.size)
            assertEquals(places.size + 1, hints.size)
            assertTrue(places.all { it.technique in singles && it.via == null })
        }
    }

    @Test
    fun `every hinted digit equals the solution, through eliminations and reveals`() {
        val revealed = playOut(hard, SudokuHints(emptyList()))
        assertTrue(revealed.any { it is SudokuHint.Reveal })
        assertTrue(
            revealed.filterIsInstance<SudokuHint.Place>().map { it.technique }.containsAll(singles),
        )
        assertTrue(playOut(hard).any { it is SudokuHint.Place && it.via != null })
    }

    @Test
    fun `box hidden single outranks a naked single on the same board`() {
        val board = SudokuBoard.of(classic)
        val candidates = SudokuCandidates.of(classic, board)
        // r5c5 sees every digit but 5: a naked single is available.
        assertEquals(1 shl 4, candidates[40])

        val hint = SudokuHints().hint(classic, board) as SudokuHint.Place
        assertEquals(SudokuTechnique.HIDDEN_SINGLE_BOX, hint.technique)
        assertEquals(UnitKind.BOX, hint.unit?.kind)
        assertEquals(classic.solution[hint.cell], hint.digit)
    }

    @Test
    fun `a naked single carries no unit`() {
        // Row 0 holds 3 4 5 and column 0 holds 1 2, so cell 0 can only be 6 while every unit
        // still has two or more homes for each missing digit.
        val solution = KnownSolutions.grid("612345 345612 456123 123456 561234 234561")
        val givens = MutableList(36) { 0 }.apply {
            this[3] = 3; this[4] = 4; this[5] = 5
            this[18] = 1; this[30] = 2
        }
        val puzzle = SudokuPuzzle(SudokuVariants.MINI, givens, solution)
        val candidates = SudokuCandidates.of(puzzle, SudokuBoard.of(puzzle))
        assertEquals(1 shl 5, candidates[0])

        val hint = SudokuHints().hint(puzzle, SudokuBoard.of(puzzle))
        assertEquals(SudokuHint.Place(0, 6, SudokuTechnique.NAKED_SINGLE, null, null), hint)
    }

    @Test
    fun `a conflict outranks a wrong entry, which outranks a technique step`() {
        val clean = SudokuBoard.of(classic)
        // r1c3 is 4 in the solution; a 5 there clashes with the given 5 at r1c1.
        val conflicting = clean.put(2, 5).put(78, 3)
        assertEquals(
            SudokuHint.Conflict(SudokuConflict(SudokuUnit(UnitKind.ROW, 0), 5, listOf(0, 2))),
            SudokuHints().hint(classic, conflicting),
        )

        // r1c3 = 1 and r9c7 = 3 break no rule, yet both differ from the solution (4 and 1).
        val wrong = clean.put(2, 1).put(78, 3)
        assertEquals(SudokuHint.Wrong(listOf(2, 78)), SudokuHints().hint(classic, wrong))

        assertTrue(SudokuHints().hint(classic, clean) is SudokuHint.Place)
    }

    @Test
    fun `the player's notes never feed the ladder`() {
        val clean = SudokuBoard.of(classic)
        val noted = clean.cells.indices.fold(clean) { board, idx ->
            if (board.digitAt(idx) == 0) board.with(idx, SudokuCell.Notes(1)) else board
        }
        assertEquals(SudokuHints().hint(classic, clean), SudokuHints().hint(classic, noted))
    }

    @Test
    fun `a solved board hints None`() {
        val solved = SudokuBoard(mini.geometry, mini.solution.map { SudokuCell.Entry(it) })
        assertEquals(SudokuHint.None, SudokuHints().hint(mini, solved))
    }

    @Test
    fun `without a working step the reveal picks the open cell with fewest candidates`() {
        val board = stalled(hard)
        val candidates = SudokuCandidates.of(hard, board)
        val expected = candidates.indices.filter { board.digitAt(it) == 0 }.minBy { Integer.bitCount(candidates[it]) }

        assertEquals(SudokuHint.Reveal(expected, hard.solution[expected]), SudokuHints(emptyList()).hint(hard, board))
    }

    @Test
    fun `an elimination that unlocks a single shows up as via, hardest first`() {
        val board = stalled(hard)
        val target = SudokuCandidates.of(hard, board).let { c -> c.indices.first { c[it] != 0 } }
        var narrowed = 0
        val idle = TestStep(SudokuTechnique.NAKED_PAIR) { _, _ -> false }
        val hardOnce = TestStep(SudokuTechnique.CLAIMING) { _, candidates ->
            // Clears one false candidate, once, from a cell with at least three, so no single appears.
            val cell = candidates.indices.firstOrNull { Integer.bitCount(candidates[it]) >= 3 && it != target }
            if (narrowed > 0 || cell == null) return@TestStep false
            narrowed++
            val falseBit = Integer.highestOneBit(candidates[cell] and (1 shl (hard.solution[cell] - 1)).inv())
            candidates[cell] = candidates[cell] and falseBit.inv()
            true
        }
        var collapsed = 0
        val easyCollapse = TestStep(SudokuTechnique.POINTING) { _, candidates ->
            val bit = 1 shl (hard.solution[target] - 1)
            if (candidates[target] == bit) return@TestStep false
            candidates[target] = bit
            collapsed++
            true
        }

        val hint = SudokuHints(listOf(idle, hardOnce, easyCollapse)).hint(hard, board) as SudokuHint.Place

        assertEquals(SudokuTechnique.CLAIMING, hint.via)
        assertEquals(hard.solution[hint.cell], hint.digit)
        assertTrue(hint.technique in singles)
        assertEquals(1, narrowed)
        assertEquals(1, collapsed)
    }

    private class TestStep(
        override val technique: SudokuTechnique,
        private val body: (SudokuGeometry, IntArray) -> Boolean,
    ) : EliminationStep {
        override fun eliminate(geometry: SudokuGeometry, candidates: IntArray): Boolean = body(geometry, candidates)
    }
}
