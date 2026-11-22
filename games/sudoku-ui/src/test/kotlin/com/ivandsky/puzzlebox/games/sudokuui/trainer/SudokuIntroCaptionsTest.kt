package com.ivandsky.puzzlebox.games.sudokuui.trainer

import com.ivandsky.puzzlebox.game.trainer.BoardKind
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuUnit
import com.ivandsky.puzzlebox.games.sudoku.UnitKind
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuTechnique
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.SudokuStep
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuPractice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The plain captions of a Mini Sudoku intro demo. The train step track shows only the evidence caption (frame 1) and the
 * first landing's (frame 2), so frame 2 must tell the whole landing, closing line included, however many squares land.
 */
class SudokuIntroCaptionsTest {

    private val puzzle: SudokuPuzzle = SudokuTrainerFixtures.eliminating.getValue("rnd:1411")
    private val before: SudokuBoard = SudokuBoard.of(puzzle)

    private val notes = "Every open square shows, in small numbers, the numbers it can still take."

    private fun digits(vararg d: Int): Int = d.fold(0) { mask, digit -> mask or (1 shl (digit - 1)) }

    private fun elimination(
        technique: SudokuTechnique,
        unit: SudokuUnit,
        group: Int,
        struck: Map<Int, Int>,
        evidence: Set<Int>,
    ) = SudokuStep.Elimination(technique, unit, group, struck, evidence)

    private fun captions(step: SudokuStep) = SudokuIntroCaptions.of(step, puzzle, before)

    /** Every landing frame tells the whole landing, so whichever the step track shows, nothing is lost. */
    private fun assertWholeLanding(captions: List<String>, squares: Int, landing: String) {
        assertEquals(squares + 2, captions.size)
        assertEquals(List(squares) { landing }, captions.drop(2))
    }

    @Test
    fun `the first caption of every demo says what the small numbers are`() {
        val steps = listOf(
            elimination(SudokuTechnique.POINTING, SudokuUnit(UnitKind.BOX, 0), digits(4), mapOf(13 to digits(4)), setOf(1, 7)),
            elimination(SudokuTechnique.CLAIMING, SudokuUnit(UnitKind.COLUMN, 1), digits(1), mapOf(0 to digits(1)), setOf(1, 7)),
            elimination(SudokuTechnique.NAKED_PAIR, SudokuUnit(UnitKind.ROW, 2), digits(4, 5), mapOf(15 to digits(4)), setOf(12, 13)),
            elimination(SudokuTechnique.HIDDEN_PAIR, SudokuUnit(UnitKind.COLUMN, 4), digits(3, 5), mapOf(4 to digits(1)), setOf(4, 10)),
        )
        for (step in steps) {
            val result = captions(step)
            assertTrue(result[0], result[0].startsWith(notes))
            assertTrue(result[1], result[1].startsWith(notes))
        }
    }

    @Test
    fun `pointing tells the whole landing in one caption, for several squares and for one`() {
        val box = SudokuUnit(UnitKind.BOX, 0)
        val several = elimination(SudokuTechnique.POINTING, box, digits(4), mapOf(13 to digits(4), 19 to digits(4)), setOf(1, 7))
        val one = elimination(SudokuTechnique.POINTING, box, digits(4), mapOf(13 to digits(4)), setOf(1, 7))
        val landing = "The box's 4 must be in column 2, so 4 comes off the rest of column 2."
        assertWholeLanding(captions(several), 2, landing)
        assertWholeLanding(captions(one), 1, landing)
        assertEquals(
            "In this box, 4 can only go on these squares. They all sit in column 2.",
            captions(several)[1].removePrefix("$notes "),
        )
    }

    @Test
    fun `pointing along a row names the row`() {
        val step = elimination(SudokuTechnique.POINTING, SudokuUnit(UnitKind.BOX, 0), digits(3), mapOf(4 to digits(3)), setOf(0, 1))
        assertWholeLanding(captions(step), 1, "The box's 3 must be in row 1, so 3 comes off the rest of row 1.")
    }

    @Test
    fun `claiming tells the whole landing in one caption, for several squares and for one`() {
        val column = SudokuUnit(UnitKind.COLUMN, 1)
        val several = elimination(
            SudokuTechnique.CLAIMING, column, digits(1), mapOf(0 to digits(1), 2 to digits(1), 6 to digits(1), 8 to digits(1)), setOf(1, 7),
        )
        val one = elimination(SudokuTechnique.CLAIMING, column, digits(1), mapOf(0 to digits(1)), setOf(1, 7))
        val landing = "In column 2, 1 can only go in this box, so 1 comes off the rest of the box."
        assertWholeLanding(captions(several), 4, landing)
        assertWholeLanding(captions(one), 1, landing)
        assertEquals(
            "In column 2, 1 can only go on these squares. They all sit in one box.",
            captions(several)[1].removePrefix("$notes "),
        )
    }

    @Test
    fun `naked pair names both numbers and the unit for every square that loses one`() {
        val row = SudokuUnit(UnitKind.ROW, 2)
        val several = elimination(SudokuTechnique.NAKED_PAIR, row, digits(4, 5), mapOf(14 to digits(4), 16 to digits(5)), setOf(12, 13))
        val one = elimination(SudokuTechnique.NAKED_PAIR, row, digits(4, 5), mapOf(14 to digits(4, 5)), setOf(12, 13))
        val landing = "4 and 5 must go in these two squares, so no other square in row 3 can take them."
        assertWholeLanding(captions(several), 2, landing)
        assertWholeLanding(captions(one), 1, landing)
    }

