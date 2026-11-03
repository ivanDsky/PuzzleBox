package com.ivandsky.puzzlebox.data.network

import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import kotlinx.coroutines.runBlocking
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

/** The flight-GET ladder (research R-19-1) on the Tango route, against MockWebServer. */
class LinkedInTangoClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: LinkedInTangoClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val retrofit = retrofit(
            baseUrl = server.url("/").toString(),
            client = OkHttpClient(),
            json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true },
        )
        client = LinkedInTangoClient(retrofit.create(LinkedInTangoApi::class.java))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun ok(body: String) = MockResponse().setResponseCode(200).setBody(body)

    /** R-19-1 throttle signature: 200, zero-byte body, soft-redirect `location`. */
    private fun throttled() = MockResponse().setResponseCode(200)
        .addHeader("location", "https://www.linkedin.com/games/")

    private fun dataBearing(): String =
        """{"tangoGamePuzzle":{"gridSize":6,"given":[]}}"""

    @Test
    fun `flight GET is the primary request and carries the route-context headers`() = runBlocking {
        server.enqueue(ok(dataBearing()))

        val result = client.fetchDailyBody()

        assertTrue(result is FetchResult.Ok)
        assertEquals(1, server.requestCount) // data-bearing stream: no fallback requests
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/games/tango/?skipStartScreen=true", request.path)
        assertEquals("true", request.getHeader("x-li-rsc-stream"))
        assertEquals("/games/tango/", request.getHeader("x-li-initial-url"))
        assertNull(request.getHeader("csrf-token")) // cookieless/csrf-free route (R-19-1)
    }

    @Test
    fun `data-less stream falls back to the page body scan`() = runBlocking {
        server.enqueue(ok("""{"tangoGamePuzzle":"mentioned, not present"}"""))
        server.enqueue(ok("""{"tangoGamePuzzle":{"gridSize":6}}"""))

        val result = client.fetchDailyBody()

        assertTrue(result is FetchResult.Ok)
        assertEquals(2, server.requestCount)
        assertEquals("/games/tango/?skipStartScreen=true", server.takeRequest().path)
        assertEquals("/games/tango/", server.takeRequest().path)
    }

    @Test
    fun `empty 200 with location is Throttled, not Unavailable`() = runBlocking {
        server.enqueue(throttled())
        server.enqueue(ok("shell, no puzzle"))

        val result = client.fetchDailyBody()

        val error = (result as? FetchResult.Err)?.error
        assertTrue("expected Throttled, got $result", error is FetchError.Throttled)
    }

    @Test
    fun `403 on the flight GET stays an Unavailable, not a throttle`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403))
        server.enqueue(MockResponse().setResponseCode(403))

        val result = client.fetchDailyBody()

        val error = (result as? FetchResult.Err)?.error
        assertTrue("expected Unavailable, got $result", error is FetchError.Unavailable)
    }

    @Test
    fun `transport failure on the flight still attempts the page scan`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(ok(dataBearing()))

        val result = client.fetchDailyBody()

        assertTrue(result is FetchResult.Ok)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `nothing data-bearing anywhere is a sealed Unavailable with reasons`() = runBlocking {
        server.enqueue(ok("shell, no puzzle"))
        server.enqueue(ok("shell, still no puzzle"))

        val result = client.fetchDailyBody()

        val error = (result as? FetchResult.Err)?.error as? FetchError.Unavailable
        assertTrue(error != null)
        assertTrue(error!!.detail.orEmpty().contains("no tangoGamePuzzle"))
    }
}
