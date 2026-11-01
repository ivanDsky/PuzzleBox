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

/** The flight-GET ladder (research R-19-1), against MockWebServer. */
class LinkedInZipClientTest {

    private lateinit var server: MockWebServer
    private lateinit var jar: LinkedInSessionCookieJar
    private lateinit var client: LinkedInZipClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        jar = LinkedInSessionCookieJar()
        val retrofit = retrofit(
            baseUrl = server.url("/").toString(),
            client = OkHttpClient.Builder().cookieJar(jar).addInterceptor(LinkedInCsrfInterceptor(jar)).build(),
            json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true },
        )
        client = LinkedInZipClient(retrofit.create(LinkedInZipApi::class.java))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun ok(body: String) = MockResponse().setResponseCode(200).setBody(body)

    /** R-19-1 throttle signature: 200, zero-byte body, soft-redirect `location`. */
    private fun throttled() = MockResponse().setResponseCode(200)
        .addHeader("location", "https://www.linkedin.com/games/")

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader).getResource("fixtures/$name")!!.readText()

    private fun pageCarryingData(): String =
        """{"trailGamePuzzle":{"gridSize":2,"orderedSequence":[0,3]},"todaysGameEditionText":"9"}"""

    @Test
    fun `flight GET is the primary request and carries the route-context headers`() = runBlocking {
        server.enqueue(ok(fixture("linkedin-zip-rsc-592.txt")))

        val result = client.fetchDailyBody()

        assertTrue(result is FetchResult.Ok)
        assertEquals(1, server.requestCount) // data-bearing stream: no fallback requests
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/games/zip/?skipStartScreen=true", request.path)
        assertEquals("true", request.getHeader("x-li-rsc-stream"))
        assertEquals("/games/zip/", request.getHeader("x-li-initial-url"))
        assertNull(request.getHeader("csrf-token")) // cookieless/csrf-free route (R-19-1)
        assertNull(request.getHeader("content-type"))
    }

    @Test
    fun `data-less stream falls back to the page body scan`() = runBlocking {
        server.enqueue(ok("2:\"\$Sreact.fragment\"\n5:I[0]")) // stream, no puzzle object
        server.enqueue(ok(pageCarryingData()))

        val result = client.fetchDailyBody()

        assertTrue(result is FetchResult.Ok)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `empty 200 with location is Throttled, not Unavailable`() = runBlocking {
        server.enqueue(throttled())
        server.enqueue(ok(fixture("linkedin-zip-page-shell.html"))) // last resort, no data

        val result = client.fetchDailyBody()

        assertTrue(result is FetchResult.Err && (result as FetchResult.Err).error is FetchError.Throttled)
        val throttledDetail = (result as FetchResult.Err).error as FetchError.Throttled
        assertTrue(throttledDetail.detail!!.contains("throttle"))
        assertTrue(throttledDetail.detail!!.contains("page")) // ladder kept walking after the throttle
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `http failure on the stream degrades to the page scan`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(ok(pageCarryingData()))

        val result = client.fetchDailyBody()

        assertTrue(result is FetchResult.Ok)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `403 on the flight GET stays an Unavailable, not a throttle`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403))
        server.enqueue(ok(fixture("linkedin-zip-page-shell.html")))

        val result = client.fetchDailyBody()

        assertTrue(result is FetchResult.Err && (result as FetchResult.Err).error is FetchError.Unavailable)
        assertTrue(((result as FetchResult.Err).error as FetchError.Unavailable).detail!!.contains("403"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `nothing data-bearing anywhere is a sealed Unavailable with reasons`() = runBlocking {
        server.enqueue(ok("2:\"\$Sreact.fragment\"\n4:[\"no data\"]\n"))
        server.enqueue(ok(fixture("linkedin-zip-page-shell.html")))

        val result = client.fetchDailyBody()

        assertTrue(result is FetchResult.Err && (result as FetchResult.Err).error is FetchError.Unavailable)
        val unavailable = (result as FetchResult.Err).error as FetchError.Unavailable
        assertTrue(unavailable.detail!!.contains("stream"))
        assertTrue(unavailable.detail!!.contains("page"))
    }

    @Test
    fun `transport failure on the flight still attempts the page scan`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(ok(pageCarryingData()))

        val result = client.fetchDailyBody()

        assertTrue(result is FetchResult.Ok)
        assertEquals(2, server.requestCount) // failed flight GET, then the page GET
        assertEquals("GET", server.takeRequest().method)
        assertEquals("GET", server.takeRequest().method)
    }
}
