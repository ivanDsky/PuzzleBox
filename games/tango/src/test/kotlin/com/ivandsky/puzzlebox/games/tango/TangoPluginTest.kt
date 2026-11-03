package com.ivandsky.puzzlebox.games.tango

import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.games.tango.engine.TangoMove
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** ZipDefinition contract surface + the full golden path demo on the bundled fixture. */
class TangoPluginTest {

    @Test
    fun `definition identity and cadence`() {
        assertEquals(com.ivandsky.puzzlebox.model.GameId("tango"), TangoDefinition.id)
        assertEquals("Tango", TangoDefinition.displayName)
        assertTrue(TangoDefinition.cadence is com.ivandsky.puzzlebox.game.Cadence.Daily)
    }

    @Test
    fun `decode accepts the bundled fixture bytes`() {
        val decoded = TangoDefinition.decode(TangoTestSupport.sampleBytes)
        assertTrue(decoded is DecodeResult.Ok)
        val puzzle = (decoded as DecodeResult.Ok).value
        assertEquals(TangoDefinition.id, puzzle.gameId)
        assertEquals(com.ivandsky.puzzlebox.game.VariantId("tango/n6"), puzzle.variant)
        assertTrue(puzzle.payload.contentEquals(TangoTestSupport.sampleBytes))
    }

    @Test
    fun `encode rejects a foreign envelope`() {
        val foreign = Puzzle(
            gameId = com.ivandsky.puzzlebox.model.GameId("zip"),
            variant = com.ivandsky.puzzlebox.game.VariantId("zip/n6"),
            formatVersion = 1,
            payload = ByteArray(0),
        )
        try {
            TangoDefinition.encode(foreign)
            throw AssertionError("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
        }
    }

    @Test
    fun `initialSession decodes a Puzzle envelope into a fresh session`() {
        val puzzle = (TangoDefinition.decode(TangoTestSupport.sampleBytes) as DecodeResult.Ok).value
        val session = (tangoPlugin.initialSession(puzzle) as DecodeResult.Ok).value
        assertEquals(TangoTestSupport.sample, session.puzzle)
        assertEquals(emptyMap<Int, TangoCell>(), session.placements)
    }

    @Test
    fun `stateOf merges givens under the player overlay`() {
        val puzzle = (TangoDefinition.decode(TangoTestSupport.sampleBytes) as DecodeResult.Ok).value
        val session = (tangoPlugin.initialSession(puzzle) as DecodeResult.Ok).value
            .withMove(TangoMove(2, TangoCell.MOON), LocalDate.of(2026, 11, 2).atStartOfDay().toInstant(java.time.ZoneOffset.UTC))
        val state = tangoPlugin.stateOf(session)
        assertEquals(TangoCell.MOON, state.grid[2])
        assertEquals(TangoCell.SUN, state.grid[0]) // given
    }
}
