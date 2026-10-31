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

/** The anonymous handshake + fallback ladder (research 01 §2/§4), against MockWebServer. */
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

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader).getResource("fixtures/$name")!!.readText()

    private fun pageCarryingData(): String =
        """{"trailGamePuzzle":{"gridSize":2,"orderedSequence":[0,3]},"todaysGameEditionText":"9"}"""

    @Test
    fun `handshake GET precedes the POST and its JSESSIONID becomes the csrf token`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .addHeader("Set-Cookie", "JSESSIONID=ajax:1234567890123456789; Path=/")
                .setBody("session page, no puzzle data"),
        )
        server.enqueue(ok(fixture("linkedin-zip-rsc-592.txt")))

        val result = client.fetchDailyBody()

        assertTrue(result is FetchResult.Ok)
        val first = server.takeRequest()
        assertEquals("/games/zip/", first.path)
        assertNull(first.getHeader("csrf-token"))
        val post = server.takeRequest()
        assertEquals("/flagship-web/games/zip/?skipStartScreen=true", post.path)
        assertEquals("POST", post.method)
        assertEquals("ajax:1234567890123456789", post.getHeader("csrf-token"))
        assertEquals("true", post.getHeader("x-li-rsc-stream"))
        assertTrue(post.getHeader("content-type")!!.startsWith("application/json"))
        assertTrue(post.body.readUtf8().contains("GamePlayRouterPage"))
    }

    @Test
    fun `data-less stream falls back to the page body scan`() = runBlocking {
        server.enqueue(ok(pageCarryingData()))
        server.enqueue(ok("2:\"\$Sreact.fragment\"\n5:I[0]")) // stream, no puzzle object

        val result = client.fetchDailyBody()

        assertTrue(result is FetchResult.Ok)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `http failure on the stream degrades to the page scan`() = runBlocking {
        server.enqueue(ok(pageCarryingData()))
        server.enqueue(MockResponse().setResponseCode(503))

        val result = client.fetchDailyBody()

        assertTrue(result is FetchResult.Ok)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `nothing data-bearing anywhere is a sealed Unavailable with reasons`() = runBlocking {
        server.enqueue(ok(fixture("linkedin-zip-page-shell.html")))
        server.enqueue(ok("2:\"\$Sreact.fragment\"\n4:[\"rate limited\"]\n"))

        val result = client.fetchDailyBody()

        assertTrue(result is FetchResult.Err && (result as FetchResult.Err).error is FetchError.Unavailable)
        val err = result as FetchResult.Err
        val unavailable = err.error as FetchError.Unavailable
        assertTrue(unavailable.detail!!.contains("stream"))
        assertTrue(unavailable.detail!!.contains("page"))
    }

    @Test
    fun `transport failure on the handshake still attempts the stream`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(ok(fixture("linkedin-zip-rsc-592.txt")))

        val result = client.fetchDailyBody()

        assertTrue(result is FetchResult.Ok)
        assertEquals(2, server.requestCount) // handshake GET (failed), then the stream POST
        server.takeRequest() // the failed GET
        assertEquals("POST", server.takeRequest().method)
    }
}
