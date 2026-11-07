package com.ivandsky.puzzlebox.games.sudoku.editor

import com.ivandsky.puzzlebox.game.Command
import com.ivandsky.puzzlebox.game.CommandHistory
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.ReplaceDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuDraftCodecTest {

    private fun blank(size: Int) = SudokuDraft(size, List(size * size) { 0 })

    private fun roundTrip(state: DraftState<SudokuDraft>) =
        (SudokuDraftCodec.decode(SudokuDraftCodec.encode(state)) as DecodeResult.Ok).value

    private fun malformed(bytes: ByteArray) {
        val result = SudokuDraftCodec.decode(bytes)
        assertTrue("expected Malformed, got $result", result is DecodeResult.Err && result.error is DecodeError.Malformed)
    }

    private fun resizedState(): DraftState<SudokuDraft> {
        val filled = SudokuDraft(6, listOf(1, 2) + List(34) { 0 })
        val resize = ReplaceDraft(filled, blank(9))
        val redo = ReplaceDraft(blank(9), SudokuDraft(9, List(81) { it % 10 }))
        return DraftState(blank(9), CommandHistory(listOf(ReplaceDraft(blank(6), filled), resize), listOf(redo)))
    }

    @Test
    fun `a draft with replace history in both stacks round-trips`() {
        assertEquals(resizedState(), roundTrip(resizedState()))
    }

    @Test
    fun `an empty history round-trips`() {
        val state = DraftState(SudokuDraft(6, List(36) { it % 7 }))
        assertEquals(state, roundTrip(state))
    }

    @Test
    fun `a 9x9 draft keeps its size so the Mini editor can refuse it`() {
        val decoded = roundTrip(DraftState(blank(9)))
        assertEquals(9, decoded.draft.size)
    }

    @Test
    fun `history beyond the bound keeps the newest steps`() {
        fun digits(k: Int) = SudokuDraft(6, List(36) { k % 7 })
        val count = SudokuDraftCodec.MAX_HISTORY + 1
        val steps: List<Command<SudokuDraft>> = List(count) { i -> ReplaceDraft(digits(i), digits(i + 1)) }

        val decoded = roundTrip(DraftState(digits(count), CommandHistory(steps)))
        assertEquals(steps.drop(1), decoded.history.done)
    }

    @Test
    fun `unknown version maps to UnsupportedVersion`() {
        val bytes = SudokuDraftCodec.encode(DraftState(blank(6))).also { it[0] = 2 }

        assertEquals(
            DecodeResult.Err(DecodeError.UnsupportedVersion(2, SudokuDraftCodec.VERSION)),
            SudokuDraftCodec.decode(bytes),
        )
    }

    @Test
    fun `unsupported size, digit out of range, unknown tag, truncation and trailing bytes are malformed`() {
        val bytes = SudokuDraftCodec.encode(DraftState(blank(6), CommandHistory(listOf(ReplaceDraft(blank(6), blank(6))))))

        malformed(byteArrayOf())
        malformed(bytes.copyOf().also { it[1] = 7 })
        malformed(bytes.copyOf().also { it[2] = 7 })
        malformed(bytes.copyOf().also { it[2 + 36 + 2] = 0x7F })
        malformed(bytes.copyOf(bytes.size - 1))
        malformed(bytes + 0)
    }

    @Test
    fun `history that does not chain to the board is malformed`() {
        val state = DraftState(blank(6), CommandHistory(listOf(ReplaceDraft(blank(6), blank(9)))))

        malformed(SudokuDraftCodec.encode(state))
    }
}
