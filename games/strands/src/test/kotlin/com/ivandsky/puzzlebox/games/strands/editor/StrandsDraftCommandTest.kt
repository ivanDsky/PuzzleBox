package com.ivandsky.puzzlebox.games.strands.editor

import com.ivandsky.puzzlebox.game.Command
import com.ivandsky.puzzlebox.game.CommandHistory
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.ReplaceDraft
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraft.Companion.BLANK
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraft.Companion.NO_SPANGRAM
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrandsDraftCommandTest {
    private val cells = StrandsDraft.ROWS * StrandsDraft.COLS
    private val first = StrandsDraftPath(listOf(0, 1, 2), "ABC")
    private val second = StrandsDraftPath(listOf(6, 7), null)
    private val third = StrandsDraftPath(listOf(12, 13, 14), "XYZ")

    private val draft = StrandsDraft(
        letters = "ABC" + BLANK.toString().repeat(cells - 3),
        clue = "old",
        paths = listOf(first, second, third),
        spangram = 2,
    )

    private fun assertReverts(command: StrandsDraftCommand, from: StrandsDraft = draft): StrandsDraft {
        val applied = command.apply(from)
        assertEquals(from, command.revert(applied))
        return applied
    }

    @Test
    fun `AddPath appends a path and revert removes it`() {
        val added = assertReverts(StrandsDraftCommand.AddPath(StrandsDraftPath(listOf(20, 21), null)))

        assertEquals(listOf(first, second, third, StrandsDraftPath(listOf(20, 21), null)), added.paths)
    }

    @Test
    fun `EditPath rewrites the path and writes its letters, revert restores the blanks`() {
        val after = StrandsDraftPath(listOf(6, 7, 8), "DOG")
        val lettersAfter = draft.letters.replaceRange(6, 9, "DOG")

        val edited = assertReverts(StrandsDraftCommand.EditPath(1, second, after, draft.letters, lettersAfter))

        assertEquals(after, edited.paths[1])
        assertEquals(lettersAfter, edited.letters)
        assertEquals(draft.letters, StrandsDraftCommand.EditPath(1, second, after, draft.letters, lettersAfter).revert(edited).letters)
    }

    @Test
    fun `RemovePath of an earlier path shifts the spangram down`() {
        val removed = assertReverts(StrandsDraftCommand.RemovePath(0, first, draft.spangram))

        assertEquals(listOf(second, third), removed.paths)
        assertEquals(1, removed.spangram)
    }

    @Test
    fun `RemovePath of a later path keeps the spangram`() {
        val withFirstSpangram = draft.copy(spangram = 0)

        val removed = assertReverts(StrandsDraftCommand.RemovePath(2, third, 0), withFirstSpangram)

        assertEquals(0, removed.spangram)
    }

    @Test
    fun `RemovePath of the spangram path clears the spangram and revert restores it`() {
        val removed = assertReverts(StrandsDraftCommand.RemovePath(2, third, 2))

        assertEquals(NO_SPANGRAM, removed.spangram)
        assertEquals(listOf(first, second), removed.paths)
    }

    @Test
    fun `SetSpangram picks the path and revert restores the previous pick`() {
        val picked = assertReverts(StrandsDraftCommand.SetSpangram(0, draft.spangram))

        assertEquals(0, picked.spangram)
    }

    @Test
    fun `SetSpangram from none reverts to none`() {
        val none = draft.copy(spangram = NO_SPANGRAM)

        assertEquals(1, assertReverts(StrandsDraftCommand.SetSpangram(1, NO_SPANGRAM), none).spangram)
    }

    @Test
    fun `SetClue replaces the clue and revert restores it`() {
        assertEquals("new", assertReverts(StrandsDraftCommand.SetClue("new", draft.clue)).clue)
    }

    private val every: List<StrandsDraftCommand> = listOf(
        StrandsDraftCommand.AddPath(StrandsDraftPath(listOf(20, 21), "Café")),
        StrandsDraftCommand.EditPath(1, second, StrandsDraftPath(listOf(6, 7, 8), "DOG"), draft.letters, draft.letters.replaceRange(6, 9, "DOG")),
        StrandsDraftCommand.RemovePath(2, third, 2),
        StrandsDraftCommand.SetSpangram(0, 2),
        StrandsDraftCommand.SetClue("Café tour", "old"),
    )

    private fun roundTrip(state: DraftState<StrandsDraft>) =
        (StrandsDraftCodec.decode(StrandsDraftCodec.encode(state)) as DecodeResult.Ok).value

    @Test
    fun `every command survives the codec in both stacks`() {
        val history: CommandHistory<StrandsDraft, Command<StrandsDraft>> =
            CommandHistory(every + ReplaceDraft(draft, draft.copy(clue = "x")), every.reversed())
        val state = DraftState(draft, history)

        assertEquals(state, roundTrip(state))
    }

    @Test
    fun `a decoded history undoes back to the start`() {
        var board = draft
        val done = mutableListOf<Command<StrandsDraft>>()
        every.forEach { command -> done += command; board = command.apply(board) }

        val decoded = roundTrip(DraftState(board, CommandHistory(done, emptyList())))

        var state = decoded.draft
        var history = decoded.history
        while (true) {
            val undone = history.undo(state) ?: break
            state = undone.state
            history = undone.history
        }
        assertEquals(draft, state)
    }

    @Test
    fun `a stored command whose board is malformed is rejected`() {
        val bytes = StrandsDraftCodec.encode(DraftState(draft, CommandHistory(listOf(StrandsDraftCommand.SetClue("a", "b")), emptyList())))

        val result = StrandsDraftCodec.decode(bytes.copyOf(bytes.size - 3))

        assertTrue(result is DecodeResult.Err && result.error is DecodeError.Malformed)
    }
}
