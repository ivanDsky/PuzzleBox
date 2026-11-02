package com.ivandsky.puzzlebox.games.queens.feed

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.games.queens.codec.QueensCodec
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.queensVariantId
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Offline replay path (queens-06 DoD): body -> extractor -> wire parser -> A13 gate ->
 * canonical codec bytes under gameId=queens — fake-backed, no network.
 */
class LinkedInQueensDailySourceTest {

    private val date = LocalDate.of(2026, 11, 2)

    private fun fixtureBody(): String =
        LinkedInQueensDailySourceTest::class.java.getResourceAsStream("/fixtures/queens-daily-graphql.json")!!
            .readBytes().decodeToString()

    private fun source(body: String) = LinkedInQueensDailySource { FetchResult.Ok(body) }

    private fun failing(error: FetchError) =
        LinkedInQueensDailySource { FetchResult.Err(error) }

    @Test
    fun `graphql fixture fetches to a queens envelope`() = runTest {
        val result = source(fixtureBody()).fetchDaily(date)
        val daily = (result as FetchResult.Ok).value
        assertEquals(QueensDefinition.id, daily.puzzle.gameId)
        assertEquals(queensVariantId(8), daily.puzzle.variant)
        assertEquals(QueensCodec.VERSION, daily.puzzle.formatVersion)
        assertEquals(date, daily.ref.date)
    }

    @Test
    fun `stored bytes decode back to the playable puzzle`() = runTest {
        val envelope = (source(fixtureBody()).fetchDaily(date) as FetchResult.Ok).value.puzzle
        val decoded = QueensCodec.decode(envelope.payload)
        assertTrue("cache bytes must decode: ${(decoded as? DecodeResult.Err)?.error}", decoded is DecodeResult.Ok)
        assertEquals(8, (decoded as DecodeResult.Ok).value.size)
        assertNotNull(decoded.value.solution)
    }

    @Test
    fun `puzzle source seam returns the envelope`() = runTest {
        val envelope = (source(fixtureBody()).fetch(date) as FetchResult.Ok).value
        assertEquals(QueensDefinition.id, envelope.gameId)
    }

    @Test
    fun `gate failure surfaces as Untrusted never playable`() = runTest {
        // Tamper the wire: move the row-1 queen onto row 0 (two queens in one row) -> gate rejects.
        val tampered = fixtureBody().replace("""{"row": 1, "col": 2}""", """{"row": 0, "col": 6}""")
        val result = source(tampered).fetchDaily(date)
        val error = (result as FetchResult.Err).error
        assertTrue("expected Untrusted, got $error", error is FetchError.Untrusted)
    }

    @Test
    fun `body without puzzle degrades to Unavailable`() = runTest {
        val error = (source("{}").fetchDaily(date) as FetchResult.Err).error
        assertTrue(error is FetchError.Unavailable)
    }

    @Test
    fun `transport error propagates typed`() = runTest {
        val error = (failing(FetchError.Throttled("soft 200")).fetchDaily(date) as FetchResult.Err).error
        assertTrue(error is FetchError.Throttled)
    }
}
