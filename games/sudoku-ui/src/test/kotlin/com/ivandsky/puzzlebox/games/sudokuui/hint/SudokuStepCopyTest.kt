package com.ivandsky.puzzlebox.games.sudokuui.hint

import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuSamples
import com.ivandsky.puzzlebox.games.sudoku.SudokuUnit
import com.ivandsky.puzzlebox.games.sudoku.SudokuVariants
import com.ivandsky.puzzlebox.games.sudoku.UnitKind
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuHint
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuTechnique
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.SudokuStep
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.emittedEliminationTechniques
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuTechniqueIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuStepCopyTest {

    private val classic = SudokuSamples.classic
    private val classicBoard = SudokuBoard.of(classic)

    private val mini = SudokuPuzzle(
        SudokuVariants.MINI,
        List(36) { 0 },
        "612345345612456123123456561234234561".map { it.digitToInt() },
    )

    /** A mini board holding [givens] (cell to digit) and nothing else. */
    private fun miniBoard(vararg givens: Pair<Int, Int>): SudokuBoard =
        givens.fold(SudokuBoard.of(mini)) { board, (cell, digit) -> board.with(cell, SudokuCell.Given(digit)) }

    private fun bit(digit: Int) = 1 shl (digit - 1)

    private fun place(cell: Int, digit: Int, technique: SudokuTechnique, unit: SudokuUnit?) =
        SudokuHint.Place(cell, digit, technique, unit, null, emptyList())

    private fun text(step: SudokuStep, puzzle: SudokuPuzzle = classic, board: SudokuBoard = classicBoard) =
        SudokuStepCopy.text(step, puzzle, board)

    private val row0 = SudokuUnit(UnitKind.ROW, 0)
    private val col3 = SudokuUnit(UnitKind.COLUMN, 3)
    private val box0 = SudokuUnit(UnitKind.BOX, 0)

    /** One step of every kind the stepper can emit, on the classic board. */
    private fun everyStep(): Map<String, SudokuStep> {
        val single = { technique: SudokuTechnique, unit: SudokuUnit? ->
            SudokuStep.Single(place(2, 4, technique, unit), null, setOf(0, 1))
        }
        val struck = mapOf(45 to bit(1), 46 to bit(1))
        val eliminations = emittedEliminationTechniques
            .associate { technique ->
                val unit = if (technique == SudokuTechnique.POINTING) box0 else col3
                val group = if (technique == SudokuTechnique.POINTING || technique == SudokuTechnique.CLAIMING) bit(1) else bit(7) or bit(8)
                "$technique" to SudokuStep.Elimination(technique, unit, group, if (technique == SudokuTechnique.POINTING || technique == SudokuTechnique.CLAIMING) struck else mapOf(9 to bit(7)), setOf(2, 3))
            }
        return mapOf(
            "HIDDEN_SINGLE_BOX" to single(SudokuTechnique.HIDDEN_SINGLE_BOX, box0),
            "HIDDEN_SINGLE_LINE" to single(SudokuTechnique.HIDDEN_SINGLE_LINE, col3),
            "NAKED_SINGLE" to single(SudokuTechnique.NAKED_SINGLE, null),
            "LAST_CELL" to SudokuStep.Single(place(2, 4, SudokuTechnique.HIDDEN_SINGLE_LINE, row0), row0, emptySet()),
            "REVEAL" to SudokuStep.Reveal(4, 7),
            "MISTAKE" to SudokuStep.Mistake(2, 9, null),
            "CLASH" to SudokuStep.Mistake(2, 9, 0),
        ) + eliminations
    }

    @Test
    fun `every technique and the last cell has copy`() {
        val steps = everyStep()
        for ((name, step) in steps) assertFalse("$name has text", text(step).isBlank())
    }

    @Test
    fun `a last cell reads as a last cell, not as the hidden single it carries`() {
        val step = SudokuStep.Single(place(2, 6, SudokuTechnique.HIDDEN_SINGLE_LINE, row0), row0, emptySet())

        assertEquals("Row 1 has one open square left, and it needs 6", text(step))
    }

    @Test
    fun `a last cell in a box says this box, never a box number`() {
        val step = SudokuStep.Single(place(2, 6, SudokuTechnique.HIDDEN_SINGLE_BOX, box0), box0, emptySet())

        assertEquals("This box has one open square left, and it needs 6", text(step))
    }

    @Test
    fun `hidden singles name the box or the line`() {
        assertEquals(
            "In this box only this square can take 4 (hidden single)",
            text(SudokuStep.Single(place(2, 4, SudokuTechnique.HIDDEN_SINGLE_BOX, box0), null, emptySet())),
        )
        assertEquals(
            "In column 4 only this square can take 2 (hidden single)",
            text(SudokuStep.Single(place(2, 2, SudokuTechnique.HIDDEN_SINGLE_LINE, col3), null, emptySet())),
        )
    }

    @Test
    fun `a naked single lists the sorted digits of its blockers on the board`() {
        val board = miniBoard(1 to 6, 2 to 1, 3 to 5, 4 to 2, 5 to 3)
        val step = SudokuStep.Single(place(0, 4, SudokuTechnique.NAKED_SINGLE, null), null, setOf(1, 2, 3, 4, 5))

        assertEquals("It sees 1, 2, 3, 5, 6, so it is 4 (naked single)", text(step, mini, board))
    }

    @Test
    fun `a naked single names the digits it sees, then the digits only a strike rules out`() {
        val board = miniBoard(1 to 3, 2 to 1)
        val one = SudokuStep.Single(place(0, 4, SudokuTechnique.NAKED_SINGLE, null), null, setOf(1, 2), mapOf(0 to bit(2)))
        val several = SudokuStep.Single(place(0, 4, SudokuTechnique.NAKED_SINGLE, null), null, setOf(1, 2), mapOf(0 to (bit(2) or bit(5) or bit(6))))

        assertEquals("It sees 1, 3 and has 2 ruled out, so it is 4 (naked single)", text(one, mini, board))
        assertEquals("It sees 1, 3 and has 2, 5 and 6 ruled out, so it is 4 (naked single)", text(several, mini, board))
    }

    @Test
    fun `a naked single that sees nothing names only its struck digits`() {
        val struck = mapOf(0 to (bit(1) or bit(2) or bit(3) or bit(5) or bit(6)))
        val step = SudokuStep.Single(place(0, 4, SudokuTechnique.NAKED_SINGLE, null), null, emptySet(), struck)

        assertEquals("It has 1, 2, 3, 5 and 6 ruled out, so it is 4 (naked single)", text(step, mini, miniBoard()))
    }

    @Test
    fun `pointing names the digit from the mask for every digit and the shared line`() {
        for (digit in 1..9) {
            val row = SudokuStep.Elimination(
                SudokuTechnique.POINTING, SudokuUnit(UnitKind.BOX, 3), bit(digit), mapOf(51 to bit(digit), 52 to bit(digit)), setOf(45, 46),
            )
            val col = SudokuStep.Elimination(
                SudokuTechnique.POINTING, SudokuUnit(UnitKind.BOX, 1), bit(digit), mapOf(32 to bit(digit), 41 to bit(digit)), setOf(5, 14),
            )

            assertEquals(
                "This box puts its $digit in row 6, so the rest of row 6 can't take $digit (pointing)",
                text(row),
            )
            assertEquals(
                "This box puts its $digit in column 6, so the rest of column 6 can't take $digit (pointing)",
                text(col),
            )
        }
    }

    @Test
    fun `pointing along a column names the column both times when one cell is struck`() {
        val step = SudokuStep.Elimination(SudokuTechnique.POINTING, SudokuUnit(UnitKind.BOX, 1), bit(3), mapOf(32 to bit(3)), setOf(5, 14))

        assertEquals("This box puts its 3 in column 6, so the rest of column 6 can't take 3 (pointing)", text(step))
    }

    @Test
    fun `pointing along a row names the row both times when one cell is struck`() {
        val step = SudokuStep.Elimination(SudokuTechnique.POINTING, SudokuUnit(UnitKind.BOX, 3), bit(3), mapOf(51 to bit(3)), setOf(45, 46))

        assertEquals("This box puts its 3 in row 6, so the rest of row 6 can't take 3 (pointing)", text(step))
    }

    @Test
    fun `claiming names the line and the digit from the mask for every digit`() {
        for (digit in 1..9) {
            val step = SudokuStep.Elimination(SudokuTechnique.CLAIMING, SudokuUnit(UnitKind.COLUMN, 4), bit(digit), mapOf(0 to bit(digit)), setOf(4))

            assertEquals("Column 5 puts its $digit in this box, so the rest of the box can't take $digit (claiming)", text(step))
        }
    }

    @Test
    fun `a pointing step that strikes two digits is refused`() {
        val step = SudokuStep.Elimination(SudokuTechnique.POINTING, box0, bit(1) or bit(2), mapOf(45 to (bit(1) or bit(2))), setOf(2))

        assertTrue(runCatching { text(step) }.isFailure)
    }

    @Test
    fun `a naked pair names its group digits, not the board's candidates`() {
        // The empty board leaves every digit open; the group is what the engine proved, after earlier strikes.
        val step = SudokuStep.Elimination(SudokuTechnique.NAKED_PAIR, row0, bit(2) or bit(5), mapOf(6 to bit(2)), setOf(0, 1))

        assertEquals("Two squares only take 2 and 5, so the rest of row 1 loses both (naked pair)", text(step, mini, miniBoard()))
    }

    @Test
    fun `a hidden pair names its group digits, not the board's candidates`() {
        val step = SudokuStep.Elimination(SudokuTechnique.HIDDEN_PAIR, row0, bit(1) or bit(6), mapOf(0 to bit(4), 1 to bit(4)), setOf(0, 1))

        assertEquals("1 and 6 fit only these two squares, so they keep no other note (hidden pair)", text(step, mini, miniBoard()))
    }

    @Test
    fun `a reveal names its cell one-based`() {
        assertEquals("Row 5, column 1 is 5 — no simple step here", text(SudokuStep.Reveal(36, 5)))
    }

    @Test
    fun `a mistake names the digit and a clash names the shared unit`() {
        assertEquals("This 2 isn't in the solution", text(SudokuStep.Mistake(2, 2, null)))
        assertEquals(
            "This 1 repeats the 1 in its box — and isn't in the solution",
            text(SudokuStep.Mistake(2, 1, 0)),
        )
        assertEquals(
            "This 1 repeats the 1 in its row 1 — and isn't in the solution",
            text(SudokuStep.Mistake(8, 1, 0)),
        )
        assertEquals(
            "This 1 repeats the 1 in its column 3 — and isn't in the solution",
            text(SudokuStep.Mistake(74, 1, 2)),
        )
    }

    @Test
    fun `no text starts with a technique name, and each is one line within the budget`() {
        val names = listOf(
            "Hidden single", "Naked single", "Last cell", "Pointing", "Claiming", "Naked pair", "Hidden pair", "Reveal",
        )
        val worstSingle = SudokuStep.Single(place(0, 4, SudokuTechnique.NAKED_SINGLE, null), null, setOf(1, 2, 3, 4, 5))
        val worstBoard = miniBoard(1 to 6, 2 to 1, 3 to 5, 4 to 2, 5 to 3)
        // Classic, as many seen digits as struck ones: the longest a naked single's line gets.
        val classicWorstBoard = (1..4).fold(classicBoard) { board, cell -> board.with(cell, SudokuCell.Given(cell)) }
        val classicWorst = SudokuStep.Single(
            place(0, 9, SudokuTechnique.NAKED_SINGLE, null), null, setOf(1, 2, 3, 4), mapOf(0 to (bit(5) or bit(6) or bit(7) or bit(8))),
        )
        val texts = everyStep().mapValues { (_, step) -> text(step) } +
            ("WORST_NAKED" to text(worstSingle, mini, worstBoard)) +
            ("WORST_NAKED_STRUCK" to text(classicWorst, classic, classicWorstBoard)) +
            ("WORST_CLASH" to text(SudokuStep.Mistake(2, 8, 0)))

        for ((key, line) in texts) {
            assertFalse("$key is one line", '\n' in line)
            assertTrue("$key is ${line.length} chars: $line", line.length <= 80)
            for (name in names) assertFalse("$key starts with $name: $line", line.startsWith(name, ignoreCase = true))
        }
        for (name in listOf("HIDDEN_SINGLE_BOX", "HIDDEN_SINGLE_LINE", "LAST_CELL", "POINTING", "CLAIMING", "REVEAL", "MISTAKE", "CLASH")) {
            assertTrue("$name names the digit", texts.getValue(name).any { it.isDigit() })
        }
    }

    @Test
    fun `a solved board has no callout`() {
        assertTrue(runCatching { text(SudokuStep.Solved) }.isFailure)
    }

    @Test
    fun `a technique's list line has no this, no digit, cell or line, and no technique name in brackets`() {
        val byName = SudokuTechniqueIds.all.associate { it.value to SudokuStepCopy.technique(it) }

        assertEquals(
            mapOf(
                "last-cell" to "A row, column or box has one open square: put in the number it is missing.",
                "hidden-single-box" to "A box where one number fits in only one square.",
                "hidden-single-line" to "A row or column where one number fits in only one square.",
                "naked-single" to "A square with only one number it can still take: that number goes there.",
                "pointing" to "In a box, a number fits only along one row or column, so take it off the rest of that row or column.",
                "claiming" to "In a column, a number fits only inside one box, so take it off the rest of that box.",
                "naked-pair" to "Two squares in one row, column or box can only take the same two numbers, " +
                    "so the rest of that row, column or box loses both.",
                "hidden-pair" to "Two numbers fit only in the same two squares, so those squares lose every other note.",
            ),
            byName,
        )
        for ((id, line) in byName) {
            assertFalse("$id: $line", Regex("\\b(this|digits?|cells?|lines?)\\b", RegexOption.IGNORE_CASE).containsMatchIn(line))
            assertFalse("$id keeps no bracket: $line", '(' in line)
        }
    }

    @Test
    fun `every listed technique has a generic line within the technique list's budget`() {
        for (id in SudokuTechniqueIds.all) {
            val line = SudokuStepCopy.technique(id)
            assertFalse("${id.value} is one line", '\n' in line)
            assertTrue("${id.value} is ${line.length} chars: $line", line.length <= 130)
        }
    }
}
