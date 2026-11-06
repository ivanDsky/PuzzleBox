package com.ivandsky.puzzlebox.games.tango.feed

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.FeedRequest
import com.ivandsky.puzzlebox.game.FeedTransport
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.codec.LotkaWireParser
import com.ivandsky.puzzlebox.games.tango.codec.TangoCodec
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
 * Wire → engine seam over today's live LinkedIn Tango stream (#759, research 02 sample):
 * extract `lotkaGamePuzzle`, parse it, store canonical TangoCodec bytes in a Puzzle
 * envelope, and round-trip them through [TangoDefinition.decode]. The transport is a
 * fake; the network ladder itself is core/network's LinkedInFeedTransportTest. Past days
 * come by number from games-by-id (ADR-0017) over the research's captured by-id bodies.
 */
class LinkedInTangoDailySourceTest {

    private val date = LocalDate.of(2026, 11, 5)

    /** 2026-11-05 12:00 PT: today's Tango is #759. */
    private val clock = Clock.fixed(
        ZonedDateTime.of(2026, 11, 5, 12, 0, 0, 0, ZoneId.of("America/Los_Angeles")).toInstant(),
        ZoneId.of("UTC"),
    )

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader).getResource("fixtures/$name")!!.readText()

    private fun live() = fixture("01-tango-stream-today.txt")

    private fun source(body: String) = LinkedInTangoDailySource({ FetchResult.Ok(body) }, clock)

    /** A transport over [body] that records every request it is sent. */
    private class Recording(body: String) : FeedTransport {
        val requests = mutableListOf<FeedRequest>()
        private val reply = FetchResult.Ok(body)
        override suspend fun fetch(request: FeedRequest): FetchResult<String> = reply.also { requests += request }
    }

    @Test
    fun `live stream parses and stores canonical codec bytes`() = runTest {
        val result = source(live()).fetchDaily(date)

        val daily = (result as? FetchResult.Ok)?.value
        assertTrue("expected Ok, got $result", daily != null)
        assertEquals("tango", daily!!.puzzle.gameId.value)
        assertEquals("tango/n6", daily.puzzle.variant.value)
        assertEquals(759, daily.ref.puzzleNumber)
        assertEquals(date, daily.ref.date)
        // The stored bytes decode back into the envelope — the single reader path.
        val decoded = TangoDefinition.decode(daily.puzzle.payload)
        assertTrue(decoded is DecodeResult.Ok)
    }

    @Test
    fun `stored bytes carry the live givens and markers`() = runTest {
        val envelope = (source(live()).fetchDaily(date) as FetchResult.Ok).value.puzzle
        val puzzle = (TangoCodec.decode(envelope.payload) as DecodeResult.Ok).value

        assertEquals(6, puzzle.n)
        assertEquals(setOf(0, 1, 2, 7, 25, 30, 31, 32), puzzle.givens.keys)
        assertEquals(TangoCell.MOON, puzzle.givens[2])
        assertEquals(6, puzzle.markers.size)
    }

    @Test
    fun `puzzle source seam returns the envelope without the ref`() = runTest {
        val result = source(live()).fetch(date)

        val puzzle = (result as? FetchResult.Ok)?.value
        assertTrue(puzzle != null)
        assertEquals("tango/n6", puzzle!!.variant.value)
        assertEquals(TangoCodec.VERSION, puzzle.formatVersion)
    }

    @Test
    fun `feed request walks the stream then the page shell and accepts only data-bearing bodies`() = runTest {
        var seen: FeedRequest? = null
        LinkedInTangoDailySource({ request -> seen = request; FetchResult.Ok(live()) }, clock).fetchDaily(date)

        val request = seen!!
        assertEquals("games/tango/", request.path)
        assertEquals("lotkaGamePuzzle", request.objectName)
        assertNull(request.graphqlBody)
        assertTrue(request.scanPageShell)
        assertTrue(request.accepts(live()))
        assertFalse(request.accepts("""{"lotkaGamePuzzle":"mentioned, not present"}"""))
    }

    @Test
    fun `structurally broken payload is Unavailable (parse family)`() = runTest {
        val result = source("""{"lotkaGamePuzzle":{"gridSize":99}}""").fetchDaily(date)

        assertTrue((result as? FetchResult.Err)?.error is FetchError.Unavailable)
    }

    @Test
    fun `ambiguous payload is Untrusted, never Ok (A15 at the source boundary)`() = runTest {
        // Well-formed tango/n6 with a valid solution but no presets or edges: the solver
        // cannot pin a unique solution. The source must refuse BEFORE the offline cache
        // ever sees the payload (Zip oracle parity); the play-time gate stays as
        // defense-in-depth.
        val solution = TangoSeedBackedSource.SEED_PAYLOAD.substringAfter("\"solution\":").substringBefore(']') + "]"
        val ambiguous = """{"lotkaGamePuzzle":{"gridSize":6,"solution":$solution}}"""

        val result = source(ambiguous).fetchDaily(date)

        val error = (result as? FetchResult.Err)?.error as? FetchError.Untrusted
        assertTrue("expected Untrusted, got $result", error != null)
    }

    @Test
    fun `transport failure is Unavailable`() = runTest {
        val result = LinkedInTangoDailySource(
            { FetchResult.Err(FetchError.Unavailable("stream transport: EOFException")) },
            clock,
        ).fetchDaily(date)

        assertTrue((result as? FetchResult.Err)?.error is FetchError.Unavailable)
    }

    @Test
    fun `body without the puzzle object is Unavailable`() = runTest {
        val error = (source("shell, no puzzle").fetchDaily(date) as FetchResult.Err).error

        assertEquals("no lotkaGamePuzzle in body", (error as FetchError.Unavailable).detail)
    }

    @Test
    fun `archive starts at Tango #1 and numbers days from it`() {
        val tango = source(live())

        assertEquals(LocalDate.of(2024, 10, 8), tango.archiveSince)
        assertEquals(1, tango.puzzleNumber(LocalDate.of(2024, 10, 8)))
        assertEquals(759, tango.puzzleNumber(LocalDate.of(2026, 11, 5)))
        assertNull(tango.puzzleNumber(LocalDate.of(2024, 10, 7)))
    }

    @Test
    fun `past day sends the games-by-id request for its number, never accepting the stream body`() = runTest {
        val transport = Recording(fixture("linkedin-tango-byid-758.json"))
        LinkedInTangoDailySource(transport, clock).fetchPast(date.minusDays(1))

        val request = transport.requests.single()
        assertEquals("games/tango/", request.path)
        assertTrue(request.persistedQuery!!.variables.endsWith("%2C5%2C758%29)"))
        assertFalse(request.scanPageShell)
        assertFalse(request.accepts(live()))
        assertTrue(request.accepts(fixture("linkedin-tango-byid-758.json")))
    }

    @Test
    fun `by-id #758 parses, passes the uniqueness gate, and takes the requested number`() = runTest {
        val yesterday = date.minusDays(1)

        val result = source(fixture("linkedin-tango-byid-758.json")).fetchPast(yesterday)

        val daily = (result as FetchResult.Ok).value
        assertEquals(758, daily.ref.puzzleNumber)
        assertEquals(yesterday, daily.ref.date)
        val match = TangoGameExtractor.extract(fixture("linkedin-tango-byid-758.json"))!!
        val wire = (LotkaWireParser.parse(match.lotkaGamePuzzleJson, yesterday) as DecodeResult.Ok).value
        assertArrayEquals(TangoCodec.encode(wire.puzzle), daily.puzzle.payload)
    }

    @Test
    fun `by-id #1 opens on day 1`() = runTest {
        val day1 = LocalDate.of(2024, 10, 8)

        val daily = (source(fixture("linkedin-tango-byid-1.json")).fetchPast(day1) as FetchResult.Ok).value

        assertEquals(1, daily.ref.puzzleNumber)
        assertEquals("tango/n6", daily.puzzle.variant.value)
    }

    @Test
    fun `by-id body whose entityUrn is another number is Untrusted`() = runTest {
        val result = source(fixture("linkedin-tango-byid-301.json")).fetchPast(date.minusDays(1))

        assertTrue((result as FetchResult.Err).error is FetchError.Untrusted)
    }

    @Test
    fun `by-id body with a null puzzle is NotFound`() = runTest {
        val empty = fixture("linkedin-tango-byid-758.json").replace("\"gamePuzzle\":{", "\"gamePuzzle\":null,\"dropped\":{")

        val result = source(empty).fetchPast(date.minusDays(1))

        assertTrue((result as FetchResult.Err).error is FetchError.NotFound)
    }

    @Test
    fun `a number above today's or before #1 is NotFound and sends no request`() = runTest {
        val transport = Recording(fixture("linkedin-tango-byid-758.json"))
        val tango = LinkedInTangoDailySource(transport, clock)

        val tomorrow = tango.fetchPast(LocalDate.of(2026, 11, 6))
        val beforeDay1 = tango.fetchPast(LocalDate.of(2024, 10, 7))

        assertTrue((tomorrow as FetchResult.Err).error is FetchError.NotFound)
        assertTrue((beforeDay1 as FetchResult.Err).error is FetchError.NotFound)
        assertTrue(transport.requests.isEmpty())
    }
}
