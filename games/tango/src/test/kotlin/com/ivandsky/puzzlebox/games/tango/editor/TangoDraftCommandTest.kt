package com.ivandsky.puzzlebox.games.tango.editor

import com.ivandsky.puzzlebox.game.CommandHistory
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

/** Tango draft history on disk: the frozen v1 bytes and the commands the codec stores by tag. */
class TangoDraftCommandTest {

    private val blank = TangoDraft(emptyMap(), emptyList())

    private fun fixture(name: String): ByteArray =
        TangoDraftCommandTest::class.java.getResourceAsStream("/fixtures/$name")!!.use { it.readBytes() }

    private fun decodeOk(bytes: ByteArray): DraftState<TangoDraft> = (TangoDraftCodec.decode(bytes) as DecodeResult.Ok).value

    /** What `tango-draft-v1.bin` holds: two replace steps done, one undone, markers in (cell, side) order. */
    private fun fixtureState(): DraftState<TangoDraft> {
        val one = TangoDraft(mapOf(0 to TangoCell.SUN), emptyList())
        val board = TangoDraft(
            mapOf(0 to TangoCell.SUN, 35 to TangoCell.MOON),
            listOf(TangoMarker(2, TangoEdgeSide.DOWN, TangoMarkerKind.OPPOSITE), TangoMarker(7, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME)),
        )
        val redo = ReplaceDraft(board, board.copy(markers = board.markers.drop(1)))
        return DraftState(board, CommandHistory(listOf(ReplaceDraft(blank, one), ReplaceDraft(one, board)), listOf(redo)))
    }

    @Test
    fun `frozen v1 fixture decodes to the expected draft and both stacks`() {
        val decoded = decodeOk(fixture("tango-draft-v1.bin"))

        assertEquals(fixtureState(), decoded)
        assertTrue(decoded.history.done.all { it is ReplaceDraft<*> })
        assertTrue(decoded.history.undone.single() is ReplaceDraft<*>)
    }

    @Test
    fun `encoding the fixture state reproduces the frozen bytes`() {
        assertArrayEquals(fixture("tango-draft-v1.bin"), TangoDraftCodec.encode(fixtureState()))
    }
}
