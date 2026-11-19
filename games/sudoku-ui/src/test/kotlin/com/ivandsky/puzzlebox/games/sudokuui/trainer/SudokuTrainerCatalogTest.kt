package com.ivandsky.puzzlebox.games.sudokuui.trainer

import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuTechniqueIds
import com.ivandsky.puzzlebox.games.sudokuui.hint.SudokuStepCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuTrainerCatalogTest {

    private val catalog = SudokuTrainerFixtures.trainer.catalog()

    @Test
    fun `the catalog lists the eight Mini Sudoku techniques in the stepper's order`() {
        assertEquals(SudokuTechniqueIds.all, catalog.map { it.id })
        assertEquals(8, catalog.size)
        assertEquals(catalog.size, catalog.map { it.id }.toSet().size)
    }

    @Test
    fun `each technique has its list name`() {
        assertEquals(
            listOf(
                "Last cell",
                "Hidden single (box)",
                "Hidden single (row or column)",
                "Naked single",
                "Pointing",
                "Claiming",
                "Naked pair",
                "Hidden pair",
            ),
            catalog.map { it.name },
        )
    }

    @Test
    fun `each line is the hint's sentence for that technique, in generic words`() {
        assertEquals(
            listOf(
                "This line has one cell left, and it needs a certain digit",
                "In this box only this cell can take a certain digit (hidden single)",
                "In this line only this cell can take a certain digit (hidden single)",
                "It sees every other digit, so it is the one left (naked single)",
                "This box puts its digit in a line, so the rest of that line can't take it (pointing)",
                "This line puts its digit in this box, so the rest of the box can't take it (claiming)",
                "These two cells only take two digits, so the rest of this line loses both (naked pair)",
                "Two digits only fit these two cells, so they take nothing else (hidden pair)",
            ),
            catalog.map { it.line },
        )
        for (row in catalog) assertEquals(row.id.value, SudokuStepCopy.technique(row.id), row.line)
    }

    @Test
    fun `each line is one sentence that explains the step before it names the technique`() {
        for (row in catalog) {
            assertFalse("${row.id.value} is one line: ${row.line}", '\n' in row.line)
            assertTrue("${row.id.value} is ${row.line.length} chars: ${row.line}", row.line.length <= LINE_BUDGET)
            assertFalse("${row.id.value} opens with its name: ${row.line}", row.line.startsWith(row.name.substringBefore(" ("), ignoreCase = true))
            assertFalse("${row.id.value} names a digit of some step: ${row.line}", row.line.any { it.isDigit() })
        }
    }

    @Test
    fun `a name is no longer than a list row holds`() {
        for (row in catalog) assertTrue("${row.name} is ${row.name.length} chars", row.name.length <= NAME_BUDGET)
    }

    private companion object {
        /** The longest line a technique row holds: the pointing, claiming and pair sentences run to 86, past the hints' own 80 for a line with real digits. */
        const val LINE_BUDGET = 90
        const val NAME_BUDGET = 30
    }
}
