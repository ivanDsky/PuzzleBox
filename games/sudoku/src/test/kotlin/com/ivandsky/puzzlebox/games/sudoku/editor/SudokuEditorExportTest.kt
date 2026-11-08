package com.ivandsky.puzzlebox.games.sudoku.editor

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.SharedOpen
import com.ivandsky.puzzlebox.game.editor.SharedOpenResult
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.canShare
import com.ivandsky.puzzlebox.game.editor.encodeShare
import com.ivandsky.puzzlebox.games.sudoku.KnownSolutions
import com.ivandsky.puzzlebox.games.sudoku.SudokuDefinition
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.codec.SudokuCodec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuEditorExportTest {

    private class Board(val definition: SudokuDefinition, givens: String, solution: String) {
        val editor = SudokuEditor(definition)
        val draft = SudokuDraft(definition.variant.geometry.size, KnownSolutions.grid(givens))
        val solved = KnownSolutions.grid(solution)
    }

    private val mini = Board(SudokuGames.MINI, KnownSolutions.MINI_SAMPLE_GIVENS, KnownSolutions.MINI_SAMPLE_SOLUTION)
    private val classic =
        Board(SudokuGames.CLASSIC, KnownSolutions.CLASSIC_SAMPLE_GIVENS, KnownSolutions.CLASSIC_SAMPLE_SOLUTION)

    private fun fixture(): ByteArray =
        SudokuEditorExportTest::class.java.getResourceAsStream("/editor/sudoku-share-v1.bin")!!.use { it.readBytes() }

    @Test
    fun `toPuzzle carries the givens and the solution through SudokuCodec for both sizes`() {
        for (board in listOf(mini, classic)) {
            val puzzle = board.editor.toPuzzle(board.draft, Solution(board.solved))

            assertEquals(board.definition.id, puzzle.gameId)
            assertEquals(board.definition.variant.id, puzzle.variant)
            assertEquals(SudokuCodec.VERSION, puzzle.formatVersion)
            val decoded = (SudokuCodec.decode(puzzle.payload) as DecodeResult.Ok).value
            assertEquals(SudokuPuzzle(board.definition.variant, board.draft.givens, board.solved), decoded)
        }
    }

    @Test
    fun `a Sudoku draft can share and encodes its current board`() {
        val draft = EditorDraft(mini.editor, DraftState(mini.draft))

        assertTrue(draft.canShare)
        assertArrayEquals(fixture(), draft.encodeShare())
    }

    @Test
    fun `opening the frozen fixture yields a Mini Sudoku puzzle with its one solution`() {
        val result = SharedOpen.verify(mini.editor, mini.editor.shareFormatVersion, fixture(), Budget.Unlimited)

        val verified = result as SharedOpenResult.Verified
        val puzzle = (SudokuCodec.decode(verified.puzzle.payload) as DecodeResult.Ok).value
        assertEquals(SudokuGames.MINI.id, verified.puzzle.gameId)
        assertEquals(SudokuGames.MINI.variant.id, verified.variant)
        assertEquals(mini.draft.givens, puzzle.givens)
        assertEquals(mini.solved, puzzle.solution)
    }

    @Test
    fun `a newer share version on the fixture needs an update`() {
        val result =
            SharedOpen.verify(mini.editor, mini.editor.shareFormatVersion, fixture().also { it[0] = 2 }, Budget.Unlimited)

        assertEquals(SharedOpenResult.NeedsUpdate(found = 2, maxKnown = 1), result)
    }
}
