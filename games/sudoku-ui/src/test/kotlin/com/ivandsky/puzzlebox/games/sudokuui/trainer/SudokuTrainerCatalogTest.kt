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
    fun `each line is the technique's generic list line`() {
        assertEquals(
            listOf(
                "A row, column or box has one open square: put in the number it is missing.",
                "A box where one number fits in only one square.",
                "A row or column where one number fits in only one square.",
                "A square with only one number it can still take: that number goes there.",
                "In a box, a number fits only along one row or column, so take it off the rest of that row or column.",
                "In a column, a number fits only inside one box, so take it off the rest of that box.",
                "Two squares in one row, column or box can only take the same two numbers, so the rest of that row, column or box loses both.",
                "Two numbers fit only in the same two squares, so those squares lose every other note.",
            ),
            catalog.map { it.line },
        )
        for (row in catalog) assertEquals(row.id.value, SudokuStepCopy.technique(row.id), row.line)
    }

    @Test
    fun `each line is one line of plain words that names no technique and no digit`() {
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
        /** The longest line a technique row holds: the pair sentences run to 125 and wrap on the row, past the hints' own 80 for a line with real digits. */
        const val LINE_BUDGET = 130
        const val NAME_BUDGET = 30
    }
}
