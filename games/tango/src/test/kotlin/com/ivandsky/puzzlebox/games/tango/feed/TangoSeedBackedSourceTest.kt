package com.ivandsky.puzzlebox.games.tango.feed

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.games.tango.codec.TangoWireParser
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
    fun `seed payload parses to a uniquely solvable n6 puzzle`() {
        val parsed = TangoWireParser.parse(TangoSeedBackedSource.SEED_PAYLOAD, day)
        assertTrue(parsed is DecodeResult.Ok)
        val daily = (parsed as DecodeResult.Ok).value
        assertEquals("tango/n6", daily.puzzle.variant.value)
        assertEquals(6, daily.puzzle.n)
        assertEquals(12, daily.puzzle.givens.size)
        assertTrue(TangoUniqueness.isUniquelySolvable(daily.puzzle))
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
