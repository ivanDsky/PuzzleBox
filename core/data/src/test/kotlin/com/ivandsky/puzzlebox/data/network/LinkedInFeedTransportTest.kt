package com.ivandsky.puzzlebox.data.network

import com.ivandsky.puzzlebox.game.FeedRequest
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

/**
 * The one LinkedIn ladder every game's DailySource rides (research R-19-1), against
 * MockWebServer: stream GET → graphql POST (when the request carries a body) → page-shell
 * GET (when asked). The request's `accepts` — the game's extractor — decides which body wins.
 */
class LinkedInFeedTransportTest {

    private lateinit var server: MockWebServer
    private lateinit var jar: LinkedInSessionCookieJar
    private lateinit var transport: LinkedInFeedTransport

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
        transport = LinkedInFeedTransport(retrofit.create(LinkedInGamesApi::class.java))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun request(
        path: String = "games/zip/",
        objectName: String = "trailGamePuzzle",
        graphqlBody: String? = null,
        scanPageShell: Boolean = true,
    ) = FeedRequest(
        path = path,
        objectName = objectName,
        accepts = { it.contains("\"$objectName\"") },
        graphqlBody = graphqlBody,
        scanPageShell = scanPageShell,
    )

    private fun ok(body: String) = MockResponse().setResponseCode(200).setBody(body)

    /** R-19-1 throttle signature: 200, zero-byte body, soft-redirect `location`. */
    private fun throttled() = MockResponse().setResponseCode(200)
        .addHeader("location", "https://www.linkedin.com/games/")

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader).getResource("fixtures/$name")!!.readText()

    private fun dataBearing(objectName: String = "trailGamePuzzle"): String =
        """{"$objectName":{"gridSize":2,"orderedSequence":[0,3]},"todaysGameEditionText":"9"}"""

    private val dataLessStream = "2:\"\$Sreact.fragment\"\n5:I[0]"

    @Test
    fun `flight GET is the primary request and carries the route-context headers`() = runBlocking {
        server.enqueue(ok("1:" + dataBearing()))

        val result = transport.fetch(request())

        assertTrue(result is FetchResult.Ok)
        assertEquals(1, server.requestCount) // data-bearing stream: no fallback requests
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/games/zip/?skipStartScreen=true", recorded.path)
        assertEquals("true", recorded.getHeader("x-li-rsc-stream"))
        assertEquals("/games/zip/", recorded.getHeader("x-li-initial-url"))
        assertNull(recorded.getHeader("csrf-token")) // cookieless/csrf-free route (R-19-1)
        assertNull(recorded.getHeader("content-type"))
    }

    @Test
    fun `the route is the request path - one transport serves every game`() = runBlocking {
        server.enqueue(ok(dataBearing("tangoGamePuzzle")))

        val result = transport.fetch(request(path = "games/tango/", objectName = "tangoGamePuzzle"))

        assertTrue(result is FetchResult.Ok)
        val recorded = server.takeRequest()
        assertEquals("/games/tango/?skipStartScreen=true", recorded.path)
        assertEquals("/games/tango/", recorded.getHeader("x-li-initial-url"))
    }

    @Test
    fun `data-less stream falls back to the page body scan`() = runBlocking {
        server.enqueue(ok(dataLessStream))
        server.enqueue(ok(dataBearing()))

        val result = transport.fetch(request())

        assertTrue(result is FetchResult.Ok)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `empty 200 with location is Throttled, not Unavailable`() = runBlocking {
        server.enqueue(throttled())
        server.enqueue(ok(fixture("linkedin-zip-page-shell.html"))) // last resort, no data

        val result = transport.fetch(request())

        val error = (result as FetchResult.Err).error as FetchError.Throttled
        assertTrue(error.detail!!.contains("throttle"))
        assertTrue(error.detail!!.contains("page")) // ladder kept walking after the throttle
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `http failure on the stream degrades to the page scan`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(ok(dataBearing()))

        val result = transport.fetch(request())

        assertTrue(result is FetchResult.Ok)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `403 on the flight GET stays an Unavailable, not a throttle`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403))
        server.enqueue(ok(fixture("linkedin-zip-page-shell.html")))

        val result = transport.fetch(request())

        val error = (result as FetchResult.Err).error as FetchError.Unavailable
        assertTrue(error.detail!!.contains("403"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `nothing data-bearing anywhere is a sealed Unavailable with reasons`() = runBlocking {
        server.enqueue(ok("2:\"\$Sreact.fragment\"\n4:[\"no data\"]\n"))
        server.enqueue(ok(fixture("linkedin-zip-page-shell.html")))

        val result = transport.fetch(request())

        val error = (result as FetchResult.Err).error as FetchError.Unavailable
        assertTrue(error.detail!!.contains("stream"))
        assertTrue(error.detail!!.contains("page"))
    }

    @Test
    fun `transport failure on the flight still attempts the page scan`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(ok(dataBearing()))

        val result = transport.fetch(request())

        assertTrue(result is FetchResult.Ok)
        assertEquals(2, server.requestCount) // failed flight GET, then the page GET
        assertEquals("GET", server.takeRequest().method)
        assertEquals("GET", server.takeRequest().method)
    }

    @Test
    fun `graphql rung runs after a data-less stream and before the page shell`() = runBlocking {
        server.enqueue(ok(dataLessStream))
        server.enqueue(ok(dataBearing("queensGamePuzzle")))

        val result = transport.fetch(
            request(path = "games/queens/", objectName = "queensGamePuzzle", graphqlBody = """{"q":1}"""),
        )

        assertTrue(result is FetchResult.Ok)
        assertEquals(2, server.requestCount)
        assertEquals("GET", server.takeRequest().method)
        val graphql = server.takeRequest()
        assertEquals("POST", graphql.method)
        assertEquals("/voyager/api/graphql", graphql.path)
        assertEquals("""{"q":1}""", graphql.body.readUtf8())
    }

    @Test
    fun `without a page-shell rung the ladder ends after graphql`() = runBlocking {
        server.enqueue(ok(dataLessStream))
        server.enqueue(MockResponse().setResponseCode(500))

        val result = transport.fetch(
            request(objectName = "queensGamePuzzle", graphqlBody = "{}", scanPageShell = false),
        )

        val error = (result as FetchResult.Err).error as FetchError.Unavailable
        assertTrue(error.detail!!.contains("graphql HTTP 500"))
        assertEquals(2, server.requestCount)
    }
}
