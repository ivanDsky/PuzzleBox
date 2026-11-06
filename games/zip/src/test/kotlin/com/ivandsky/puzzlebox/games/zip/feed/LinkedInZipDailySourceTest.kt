package com.ivandsky.puzzlebox.games.zip.feed

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.FeedRequest
import com.ivandsky.puzzlebox.game.FeedTransport
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.codec.ZipCodec
import com.ivandsky.puzzlebox.games.zip.codec.ZipWireParser
import com.ivandsky.puzzlebox.games.zip.engine.ZipSolver
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wire → engine seam over the golden payload: parse to ZipWireParser's input, run the A16
 * oracle gate, store canonical ZipCodec bytes in a Puzzle envelope. The transport is a
 * fake; the network ladder itself is core/network's LinkedInFeedTransportTest. Past days
 * come by number from games-by-id (ADR-0017) over the research's captured by-id bodies.
 */
class LinkedInZipDailySourceTest {

    private val date = LocalDate.of(2026, 10, 30)

    /** 2026-11-05 12:00 PT: today's Zip is #598. */
    private val clock = Clock.fixed(
        ZonedDateTime.of(2026, 11, 5, 12, 0, 0, 0, ZoneId.of("America/Los_Angeles")).toInstant(),
        ZoneId.of("UTC"),
    )

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader).getResource("fixtures/$name")!!.readText()

    private fun golden() = fixture("linkedin-zip-rsc-592.txt")

    private fun source(body: String) = LinkedInZipDailySource({ FetchResult.Ok(body) }, clock)

    /** A source over [body] that records every request it sends. */
    private class Recording(body: String) : FeedTransport {
        val requests = mutableListOf<FeedRequest>()
        private val reply = FetchResult.Ok(body)
        override suspend fun fetch(request: FeedRequest): FetchResult<String> = reply.also { requests += request }
    }

    @Test
    fun `golden payload parses, passes the oracle gate, and stores canonical codec bytes`() = runTest {
        val result = source(golden()).fetchDaily(date)

        assertTrue(result is FetchResult.Ok)
        val daily = (result as FetchResult.Ok).value
        assertEquals("zip", daily.puzzle.gameId.value)
        assertEquals("zip/n6", daily.puzzle.variant.value)
        assertEquals(ZipCodec.VERSION, daily.puzzle.formatVersion)
        assertEquals(592, daily.ref.puzzleNumber)
        assertEquals(date, daily.ref.date)
        assertEquals(ZipDefinition.id, daily.ref.gameId)
        // Envelope bytes decode back to the same typed puzzle the parser produced.
        val reparsed = ZipCodec.decode(daily.puzzle.payload)
        assertTrue(reparsed is DecodeResult.Ok)
        reparsed as DecodeResult.Ok
        assertEquals(6, reparsed.value.board.n)
        assertEquals(4, reparsed.value.board.walls.size)
        assertEquals(36, reparsed.value.solution!!.size)
        // A16 double-check on the stored artifact: unique and equal to shipped.
        assertEquals(1, ZipSolver().countSolutions(reparsed.value))
    }

    @Test
    fun `puzzle source seam returns the envelope without the ref`() = runTest {
        val result = source(golden()).fetch(date)

        assertTrue(result is FetchResult.Ok)
        assertEquals("zip", (result as FetchResult.Ok).value.gameId.value)
    }

    @Test
    fun `feed request walks the stream then the page shell and accepts only data-bearing bodies`() = runTest {
        val transport = Recording(golden())
        LinkedInZipDailySource(transport, clock).fetchDaily(date)

        val request = transport.requests.single()
        assertEquals("games/zip/", request.path)
        assertEquals("trailGamePuzzle", request.objectName)
        assertNull(request.graphqlBody)
        assertNull(request.persistedQuery)
        assertTrue(request.scanPageShell)
        assertTrue(request.accepts(golden()))
        assertFalse(request.accepts(fixture("linkedin-zip-page-shell.html")))
    }

    @Test
    fun `shipped solution that breaks the rules is Untrusted, never playable`() = runTest {
        // Corrupt the shipped path's first cells: waypoint order (I4) no longer verifies.
        val body = golden().replace("\"solution\":[10,9,15", "\"solution\":[9,10,15")

        val result = source(body).fetchDaily(date)

        assertTrue(result is FetchResult.Err && (result as FetchResult.Err).error is FetchError.Untrusted)
    }

    @Test
    fun `non-unique board is Untrusted`() = runTest {
        // Remove all walls from the golden board: many Hamiltonian paths exist.
        val open = golden().substringBefore("\"walls\":") + "\"walls\":[]," +
            "\"wallHints\"" + golden().substringAfter("\"wallHints\"")

        val result = source(open).fetchDaily(date)

        assertTrue(result is FetchResult.Err && (result as FetchResult.Err).error is FetchError.Untrusted)
        assertTrue(((result as FetchResult.Err).error as FetchError.Untrusted).detail.contains("solutions"))
    }

    @Test
    fun `transport failure propagates typed`() = runTest {
        val unavailable = LinkedInZipDailySource({ FetchResult.Err(FetchError.Unavailable("stream transport: EOFException")) }, clock)
        val throttled = LinkedInZipDailySource({ FetchResult.Err(FetchError.Throttled("throttle")) }, clock)

        assertTrue((unavailable.fetchDaily(date) as FetchResult.Err).error is FetchError.Unavailable)
        assertTrue((throttled.fetchDaily(date) as FetchResult.Err).error is FetchError.Throttled)
        assertTrue((unavailable.fetchPast(date) as FetchResult.Err).error is FetchError.Unavailable)
        assertTrue((throttled.fetchPast(date) as FetchResult.Err).error is FetchError.Throttled)
    }

    @Test
    fun `body without the puzzle object is Unavailable`() = runTest {
        val error = (source(fixture("linkedin-zip-page-shell.html")).fetchDaily(date) as FetchResult.Err).error

        assertTrue(error is FetchError.Unavailable)
        assertEquals("no trailGamePuzzle in body", (error as FetchError.Unavailable).detail)
    }

    @Test
    fun `structurally broken payload is Unavailable (parse family), not Untrusted`() = runTest {
        val result = source(
            """{"trailGamePuzzle":{"gridSize":6,"orderedSequence":[]},"todaysGameEditionText":"592"}""",
        ).fetchDaily(date)

        assertTrue(result is FetchResult.Err && (result as FetchResult.Err).error is FetchError.Unavailable)
        assertNull(result.getOrNull())
    }

    @Test
    fun `archive starts at Zip #1 and numbers days from it`() {
        val zip = source(golden())

        assertEquals(LocalDate.of(2025, 3, 18), zip.archiveSince)
        assertEquals(1, zip.puzzleNumber(LocalDate.of(2025, 3, 18)))
        assertEquals(598, zip.puzzleNumber(LocalDate.of(2026, 11, 5)))
        assertNull(zip.puzzleNumber(LocalDate.of(2025, 3, 17)))
    }

    @Test
    fun `past day sends the games-by-id request for its number, never accepting the stream body`() = runTest {
        val transport = Recording(fixture("02-byid-gt6-592.json"))
        LinkedInZipDailySource(transport, clock).fetchPast(date)

        val request = transport.requests.single()
        assertEquals("games/zip/", request.path)
        assertTrue(request.persistedQuery!!.variables.endsWith("%2C6%2C592%29)"))
        assertFalse(request.scanPageShell)
        assertFalse(request.accepts(golden()))
        assertTrue(request.accepts(fixture("02-byid-gt6-592.json")))
    }

    @Test
    fun `by-id #592 passes the oracle gate and equals the live-captured #592 puzzle`() = runTest {
        val result = source(fixture("02-byid-gt6-592.json")).fetchPast(date)

        val daily = (result as FetchResult.Ok).value
        assertEquals(592, daily.ref.puzzleNumber)
        assertEquals(date, daily.ref.date)
        val live = (ZipWireParser.parse(fixture("zip-daily-592.json"), date, "592") as DecodeResult.Ok).value
        assertArrayEquals(ZipCodec.encode(live.puzzle), daily.puzzle.payload)
    }

    @Test
    fun `by-id #1 parses and passes the oracle gate`() = runTest {
        val day1 = LocalDate.of(2025, 3, 18)

        val result = source(fixture("02-byid-gt6-1.json")).fetchPast(day1)

        val daily = (result as FetchResult.Ok).value
        assertEquals(1, daily.ref.puzzleNumber)
        assertEquals(day1, daily.ref.date)
        assertEquals("zip/n6", daily.puzzle.variant.value)
    }

    @Test
    fun `by-id body whose entityUrn is another number is Untrusted`() = runTest {
        val result = source(fixture("02-byid-gt6-592.json")).fetchPast(date.minusDays(1))

        assertTrue((result as FetchResult.Err).error is FetchError.Untrusted)
    }

    @Test
    fun `by-id body with a null puzzle is NotFound`() = runTest {
        val empty = fixture("02-byid-gt6-592.json").replace("\"gamePuzzle\":{", "\"gamePuzzle\":null,\"dropped\":{")

        val result = source(empty).fetchPast(date)

        assertTrue((result as FetchResult.Err).error is FetchError.NotFound)
    }

    @Test
    fun `a number above today's is NotFound and sends no request`() = runTest {
        val transport = Recording(fixture("02-byid-gt6-592.json"))
        val zip = LinkedInZipDailySource(transport, clock)

        val tomorrow = zip.fetchPast(LocalDate.of(2026, 11, 6))
        val beforeDay1 = zip.fetchPast(LocalDate.of(2025, 3, 17))

        assertTrue((tomorrow as FetchResult.Err).error is FetchError.NotFound)
        assertTrue((beforeDay1 as FetchResult.Err).error is FetchError.NotFound)
        assertTrue(transport.requests.isEmpty())
    }
}
