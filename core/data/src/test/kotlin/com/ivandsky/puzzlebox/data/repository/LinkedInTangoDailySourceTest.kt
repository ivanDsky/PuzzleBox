package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.data.network.LinkedInTangoApi
import com.ivandsky.puzzlebox.data.network.LinkedInTangoClient
import com.ivandsky.puzzlebox.data.network.retrofit
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarker
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.codec.TangoCodec
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Wire → engine seam over the golden Tango payload (research 01, [INFERRED] schema):
 * parse to TangoWireParser's input, store canonical TangoCodec bytes in a Puzzle
 * envelope, and round-trip them through [TangoDefinition.decode].
 */
class LinkedInTangoDailySourceTest {

    private lateinit var server: MockWebServer
    private lateinit var source: LinkedInTangoDailySource
    private val date = LocalDate.of(2026, 11, 2)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val json = Json { ignoreUnknownKeys = true }
        val retrofit = retrofit(
            baseUrl = server.url("/").toString(),
            client = OkHttpClient(),
            json = json,
        )
        source = LinkedInTangoDailySource(LinkedInTangoClient(retrofit.create(LinkedInTangoApi::class.java)))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun ok(body: String) = MockResponse().setResponseCode(200).setBody(body)

    private fun golden() =
        requireNotNull(javaClass.classLoader).getResource("fixtures/linkedin-tango-rsc-301.txt")!!.readText()

    @Test
    fun `golden payload parses and stores canonical codec bytes`() = runBlocking {
        server.enqueue(ok(golden()))

        val result = source.fetchDaily(date)

        val daily = (result as? FetchResult.Ok)?.value
        assertTrue("expected Ok, got $result", daily != null)
        assertEquals("tango", daily!!.puzzle.gameId.value)
        assertEquals("tango/n6", daily.puzzle.variant.value)
        assertEquals(301, daily.ref.puzzleNumber)
        assertEquals(date, daily.ref.date)
        // The stored bytes decode back into the envelope — the single reader path.
        val decoded = TangoDefinition.decode(daily.puzzle.payload)
        assertTrue(decoded is com.ivandsky.puzzlebox.game.DecodeResult.Ok)
    }

    @Test
    fun `fixture givens and markers decode correctly`() = runBlocking {
        server.enqueue(ok(golden()))

        val result = source.fetchDaily(date)

        val envelope = (result as? FetchResult.Ok)?.value!!.puzzle
        val puzzle = (TangoCodec.decode(envelope.payload) as com.ivandsky.puzzlebox.game.DecodeResult.Ok).value
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
    fun `puzzle source seam returns the envelope without the ref`() = runBlocking {
        server.enqueue(ok(golden()))

        val result = source.fetch(date)

        val puzzle = (result as? FetchResult.Ok)?.value
        assertTrue(puzzle != null)
        assertEquals("tango/n6", puzzle!!.variant.value)
        assertEquals(TangoCodec.VERSION, puzzle.formatVersion)
    }

    @Test
    fun `payload naming an unknown variant is Unavailable, never guessed (ADR-0005)`() = runBlocking {
        val unknown = """{"tangoGamePuzzle":{"gridSize":6,"variant":"tango/candidate-9","given":[],"constraints":[]}}"""
        server.enqueue(ok(unknown))

        val result = source.fetchDaily(date)

        val error = (result as? FetchResult.Err)?.error as? FetchError.Unavailable
        assertTrue(error != null)
        assertTrue(error!!.detail.orEmpty().contains("tango/candidate-9"))
    }

    @Test
    fun `structurally broken payload is Unavailable (parse family)`() = runBlocking {
        server.enqueue(ok("""{"tangoGamePuzzle":{"gridSize":99}}"""))

        val result = source.fetchDaily(date)

        assertTrue((result as? FetchResult.Err)?.error is FetchError.Unavailable)
    }

    @Test
    fun `ambiguous payload is Untrusted, never Ok (A15 at the source boundary)`() = runBlocking {
        // Parses cleanly as tango/n6 (valid variant, well-formed wire) but every cell is
        // free — the solver cannot pin a unique solution. The source must refuse BEFORE
        // the offline cache ever sees the payload (Zip oracle parity); the play-time
        // gate stays as defense-in-depth.
        val ambiguous = """{"tangoGamePuzzle":{"gridSize":6,"variant":"tango/n6","given":[],"constraints":[]}}"""
        server.enqueue(ok(ambiguous))

        val result = source.fetchDaily(date)

        val error = (result as? FetchResult.Err)?.error as? FetchError.Untrusted
        assertTrue("expected Untrusted, got $result", error != null)
    }

    @Test
    fun `transport failure is Unavailable`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(ok("shell, no puzzle"))

        val result = source.fetchDaily(date)

        assertTrue((result as? FetchResult.Err)?.error is FetchError.Unavailable)
    }
}
