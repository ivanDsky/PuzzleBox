package com.ivandsky.puzzlebox.games.tango

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.games.tango.codec.TangoCodec
import com.ivandsky.puzzlebox.games.tango.codec.TangoSessionCodec
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.LocalDate
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tango's replay hand-off (tango-05): the blob round-trips through Tango's own session
 * codec with THIS day's identity (ADR-0005 envelope); an archive the A15 gate refuses
 * never launches (A16).
 */
class TangoPlayAgainSessionTest {

    private val day: LocalDate = LocalDate.of(2026, 10, 31)

    private fun envelope(puzzle: TangoPuzzle = TangoTestSupport.sample): Puzzle = Puzzle(
        gameId = TangoDefinition.id,
        variant = puzzle.variant,
        formatVersion = TangoCodec.VERSION,
        payload = TangoCodec.encode(puzzle),
    )

    private fun DecodeResult<ByteArray>.session(): TangoSession {
        val blob = SessionBlob.decode((this as DecodeResult.Ok).value)
        assertEquals(TangoDefinition.id, blob?.game)
        val restored = TangoSessionCodec.deserialize(blob!!.session)
        assertTrue("codec must accept the blob", restored is DecodeResult.Ok)
        return (restored as DecodeResult.Ok).value as TangoSession
    }

    @Test
    fun `tango blob round-trips through its session codec`() {
        val original = TangoTestSupport.sample
        val session = tangoPlugin.playAgainSession(envelope(original), PuzzleKey.Daily(day, 301)).session()

        assertEquals(TangoPuzzleRef(PuzzleKey.Daily(day, 301)), session.ref)
        assertEquals(original, session.puzzle)
        assertArrayEquals(original.markers.toTypedArray(), session.puzzle.markers.toTypedArray())
    }

    @Test
    fun `missing puzzle number still yields a valid ref`() {
        assertEquals(TangoPuzzleRef(PuzzleKey.Daily(day, null)), tangoPlugin.playAgainSession(envelope(), PuzzleKey.Daily(day, null)).session().ref)
    }

    @Test
    fun `a custom key replays carrying the friend's puzzle identity`() {
        val custom = PuzzleKey.Custom("K7Q2MX")
        assertEquals(TangoPuzzleRef(custom), tangoPlugin.playAgainSession(envelope(), custom).session().ref)
    }

    @Test
    fun `ambiguous tango archive is refused by the A15 gate (A16)`() {
        val ambiguous = TangoPuzzle(variant = TangoTestSupport.variant, n = 6, givens = emptyMap(), markers = emptyList())

        assertEquals(
            DecodeResult.Err(DecodeError.Malformed("archived puzzle is not uniquely solvable")),
            tangoPlugin.playAgainSession(envelope(ambiguous), PuzzleKey.Daily(day, 301)),
        )
    }
}
