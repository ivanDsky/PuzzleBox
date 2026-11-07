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

    @Test
    fun `the format stays version 1 with crowns`() {
        assertEquals(1, QueensDraftCodec.VERSION)
        assertEquals(1, QueensDraftCodec.encode(crownedState())[0].toInt())
    }

    /**
     * What `queens-draft-v1-crowns.bin` holds (bytes written by hand from the layout): a crowned
     * 5×5 → 4×4 resize, a seeded crown on 5, a paint of 0 and 15, a crown on painted 15, the
     * crown on 5 lifted (its paint stays); redo stack: a crowned 4×4 → 6×6 resize, then a seeded
     * crown on the 6×6. Board crowns {15}.
     */
    private fun crownedState(): DraftState<QueensDraft> {
        val resize = ReplaceDraft(blank(5).copy(regions = listOf(0) + List(24) { UNPAINTED }, crowns = setOf(0)), blank(4))
        val seed = QueensDraftCommand.Crown(5, placed = true, seed = 0)
        val paint = seed.apply(blank(4)).paint(setOf(0, 15), 1)
        val crown15 = QueensDraftCommand.Crown(15, placed = true, seed = null)
        val lift5 = QueensDraftCommand.Crown(5, placed = false, seed = null)
        val done = listOf(resize, seed, paint, crown15, lift5)
        val board = done.drop(1).fold(resize.after) { d, step -> step.apply(d) }
        val undone = listOf(QueensDraftCommand.Crown(7, placed = true, seed = 0), ReplaceDraft(board, blank(6)))
        return DraftState(board, CommandHistory(done, undone))
    }

    @Test
    fun `frozen crowned fixture decodes to its pinned state and re-encodes byte-identically`() {
        val bytes = fixture("queens-draft-v1-crowns.bin")

        val decoded = decodeOk(bytes)

        assertEquals(crownedState(), decoded)
        assertEquals(setOf(15), decoded.draft.crowns)
        assertEquals(listOf(1, UNPAINTED, UNPAINTED, UNPAINTED, UNPAINTED, 0), decoded.draft.regions.take(6))
        assertEquals(setOf(0), ((decoded.history.done[0] as ReplaceDraft<*>).before as QueensDraft).crowns)
        assertArrayEquals(bytes, QueensDraftCodec.encode(crownedState()))
    }

    @Test
    fun `crowned history undoes back to the crowned 5x5 and redoes through the 6x6 crown`() {
        val state = decodeOk(fixture("queens-draft-v1-crowns.bin"))

        var undo = CommandHistory.Step(state.draft, state.history)
        while (undo.history.canUndo) undo = undo.history.undo(undo.state)!!
        assertEquals(setOf(0), undo.state.crowns)
        assertEquals(5, undo.state.size)

        val first = state.history.redo(state.draft)!!
        val second = first.history.redo(first.state)!!
        assertEquals(6, second.state.size)
        assertEquals(setOf(7), second.state.crowns)
        assertEquals(0, second.state.regions[7])
    }

    @Test
    fun `a crowned draft with crown and crowned replace steps in both stacks round-trips`() {
        val crowned = blank(4).copy(crowns = setOf(2))
        val state = DraftState(
            crowned.copy(crowns = setOf(2, 8)),
            CommandHistory(
                listOf(ReplaceDraft(blank(6), crowned), QueensDraftCommand.Crown(8, placed = true, seed = null)),
                listOf(QueensDraftCommand.Crown(1, placed = false, seed = null), ReplaceDraft(crowned, blank(5).copy(crowns = setOf(3)))),
            ),
        )

        assertEquals(state, roundTrip(state))
    }

    @Test
    fun `a crown-free draft writes no crowns section`() {
        assertEquals(1 + 1 + 16 + 2 + 2, QueensDraftCodec.encode(DraftState(blank(4))).size)
    }

    @Test
    fun `a truncated crowns section is malformed`() {
        val bytes = fixture("queens-draft-v1-crowns.bin")

        malformed(bytes.copyOf(bytes.size - 1)) // top-level section: k = 1, cell missing
        malformed(bytes.copyOf(bytes.size - 2) + 0) // top-level section present with k = 0
        malformed(bytes.copyOf(bytes.size - 3)) // redo replace: after-board crowns missing
    }

    @Test
    fun `crowns off the board, out of order or on a replace without crowns are malformed`() {
        malformed(fixture("queens-draft-v1-crowns.bin").also { it[it.size - 1] = 16 }) // cell 16 on a 4×4
        val twoCrowns = QueensDraftCodec.encode(DraftState(blank(4).copy(crowns = setOf(3, 9))))
        malformed(twoCrowns.also { it[it.size - 2] = 9; it[it.size - 1] = 3 })

        // A crown-free resize re-spelled as TAG_REPLACE_CROWNED with two empty crown lists.
        val plain = QueensDraftCodec.encode(DraftState(blank(4), CommandHistory(listOf(ReplaceDraft(blank(5), blank(4))))))
        val tag = 2 + 16 + 2
        val before = plain.copyOfRange(tag + 1, tag + 1 + 26)
        val after = plain.copyOfRange(tag + 1 + 26, tag + 1 + 26 + 17)
        malformed(plain.copyOf(tag) + 0x04 + before + 0 + after + 0 + plain.copyOfRange(plain.size - 2, plain.size))
    }

    @Test
    fun `a crown removal carrying a seed, a bad placed flag or an off-board seed is malformed`() {
        val crown = QueensDraftCodec.encode(DraftState(blank(4), CommandHistory(listOf(QueensDraftCommand.Crown(3, placed = true, seed = 0)))))
        val tag = 2 + 16 + 2

        malformed(crown.copyOf().also { it[tag + 2] = 0 }) // removal with seed 0
        malformed(crown.copyOf().also { it[tag + 2] = 2 }) // placed flag 2
        malformed(crown.copyOf().also { it[tag + 3] = 4 }) // seed 4 on a 4×4
    }
}
