package com.ivandsky.puzzlebox.games.sudoku.engine.trainer

import com.ivandsky.puzzlebox.game.trainer.BoardKind
import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuPracticeReviewEndTest {

    private val reviews = SudokuTrainerFixtures.starts.filter { it.kind == BoardKind.Review }

    @Test
    fun `a trained step lies ahead of every review start, and none lies ahead once the walk has made the last one`() {
        for (start in reviews) {
            assertTrue("${start.name} ${start.technique.value}", SudokuPractice.trainedAhead(start.puzzle, start.board, start.technique))
            var board: SudokuBoard = start.board
            var last = board
            while (SudokuPractice.trainedAhead(start.puzzle, board, start.technique)) {
                last = board
                val shown = checkNotNull(SudokuPractice.showMe(start.puzzle, board))
                board = SudokuPractice.commands(board, shown.step).fold(board) { acc, command -> command.apply(acc) }
            }
            // The last board with a trained step ahead made that step, or a step on the way to it.
            assertTrue("${start.name} ${start.technique.value}", last != board)
        }
    }

    @Test
    fun `a review ends a few moves after its last trained step, not at the next rung`() {
        var shorter = 0
        for (start in reviews) {
            var board: SudokuBoard = start.board
            var sinceTrained = 0
            var moves = 0
            while (!SudokuPractice.isDone(start.puzzle, board, start.technique, start.kind, sinceTrained)) {
                val shown = checkNotNull(SudokuPractice.showMe(start.puzzle, board))
                board = SudokuPractice.commands(board, shown.step).fold(board) { acc, command -> command.apply(acc) }
                sinceTrained = if (shown.rung == start.technique) 0 else sinceTrained + 1
                moves++
            }
            val label = "${start.name} ${start.technique.value}"
            assertFalse("$label: nothing trained is left", SudokuPractice.trainedAhead(start.puzzle, board, start.technique))
            assertTrue("$label: $sinceTrained moves since the last trained step", sinceTrained <= SudokuPractice.REVIEW_GRACE)
            var oldMoves = 0
            var old: SudokuBoard = start.board
            while (!SudokuPractice.isDone(start.puzzle, old, start.technique, start.kind)) {
                val shown = checkNotNull(SudokuPractice.showMe(start.puzzle, old))
                old = SudokuPractice.commands(old, shown.step).fold(old) { acc, command -> command.apply(acc) }
                oldMoves++
            }
            assertTrue("$label: $moves moves against the old $oldMoves", moves <= oldMoves)
            if (moves < oldMoves) shorter++
        }
        assertTrue("some review ends sooner than before", shorter > 0)
    }

    @Test
    fun `a focus board ends as it did, whatever the moves since a trained step`() {
        for (start in SudokuTrainerFixtures.starts.filter { it.kind == BoardKind.Focus }) {
            assertEquals(
                SudokuPractice.isDone(start.puzzle, start.board, start.technique, start.kind),
                SudokuPractice.isDone(start.puzzle, start.board, start.technique, start.kind, 100),
            )
        }
    }
}
