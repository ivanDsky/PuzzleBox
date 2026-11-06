package com.ivandsky.puzzlebox.games.tango.feed

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarker
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.codec.LotkaWireParser
import com.ivandsky.puzzlebox.games.tango.engine.TangoUniqueness
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The seed is the user's guaranteed-playable board while Tango auth is deferred. */
class TangoSeedBackedSourceTest {

    private val day = LocalDate.of(2026, 11, 2)

    private fun seedBacked(error: FetchError) =
        TangoSeedBackedSource(LinkedInTangoDailySource { FetchResult.Err(error) })

    @Test
    fun `lotka-shaped seed payload parses to the same uniquely solvable n6 grid`() {
        val parsed = LotkaWireParser.parse(TangoSeedBackedSource.SEED_PAYLOAD, day)
        assertTrue("expected Ok, got $parsed", parsed is DecodeResult.Ok)
        val puzzle = (parsed as DecodeResult.Ok).value.puzzle
        assertEquals("tango/n6", puzzle.variant.value)
        assertEquals(6, puzzle.n)
        // The pre-lotka seed's grid, unchanged: its canonical bytes (and any row equal to it) stay the same.
        val sun = TangoCell.SUN
        val moon = TangoCell.MOON
        assertEquals(
            mapOf(
                0 to sun, 3 to sun, 7 to moon, 10 to moon, 13 to sun, 15 to sun,
                16 to sun, 18 to sun, 21 to moon, 27 to moon, 31 to sun, 35 to moon,
            ),
            puzzle.givens,
        )
        assertEquals(
            listOf(
                TangoMarker(0, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
                TangoMarker(15, TangoEdgeSide.DOWN, TangoMarkerKind.OPPOSITE),
            ),
            puzzle.markers,
        )
        assertTrue(TangoUniqueness.isUniquelySolvable(puzzle))
    }

    @Test
    fun `unavailable primary degrades to seed daily`() = runTest {
        val result = seedBacked(FetchError.Unavailable("test: no session")).fetchDaily(day)
        assertTrue(result is FetchResult.Ok)
        assertEquals("tango/n6", (result as FetchResult.Ok).value.puzzle.variant.value)
        assertEquals(day, result.value.ref.date)
    }

    @Test
    fun `throttled primary is never masked by the seed`() = runTest {
        val result = seedBacked(FetchError.Throttled("test throttle")).fetchDaily(day)
        assertTrue((result as FetchResult.Err).error is FetchError.Throttled)
    }
}
