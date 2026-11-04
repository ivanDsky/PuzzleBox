package com.ivandsky.puzzlebox.games.zip.feed

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.FeedRequest
import com.ivandsky.puzzlebox.game.FeedTransport
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.codec.ZipCodec
import com.ivandsky.puzzlebox.games.zip.engine.ZipSolver
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wire → engine seam over the golden payload: parse to ZipWireParser's input, run the A16
 * oracle gate, store canonical ZipCodec bytes in a Puzzle envelope. The transport is a
 * fake; the network ladder itself is core/data's LinkedInFeedTransportTest.
 */
class LinkedInZipDailySourceTest {

    private val date = LocalDate.of(2026, 10, 30)

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader).getResource("fixtures/$name")!!.readText()

    private fun golden() = fixture("linkedin-zip-rsc-592.txt")

    private fun source(body: String) = LinkedInZipDailySource { FetchResult.Ok(body) }

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
        var seen: FeedRequest? = null
        LinkedInZipDailySource { request -> seen = request; FetchResult.Ok(golden()) }.fetchDaily(date)

        val request = seen!!
        assertEquals("games/zip/", request.path)
        assertEquals("trailGamePuzzle", request.objectName)
        assertNull(request.graphqlBody)
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
        val unavailable = LinkedInZipDailySource { FetchResult.Err(FetchError.Unavailable("stream transport: EOFException")) }
        val throttled = LinkedInZipDailySource { FetchResult.Err(FetchError.Throttled("throttle")) }

        assertTrue((unavailable.fetchDaily(date) as FetchResult.Err).error is FetchError.Unavailable)
        assertTrue((throttled.fetchDaily(date) as FetchResult.Err).error is FetchError.Throttled)
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
}
