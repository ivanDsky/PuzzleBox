package com.ivandsky.puzzlebox.games.sudoku.engine.hints

import com.ivandsky.puzzlebox.games.sudoku.KnownSolutions
import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuGeometry
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuVariants
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EliminationStepsTest {

    private val classic = SudokuVariants.CLASSIC.geometry
    private val mini = SudokuVariants.MINI.geometry

    /** Norvig's grid2 (research 04): a local fixture only, its licence keeps it out of shipped content. */
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

    private fun bit(digit: Int): Int = 1 shl (digit - 1)

    private fun open(geometry: SudokuGeometry): IntArray = IntArray(geometry.cellCount) { (1 shl geometry.size) - 1 }

    private fun step(technique: SudokuTechnique): EliminationStep = EliminationSteps.DEFAULT.single { it.technique == technique }

    /** Runs [technique] once and asserts it cleared exactly [cleared] (cell to digits) and nothing else. */
    private fun assertClears(technique: SudokuTechnique, geometry: SudokuGeometry, candidates: IntArray, cleared: Map<Int, Int>) {
        val expected = candidates.copyOf()
        for ((cell, digits) in cleared) expected[cell] = expected[cell] and digits.inv()

        assertTrue("$technique fired", step(technique).eliminate(geometry, candidates))
        assertArrayEquals(expected, candidates)
    }

    /** Every open cell still holds its solution digit. */
    private fun assertSound(puzzle: SudokuPuzzle, candidates: IntArray, after: SudokuTechnique) {
        for (idx in candidates.indices) {
            if (candidates[idx] == 0) continue
            assertTrue("$after struck the solution digit at $idx", candidates[idx] and bit(puzzle.solution[idx]) != 0)
        }
    }

    /** Wraps each ladder step so every elimination it makes on [puzzle] is checked for soundness. */
    private fun audited(puzzle: SudokuPuzzle, fired: MutableList<SudokuTechnique>): List<EliminationStep> =
        EliminationSteps.DEFAULT.map { inner ->
            object : EliminationStep {
                override val technique = inner.technique

                override fun eliminate(geometry: SudokuGeometry, candidates: IntArray): Boolean {
                    val changed = inner.eliminate(geometry, candidates)
                    if (changed) {
                        fired += technique
                        assertSound(puzzle, candidates, technique)
                    }
                    return changed
                }
            }
        }

    /** Plays every hinted digit until solved, checking each digit against the solution. */
    private fun playOut(puzzle: SudokuPuzzle, hints: SudokuHints): List<SudokuHint> {
        var board = SudokuBoard.of(puzzle)
        val seen = mutableListOf<SudokuHint>()
        while (true) {
            val hint = hints.hint(puzzle, board)
            seen += hint
            board = when (hint) {
                is SudokuHint.Place -> {
                    assertEquals("hinted digit at ${hint.cell}", puzzle.solution[hint.cell], hint.digit)
                    board.with(hint.cell, SudokuCell.Entry(hint.digit))
                }
                is SudokuHint.Reveal -> {
                    assertEquals("revealed digit at ${hint.cell}", puzzle.solution[hint.cell], hint.digit)
                    board.with(hint.cell, SudokuCell.Entry(hint.digit))
                }
                SudokuHint.None -> return seen
                else -> throw AssertionError("unexpected $hint on a clean board")
            }
        }
    }

    @Test
    fun `the ladder runs easiest first after the singles`() {
        assertEquals(
            listOf(
                SudokuTechnique.POINTING,
                SudokuTechnique.CLAIMING,
                SudokuTechnique.NAKED_PAIR,
                SudokuTechnique.HIDDEN_PAIR,
                SudokuTechnique.NAKED_TRIPLE,
                SudokuTechnique.HIDDEN_TRIPLE,
            ),
            EliminationSteps.DEFAULT.map { it.technique },
        )
    }

    @Test
    fun `grid2 solves soundly with singles, the ladder and reveals, and needs pointing`() {
        val fired = mutableListOf<SudokuTechnique>()
        val hints = playOut(hard, SudokuHints(audited(hard, fired)))

        val places = hints.filterIsInstance<SudokuHint.Place>()
        assertTrue(places.all { it.technique in singles })
        assertTrue(places.any { it.via == SudokuTechnique.POINTING })
        assertTrue(fired.isNotEmpty())
    }

    @Test
    fun `every step alone stays sound on grid2 and the samples until it stops firing`() {
        for (puzzle in listOf(hard, KnownSolutions.miniSample, KnownSolutions.classicSample)) {
            for (step in EliminationSteps.DEFAULT) {
                val candidates = SudokuCandidates.of(puzzle, SudokuBoard.of(puzzle))
                while (step.eliminate(puzzle.geometry, candidates)) assertSound(puzzle, candidates, step.technique)
            }
        }
    }

    @Test
    fun `no step fires on a board where every digit can go anywhere`() {
        for (step in EliminationSteps.DEFAULT) {
            val candidates = open(classic)
            assertFalse("${step.technique} fired", step.eliminate(classic, candidates))
            assertArrayEquals(open(classic), candidates)
        }
    }

    @Test
    fun `pointing clears a digit from the rest of the row its box confines it to`() {
        val candidates = open(classic)
        // Box 0 keeps 1 only on row 0.
        for (cell in intArrayOf(9, 10, 11, 18, 19, 20)) candidates[cell] = candidates[cell] and bit(1).inv()

        assertClears(SudokuTechnique.POINTING, classic, candidates, (3..8).associateWith { bit(1) })
    }

    @Test
    fun `pointing works down a column of a two-row box`() {
        val candidates = open(mini)
        // Box 0 (rows 0-1, cols 0-2) keeps 1 only in column 0.
        for (cell in intArrayOf(1, 2, 7, 8)) candidates[cell] = candidates[cell] and bit(1).inv()

        assertClears(SudokuTechnique.POINTING, mini, candidates, listOf(12, 18, 24, 30).associateWith { bit(1) })
    }

    @Test
    fun `claiming clears a digit from the rest of the box its row confines it to`() {
        val candidates = open(classic)
        // Row 0 keeps 1 only inside box 0.
        for (cell in 3..8) candidates[cell] = candidates[cell] and bit(1).inv()

        assertFalse(step(SudokuTechnique.POINTING).eliminate(classic, candidates.copyOf()))
        assertClears(SudokuTechnique.CLAIMING, classic, candidates, listOf(9, 10, 11, 18, 19, 20).associateWith { bit(1) })
    }

    @Test
    fun `naked pair clears its two digits from the rest of the row and leaves placed cells empty`() {
        val candidates = open(classic)
        candidates[0] = bit(1) or bit(2)
        candidates[1] = bit(1) or bit(2)
        candidates[8] = 0

        assertClears(SudokuTechnique.NAKED_PAIR, classic, candidates, (2..7).associateWith { bit(1) or bit(2) })
        assertEquals(0, candidates[8])
    }

    @Test
    fun `hidden pair strips every other digit from its two cells`() {
        val candidates = open(classic)
        // In row 0, 1 and 2 fit only cells 0 and 1.
        for (cell in 2..8) candidates[cell] = candidates[cell] and (bit(1) or bit(2)).inv()
        val others = (bit(1) or bit(2)).inv() and candidates[0]

        assertClears(SudokuTechnique.HIDDEN_PAIR, classic, candidates, mapOf(0 to others, 1 to others))
    }

    @Test
    fun `naked triple clears three digits no single cell holds all of`() {
        val candidates = open(classic)
        candidates[0] = bit(1) or bit(2)
        candidates[1] = bit(2) or bit(3)
        candidates[2] = bit(1) or bit(3)
        val triple = bit(1) or bit(2) or bit(3)

        assertFalse(step(SudokuTechnique.NAKED_PAIR).eliminate(classic, candidates.copyOf()))
        assertClears(SudokuTechnique.NAKED_TRIPLE, classic, candidates, (3..8).associateWith { triple })
    }

    @Test
    fun `hidden triple strips every other digit from its three cells`() {
        val candidates = open(classic)
        val triple = bit(1) or bit(2) or bit(3)
        // In row 0, 1, 2 and 3 fit only cells 0-2, each digit in just two of them.
        for (cell in 3..8) candidates[cell] = candidates[cell] and triple.inv()
        candidates[0] = candidates[0] and bit(2).inv()
        candidates[1] = candidates[1] and bit(3).inv()
        candidates[2] = candidates[2] and bit(1).inv()

        assertFalse(step(SudokuTechnique.HIDDEN_PAIR).eliminate(classic, candidates.copyOf()))
        assertClears(SudokuTechnique.HIDDEN_TRIPLE, classic, candidates, (0..2).associateWith { triple.inv() })
    }
}
