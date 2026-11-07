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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TangoDraftCodecTest {

    private val blank = TangoDraft(emptyMap(), emptyList())
    private val seeded = TangoDraft(
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
    fun `an unknown version is UnsupportedVersion`() {
        val bytes = TangoDraftCodec.encode(DraftState(seeded))
        bytes[0] = (TangoDraftCodec.VERSION + 1).toByte()
        assertErr(bytes) { it is DecodeError.UnsupportedVersion }
    }

    @Test
    fun `truncated, trailing or off-board bytes are Malformed`() {
        val bytes = TangoDraftCodec.encode(DraftState(seeded))
        assertErr(byteArrayOf()) { it is DecodeError.Malformed }
        assertErr(bytes.copyOf(bytes.size - 1)) { it is DecodeError.Malformed }
        assertErr(bytes + 0) { it is DecodeError.Malformed }
        val offBoard = TangoDraftCodec.encode(DraftState(TangoDraft(emptyMap(), listOf(TangoMarker(5, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME)))))
        assertErr(offBoard) { it is DecodeError.Malformed }
    }
}
