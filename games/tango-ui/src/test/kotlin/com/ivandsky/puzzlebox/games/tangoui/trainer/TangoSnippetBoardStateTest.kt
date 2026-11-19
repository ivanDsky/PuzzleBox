package com.ivandsky.puzzlebox.games.tangoui.trainer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TangoSnippetBoardStateTest {

    private val snippets = TangoTrainerFixtures.snippets

    @Test
    fun `the board starts as the cut, givens and walked cells in, the target open`() {
        assertTrue(snippets.isNotEmpty())
        for (snippet in snippets) {
            val state = TangoSnippetBoardState(snippet)

            assertEquals(snippet.cut.gridBefore, state.grid)
            assertNull(state.played)
            assertTrue(snippet.puzzle.givens.all { (idx, value) -> state.grid[idx] == value })
            assertNull(state.grid[snippet.cut.step.idx])
        }
    }

    @Test
    fun `every cell the board takes a tap on is one the move check accepts`() {
        var cells = 0
        for (snippet in snippets) {
            // classify throws on a cell that is off the board or already filled
            cells += TangoTrainerFixtures.openCells(snippet).size
        }
        assertTrue(cells > 0)
    }

    @Test
    fun `a tap makes the move that follows the target step, and the move stays drawn`() {
        for (snippet in snippets) {
            val state = TangoSnippetBoardState(snippet)
            val idx = TangoTrainerFixtures.openCells(snippet).keys.first()

            val move = state.tap(idx)

            assertEquals(TangoTrainerFixtures.moveAt(snippet, idx), move)
            assertEquals(move, state.played)
            assertEquals(snippet.cut.step.value, state.grid[idx])
            assertEquals(
                "the rest of the grid is the cut's own",
                snippet.cut.gridBefore.mapIndexed { i, cell -> if (i == idx) snippet.value else cell },
                state.grid,
            )
        }
    }

    @Test
    fun `one move only, and a given, a filled or an off-board cell takes no tap`() {
        for (snippet in snippets) {
            val open = TangoTrainerFixtures.openCells(snippet).keys
            val fresh = TangoSnippetBoardState(snippet)
            val taken = (0 until snippet.puzzle.cellCount).filter { it !in open }

            assertTrue("the cut holds the givens", snippet.puzzle.givens.keys.all { it in taken })
            taken.forEach { assertNull("${snippet.technique.value} cell $it", fresh.tap(it)) }
            assertNull("off the board", fresh.tap(-1))
            assertNull("off the board", fresh.tap(snippet.puzzle.cellCount))
            assertNull("no move was made", fresh.played)

            assertEquals(TangoTrainerFixtures.moveAt(snippet, open.first()), fresh.tap(open.first()))
            val after = fresh.grid
            open.forEach { assertNull(fresh.tap(it)) }
            assertEquals("a second tap changes nothing", after, fresh.grid)
        }
    }
}
