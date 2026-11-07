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
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The seed is the user's guaranteed-playable board while Tango auth is deferred. */
class TangoSeedBackedSourceTest {

    private val day = LocalDate.of(2026, 11, 2)

    /** 2026-11-05 12:00 PT: today's Tango is #759. */
    private val clock = Clock.fixed(
        ZonedDateTime.of(2026, 11, 5, 12, 0, 0, 0, ZoneId.of("America/Los_Angeles")).toInstant(),
        ZoneId.of("UTC"),
    )

    private fun seedBacked(error: FetchError) =
        TangoSeedBackedSource(LinkedInTangoDailySource({ FetchResult.Err(error) }, clock))

    private fun byId758(): String =
        requireNotNull(javaClass.classLoader).getResource("fixtures/linkedin-tango-byid-758.json")!!.readText()

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
        assertEquals(day, (result.value.ref.key as PuzzleKey.Daily).date)
    }

    @Test
    fun `throttled primary is never masked by the seed`() = runTest {
        val result = seedBacked(FetchError.Throttled("test throttle")).fetchDaily(day)
        assertTrue((result as FetchResult.Err).error is FetchError.Throttled)
    }

    @Test
    fun `offline past day is Unavailable, never the seed`() = runTest {
        val result = seedBacked(FetchError.Unavailable("test: offline")).fetchPast(day)

        assertTrue("expected Err, got $result", (result as? FetchResult.Err)?.error is FetchError.Unavailable)
    }

    @Test
    fun `throttled past day stays throttled`() = runTest {
        val result = seedBacked(FetchError.Throttled("test throttle")).fetchPast(day)

        assertTrue((result as FetchResult.Err).error is FetchError.Throttled)
    }

    @Test
    fun `past day comes from the primary's by-id fetch with its number`() = runTest {
        val source = TangoSeedBackedSource(LinkedInTangoDailySource({ FetchResult.Ok(byId758()) }, clock))

        val daily = (source.fetchPast(LocalDate.of(2026, 11, 4)) as FetchResult.Ok).value

        assertEquals(758, (daily.ref.key as PuzzleKey.Daily).puzzleNumber)
    }

    @Test
    fun `archive range and numbering are the primary's`() {
        val source = seedBacked(FetchError.Unavailable("unused"))

        assertEquals(LocalDate.of(2024, 10, 8), source.archiveSince)
        assertEquals(759, source.puzzleNumber(LocalDate.of(2026, 11, 5)))
    }

    @Test
    fun `the seed is a placeholder and a fetched puzzle is not`() = runTest {
        val source = TangoSeedBackedSource(LinkedInTangoDailySource({ FetchResult.Ok(byId758()) }, clock))
        val seed = (seedBacked(FetchError.Unavailable("offline")).fetchDaily(day) as FetchResult.Ok).value.puzzle
        val fetched = (source.fetchPast(LocalDate.of(2026, 11, 4)) as FetchResult.Ok).value.puzzle

        assertTrue(source.isPlaceholder(seed))
        assertFalse(source.isPlaceholder(fetched))
    }
}
