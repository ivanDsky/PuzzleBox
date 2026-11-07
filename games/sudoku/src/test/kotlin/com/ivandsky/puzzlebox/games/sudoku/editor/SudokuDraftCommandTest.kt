package com.ivandsky.puzzlebox.games.sudoku.editor

import com.ivandsky.puzzlebox.game.Command
import com.ivandsky.puzzlebox.game.CommandHistory
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.ReplaceDraft
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sudoku given edits as undoable commands and their draft-codec tags (spec A5). */
class SudokuDraftCommandTest {

    @Test
    fun `place sets the given and captures the previous digit`() {
        val draft = board(7 to 2)

        val command = draft.setGiven(14, 3)

        assertEquals(SudokuDraftCommand.SetGiven(14, 3, 0), command)
        assertEquals(board(7 to 2, 14 to 3), command.apply(draft))
    }

    @Test
    fun `place reverts to the draft it was built against`() {
        val draft = board(7 to 2)
        val command = draft.setGiven(14, 3)

        assertEquals(draft, command.revert(command.apply(draft)))
    }

    @Test
    fun `erase clears the given and reverts it`() {
        val draft = board(7 to 2, 14 to 3)
        val command = draft.setGiven(14, 0)

        assertEquals(SudokuDraftCommand.SetGiven(14, 0, 3), command)
        assertEquals(board(7 to 2), command.apply(draft))
        assertEquals(draft, command.revert(command.apply(draft)))
    }

    @Test
    fun `overwriting a given reverts to the old digit`() {
        val draft = board(7 to 2)
        val command = draft.setGiven(7, 5)

        assertEquals(board(7 to 5), command.apply(draft))
        assertEquals(draft, command.revert(command.apply(draft)))
    }

    @Test
    fun `history with given edits round-trips through the codec`() {
        val start = board()
        val placed = start.setGiven(0, 1)
        val erased = placed.apply(start).setGiven(0, 0)
        val replaced = ReplaceDraft(board(), board(35 to 6))
        val done = listOf<Command<SudokuDraft>>(placed, erased, replaced)
        val state = DraftState(board(35 to 6), CommandHistory(done, listOf(start.setGiven(1, 4))))

        val decoded = decode(SudokuDraftCodec.encode(state))

        assertEquals(state.draft, decoded.draft)
        assertEquals(state.history.done, decoded.history.done)
        assertEquals(state.history.undone, decoded.history.undone)
    }

    @Test
    fun `undo after a decode walks the given edits back`() {
        val start = board()
        val first = start.setGiven(3, 2)
        val afterFirst = first.apply(start)
        val second = afterFirst.setGiven(9, 5)
        val afterSecond = second.apply(afterFirst)
        val state = DraftState(afterSecond, CommandHistory(listOf<Command<SudokuDraft>>(first, second), emptyList()))

        val decoded = decode(SudokuDraftCodec.encode(state))
        val undone = decoded.history.done.asReversed().fold(decoded.draft) { d, step -> step.revert(d) }

        assertEquals(start, undone)
    }

    @Test
    fun `a given edit that does not fit the board is malformed`() {
        val state = DraftState(board(), CommandHistory(listOf<Command<SudokuDraft>>(SudokuDraftCommand.SetGiven(3, 2, 0)), emptyList()))

        val result = SudokuDraftCodec.decode(SudokuDraftCodec.encode(state))

        assertTrue(result is DecodeResult.Err && result.error is DecodeError.Malformed)
    }

    @Test
    fun `a given edit with a digit past the board size is malformed`() {
        val ok = DraftState(board(3 to 6), CommandHistory(listOf<Command<SudokuDraft>>(SudokuDraftCommand.SetGiven(3, 6, 0)), emptyList()))
        val bytes = SudokuDraftCodec.encode(ok)
        bytes[bytes.size - 4] = 7 // tail: [digit][previous][u16 0 redo steps]

        assertTrue(SudokuDraftCodec.decode(bytes) is DecodeResult.Err)
    }

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
