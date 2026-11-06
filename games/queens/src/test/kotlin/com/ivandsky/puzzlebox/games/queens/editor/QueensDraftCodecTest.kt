package com.ivandsky.puzzlebox.games.queens.editor

import com.ivandsky.puzzlebox.game.Command
import com.ivandsky.puzzlebox.game.CommandHistory
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.ReplaceDraft
import com.ivandsky.puzzlebox.games.queens.editor.QueensDraft.Companion.UNPAINTED
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Draft codec v1 (ADR-0005): board plus bounded undo/redo history of tagged
 * [QueensDraftCommand.Paint] and [ReplaceDraft] steps (spec A5, A24, S13).
 */
class QueensDraftCodecTest {

    private fun blank(size: Int) = QueensDraft(size, List(size * size) { UNPAINTED })

    private fun decodeOk(bytes: ByteArray): DraftState<QueensDraft> =
        (QueensDraftCodec.decode(bytes) as DecodeResult.Ok).value

    private fun roundTrip(state: DraftState<QueensDraft>) = decodeOk(QueensDraftCodec.encode(state))

    private fun malformed(bytes: ByteArray) {
        val result = QueensDraftCodec.decode(bytes)
        assertTrue("expected Malformed, got $result", result is DecodeResult.Err && result.error is DecodeError.Malformed)
    }

    private fun fixture(name: String): ByteArray =
        QueensDraftCodecTest::class.java.getResourceAsStream("/fixtures/$name")!!.use { it.readBytes() }

    /**
     * What `queens-draft-v1.bin` holds: a 5×5 → 4×4 resize, then a two-cell paint, undone
     * stack holding one paint.
     */
    private fun fixtureState(): DraftState<QueensDraft> {
        val resize = ReplaceDraft(blank(5), blank(4))
        val paint = blank(4).paint(setOf(0, 1), 0)
        val board = paint.apply(resize.after)
        val redo = board.paint(setOf(2), 1)
        return DraftState(board, CommandHistory(listOf(resize, paint), listOf(redo)))
    }

    @Test
    fun `frozen v1 fixture decodes to the expected draft and both stacks`() {
        val decoded = decodeOk(fixture("queens-draft-v1.bin"))

        assertEquals(fixtureState(), decoded)
        assertEquals(listOf(0, 0) + List(14) { UNPAINTED }, decoded.draft.regions)
        assertTrue(decoded.history.done[0] is ReplaceDraft<*>)
        assertTrue(decoded.history.done[1] is QueensDraftCommand.Paint)
        assertTrue(decoded.history.undone.single() is QueensDraftCommand.Paint)
    }

    @Test
    fun `encoding the fixture state reproduces the frozen bytes`() {
        assertArrayEquals(fixture("queens-draft-v1.bin"), QueensDraftCodec.encode(fixtureState()))
    }

    @Test
    fun `decoded history undoes and redoes through every step`() {
        val state = decodeOk(fixture("queens-draft-v1.bin"))

        val redone = state.history.redo(state.draft)!!
        assertEquals(listOf(0, 0, 1) + List(13) { UNPAINTED }, redone.state.regions)
        val undone1 = redone.history.undo(redone.state)!!.let { it.history.undo(it.state)!! }
        assertEquals(blank(4), undone1.state)
        assertEquals(blank(5), undone1.history.undo(undone1.state)!!.state)
    }

    @Test
    fun `round trip keeps the draft and an empty history`() {
        val state = DraftState(blank(8))
        assertEquals(state, roundTrip(state))
    }

    @Test
    fun `history beyond the bound keeps the newest steps, oldest dropped first`() {
        val steps: List<Command<QueensDraft>> = List(QueensDraftCodec.MAX_HISTORY + 1) { i ->
            QueensDraftCommand.Paint(setOf(i % 16), i % 4, mapOf(i % 16 to UNPAINTED))
        }
        val state = DraftState(blank(4), CommandHistory(steps, steps))

        val decoded = roundTrip(state)

        assertEquals(QueensDraftCodec.MAX_HISTORY, decoded.history.done.size)
        assertEquals(steps.drop(1), decoded.history.done)
        assertEquals(QueensDraftCodec.MAX_HISTORY, decoded.history.undone.size)
        assertEquals(steps.drop(1), decoded.history.undone)
    }

    @Test
    fun `version 2 maps to UnsupportedVersion`() {
        val bytes = fixture("queens-draft-v1.bin").also { it[0] = 2 }

        val result = QueensDraftCodec.decode(bytes)

        assertEquals(DecodeResult.Err(DecodeError.UnsupportedVersion(2, QueensDraftCodec.VERSION)), result)
    }

    @Test
    fun `unknown history tag is malformed`() {
        val bytes = QueensDraftCodec.encode(DraftState(blank(4), CommandHistory(listOf(blank(4).paint(setOf(0), 0)))))
        bytes[2 + 16 + 2] = 0x7F // first done entry's tag byte

        malformed(bytes)
    }

    @Test
    fun `truncated or trailing bytes are malformed`() {
        val bytes = fixture("queens-draft-v1.bin")

        malformed(bytes.copyOf(bytes.size - 1))
        malformed(bytes + 0)
    }

    @Test
    fun `paint outside the board it applies to is malformed`() {
        // The paint sits after a 5×5 → 4×4 resize, so cell 20 only exists before it.
        val bad = QueensDraftCommand.Paint(setOf(20), 0, mapOf(20 to UNPAINTED))
        val state = DraftState(blank(4), CommandHistory(listOf(ReplaceDraft(blank(5), blank(4)), bad)))

        malformed(QueensDraftCodec.encode(state))
    }

    @Test
    fun `step count past the bound is malformed`() {
        val bytes = QueensDraftCodec.encode(DraftState(blank(4)))
        val count = QueensDraftCodec.MAX_HISTORY + 1
        bytes[2 + 16] = (count shr 8).toByte()
        bytes[2 + 16 + 1] = count.toByte()

        malformed(bytes)
    }
}
