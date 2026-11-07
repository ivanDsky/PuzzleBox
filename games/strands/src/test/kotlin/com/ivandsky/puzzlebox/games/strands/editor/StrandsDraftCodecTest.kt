package com.ivandsky.puzzlebox.games.strands.editor

import com.ivandsky.puzzlebox.game.Command
import com.ivandsky.puzzlebox.game.CommandHistory
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.ReplaceDraft
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraft.Companion.BLANK
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraft.Companion.COLS
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraft.Companion.NO_SPANGRAM
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraft.Companion.ROWS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrandsDraftCodecTest {

    private val blank = StrandsDraft(BLANK.toString().repeat(ROWS * COLS), "", emptyList(), NO_SPANGRAM)

    private val traced = StrandsDraft(
        letters = "ABCDEF".repeat(ROWS),
        clue = "Café tour",
        paths = listOf(StrandsDraftPath(listOf(0, 1, 2), "ABC"), StrandsDraftPath(listOf(47, 41), null)),
        spangram = 0,
    )

    private fun roundTrip(state: DraftState<StrandsDraft>) =
        (StrandsDraftCodec.decode(StrandsDraftCodec.encode(state)) as DecodeResult.Ok).value

    private fun malformed(bytes: ByteArray) {
        val result = StrandsDraftCodec.decode(bytes)
        assertTrue("expected Malformed, got $result", result is DecodeResult.Err && result.error is DecodeError.Malformed)
    }

    @Test
    fun `a draft with ReplaceDraft history in both stacks round-trips`() {
        val state = DraftState(
            traced,
            CommandHistory(listOf(ReplaceDraft(blank, traced)), listOf(ReplaceDraft(traced, blank))),
        )

        assertEquals(state, roundTrip(state))
    }

    @Test
    fun `an empty history round-trips`() {
        assertEquals(DraftState(blank), roundTrip(DraftState(blank)))
    }

    @Test
    fun `unknown version maps to UnsupportedVersion`() {
        val bytes = StrandsDraftCodec.encode(DraftState(traced)).also { it[0] = 2 }

        assertEquals(
            DecodeResult.Err(DecodeError.UnsupportedVersion(2, StrandsDraftCodec.VERSION)),
            StrandsDraftCodec.decode(bytes),
        )
    }

    @Test
    fun `history beyond the bound keeps the newest steps`() {
        val steps: List<Command<StrandsDraft>> = List(StrandsDraftCodec.MAX_HISTORY + 1) { i ->
            ReplaceDraft(blank.copy(clue = "$i"), blank.copy(clue = "${i + 1}"))
        }

        val decoded = roundTrip(DraftState(blank, CommandHistory(steps, emptyList())))

        assertEquals(steps.drop(1), decoded.history.done)
    }

    @Test
    fun `truncated, trailing or off-board bytes are malformed`() {
        val bytes = StrandsDraftCodec.encode(DraftState(traced))

        malformed(ByteArray(0))
        malformed(bytes.copyOf(bytes.size - 1))
        malformed(bytes + 0)
        malformed(StrandsDraftCodec.encode(DraftState(traced.copy(letters = "AB"))))
        malformed(StrandsDraftCodec.encode(DraftState(traced.copy(spangram = 2))))
        malformed(StrandsDraftCodec.encode(DraftState(traced.copy(paths = listOf(StrandsDraftPath(listOf(48), null))))))
    }
}
