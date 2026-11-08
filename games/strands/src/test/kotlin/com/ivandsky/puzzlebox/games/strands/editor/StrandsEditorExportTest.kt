package com.ivandsky.puzzlebox.games.strands.editor

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.games.strands.StrandsPuzzle
import com.ivandsky.puzzlebox.games.strands.codec.StrandsCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrandsEditorExportTest {
    private val cols = StrandsDraft.COLS

    private fun word(row: Int) = "${'A' + row}".repeat(cols)

    private val letters = List(StrandsDraft.ROWS) { word(it) }.joinToString("")

    /** Row 3 is drawn right to left, row 1 is the spangram. */
    private fun rowCells(row: Int) = (0 until cols).map { row * cols + it }.let { if (row == 3) it.reversed() else it }

    private val draft = StrandsDraft(
        letters = letters,
        clue = "clue",
        paths = List(StrandsDraft.ROWS) { StrandsDraftPath(rowCells(it), word(it)) },
        spangram = 1,
    )

    private fun exported(draft: StrandsDraft = this.draft): StrandsPuzzle {
        val verdict = StrandsEditor.analyze(draft, Budget.Unlimited)
        val solution = (verdict as Verdict.Unique).solution
        val puzzle = StrandsEditor.toPuzzle(draft, solution)
        assertEquals(StrandsDefinition.id, puzzle.gameId)
        assertEquals(StrandsCodec.VERSION, puzzle.formatVersion)
        return (StrandsCodec.decode(puzzle.payload) as DecodeResult.Ok).value
    }

    private fun coords(cells: List<Int>) = cells.map { Coord(it / cols, it % cols) }

    @Test
    fun `the exported puzzle carries the drawn paths and the spangram`() {
        val puzzle = exported()

        assertEquals(letters, puzzle.letters)
        assertEquals("clue", puzzle.clue)
        assertEquals(word(1), puzzle.spangram.text)
        assertEquals(coords(rowCells(1)), puzzle.spangram.cells)
        assertEquals((0 until StrandsDraft.ROWS).filter { it != 1 }.map(::word), puzzle.themeWords.map { it.text })
        assertEquals(coords(rowCells(3)), puzzle.themeWords.first { it.text == word(3) }.cells)
    }

    @Test
    fun `valid words contain every theme word and the spangram`() {
        val puzzle = exported()

        assertTrue(puzzle.validWords.containsAll(puzzle.themeWords.map { it.text }))
        assertTrue(word(1) in puzzle.validWords)
    }

    @Test
    fun `a path drawn short of its word takes its cells from the tiling`() {
        val short = draft.copy(paths = draft.paths.mapIndexed { i, p -> if (i == 2) p.copy(cells = p.cells.dropLast(1)) else p })

        val puzzle = exported(short)

        assertEquals(coords(rowCells(2)), puzzle.themeWords.first { it.text == word(2) }.cells)
    }
}
