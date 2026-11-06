package com.ivandsky.puzzlebox.games.zip

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.games.zip.codec.ZipCodec
import com.ivandsky.puzzlebox.games.zip.codec.ZipSessionCodec
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Zip's replay hand-off (ticket 14 → 13): the blob round-trips through Zip's own session
 * codec with THIS day's identity (ADR-0005 envelope); an unreadable payload never
 * launches (A16).
 */
class ZipReplaySessionTest {

    private val day: LocalDate = LocalDate.of(2026, 10, 31)

    private fun envelope(puzzle: ZipPuzzle = ZipTestSupport.n5Puzzle()): Puzzle = Puzzle(
        gameId = ZipDefinition.id,
        variant = puzzle.variant,
        formatVersion = ZipCodec.VERSION,
        payload = ZipCodec.encode(puzzle),
    )

    @Test
    fun `zip blob round-trips through the play session codec`() {
        val original = ZipTestSupport.n5Puzzle()
        val session = zipPlugin.replaySession(envelope(original), PuzzleKey.Daily(day, 592)).getOrThrow()

        // The envelope NAMES the game (ADR-0005) — the play route dispatches on it.
        val blob = SessionBlob.decode(session)
        assertEquals(ZipDefinition.id, blob?.game)

        val restored = ZipSessionCodec.deserialize(blob!!.session)
        assertTrue("codec must accept the blob", restored is DecodeResult.Ok)
        val zip = (restored as DecodeResult.Ok).value
        // The ref is the replayed DAY's identity, never today's (ADR-0004/A4 attribution).
        assertEquals(ZipPuzzleRef(PuzzleKey.Daily(day, 592)), zip.ref)
        assertEquals(original, zip.puzzle)
        assertTrue(!zip.isComplete)
        assertTrue(zip.path.isEmpty())
    }

    @Test
    fun `missing puzzle number still yields a valid ref`() {
        val blob = SessionBlob.decode(zipPlugin.replaySession(envelope(), PuzzleKey.Daily(day, null)).getOrThrow())
        val zip = ZipSessionCodec.deserialize(blob!!.session).getOrThrow()
        assertEquals(ZipPuzzleRef(PuzzleKey.Daily(day, null)), zip.ref)
    }

    @Test
    fun `a custom key replays carrying the friend's puzzle identity`() {
        val custom = PuzzleKey.Custom("K7Q2MX")
        val blob = SessionBlob.decode(zipPlugin.replaySession(envelope(), custom).getOrThrow())
        assertEquals(ZipPuzzleRef(custom), ZipSessionCodec.deserialize(blob!!.session).getOrThrow().ref)
    }

    @Test
    fun `unreadable zip payload never launches (A16)`() {
        val corrupt = Puzzle(
            gameId = ZipDefinition.id,
            variant = ZipTestSupport.n5Puzzle().variant,
            formatVersion = ZipCodec.VERSION,
            payload = "this is not a zip".toByteArray(Charsets.UTF_8),
        )
        val key = PuzzleKey.Daily(day, 592)
        // The version byte is the ASCII 't' the engine never shipped — the explicit
        // unsupported-version branch, not a guessed decode (ADR-0005).
        assertEquals(
            DecodeResult.Err(DecodeError.UnsupportedVersion(found = 't'.code, maxKnown = ZipCodec.VERSION)),
            zipPlugin.replaySession(corrupt, key),
        )
    }
}
