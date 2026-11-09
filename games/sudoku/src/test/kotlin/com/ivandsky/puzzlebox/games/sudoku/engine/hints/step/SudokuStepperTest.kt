package com.ivandsky.puzzlebox.games.sudoku.engine.hints.step

import com.ivandsky.puzzlebox.games.sudoku.KnownSolutions
import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuUnit
import com.ivandsky.puzzlebox.games.sudoku.SudokuVariants
import com.ivandsky.puzzlebox.games.sudoku.UnitKind
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuHint
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuTechnique
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuStepperTest {

    private val mini = KnownSolutions.miniSample

    /** Norvig's grid2, as in SudokuHintsTest: singles stall on it, so the replay reaches Reveal. */
    private val hard = SudokuPuzzle(
        SudokuVariants.CLASSIC,
        KnownSolutions.grid("4.....8.5.3..........7......2.....6.....8.4......1.......6.3.7.5..2.....1.4......"),
        KnownSolutions.grid("417369825632158947958724316825437169791586432346912758289643571573291684164875293"),
    )

    /** The mini sample's solution: 543162 216435 624513 351246 132654 465321. */
    private val sampleSolution = KnownSolutions.grid(KnownSolutions.MINI_SAMPLE_SOLUTION)

    /** A second mini solution: 612345 345612 456123 123456 561234 234561. */
    private val shifted = KnownSolutions.grid("612345 345612 456123 123456 561234 234561")

    private val digit6 = 1 shl 5

    private fun puzzle(solution: List<Int>, givens: Map<Int, Int>): SudokuPuzzle =
        SudokuPuzzle(SudokuVariants.MINI, List(36) { givens[it] ?: 0 }, solution)

    /** [solution] with every cell given except [open]. */
    private fun allBut(solution: List<Int>, vararg open: Int): SudokuPuzzle =
        SudokuPuzzle(SudokuVariants.MINI, solution.mapIndexed { idx, d -> if (idx in open) 0 else d }, solution)

    private fun next(puzzle: SudokuPuzzle, board: SudokuBoard = SudokuBoard.of(puzzle), lastMove: Int? = null, known: Map<Int, Int> = emptyMap()) =
        SudokuStepper.next(puzzle, board, lastMove, known)

    private fun SudokuBoard.put(idx: Int, digit: Int): SudokuBoard = with(idx, SudokuCell.Entry(digit))

    private fun single(cell: Int, digit: Int, technique: SudokuTechnique, unit: SudokuUnit?, lastCell: SudokuUnit?, blockers: Set<Int>) =
        SudokuStep.Single(SudokuHint.Place(cell, digit, technique, unit, null, emptyList()), lastCell, blockers)

    private fun unit(kind: UnitKind, index: Int) = SudokuUnit(kind, index)

    // Givens on `shifted`: col 0 holds 4 1 5 2 below row 1, and the 6 at r1c3 keeps 6 out of r1c0.
    // Only line single: 6 at cell 0 in col 0. Naked single: cell 6 can only be 3. No box single, no last cell.
    private val lineAndNaked = mapOf(9 to 6, 12 to 4, 18 to 1, 24 to 5, 30 to 2)

    // Adds 6s in col 2 and col 1, leaving cell 0 the only home for 6 in box 0 as well.
    private val boxLineAndNaked = lineAndNaked + mapOf(14 to 6, 25 to 6)

    @Test
    fun `a wrong entry comes before any single and names no partner when it repeats nothing`() {
        // Cell 0 is 5 in the solution; no peer holds a 1.
        assertEquals(SudokuStep.Mistake(0, 1, null), next(mini, SudokuBoard.of(mini).put(0, 1)))
    }

    @Test
    fun `a wrong entry that repeats a digit in its box names the clash partner`() {
        // Cell 12 is 6 in the solution; the given 1 at cell 20 shares only its box.
        assertEquals(SudokuStep.Mistake(12, 1, 20), next(mini, SudokuBoard.of(mini).put(12, 1)))
    }

    @Test
    fun `of two wrong entries the one nearest the last move goes first, then the lower index`() {
        val board = SudokuBoard.of(mini).put(0, 1).put(12, 1)
        assertEquals(12, (next(mini, board, lastMove = 30) as SudokuStep.Mistake).cell)
        assertEquals(0, (next(mini, board, lastMove = 1) as SudokuStep.Mistake).cell)
        // Cell 15 is three steps from both.
        assertEquals(0, (next(mini, board, lastMove = 15) as SudokuStep.Mistake).cell)
        assertEquals(0, (next(mini, board, lastMove = null) as SudokuStep.Mistake).cell)
    }

    @Test
    fun `a full board with a wrong entry is a mistake, a full correct board is solved`() {
        val full = allBut(sampleSolution)
        assertEquals(SudokuStep.Solved, next(full))
        val wrong = allBut(sampleSolution, 0)
        assertEquals(SudokuStep.Mistake(0, 4, 1), next(wrong, SudokuBoard.of(wrong).put(0, 4)))
    }

    @Test
    fun `wrong entries and notes never feed candidates`() {
        val clean = next(mini)
        val wrong = SudokuBoard.of(mini).put(3, 5)
        assertEquals(SudokuStep.Mistake(3, 5, 15), next(mini, wrong))
        assertEquals(clean, next(mini, wrong.with(3, SudokuCell.Empty)))

        val noted = SudokuBoard.of(mini).with(3, SudokuCell.Notes(0b111111)).with(11, SudokuCell.Notes(1))
        assertEquals(clean, next(mini, noted))
    }

    @Test
    fun `a unit with one empty cell is a last cell, ahead of the box single on the same cell`() {
        // Row 0 is the only unit with one empty cell; 5 is also the box 0 hidden single there.
        val puzzle = allBut(sampleSolution, 0, 6, 7, 12, 13)
        val row0 = unit(UnitKind.ROW, 0)
        assertEquals(single(0, 5, SudokuTechnique.HIDDEN_SINGLE_LINE, row0, row0, emptySet()), next(puzzle))
    }

    @Test
    fun `last cells tie on blockers, so the nearest wins, then the lower index`() {
        val puzzle = allBut(sampleSolution, 0, 35)
        val row0 = unit(UnitKind.ROW, 0)
        val row5 = unit(UnitKind.ROW, 5)
        val first = single(0, 5, SudokuTechnique.HIDDEN_SINGLE_LINE, row0, row0, emptySet())
        val last = single(35, 1, SudokuTechnique.HIDDEN_SINGLE_LINE, row5, row5, emptySet())
        assertEquals(first, next(puzzle, lastMove = null))
        assertEquals(first, next(puzzle, lastMove = 1))
        assertEquals(last, next(puzzle, lastMove = 34))
        // Cell 15 is three steps from both cells.
        assertEquals(first, next(puzzle, lastMove = 15))
    }

    @Test
    fun `the box single wins over line and naked singles, and carries the minimal blockers`() {
        // 6 leaves box 0's other open cells through r1c3 (row 1), r2c2 (col 2) and r4c1 (col 1).
        assertEquals(
            single(0, 6, SudokuTechnique.HIDDEN_SINGLE_BOX, unit(UnitKind.BOX, 0), null, setOf(9, 14, 25)),
            next(puzzle(shifted, boxLineAndNaked)),
        )
    }

    @Test
    fun `with no box single the line single wins over the naked single`() {
        // The 6 at r1c3 is the one digit keeping 6 out of col 0's other open cell.
        assertEquals(
            single(0, 6, SudokuTechnique.HIDDEN_SINGLE_LINE, unit(UnitKind.COLUMN, 0), null, setOf(9)),
            next(puzzle(shifted, lineAndNaked)),
        )
    }

    @Test
    fun `a naked single alone is returned with one blocker per digit it sees`() {
        // Row 0 holds 3 4 5 and col 0 holds 1 2, so cell 0 can only be 6; every unit has two homes per digit.
        val puzzle = puzzle(shifted, mapOf(3 to 3, 4 to 4, 5 to 5, 18 to 1, 30 to 2))
        assertEquals(single(0, 6, SudokuTechnique.NAKED_SINGLE, null, null, setOf(3, 4, 5, 18, 30)), next(puzzle))
    }

    @Test
    fun `fewer blockers wins over a nearer and lower box single`() {
        // Box 5: the 2 at r5c0 keeps 2 out of both other open cells, so 2 goes in cell 27 on one blocker.
        // Box 0: 6 goes in cell 0, but cells 2 and 7 need the 6s at r2c2 and r4c1.
        val puzzle = puzzle(
            shifted,
            mapOf(1 to 1, 6 to 3, 8 to 5, 14 to 6, 25 to 6, 30 to 2, 28 to 3, 29 to 4, 35 to 1),
        )
        val box5 = single(27, 2, SudokuTechnique.HIDDEN_SINGLE_BOX, unit(UnitKind.BOX, 5), null, setOf(30))
        assertEquals(box5, next(puzzle, lastMove = null))
        assertEquals(box5, next(puzzle, lastMove = 1))
    }

    @Test
    fun `on a blocker tie a strike-only cell costs one mark, so the single without one wins`() {
        // As above without the 6 at r4c1: a strike keeps 6 out of cell 7 instead, so box 0's single on
        // cell 0 rings one blocker (r2c2) plus one strike mark against box 5's single on one blocker alone.
        val puzzle = puzzle(shifted, mapOf(1 to 1, 6 to 3, 8 to 5, 14 to 6, 30 to 2, 28 to 3, 29 to 4, 35 to 1))
        val known = mapOf(7 to digit6)
        val box5 = single(27, 2, SudokuTechnique.HIDDEN_SINGLE_BOX, unit(UnitKind.BOX, 5), null, setOf(30))
        assertEquals(box5, next(puzzle, lastMove = null, known = known))
        assertEquals(box5, next(puzzle, lastMove = 1, known = known))
        // Without the strike box 0 has no single at all.
        assertEquals(box5, next(puzzle, lastMove = 1))
    }

    @Test
    fun `a known strike removes its digit, so a single that needs it appears, and no strikes changes nothing`() {
        val puzzle = puzzle(shifted, lineAndNaked)
        val line = single(0, 6, SudokuTechnique.HIDDEN_SINGLE_LINE, unit(UnitKind.COLUMN, 0), null, setOf(9))
        assertEquals(line, next(puzzle, known = emptyMap()))
        // Striking 6 from r0c1 and r0c2 leaves cell 0 as box 0's only home; the strikes are not blockers.
        assertEquals(
            single(0, 6, SudokuTechnique.HIDDEN_SINGLE_BOX, unit(UnitKind.BOX, 0), null, setOf(9)),
            next(puzzle, known = mapOf(1 to digit6, 2 to digit6)),
        )
    }

    @Test
    fun `with no single the reveal names the open cell with fewest candidates nearest the last move`() {
        // Only cells 0 1 2 (row 0) and 9 10 11 (box 1) are down to three candidates.
        val puzzle = puzzle(shifted, mapOf(3 to 3, 4 to 4, 5 to 5))
        assertEquals(SudokuStep.Reveal(0, 6), next(puzzle, lastMove = null))
        assertEquals(SudokuStep.Reveal(9, 6), next(puzzle, lastMove = 35))
        assertEquals(SudokuStep.Reveal(10, 1), next(puzzle, lastMove = 10))
    }

    @Test
    fun `replaying every single and reveal from the givens solves the legacy fixtures`() {
        for (puzzle in listOf(mini, KnownSolutions.classicSample, hard)) {
            var board = SudokuBoard.of(puzzle)
            var lastMove: Int? = null
            val seen = mutableListOf<SudokuStep>()
            while (true) {
                val step = next(puzzle, board, lastMove)
                seen += step
                val (cell, digit) = when (step) {
                    is SudokuStep.Single -> step.place.cell to step.place.digit
                    is SudokuStep.Reveal -> step.cell to step.digit
                    SudokuStep.Solved -> break
                    else -> throw AssertionError("unexpected $step on a clean board")
                }
                assertEquals("digit at $cell", puzzle.solution[cell], digit)
                board = board.put(cell, digit)
                lastMove = cell
            }
            assertEquals(puzzle.givens.count { it == 0 } + 1, seen.size)
        }
    }

    @Test
    fun `the hard fixture needs a reveal once singles stall`() {
        var board = SudokuBoard.of(hard)
        while (true) {
            when (val step = next(hard, board)) {
                is SudokuStep.Single -> board = board.put(step.place.cell, step.place.digit)
                is SudokuStep.Reveal -> {
                    assertEquals(hard.solution[step.cell], step.digit)
                    assertTrue(board.digitAt(step.cell) == 0)
                    return
                }
                else -> throw AssertionError("expected singles to stall, got $step")
            }
        }
    }
}