    @Test
    fun `naked pair in a box says this box`() {
        val step = elimination(SudokuTechnique.NAKED_PAIR, SudokuUnit(UnitKind.BOX, 3), digits(2, 6), mapOf(20 to digits(2)), setOf(18, 19))
        assertWholeLanding(captions(step), 1, "2 and 6 must go in these two squares, so no other square in this box can take them.")
    }

    @Test
    fun `hidden pair tells what both pair squares keep, for two squares and for one`() {
        val column = SudokuUnit(UnitKind.COLUMN, 4)
        val several = elimination(SudokuTechnique.HIDDEN_PAIR, column, digits(3, 5), mapOf(4 to digits(1, 2), 10 to digits(4)), setOf(4, 10))
        val one = elimination(SudokuTechnique.HIDDEN_PAIR, column, digits(3, 5), mapOf(4 to digits(1, 2)), setOf(4, 10))
        val landing = "3 and 5 fit only these two squares of column 5, so the two squares keep only 3 and 5."
        assertWholeLanding(captions(several), 2, landing)
        assertWholeLanding(captions(one), 1, landing)
    }

    /** The first step of [technique] on its focus board of the fixture [name]. */
    private fun first(name: String, technique: String): Pair<SudokuStep.Single, SudokuBoard> {
        val fixture = SudokuTrainerFixtures.all.getValue(name)
        val board = checkNotNull(SudokuPractice.start(fixture, TechniqueId(technique), BoardKind.Focus)) { "$name has no $technique board" }
        return SudokuPractice.showMe(fixture, board)!!.step as SudokuStep.Single to board
    }

    private fun singleCaptions(name: String, technique: String): Pair<SudokuStep.Single, List<String>> {
        val (step, board) = first(name, technique)
        return step to SudokuIntroCaptions.of(step, SudokuTrainerFixtures.all.getValue(name), board)
    }

    @Test
    fun `a last cell names its unit, and its landing says what is missing and what to look for next`() {
        val (step, result) = singleCaptions("li:454", "last-cell")
        val digit = step.place.digit
        assertEquals(3, result.size)
        assertTrue(result[1], result[1].startsWith("$notes ") && result[1].endsWith(" has just one open square."))
        assertEquals("It is missing $digit, so $digit goes here. $digit is placed. Look for a row, column or box with one open square.", result[2])
    }

    @Test
    fun `a hidden single in a box looks for a box next, not a row or column`() {
        val (step, result) = singleCaptions("li:454", "hidden-single-box")
        val digit = step.place.digit
        assertEquals(
            "Only this square can take $digit, so $digit goes here. $digit is placed. Look for a box where a number fits in only one square.",
            result[2],
        )
    }

    @Test
    fun `a hidden single in a row or column looks for a row or column next, not a box`() {
        val (step, result) = singleCaptions("li:454", "hidden-single-line")
        val digit = step.place.digit
        assertEquals(
            "Only this square can take $digit, so $digit goes here. $digit is placed. " +
                "Look for a row or column where a number fits in only one square.",
            result[2],
        )
    }

    @Test
    fun `a naked single names the numbers its square shares a row, column or box with`() {
        val (step, board) = first("li:111", "naked-single")
        val seen = step.blockers.map { board.digitAt(it) }.distinct().sorted()
        val result = SudokuIntroCaptions.of(step, SudokuTrainerFixtures.all.getValue("li:111"), board)
        val list = if (seen.size == 1) "${seen.single()}" else "${seen.dropLast(1).joinToString(", ")} and ${seen.last()}"
        assertEquals(
            "$notes This square shares a row, column or box with $list, so only ${step.place.digit} is left in its notes.",
            result[1],
        )
        assertEquals(
            "Only ${step.place.digit} is left in its notes, so ${step.place.digit} goes here. ${step.place.digit} is placed. " +
                "Look for a square with one note left.",
            result[2],
        )
    }

    /** A naked single on cell 0 of the Mini corner whose notes are [struck] off and whose peers hold [blockers]. */
    private fun naked(blockers: Set<Int>, struck: Int) = SudokuStep.Single(
        place = com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuHint.Place(0, 3, SudokuTechnique.NAKED_SINGLE, null, null, emptyList()),
        lastCell = null,
        blockers = blockers,
        struck = if (struck == 0) emptyMap() else mapOf(0 to struck),
    )

    private fun evidence(step: SudokuStep, board: SudokuBoard) = SudokuIntroCaptions.of(step, puzzle, board)[1].removePrefix("$notes ")

    @Test
    fun `a naked single after an elimination says which numbers were taken off its notes, not only the ones it shares`() {
        // Cells 1, 2, 6 hold digits for the blockers; digit 4 is off the notes by a strike and held by no peer.
        var board = before
        val held = mapOf(1 to 1, 2 to 2, 6 to 5)
        held.forEach { (cell, digit) -> board = board.with(cell, com.ivandsky.puzzlebox.games.sudoku.SudokuCell.Entry(digit)) }
        val blockers = held.keys

        assertEquals(
            "This square shares a row, column or box with 1, 2 and 5, and 4 is already off its notes, so only 3 is left in its notes.",
            evidence(naked(blockers, digits(4)), board),
        )
        assertEquals(
            "This square shares a row, column or box with 1, 2 and 5, and 4 and 6 are already off its notes, so only 3 is left in its notes.",
            evidence(naked(blockers, digits(4, 6)), board),
        )
    }

    @Test
    fun `a naked single with every other number struck, or none shared, still has a true caption`() {
        val none = evidence(naked(emptySet(), digits(1, 2, 4, 5, 6)), before)
        assertEquals("This square has 1, 2, 4, 5 and 6 already off its notes, so only 3 is left in its notes.", none)
        assertEquals("Only 3 is left in this square's notes.", evidence(naked(emptySet(), 0), before))
    }
}
