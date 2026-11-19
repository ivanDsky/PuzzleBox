package com.ivandsky.puzzlebox.games.sudoku.engine.trainer

import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuTechnique
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.SudokuStep
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.SudokuStepper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Trainer ids are wire and index keys (spec data-3): pinned so a rename or reorder fails here. */
class SudokuTechniqueIdsTest {

    private val ids = SudokuTechniqueIds.all.map { it.value }

    @Test
    fun `the eight technique ids follow the stepper order`() {
        assertEquals(
            listOf(
                "last-cell", "hidden-single-box", "hidden-single-line", "naked-single",
                "pointing", "claiming", "naked-pair", "hidden-pair",
            ),
            ids,
        )
    }

    @Test
    fun `ids are unique and fit the stored key shape`() {
        assertEquals(ids, ids.distinct())
        val shape = Regex("^[a-z0-9-]{1,40}$")
        ids.forEach { assertTrue(it, shape.matches(it)) }
    }

    @Test
    fun `a last cell is its own id and a hidden single of the same technique is not`() {
        val walk = SudokuTrainerFixtures.dailies.values.flatMap { SudokuTechniqueWalker.walk(it) }
        val singles = walk.mapNotNull { it.step as? SudokuStep.Single }
        val last = singles.first { it.lastCell != null }
        val hidden = singles.first { it.lastCell == null && it.place.technique == SudokuTechnique.HIDDEN_SINGLE_BOX }

        assertEquals("last-cell", SudokuTechniqueIds.of(last)?.value)
        assertEquals("hidden-single-box", SudokuTechniqueIds.of(hidden)?.value)
        singles.filter { it.lastCell == null }.forEach { assertTrue(SudokuTechniqueIds.of(it)!!.value != "last-cell") }
    }

    @Test
    fun `reveals, mistakes, the end and triples have no id`() {
        val puzzle = SudokuTrainerFixtures.dailies.values.first()
        val wrong = SudokuBoard.of(puzzle).let { b ->
            val idx = b.cells.indices.first { b.digitAt(it) == 0 }
            b.with(idx, com.ivandsky.puzzlebox.games.sudoku.SudokuCell.Entry(puzzle.solution[idx] % puzzle.geometry.size + 1))
        }
        assertEquals(null, SudokuTechniqueIds.of(SudokuStepper.next(puzzle, wrong, null, emptyMap())))
        assertEquals(null, SudokuTechniqueIds.of(SudokuStep.Reveal(0, 1)))
        assertEquals(null, SudokuTechniqueIds.of(SudokuStep.Solved))
        assertEquals(null, SudokuTechniqueIds.of(SudokuStep.Elimination(SudokuTechnique.NAKED_TRIPLE, null, 0, emptyMap(), emptySet())))
    }
}
