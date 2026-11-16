package com.ivandsky.puzzlebox.games.sudoku.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.games.sudoku.KnownSolutions
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuVariants
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SudokuScoredGrader] against the hint ladder over the Mini Sudoku corpus (SudokuCorpusTest):
 * the 454 LinkedIn dailies and the 2000 generated boards.
 */
class SudokuScoredCorpusTest {

    @Serializable
    private data class Board(val id: String, val givens: String, val solution: String)

    /**
     * Catches a scored walk that parts from the ladder: a board the Studio accepts with no
     * label, a board that needs a guess with one, or a rung the hints never show.
     */
    @Test
    fun `the scored grader labels exactly the boards the ladder finishes, with the ladder's rung`() {
        var labelled = 0
        for (board in boards("sudoku-boards.json") + boards("sudoku-generated.json")) {
            val givens = KnownSolutions.grid(board.givens)
            val solution = KnownSolutions.grid(board.solution)
            val ladder = SudokuGrader.grade(SudokuPuzzle(SudokuVariants.MINI, givens, solution))
            val scored = SudokuScoredGrader.grade(SudokuDraft(6, givens), Solution(solution), Budget.Unlimited)

            assertEquals(board.id, if (ladder.needsGuessing) null else ladder.hardest, scored?.hardestRung)
            assertEquals(board.id, ladder.needsGuessing, scored == null)
            if (scored != null) labelled++
        }
        assertTrue("$labelled labelled", labelled > 2_300)
    }

    /** Catches bands that put the dailies in one level: most are Easy, and one in five or more is not. */
    @Test
    fun `most dailies are Easy and the sparse ones are not`() {
        val levels = boards("sudoku-boards.json").map { board ->
            checkNotNull(SudokuScoredGrader.grade(SudokuDraft(6, KnownSolutions.grid(board.givens)), Solution(KnownSolutions.grid(board.solution)), Budget.Unlimited)).level
        }
        val easy = levels.count { it == Difficulty.Level.Easy }

        assertTrue("$easy of ${levels.size} Easy", easy * 2 > levels.size && easy * 5 < levels.size * 4)
        assertEquals(Difficulty.Level.entries.toSet(), levels.toSet())
    }

    private fun boards(fixture: String): List<Board> = JSON.decodeFromString(KnownSolutions.fixture(fixture))

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
