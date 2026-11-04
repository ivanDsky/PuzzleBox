package com.ivandsky.puzzlebox.games.queens

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.games.queens.codec.QueensCodec
import com.ivandsky.puzzlebox.games.queens.codec.QueensSessionCodec
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Queens' replay hand-off: the blob round-trips through its own session codec with the day's identity. */
class QueensReplaySessionTest {

    private val day: LocalDate = LocalDate.of(2026, 11, 1)

    @Test
    fun `queens blob round-trips through its session codec`() {
        val original = QueensTestSupport.n4Puzzle()
        val cached = Puzzle(QueensDefinition.id, queensVariantId(4), QueensCodec.VERSION, QueensCodec.encode(original))

        val blob = SessionBlob.decode((queensPlugin.replaySession(cached, day, 916) as DecodeResult.Ok).value)
        assertEquals(QueensDefinition.id, blob?.game)

        val restored = QueensSessionCodec.deserialize(blob!!.session)
        assertTrue("codec must accept the blob", restored is DecodeResult.Ok)
        val session = (restored as DecodeResult.Ok).value
        assertEquals(QueensPuzzleRef(day, 916), session.ref)
        assertEquals(original, session.puzzle)
        assertTrue(session.queens.isEmpty())
    }
}
