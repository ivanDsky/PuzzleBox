package com.ivandsky.puzzlebox.games.queensui.trainer

import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.MarkKind
import com.ivandsky.puzzlebox.games.queens.engine.trainer.QueensTrainerMove
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueensSnippetBoardStateTest {

    private val snippets = QueensTrainerFixtures.snippets

    @Test
    fun `the board starts as the cut with Auto-cross on, whatever the player's setting`() {
        assertTrue(snippets.isNotEmpty())
        for (snippet in snippets) {
            val session = QueensSnippetBoardState(snippet).session
            val board = snippet.cut.boardBefore

            assertEquals(board.queens.toSet(), session.queens.toSet())
            assertEquals(board.manual, session.board.manual)
            assertTrue(session.autoCross)
            // A queen's row, column, color and neighbors are crossed already, as the move check reads the board.
            val crossed = session.marks.filterValues { it.kind == MarkKind.AUTO }.keys
            assertTrue("${snippet.technique.value}: a queen forces crosses", board.queens.isEmpty() || crossed.isNotEmpty())
        }
    }

    @Test
    fun `every square the board takes a tap on is one the move check accepts`() {
        var squares = 0
        for (snippet in snippets) {
            // classify throws on a square that is off the board or already holds a queen or a cross
            squares += QueensTrainerFixtures.openSquares(snippet).size
        }
        assertTrue(squares > 0)
    }

    @Test
    fun `a tap makes the move that follows the target step, and the move stays drawn`() {
        for (snippet in snippets) {
            val state = QueensSnippetBoardState(snippet)
            val cell = QueensTrainerFixtures.openSquares(snippet).keys.first()

            val move = state.tap(cell)

            assertEquals(QueensTrainerFixtures.moveAt(snippet, cell), move)
            assertEquals(move, state.played)
            when (move) {
                is QueensTrainerMove.Place -> assertTrue(Cell(cell) in state.session.queens)
                is QueensTrainerMove.Cross -> assertEquals(MarkKind.MANUAL, state.session.markAt(Cell(cell))?.kind)
                null -> throw AssertionError("an open square takes the tap")
            }
        }
    }

    @Test
    fun `one move only, and a square holding a queen or a cross takes no tap`() {
        for (snippet in snippets) {
            val open = QueensTrainerFixtures.openSquares(snippet).keys
            val fresh = QueensSnippetBoardState(snippet)
            val taken = (0 until snippet.puzzle.cellCount).filter { it !in open }

            taken.forEach { assertNull("${snippet.technique.value} square $it", fresh.tap(it)) }
            assertNull("off the board", fresh.tap(-1))
            assertNull("off the board", fresh.tap(snippet.puzzle.cellCount))
            assertNull("no move was made", fresh.played)

            assertEquals(QueensTrainerFixtures.moveAt(snippet, open.first()), fresh.tap(open.first()))
            val after = fresh.session
            open.forEach { assertNull(fresh.tap(it)) }
            assertEquals("a second tap changes nothing", after, fresh.session)
        }
    }
}
