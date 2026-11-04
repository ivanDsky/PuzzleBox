package com.ivandsky.puzzlebox.games.tango.feed

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.FeedRequest
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarker
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.codec.TangoCodec
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wire → engine seam over the golden Tango payload (research 01, [INFERRED] schema):
 * parse to TangoWireParser's input, store canonical TangoCodec bytes in a Puzzle
 * envelope, and round-trip them through [TangoDefinition.decode]. The transport is a
 * fake; the network ladder itself is core/data's LinkedInFeedTransportTest.
 */
class LinkedInTangoDailySourceTest {

    private val date = LocalDate.of(2026, 11, 2)

    private fun golden() =
        requireNotNull(javaClass.classLoader).getResource("fixtures/linkedin-tango-rsc-301.txt")!!.readText()

    private fun source(body: String) = LinkedInTangoDailySource { FetchResult.Ok(body) }

    @Test
    fun `golden payload parses and stores canonical codec bytes`() = runTest {
        val result = source(golden()).fetchDaily(date)

        val daily = (result as? FetchResult.Ok)?.value
        assertTrue("expected Ok, got $result", daily != null)
        assertEquals("tango", daily!!.puzzle.gameId.value)
        assertEquals("tango/n6", daily.puzzle.variant.value)
        assertEquals(301, daily.ref.puzzleNumber)
        assertEquals(date, daily.ref.date)
        // The stored bytes decode back into the envelope — the single reader path.
        val decoded = TangoDefinition.decode(daily.puzzle.payload)
        assertTrue(decoded is DecodeResult.Ok)
    }

    @Test
    fun `fixture givens and markers decode correctly`() = runTest {
        val result = source(golden()).fetchDaily(date)

        val envelope = (result as? FetchResult.Ok)?.value!!.puzzle
        val puzzle = (TangoCodec.decode(envelope.payload) as DecodeResult.Ok).value
        assertEquals(6, puzzle.n)
        // Givens from the fixture grid (row-major): idx0=S, 3=S, 7=M, 10=M, 13=S, 15=S,
        // 16=S, 18=S, 21=M, 27=M, 31=S, 35=M. Fix-round-1: the fixture models a trusted
        // daily, so its puzzle is uniquely solvable (the old grid was unsatisfiable —
        // givens 15=S and 21=S sat on the x edge — and is now refused by the A15 gate).
        assertEquals(
            mapOf(
                0 to TangoCell.SUN,
                3 to TangoCell.SUN,
                7 to TangoCell.MOON,
                10 to TangoCell.MOON,
                13 to TangoCell.SUN,
                15 to TangoCell.SUN,
                16 to TangoCell.SUN,
                18 to TangoCell.SUN,
                21 to TangoCell.MOON,
                27 to TangoCell.MOON,
                31 to TangoCell.SUN,
                35 to TangoCell.MOON,
            ),
            puzzle.givens,
        )
        // Markers: "=" on (0,0)-(0,1) → idx0 RIGHT SAME; "×" on (2,3)-(3,3) → idx15 DOWN OPPOSITE.
        assertEquals(
            listOf(
                TangoMarker(0, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
                TangoMarker(15, TangoEdgeSide.DOWN, TangoMarkerKind.OPPOSITE),
            ).sortedWith(compareBy({ it.cell }, { it.side.ordinal })),
            puzzle.markers.sortedWith(compareBy({ it.cell }, { it.side.ordinal })),
        )
    }

    @Test
    fun `puzzle source seam returns the envelope without the ref`() = runTest {
        val result = source(golden()).fetch(date)

        val puzzle = (result as? FetchResult.Ok)?.value
        assertTrue(puzzle != null)
        assertEquals("tango/n6", puzzle!!.variant.value)
        assertEquals(TangoCodec.VERSION, puzzle.formatVersion)
    }

    @Test
    fun `feed request walks the stream then the page shell and accepts only data-bearing bodies`() = runTest {
        var seen: FeedRequest? = null
        LinkedInTangoDailySource { request -> seen = request; FetchResult.Ok(golden()) }.fetchDaily(date)

        val request = seen!!
        assertEquals("games/tango/", request.path)
        assertEquals("tangoGamePuzzle", request.objectName)
        assertNull(request.graphqlBody)
        assertTrue(request.scanPageShell)
        assertTrue(request.accepts(golden()))
        assertFalse(request.accepts("""{"tangoGamePuzzle":"mentioned, not present"}"""))
    }

    @Test
    fun `payload naming an unknown variant is Unavailable, never guessed (ADR-0005)`() = runTest {
        val unknown = """{"tangoGamePuzzle":{"gridSize":6,"variant":"tango/candidate-9","given":[],"constraints":[]}}"""

        val result = source(unknown).fetchDaily(date)

        val error = (result as? FetchResult.Err)?.error as? FetchError.Unavailable
        assertTrue(error != null)
        assertTrue(error!!.detail.orEmpty().contains("tango/candidate-9"))
    }

    @Test
    fun `structurally broken payload is Unavailable (parse family)`() = runTest {
        val result = source("""{"tangoGamePuzzle":{"gridSize":99}}""").fetchDaily(date)

        assertTrue((result as? FetchResult.Err)?.error is FetchError.Unavailable)
    }

    @Test
    fun `ambiguous payload is Untrusted, never Ok (A15 at the source boundary)`() = runTest {
        // Parses cleanly as tango/n6 (valid variant, well-formed wire) but every cell is
        // free — the solver cannot pin a unique solution. The source must refuse BEFORE
        // the offline cache ever sees the payload (Zip oracle parity); the play-time
        // gate stays as defense-in-depth.
        val ambiguous = """{"tangoGamePuzzle":{"gridSize":6,"variant":"tango/n6","given":[],"constraints":[]}}"""

        val result = source(ambiguous).fetchDaily(date)

        val error = (result as? FetchResult.Err)?.error as? FetchError.Untrusted
        assertTrue("expected Untrusted, got $result", error != null)
    }

    @Test
    fun `transport failure is Unavailable`() = runTest {
        val result = LinkedInTangoDailySource {
            FetchResult.Err(FetchError.Unavailable("stream transport: EOFException"))
        }.fetchDaily(date)

        assertTrue((result as? FetchResult.Err)?.error is FetchError.Unavailable)
    }

    @Test
    fun `body without the puzzle object is Unavailable`() = runTest {
        val error = (source("shell, no puzzle").fetchDaily(date) as FetchResult.Err).error

        assertEquals("no tangoGamePuzzle in body", (error as FetchError.Unavailable).detail)
    }
}
