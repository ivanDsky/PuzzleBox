package com.ivandsky.puzzlebox.games.queens.feed

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.FeedRequest
import com.ivandsky.puzzlebox.game.FeedTransport
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.games.queens.codec.QueensCodec
import com.ivandsky.puzzlebox.games.queens.codec.QueensWireParser
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.queensVariantId
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Offline replay path (queens-06 DoD): body -> extractor -> wire parser -> A13 gate ->
 * canonical codec bytes under gameId=queens — fake-backed, no network. Past days come by
 * number from games-by-id (ADR-0017) over the research's captured by-id bodies.
 */
class LinkedInQueensDailySourceTest {

    private val date = LocalDate.of(2026, 11, 2)

    /** 2026-11-05 12:00 PT: today's Queens is #919. */
    private val clock = Clock.fixed(
        ZonedDateTime.of(2026, 11, 5, 12, 0, 0, 0, ZoneId.of("America/Los_Angeles")).toInstant(),
        ZoneId.of("UTC"),
    )

    private fun fixture(name: String): String =
        LinkedInQueensDailySourceTest::class.java.getResourceAsStream("/fixtures/$name")!!
            .readBytes().decodeToString()

    private fun fixtureBody(): String = fixture("queens-daily-graphql.json")

    private fun source(body: String) = LinkedInQueensDailySource({ FetchResult.Ok(body) }, clock)

    private fun failing(error: FetchError) =
        LinkedInQueensDailySource({ FetchResult.Err(error) }, clock)

    /** A source over [body] that records every request it sends. */
    private class Recording(body: String) : FeedTransport {
        val requests = mutableListOf<FeedRequest>()
        private val reply = FetchResult.Ok(body)
        override suspend fun fetch(request: FeedRequest): FetchResult<String> = reply.also { requests += request }
    }

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
        val past = (failing(FetchError.Throttled("soft 200")).fetchPast(date) as FetchResult.Err).error
        assertTrue(past is FetchError.Throttled)
    }

    @Test
    fun `today's request is unchanged - stream then the GraphQL rung`() = runTest {
        val transport = Recording(fixtureBody())
        LinkedInQueensDailySource(transport, clock).fetchDaily(date)

        val request = transport.requests.single()
        assertEquals(LinkedInQueensDailySource.REQUEST, request)
        assertNull(request.persistedQuery)
    }

    @Test
    fun `archive starts at Queens #1 and numbers days from it`() {
        val queens = source(fixtureBody())

        assertEquals(LocalDate.of(2024, 5, 1), queens.archiveSince)
        assertEquals(1, queens.puzzleNumber(LocalDate.of(2024, 5, 1)))
        assertEquals(919, queens.puzzleNumber(LocalDate.of(2026, 11, 5)))
        assertNull(queens.puzzleNumber(LocalDate.of(2024, 4, 30)))
    }

    @Test
    fun `past day sends the games-by-id request for its number, never accepting the stream body`() = runTest {
        val transport = Recording(fixture("02-byid-gt3-916.json"))
        LinkedInQueensDailySource(transport, clock).fetchPast(date)

        val request = transport.requests.single()
        assertEquals("games/queens/", request.path)
        assertTrue(request.persistedQuery!!.variables.endsWith("%2C3%2C916%29)"))
        assertNull(request.graphqlBody)
        assertFalse(request.accepts(fixture("queens-daily-real-916.json")))
        assertTrue(request.accepts(fixture("02-byid-gt3-916.json")))
    }

    @Test
    fun `by-id #916 passes the gate and equals the live-captured #916 puzzle`() = runTest {
        val result = source(fixture("02-byid-gt3-916.json")).fetchPast(date)

        val daily = (result as FetchResult.Ok).value
        assertEquals(916, daily.ref.puzzleNumber)
        assertEquals(date, daily.ref.date)
        assertEquals(QueensDefinition.id, daily.puzzle.gameId)
        val live = QueensGameExtractor.extract(fixture("queens-daily-real-916.json"))!!
        val parsed = (QueensWireParser.parse(live.queensGamePuzzleJson, date, live.editionText) as DecodeResult.Ok).value
        assertArrayEquals(QueensCodec.encode(parsed.puzzle), daily.puzzle.payload)
    }

    @Test
    fun `by-id #1 parses and passes the gate`() = runTest {
        val day1 = LocalDate.of(2024, 5, 1)

        val daily = (source(fixture("02-byid-gt3-1.json")).fetchPast(day1) as FetchResult.Ok).value

        assertEquals(1, daily.ref.puzzleNumber)
        assertEquals(day1, daily.ref.date)
    }

    @Test
    fun `by-id #918 parses and passes the gate`() = runTest {
        val day = LocalDate.of(2026, 11, 4)

        val daily = (source(fixture("02-byid-gt3-918.json")).fetchPast(day) as FetchResult.Ok).value

        assertEquals(918, daily.ref.puzzleNumber)
        assertEquals(day, daily.ref.date)
    }

    @Test
    fun `by-id body whose entityUrn is another number is Untrusted`() = runTest {
        val result = source(fixture("02-byid-gt3-916.json")).fetchPast(date.minusDays(1))

        assertTrue((result as FetchResult.Err).error is FetchError.Untrusted)
    }

    @Test
    fun `by-id body with a null puzzle is NotFound`() = runTest {
        val empty = fixture("02-byid-gt3-916.json").replace("\"gamePuzzle\":{", "\"gamePuzzle\":null,\"dropped\":{")

        val result = source(empty).fetchPast(date)

        assertTrue((result as FetchResult.Err).error is FetchError.NotFound)
    }

    @Test
    fun `by-id board the gate rejects is Untrusted`() = runTest {
        // Tamper the shipped solution: move the row-0 queen into column 1 (column clash with row 1).
        val body = fixture("02-byid-gt3-916.json")
        val tampered = body.replaceFirst("\"col\":6,\"row\":0", "\"col\":1,\"row\":0")
        check(tampered != body)

        val result = source(tampered).fetchPast(date)

        assertTrue((result as FetchResult.Err).error is FetchError.Untrusted)
    }

    @Test
    fun `a number above today's or before #1 is NotFound and sends no request`() = runTest {
        val transport = Recording(fixture("02-byid-gt3-916.json"))
        val queens = LinkedInQueensDailySource(transport, clock)

        val tomorrow = queens.fetchPast(LocalDate.of(2026, 11, 6))
        val beforeDay1 = queens.fetchPast(LocalDate.of(2024, 4, 30))

        assertTrue((tomorrow as FetchResult.Err).error is FetchError.NotFound)
        assertTrue((beforeDay1 as FetchResult.Err).error is FetchError.NotFound)
        assertTrue(transport.requests.isEmpty())
    }
}
