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
        val eliminations = SudokuTechnique.entries.filter { it !in setOf(SudokuTechnique.HIDDEN_SINGLE_BOX, SudokuTechnique.HIDDEN_SINGLE_LINE, SudokuTechnique.NAKED_SINGLE) }
            .associate { technique ->
                val unit = if (technique == SudokuTechnique.POINTING) box0 else col3
                val group = when (technique) {
                    SudokuTechnique.POINTING, SudokuTechnique.CLAIMING -> bit(1)
                    SudokuTechnique.NAKED_PAIR, SudokuTechnique.HIDDEN_PAIR -> bit(7) or bit(8)
                    else -> bit(1) or bit(2) or bit(4)
                }
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
        for (technique in SudokuTechnique.entries) assertTrue("$technique has a step", technique.name in steps)
        for ((name, step) in steps) assertFalse("$name has text", text(step).isBlank())
    }

    @Test
    fun `a last cell reads as a last cell, not as the hidden single it carries`() {
        val step = SudokuStep.Single(place(2, 6, SudokuTechnique.HIDDEN_SINGLE_LINE, row0), row0, emptySet())

        assertEquals("Last cell: row 1 only needs 6", text(step))
    }

    @Test
    fun `hidden singles name the box or the line`() {
        assertEquals(
            "Hidden single (box): in this box only this cell can take 4",
            text(SudokuStep.Single(place(2, 4, SudokuTechnique.HIDDEN_SINGLE_BOX, box0), null, emptySet())),
        )
        assertEquals(
            "Hidden single (line): in col 4 only this cell can take 2",
            text(SudokuStep.Single(place(2, 2, SudokuTechnique.HIDDEN_SINGLE_LINE, col3), null, emptySet())),
        )
    }

    @Test
    fun `a naked single lists the sorted digits of its blockers on the board`() {
        val board = miniBoard(1 to 6, 2 to 1, 3 to 5, 4 to 2, 5 to 3)
        val step = SudokuStep.Single(place(0, 4, SudokuTechnique.NAKED_SINGLE, null), null, setOf(1, 2, 3, 4, 5))

        assertEquals("Naked single: it sees 1, 2, 3, 5, 6, so it is 4", text(step, mini, board))
    }

    @Test
    fun `pointing names the digit from the mask for every digit and the shared line`() {
        for (digit in 1..9) {
            val row = SudokuStep.Elimination(SudokuTechnique.POINTING, box0, bit(digit), mapOf(45 to bit(digit), 46 to bit(digit)), setOf(2))
            val col = SudokuStep.Elimination(SudokuTechnique.POINTING, box0, bit(digit), mapOf(5 to bit(digit), 14 to bit(digit)), setOf(0))

            assertEquals(
                "Pointing: this box puts its $digit in row 6, so the rest of row 6 can't take $digit",
                text(row),
            )
            assertEquals(
                "Pointing: this box puts its $digit in col 6, so the rest of col 6 can't take $digit",
                text(col),
            )
        }
    }

    @Test
    fun `claiming names the line and the digit from the mask for every digit`() {
        for (digit in 1..9) {
            val step = SudokuStep.Elimination(SudokuTechnique.CLAIMING, SudokuUnit(UnitKind.COLUMN, 4), bit(digit), mapOf(0 to bit(digit)), setOf(4))

            assertEquals("Claiming: col 5 puts its $digit in this box, so the rest of the box can't take $digit", text(step))
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

        assertEquals("Naked pair: these two cells only take 2 and 5, so the rest of row 1 loses both", text(step, mini, miniBoard()))
    }

    @Test
    fun `a hidden pair names its group digits, not the board's candidates`() {
        val step = SudokuStep.Elimination(SudokuTechnique.HIDDEN_PAIR, row0, bit(1) or bit(6), mapOf(0 to bit(4), 1 to bit(4)), setOf(0, 1))

        assertEquals("Hidden pair: 1 and 6 only fit these two cells, so they take nothing else", text(step, mini, miniBoard()))
    }

    @Test
    fun `a reveal names its cell one-based`() {
        assertEquals("Reveal: row 5, column 1 is 5 — no simple step here", text(SudokuStep.Reveal(36, 5)))
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
            "This 1 repeats the 1 in its col 3 — and isn't in the solution",
            text(SudokuStep.Mistake(74, 1, 2)),
        )
    }

    @Test
    fun `every text is one line within the budget and starts with its technique`() {
        val names = mapOf(
            "HIDDEN_SINGLE_BOX" to "Hidden single (box)", "HIDDEN_SINGLE_LINE" to "Hidden single (line)",
            "NAKED_SINGLE" to "Naked single", "LAST_CELL" to "Last cell", "POINTING" to "Pointing",
            "CLAIMING" to "Claiming", "NAKED_PAIR" to "Naked pair", "HIDDEN_PAIR" to "Hidden pair",
            "NAKED_TRIPLE" to "Naked triple", "HIDDEN_TRIPLE" to "Hidden triple", "REVEAL" to "Reveal",
        )
        val worstSingle = SudokuStep.Single(place(0, 4, SudokuTechnique.NAKED_SINGLE, null), null, setOf(1, 2, 3, 4, 5))
        val worstBoard = miniBoard(1 to 6, 2 to 1, 3 to 5, 4 to 2, 5 to 3)
        val texts = everyStep().mapValues { (_, step) -> text(step) } +
            ("WORST_NAKED" to text(worstSingle, mini, worstBoard)) +
            ("WORST_CLASH" to text(SudokuStep.Mistake(2, 8, 0)))

        for ((name, line) in texts) {
            assertFalse("$name is one line", '\n' in line)
            assertTrue("$name is ${line.length} chars: $line", line.length <= 80)
            names[name]?.let { assertTrue("$name starts with $it", line.startsWith("$it:")) }
        }
        for (name in listOf("HIDDEN_SINGLE_BOX", "HIDDEN_SINGLE_LINE", "LAST_CELL", "POINTING", "CLAIMING", "REVEAL", "MISTAKE", "CLASH")) {
            assertTrue("$name names the digit", texts.getValue(name).any { it.isDigit() })
        }
    }

    @Test
    fun `a solved board has no callout`() {
        assertTrue(runCatching { text(SudokuStep.Solved) }.isFailure)
    }
}
