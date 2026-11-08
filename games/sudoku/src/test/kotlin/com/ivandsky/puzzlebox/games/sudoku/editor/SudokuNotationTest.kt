package com.ivandsky.puzzlebox.games.sudoku.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.NotationResult
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sudoku notation v1 (M5-13-a): parse/print round trips, the four canonical fixtures and the
 * error coordinates. The whole-text blanking (header + `#` lines → "") is simulated here the
 * same way [com.ivandsky.puzzlebox.game.editor.NotationText] does it, so line numbers are
 * whole-text and 1-based, exactly what the real caller passes.
 */
class SudokuNotationTest {

    private val mini = SudokuEditor(SudokuGames.MINI)
    private val classic = SudokuEditor(SudokuGames.CLASSIC)

    private fun blanked(vararg lines: String): String = lines.joinToString("\n") {
        if (it.startsWith("puzzlebox ") || it.startsWith("#")) "" else it
    } + "\n"

    private fun blanked(text: String): String =
        blanked(*text.lines().dropLastWhile { it.isEmpty() }.toTypedArray())

    private fun draft(size: Int, vararg rows: String): SudokuDraft =
        SudokuDraft(size, rows.joinToString("").map { if (it == '.') 0 else it.digitToInt() })

    private fun parse(size: Int, text: String): NotationResult<SudokuDraft> =
        SudokuNotation.parse(size, text)

    // --- parse(print(d)) == Ok(d) -----------------------------------------------------------

    @Test
    fun `round trip blank draft`() {
        val d = mini.newDraft(SudokuGames.MINI.variant.id)
        assertEquals(NotationResult.Ok(d), parse(6, blanked(SudokuNotation.print(d))))
    }

    @Test
    fun `round trip fully set draft`() {
        val d = draft(6, "463251", "521436", "142563", "356142", "234615", "615324")
        assertEquals(NotationResult.Ok(d), parse(6, blanked(SudokuNotation.print(d))))
    }

    @Test
    fun `round trip partly set draft built through editor commands`() {
        var d = mini.newDraft(SudokuGames.MINI.variant.id)
        for ((cell, digit) in listOf(0 to 4, 7 to 2, 20 to 1, 35 to 5)) {
            d = d.setGiven(cell, digit).apply(d)
        }
        assertEquals(NotationResult.Ok(d), parse(6, blanked(SudokuNotation.print(d))))
    }

    @Test
    fun `round trip classic draft`() {
        val d = draft(
            9,
            "53..7....", "6..195...", ".98....6.", "8...6...3", "4..8.3..1",
            "7...2...6", ".6....28.", "...419..5", "....8..79",
        )
        assertEquals(NotationResult.Ok(d), parse(9, blanked(SudokuNotation.print(d))))
    }

    // --- fixtures ---------------------------------------------------------------------------

    private fun fixture(name: String): String =
        javaClass.getResourceAsStream("/notation/$name")!!.readBytes().decodeToString()

    @Test
    fun `fixtures parse, print canonically and match their expect line`() {
        for (name in listOf(
            "minisudoku-unique.txt",
            "minisudoku-multiple.txt",
            "minisudoku-none.txt",
            "sudoku-unique.txt",
        )) {
            val text = fixture(name)
            val expect = text.lineSequence().first { it.startsWith("# expect:") }
                .removePrefix("# expect:").trim().split(" ")
            val wantedVerdict = expect.first()
            val wantedBlocks = expect.drop(1)
            val size = if (text.contains("puzzlebox minisudoku")) 6 else 9
            val editor = if (size == 6) mini else classic

            val parsed = parse(size, blanked(*text.lines().toTypedArray()))
            assertTrue("$name should parse", parsed is NotationResult.Ok)
            val d = (parsed as NotationResult.Ok).draft

            assertEquals("$name print not canonical",
                text.trimEnd('\n').lines().filterNot { it.startsWith("#") }.joinToString("\n") + "\n",
                SudokuNotation.print(d))

            val verdict = editor.analyze(d, Budget.Unlimited)
            assertEquals("$name verdict", wantedVerdict, verdict::class.simpleName!!.lowercase())
            val blockIds = editor.publishBlocks(d, verdict).map { it.id }
            assertEquals("$name blocks", wantedBlocks, blockIds)
        }
    }

    // --- errors -----------------------------------------------------------------------------

    @Test
    fun `short row errors at its line, col 1`() {
        // header blanked -> row 1 is text line 2; row 3 (line 4) has 5 cells
        val err = parse(6, blanked("", "1..4..", "..3..6", "1..4.", "......", "5..2..", "..6..3"))
        assertEquals(NotationResult.Err(4, 1, "row 3 has 5 cells, expected 6"), err)
    }

    @Test
    fun `extra row errors at its line, col 1`() {
        val err = parse(6, blanked("", "1..4..", "..3..6", "......", "......", "5..2..", "..6..3", "..6..3"))
        assertEquals(NotationResult.Err(8, 1, "unexpected row 7 of 6"), err)
    }

    @Test
    fun `missing rows error after the last row`() {
        val err = parse(6, blanked("", "1..4..", "..3..6", "......", "......", "5..2.."))
        assertEquals(NotationResult.Err(6, 1, "missing row 6 of 6"), err)
    }

    @Test
    fun `no rows at all errors at line 1`() {
        val err = parse(6, blanked(""))
        assertEquals(NotationResult.Err(1, 1, "missing row 1 of 6"), err)
    }

    @Test
    fun `unexpected character errors at its column`() {
        // spaces are ignored, so the col counts the raw line's characters
        val err = parse(6, blanked("", "1..4..", "..x..6", "......", "......", "5..2..", "..6..3"))
        assertEquals(NotationResult.Err(3, 3, "unexpected character 'x'"), err)
    }

    @Test
    fun `digit outside 1 to size errors at its column`() {
        val err = parse(6, blanked("", "1..4..", "..3..6", "......", "....7.", "5..2..", "..6..3"))
        assertEquals(NotationResult.Err(5, 5, "digit 7 outside 1..6"), err)
    }

    @Test
    fun `zero means empty and spaces inside a row are ignored`() {
        val ok = parse(6, blanked("", "1.. 4..", "0.3..6", "......", "......", "5..2..", "..6..3"))
        assertEquals(NotationResult.Ok(draft(6, "1..4..", "..3..6", "......", "......", "5..2..", "..6..3")), ok)
    }

    // --- print ------------------------------------------------------------------------------

    @Test
    fun `print writes digits and dots with no spaces and a header`() {
        val printed = SudokuNotation.print(draft(6, "1..4..", "..3..6", "......", "......", "5..2..", "..6..3"))
        assertEquals("puzzlebox minisudoku v1\n1..4..\n..3..6\n......\n......\n5..2..\n..6..3\n", printed)
    }

    @Test
    fun `classic prints the sudoku header`() {
        val printed = SudokuNotation.print(draft(9, "53..7....", "6..195...", ".98....6.", "8...6...3",
            "4..8.3..1", "7...2...6", ".6....28.", "...419..5", "....8..79"))
        assertEquals("puzzlebox sudoku v1", printed.lineSequence().first())
    }
}
