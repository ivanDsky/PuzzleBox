package com.ivandsky.puzzlebox.games.strands.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrandsDraftAnalyzerTest {
    private val cols = StrandsDraft.COLS

    private fun rowPath(row: Int, word: String? = "W$row") = StrandsDraftPath((0 until cols).map { row * cols + it }, word)

    private fun rows(letters: List<String>) = letters.joinToString("")

    private val distinctRows = rows(List(StrandsDraft.ROWS) { "${'A' + it}".repeat(cols) })

    private fun draft(letters: String = distinctRows, paths: List<StrandsDraftPath> = List(StrandsDraft.ROWS) { rowPath(it, "${'A' + it}".repeat(cols)) }, spangram: Int = 0) =
        StrandsDraft(letters, "clue", paths, spangram)

    private fun analyze(draft: StrandsDraft, budget: Budget = Budget.Unlimited) = StrandsDraftAnalyzer.analyze(draft, budget)

    @Test
    fun `blank letters are counted`() {
        val letters = distinctRows.replaceRange(0, 3, "...")

        assertEquals(Verdict.Incomplete(IncompleteReason(StrandsDraftAnalyzer.BLANK_CELLS, 3)), analyze(draft(letters = letters)))
    }

    @Test
    fun `unnamed paths are counted`() {
        val paths = List(StrandsDraft.ROWS) { rowPath(it, word = if (it < 2) null else "${'A' + it}".repeat(cols)) }

        assertEquals(Verdict.Incomplete(IncompleteReason(StrandsDraftAnalyzer.UNNAMED, 2)), analyze(draft(paths = paths)))
    }

    @Test
    fun `a blank word counts as unnamed`() {
        val paths = List(StrandsDraft.ROWS) { rowPath(it, word = if (it < 2) " " else "${'A' + it}".repeat(cols)) }

        assertEquals(Verdict.Incomplete(IncompleteReason(StrandsDraftAnalyzer.UNNAMED, 2)), analyze(draft(paths = paths)))
    }

    @Test
    fun `a board without a spangram is Incomplete`() {
        assertEquals(
            Verdict.Incomplete(IncompleteReason(StrandsDraftAnalyzer.NO_SPANGRAM, 0)),
            analyze(draft(spangram = StrandsDraft.NO_SPANGRAM)),
        )
    }

    @Test
    fun `a board with one tiling is Unique with the word index per cell`() {
        val expected = List(StrandsDraft.ROWS * cols) { it / cols }

        assertEquals(Verdict.Unique(Solution(expected)), analyze(draft()))
    }

    @Test
    fun `two rows of one letter swap their words and are Multiple`() {
        val letters = rows(List(StrandsDraft.ROWS) { if (it < 2) "AAAAAA" else "${'A' + it}".repeat(cols) })
        val paths = List(StrandsDraft.ROWS) { rowPath(it, if (it < 2) "AAAAAA" else "${'A' + it}".repeat(cols)) }

        val multiple = analyze(draft(letters = letters, paths = paths)) as Verdict.Multiple

        val a = multiple.solutionA.values
        val b = multiple.solutionB.values
        assertEquals(List(StrandsDraft.ROWS * cols) { it / cols }, a)
        assertTrue(multiple.differingCells.isNotEmpty())
        assertEquals(a.indices.filter { a[it] != b[it] }.toSet(), multiple.differingCells)
    }

    @Test
    fun `a letter no word reaches is uncoverable`() {
        val last = StrandsDraft.ROWS * cols - 1
        val letters = distinctRows.replaceRange(last, last + 1, "Z")
        val paths = List(StrandsDraft.ROWS) { row ->
            val word = "${'A' + row}".repeat(cols)
            rowPath(row, if (row == StrandsDraft.ROWS - 1) word.dropLast(1) else word)
        }

        assertEquals(Verdict.None(setOf(last)), analyze(draft(letters = letters, paths = paths)))
    }

    @Test
    fun `a spent budget is Undetermined`() {
        assertEquals(Verdict.Undetermined, analyze(draft()) { true })
    }
}
