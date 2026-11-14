package com.ivandsky.puzzlebox.games.tango.editor

import com.ivandsky.puzzlebox.game.CommandHistory
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.ReplaceDraft
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarker
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TangoDraftCodecTest {

    private val blank = TangoDraft(6, emptyMap(), emptyList())
    private val seeded = TangoDraft(
        6,
        givens = mapOf(0 to TangoCell.SUN, 35 to TangoCell.MOON),
        markers = listOf(
            TangoMarker(2, TangoEdgeSide.DOWN, TangoMarkerKind.OPPOSITE),
            TangoMarker(7, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
        ),
    )

    private fun roundTrip(state: DraftState<TangoDraft>): DecodeResult<DraftState<TangoDraft>> =
        TangoDraftCodec.decode(TangoDraftCodec.encode(state))

    private fun assertErr(bytes: ByteArray, check: (DecodeError) -> Boolean) {
        val result = TangoDraftCodec.decode(bytes)
        assertTrue("unexpected $result", result is DecodeResult.Err && check(result.error))
    }

    @Test
    fun `a draft with ReplaceDraft history round-trips`() {
        val redo = ReplaceDraft(seeded, blank)
        val state = DraftState(seeded, CommandHistory(listOf(ReplaceDraft(blank, seeded)), listOf(redo)))

        assertEquals(DecodeResult.Ok(state), roundTrip(state))
    }

    @Test
    fun `an empty draft without history round-trips`() {
        val state = DraftState(blank)
        assertEquals(DecodeResult.Ok(state), roundTrip(state))
    }

    @Test
    fun `history keeps only the newest MAX_HISTORY steps`() {
        val steps = List(TangoDraftCodec.MAX_HISTORY + 2) { ReplaceDraft(blank, blank) }
        val state = DraftState(blank, CommandHistory(steps, emptyList()))

        val decoded = (roundTrip(state) as DecodeResult.Ok).value
        assertEquals(TangoDraftCodec.MAX_HISTORY, decoded.history.done.size)
    }

    @Test
    fun `a resize that falls off the history cap leaves v1 bytes that still decode`() {
        val blank8 = TangoDraft(8, emptyMap(), emptyList())
        val steps = listOf(ReplaceDraft(blank8, blank)) + List(TangoDraftCodec.MAX_HISTORY) { ReplaceDraft(blank, blank) }

        val bytes = TangoDraftCodec.encode(DraftState(blank, CommandHistory(steps, emptyList())))

        assertEquals(1, bytes[0].toInt())
        val decoded = (TangoDraftCodec.decode(bytes) as DecodeResult.Ok).value
        assertEquals(TangoDraftCodec.MAX_HISTORY, decoded.history.done.size)
    }

    @Test
    fun `a v2 history cut at the cap still decodes`() {
        val blank8 = TangoDraft(8, emptyMap(), emptyList())
        val steps = listOf(ReplaceDraft(blank, blank8)) + List(TangoDraftCodec.MAX_HISTORY) { ReplaceDraft(blank8, blank8) }

        val bytes = TangoDraftCodec.encode(DraftState(blank8, CommandHistory(steps, emptyList())))

        assertEquals(2, bytes[0].toInt())
        val decoded = (TangoDraftCodec.decode(bytes) as DecodeResult.Ok).value
        assertEquals(TangoDraftCodec.MAX_HISTORY, decoded.history.done.size)
    }

    @Test
    fun `an unknown version is UnsupportedVersion`() {
        val bytes = TangoDraftCodec.encode(DraftState(seeded))
        bytes[0] = (TangoDraftCodec.VERSION + 1).toByte()
        assertErr(bytes) { it == DecodeError.UnsupportedVersion(TangoDraftCodec.VERSION + 1, TangoDraftCodec.VERSION) }
    }

    @Test
    fun `truncated, trailing or off-board bytes are Malformed`() {
        val bytes = TangoDraftCodec.encode(DraftState(seeded))
        assertErr(byteArrayOf()) { it is DecodeError.Malformed }
        assertErr(bytes.copyOf(bytes.size - 1)) { it is DecodeError.Malformed }
        assertErr(bytes + 0) { it is DecodeError.Malformed }
        val offBoard = TangoDraftCodec.encode(DraftState(TangoDraft(6, emptyMap(), listOf(TangoMarker(5, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME)))))
        assertErr(offBoard) { it is DecodeError.Malformed }
    }

    private fun fixture(name: String): ByteArray =
        TangoDraftCodecTest::class.java.getResourceAsStream("/fixtures/$name")!!.use { it.readBytes() }

    /**
     * What `tango-draft-v1-commands.bin` holds (bytes written by hand from the layout): taps
     * on 3 (sun), 5 (sun, then emptied), 3 again (moon over sun); edge marks `=` then `×` on
     * 2↓, `=` on 7→, 2↓ removed. Redo stack: 7→ removed, a sun on 8. Board: moon on 3, `=` on 7→.
     */
    private fun commandsState(): DraftState<TangoDraft> {
        val done = listOf(
            TangoDraftCommand.SetCell(3, TangoCell.SUN, null),
            TangoDraftCommand.SetCell(5, TangoCell.SUN, null),
            TangoDraftCommand.SetCell(5, null, TangoCell.SUN),
            TangoDraftCommand.SetCell(3, TangoCell.MOON, TangoCell.SUN),
            TangoDraftCommand.SetMarker(2, TangoEdgeSide.DOWN, TangoMarkerKind.SAME, null),
            TangoDraftCommand.SetMarker(2, TangoEdgeSide.DOWN, TangoMarkerKind.OPPOSITE, TangoMarkerKind.SAME),
            TangoDraftCommand.SetMarker(7, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME, null),
            TangoDraftCommand.SetMarker(2, TangoEdgeSide.DOWN, null, TangoMarkerKind.OPPOSITE),
        )
        val undone = listOf(
            TangoDraftCommand.SetMarker(7, TangoEdgeSide.RIGHT, null, TangoMarkerKind.SAME),
            TangoDraftCommand.SetCell(8, TangoCell.SUN, null),
        )
        val board = TangoDraft(
            6,
            givens = mapOf(3 to TangoCell.MOON),
            markers = listOf(TangoMarker(7, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME)),
        )
        return DraftState(board, CommandHistory(done, undone))
    }

    @Test
    fun `frozen commands fixture decodes to its pinned state and re-encodes byte-identically`() {
        val bytes = fixture("tango-draft-v1-commands.bin")

        val decoded = (TangoDraftCodec.decode(bytes) as DecodeResult.Ok).value

        assertEquals(commandsState(), decoded)
        assertEquals(mapOf(3 to TangoCell.MOON), decoded.draft.givens)
        assertArrayEquals(bytes, TangoDraftCodec.encode(commandsState()))
    }

    @Test
    fun `frozen commands history undoes to a blank draft`() {
        val state = (TangoDraftCodec.decode(fixture("tango-draft-v1-commands.bin")) as DecodeResult.Ok).value

        var step = CommandHistory.Step(state.draft, state.history)
        while (step.history.canUndo) step = step.history.undo(step.state)!!

        assertEquals(blank, step.state)
    }

    /**
     * What `tango-draft-v2.bin` holds (bytes written by hand from the v2 layout): a resize from a
     * blank 6×6 to a blank 8×8, a sun on 45, `×` on 62→. Redo stack: a moon on 63. Cells 45, 62→
     * and 63 exist only on the 8×8 board.
     *
     * ```
     * 02                          version 2
     * 08 01 2d 01 01 3e 00 01     board2: size 8, given 45 = sun, marker 62 RIGHT OPPOSITE
     * 00 03                       done: 3 steps
     * 02 06 00 00 08 00 00        TAG_REPLACE blank 6×6 → blank 8×8
     * 03 2d 01 00                 TAG_SET_CELL 45 sun, was empty
     * 04 3e 00 01 ff              TAG_SET_MARKER 62 RIGHT OPPOSITE, was none
     * 00 01                       undone: 1 step
     * 03 3f 02 00                 TAG_SET_CELL 63 moon, was empty
     * ```
     */
    private fun v2State(): DraftState<TangoDraft> {
        val blank8 = TangoDraft(8, emptyMap(), emptyList())
        val done = listOf(
            ReplaceDraft(blank, blank8),
            TangoDraftCommand.SetCell(45, TangoCell.SUN, null),
            TangoDraftCommand.SetMarker(62, TangoEdgeSide.RIGHT, TangoMarkerKind.OPPOSITE, null),
        )
        val undone = listOf(TangoDraftCommand.SetCell(63, TangoCell.MOON, null))
        val board = TangoDraft(
            8,
            givens = mapOf(45 to TangoCell.SUN),
            markers = listOf(TangoMarker(62, TangoEdgeSide.RIGHT, TangoMarkerKind.OPPOSITE)),
        )
        return DraftState(board, CommandHistory(done, undone))
    }

    @Test
    fun `frozen v2 fixture decodes to its pinned state and re-encodes byte-identically`() {
        val bytes = fixture("tango-draft-v2.bin")

        assertEquals(DecodeResult.Ok(v2State()), TangoDraftCodec.decode(bytes))
        assertArrayEquals(bytes, TangoDraftCodec.encode(v2State()))
    }

    @Test
    fun `a state whose boards are all 6x6 still encodes v1 bytes`() {
        val state = DraftState(
            seeded,
            CommandHistory(listOf(ReplaceDraft(blank, seeded), seeded.cycleCell(4)), listOf(ReplaceDraft(seeded, blank))),
        )

        assertEquals(1, TangoDraftCodec.encode(state)[0].toInt())
        assertEquals(1, TangoDraftCodec.encode(DraftState(blank))[0].toInt())
    }

    @Test
    fun `a board of another size anywhere in the state encodes v2 bytes`() {
        val blank8 = TangoDraft(8, emptyMap(), emptyList())

        assertEquals(2, TangoDraftCodec.encode(DraftState(blank8))[0].toInt())
        assertEquals(2, TangoDraftCodec.encode(DraftState(blank, CommandHistory(listOf(ReplaceDraft(blank8, blank)))))[0].toInt())
        assertEquals(2, TangoDraftCodec.encode(DraftState(blank, CommandHistory(emptyList(), listOf(ReplaceDraft(blank, blank8)))))[0].toInt())
    }

    @Test
    fun `a v2 board size outside SIZES is Malformed`() {
        // Index 1 sizes the current board; 12 and 15 size the before and after boards of the resize.
        listOf(1, 12, 15).forEach { at ->
            assertErr(fixture("tango-draft-v2.bin").also { it[at] = 7 }) { it is DecodeError.Malformed }
        }
    }

    @Test
    fun `a step off the board it meets in the re-apply is Malformed`() {
        val blank8 = TangoDraft(8, emptyMap(), emptyList())
        val cellOn6 = DraftState(blank, CommandHistory(listOf(ReplaceDraft(blank8, blank), TangoDraftCommand.SetCell(40, TangoCell.SUN, null))))
        // The redo stack's last element is re-applied first: the resize to 6×6, then 5→ which has no right neighbour there.
        val markerOn6 = DraftState(
            blank8,
            CommandHistory(emptyList(), listOf(TangoDraftCommand.SetMarker(5, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME, null), ReplaceDraft(blank8, blank))),
        )
        val markerDownOn6 = DraftState(
            blank8,
            CommandHistory(emptyList(), listOf(TangoDraftCommand.SetMarker(32, TangoEdgeSide.DOWN, TangoMarkerKind.SAME, null), ReplaceDraft(blank8, blank))),
        )
        val replaceMismatch = DraftState(blank, CommandHistory(listOf(ReplaceDraft(blank, blank8))))
        val redoMismatch = DraftState(blank, CommandHistory(emptyList(), listOf(ReplaceDraft(blank8, blank))))

        listOf(cellOn6, markerOn6, markerDownOn6, replaceMismatch, redoMismatch).forEach { state ->
            assertErr(TangoDraftCodec.encode(state)) { it is DecodeError.Malformed }
        }
    }

    @Test
    fun `drafts at 4, 8 and 10 round-trip with history`() {
        listOf(4, 8, 10).forEach { n ->
            val last = n * n - 1
            val sized = TangoDraft(n, emptyMap(), emptyList())
            val marker = TangoDraftCommand.SetMarker(last - 1, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME, null)
            val cell = TangoDraftCommand.SetCell(last, TangoCell.MOON, null)
            val board = marker.apply(cell.apply(sized))
            val redo = TangoDraftCommand.SetCell(last - n, TangoCell.SUN, null)
            val undone = listOf(ReplaceDraft(redo.apply(board), blank), redo)
            val state = DraftState(board, CommandHistory(listOf(ReplaceDraft(seeded, sized), cell, marker), undone))

            val bytes = TangoDraftCodec.encode(state)

            assertEquals("n=$n", 2, bytes[0].toInt())
            assertEquals("n=$n", DecodeResult.Ok(state), TangoDraftCodec.decode(bytes))
        }
    }
}
