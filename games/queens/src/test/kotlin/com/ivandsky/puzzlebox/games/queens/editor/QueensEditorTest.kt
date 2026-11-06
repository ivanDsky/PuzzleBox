package com.ivandsky.puzzlebox.games.queens.editor

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.GameEditor
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.codec.QueensCodec
import com.ivandsky.puzzlebox.games.queens.queensVariantId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Drafts become playable puzzles (spec A10): a unique Queens draft exports through the daily
 * [QueensCodec] with its solution embedded; answer crowns never reach the puzzle (A23).
 */
class QueensEditorTest {

    private val draft = EditorDraft(QueensEditor, DraftState(QueensDraft(4, UNIQUE)))
    private val solution = (draft.analyze(Budget.Unlimited) as Verdict.Unique).solution

    @Test
    fun `a unique draft exports a Queens puzzle that decodes to its regions with the solution embedded`() {
        val puzzle = requireNotNull(draft.toPuzzle(solution))

        val decoded = (QueensCodec.decode(puzzle.payload) as DecodeResult.Ok).value
        assertEquals(QueensDefinition.id, puzzle.gameId)
        assertEquals(queensVariantId(4), puzzle.variant)
        assertEquals(QueensCodec.VERSION, puzzle.formatVersion)
        assertEquals(UNIQUE, decoded.regions)
        assertEquals(solution.values.map(::Cell), decoded.solution)
    }

    @Test
    fun `Queens can be test-solved`() {
        assertTrue(draft.canTestSolve)
    }

    @Test
    fun `an editor without DraftExport cannot be test-solved and exports nothing`() {
        val plain: GameEditor<QueensDraft> = object : GameEditor<QueensDraft> by QueensEditor {}
        val handle = EditorDraft(plain, DraftState(QueensDraft(4, UNIQUE)))

        assertFalse(handle.canTestSolve)
        assertNull(handle.toPuzzle(solution))
    }

    @Test
    fun `answer crowns never reach the puzzle`() {
        val crowned = EditorDraft(QueensEditor, DraftState(QueensDraft(4, UNIQUE, crowns = setOf(1, 7, 5))))

        assertEquals(draft.toPuzzle(solution), crowned.toPuzzle(solution))
    }

    private companion object {
        /** R0 holds B's 2 and 4 together, so only A = {1, 7, 8, 14} fits (as in QueensDraftAnalyzerTest). */
        val UNIQUE = listOf(
            0, 0, 0, 1,
            0, 0, 1, 1,
            2, 2, 2, 3,
            2, 2, 3, 3,
        )
    }
}
