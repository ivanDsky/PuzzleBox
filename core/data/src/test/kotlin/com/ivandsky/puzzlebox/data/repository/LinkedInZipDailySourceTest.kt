package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.data.network.LinkedInZipClient
import com.ivandsky.puzzlebox.data.network.LinkedInZipApi
import com.ivandsky.puzzlebox.data.network.retrofit
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.games.zip.codec.ZipCodec
import com.ivandsky.puzzlebox.games.zip.engine.ZipSolver
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Wire → engine seam over the golden payload: parse to ZipWireParser's input, run the A16
 * oracle gate, store canonical ZipCodec bytes in a Puzzle envelope.
 */
class LinkedInZipDailySourceTest {

    private lateinit var server: MockWebServer
    private lateinit var source: LinkedInZipDailySource
    private val date = LocalDate.of(2026, 10, 30)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val json = Json { ignoreUnknownKeys = true }
        val api = retrofit(
            baseUrl = server.url("/").toString(),
            client = OkHttpClient(),
            json = json,
        ).create(LinkedInZipApi::class.java)
        source = LinkedInZipDailySource(LinkedInZipClient(api))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun ok(body: String) = MockResponse().setResponseCode(200).setBody(body)

    private fun golden() =
        requireNotNull(javaClass.classLoader).getResource("fixtures/linkedin-zip-rsc-592.txt")!!.readText()

    @Test
    fun `golden payload parses, passes the oracle gate, and stores canonical codec bytes`() = runBlocking {
        server.enqueue(ok(golden()))

        val result = source.fetchDaily(date)

        assertTrue(result is FetchResult.Ok)
        val daily = (result as FetchResult.Ok).value
        assertEquals("zip", daily.puzzle.gameId.value)
        assertEquals("zip/n6", daily.puzzle.variant.value)
        assertEquals(ZipCodec.VERSION, daily.puzzle.formatVersion)
        assertEquals(592, daily.ref.puzzleNumber)
        assertEquals(date, daily.ref.date)
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
    fun `puzzle source seam returns the envelope without the ref`() = runBlocking {
        server.enqueue(ok(golden()))

        val result = source.fetch(date)

        assertTrue(result is FetchResult.Ok)
        assertEquals("zip", (result as FetchResult.Ok).value.gameId.value)
    }

    @Test
    fun `shipped solution that breaks the rules is Untrusted, never playable`() = runBlocking {
        // Corrupt the shipped path's first cells: waypoint order (I4) no longer verifies.
        server.enqueue(
            ok(
                golden().replace(
                    "\"solution\":[10,9,15",
                    "\"solution\":[9,10,15",
                ),
            ),
        )

        val result = source.fetchDaily(date)

        assertTrue(result is FetchResult.Err && (result as FetchResult.Err).error is FetchError.Untrusted)
    }

    @Test
    fun `non-unique board is Untrusted`() = runBlocking {
        // Remove all walls from the golden board: many Hamiltonian paths exist.
        val open = golden().substringBefore("\"walls\":") + "\"walls\":[]," +
            "\"wallHints\"" + golden().substringAfter("\"wallHints\"")
        server.enqueue(ok(open))

        val result = source.fetchDaily(date)

        assertTrue(result is FetchResult.Err && (result as FetchResult.Err).error is FetchError.Untrusted)
        assertTrue(((result as FetchResult.Err).error as FetchError.Untrusted).detail.contains("solutions"))
    }

    @Test
    fun `transport failure is Unavailable`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val result = source.fetchDaily(date)

        assertTrue(result is FetchResult.Err && (result as FetchResult.Err).error is FetchError.Unavailable)
    }

    @Test
    fun `structurally broken payload is Unavailable (parse family), not Untrusted`() = runBlocking {
        server.enqueue(ok("""{"trailGamePuzzle":{"gridSize":6,"orderedSequence":[]},"todaysGameEditionText":"592"}"""))

        val result = source.fetchDaily(date)

        assertTrue(result is FetchResult.Err && (result as FetchResult.Err).error is FetchError.Unavailable)
        assertNull(result.getOrNull())
    }
}
