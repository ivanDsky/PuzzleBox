package com.ivandsky.puzzlebox.games.sudoku.editor

import com.ivandsky.puzzlebox.game.CommandHistory
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.ReplaceDraft
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The frozen v1 Sudoku draft bytes keep decoding as the codec grows. */
class SudokuDraftCommandTest {

    @Test
    fun `frozen v1 fixture decodes to the pinned draft and both stacks`() {
        val decoded = decode(fixture())

        assertEquals(fixtureState(), decoded)
    }

    @Test
    fun `encoding the fixture state reproduces the frozen bytes`() {
        assertArrayEquals(fixture(), SudokuDraftCodec.encode(fixtureState()))
    }

    private fun decode(bytes: ByteArray): DraftState<SudokuDraft> {
        val result = SudokuDraftCodec.decode(bytes)
        assertTrue("expected Ok, got $result", result is DecodeResult.Ok)
        return (result as DecodeResult.Ok).value
    }

    private fun fixture(): ByteArray =
        SudokuDraftCommandTest::class.java.getResourceAsStream("/fixtures/sudoku-draft-v1.bin")!!.use { it.readBytes() }

    private fun fixtureState(): DraftState<SudokuDraft> {
        val b0 = board()
        val b1 = board(0 to 1, 7 to 2)
        val b2 = board(0 to 1, 7 to 2, 14 to 3)
        val b3 = board(7 to 2, 14 to 3)
        return DraftState(b2, CommandHistory(listOf(ReplaceDraft(b0, b1), ReplaceDraft(b1, b2)), listOf(ReplaceDraft(b2, b3))))
    }

    private fun board(vararg givens: Pair<Int, Int>): SudokuDraft {
        val cells = MutableList(36) { 0 }
        givens.forEach { (cell, digit) -> cells[cell] = digit }
        return SudokuDraft(6, cells)
    }
}
