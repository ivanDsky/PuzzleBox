package com.ivandsky.puzzlebox.network

import com.ivandsky.puzzlebox.game.FeedRequest
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.game.LinkedInEdition
import com.ivandsky.puzzlebox.game.PersistedQuery
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The one LinkedIn ladder every game's DailySource rides (research R-19-1, ADR-0016), against
 * MockWebServer: stream GET (unless skipped) → page handshake (before the first session rung,
 * only without a session) → persisted-query GET (when named) → graphql POST (when the request
 * carries a body) → page-shell GET (when asked). The request's `accepts` — the game's
 * extractor — decides which body wins.
 */
class LinkedInFeedTransportTest {

    private lateinit var server: MockWebServer
    private lateinit var jar: LinkedInSessionCookieJar
    private lateinit var transport: LinkedInFeedTransport
    private var now: Instant = Instant.parse("2026-11-05T12:00:00Z")
    private val clock = object : Clock() {
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
    }

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
        transport = LinkedInFeedTransport(retrofit.create(LinkedInGamesApi::class.java), jar, server.url("/"), clock)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `a throttle starts a cooldown - the next fetch is Throttled without a request`() = runBlocking {
        server.enqueue(throttled())
        server.enqueue(ok(fixture("linkedin-zip-page-shell.html")))
        transport.fetch(request())
        val requestsAfterThrottle = server.requestCount

        now = now.plusSeconds(1)
        val result = transport.fetch(request())

        assertTrue((result as FetchResult.Err).error is FetchError.Throttled)
        assertEquals(requestsAfterThrottle, server.requestCount)
    }

    @Test
    fun `after the cooldown requests resume`() = runBlocking {
        server.enqueue(throttled())
        server.enqueue(ok(fixture("linkedin-zip-page-shell.html")))
        transport.fetch(request())
        server.enqueue(ok(dataBearing()))

        now = now.plus(LinkedInFeedTransport.THROTTLE_COOLDOWN)
        val result = transport.fetch(request())

        assertTrue(result is FetchResult.Ok)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `the LinkedIn client gives every call a 10 s budget`() {
        assertEquals(10_000, linkedInClient(LinkedInSessionCookieJar()).callTimeoutMillis)
    }

    private fun request(
        path: String = "games/zip/",
        objectName: String = "trailGamePuzzle",
        graphqlBody: String? = null,
        persistedQuery: PersistedQuery? = null,
        scanPageShell: Boolean = true,
    ) = FeedRequest(
        path = path,
        objectName = objectName,
        accepts = { it.contains("\"$objectName\"") },
        graphqlBody = graphqlBody,
        persistedQuery = persistedQuery,
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

    private val miniSudokuQuery = PersistedQuery(queryId = "voyagerIdentityDashGames.x", variables = "(gameTypeId:7)")

    /** Puts an already-open anonymous session in the jar, as an earlier fetch's handshake would. */
    private fun holdSession(id: String = "ajax:1") {
        jar.saveFromResponse(server.url("/"), listOf(Cookie.parse(server.url("/"), "JSESSIONID=$id; Path=/")!!))
    }

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
        server.enqueue(ok(dataBearing("lotkaGamePuzzle")))

        val result = transport.fetch(request(path = "games/tango/", objectName = "lotkaGamePuzzle"))

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
        holdSession()
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
        holdSession()
        server.enqueue(ok(dataLessStream))
        server.enqueue(MockResponse().setResponseCode(500))

        val result = transport.fetch(
            request(objectName = "queensGamePuzzle", graphqlBody = "{}", scanPageShell = false),
        )

        val error = (result as FetchResult.Err).error as FetchError.Unavailable
        assertTrue(error.detail!!.contains("graphql HTTP 500"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `persisted-query GET runs after the stream and the page handshake, signed with its cookie, and its body wins`() =
        runBlocking {
            server.dispatcher = LinkedInLikeWire(sessionId = "ajax:4242")

            val result = transport.fetch(request(graphqlBody = "{}", persistedQuery = miniSudokuQuery))

            assertTrue((result as FetchResult.Ok).value.contains("identityDashGamesById"))
            assertEquals(3, server.requestCount) // POST and page-shell rungs never ran
            assertEquals("/games/zip/?skipStartScreen=true", server.takeRequest().path)
            val handshake = server.takeRequest()
            assertEquals("/games/zip/", handshake.path)
            assertNull(handshake.getHeader("x-li-rsc-stream"))
            val persisted = server.takeRequest()
            assertEquals("GET", persisted.method)
            assertEquals("/voyager/api/graphql?variables=(gameTypeId:7)&queryId=voyagerIdentityDashGames.x", persisted.path)
            assertEquals("ajax:4242", persisted.getHeader("csrf-token"))
        }

    @Test
    fun `403 on the persisted-query GET is never retried - one by-id, an Unavailable naming that rung`() = runBlocking {
        server.dispatcher = LinkedInLikeWire(sessionId = "ajax:4242", pageSessionId = "ajax:stale")

        val result = transport.fetch(
            request(objectName = "sudokuGamePuzzle", persistedQuery = miniSudokuQuery, scanPageShell = false),
        )

        val error = (result as FetchResult.Err).error as FetchError.Unavailable
        assertTrue(error.detail!!.contains("persisted-query HTTP 403"))
        assertEquals(3, server.requestCount) // stream, handshake, one by-id
    }

    @Test
    fun `a fresh session's past day sends the page handshake then games-by-id, nothing else`() = runBlocking {
        server.dispatcher = LinkedInLikeWire(sessionId = "ajax:5490")

        val result = transport.fetch(zipEdition.request(549))

        assertTrue("expected Ok, got $result", result is FetchResult.Ok)
        assertEquals(2, server.requestCount)
        val handshake = server.takeRequest()
        assertEquals("/games/zip/", handshake.path)
        assertNull(handshake.getHeader("x-li-rsc-stream"))
        assertEquals("ajax:5490", server.takeRequest().getHeader("csrf-token"))
    }

    @Test
    fun `with the session already held a past day sends games-by-id only`() = runBlocking {
        server.dispatcher = LinkedInLikeWire(sessionId = "ajax:5490")
        transport.fetch(zipEdition.request(549))

        val result = transport.fetch(zipEdition.request(549))

        assertTrue("expected Ok, got $result", result is FetchResult.Ok)
        assertEquals(3, server.requestCount)
        repeat(2) { server.takeRequest() }
        assertTrue(server.takeRequest().path!!.startsWith("/voyager/api/graphql?"))
    }

    @Test
    fun `a throttled handshake is Throttled and sends no games-by-id`() = runBlocking {
        server.enqueue(throttled())

        val result = transport.fetch(zipEdition.request(549))

        val error = (result as FetchResult.Err).error as FetchError.Throttled
        assertTrue(error.detail!!.contains("session handshake throttle"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a failed handshake is a reason and the session rung still runs`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(403).setBody("CSRF check failed."))

        val result = transport.fetch(zipEdition.request(549))

        val error = (result as FetchResult.Err).error as FetchError.Unavailable
        assertTrue(error.detail!!.contains("session handshake HTTP 503"))
        assertTrue(error.detail!!.contains("persisted-query HTTP 403"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a stream that answers sends no handshake`() = runBlocking {
        server.dispatcher = LinkedInLikeWire(sessionId = "ajax:4242", streamBody = "1:" + dataBearing())

        val result = transport.fetch(request(graphqlBody = "{}", persistedQuery = miniSudokuQuery))

        assertTrue(result is FetchResult.Ok)
        assertEquals(1, server.requestCount)
        assertNull(jar.sessionId(server.url("/")))
    }

    @Test
    fun `a graphql-only request on a fresh session is signed`() = runBlocking {
        server.dispatcher = LinkedInLikeWire(sessionId = "ajax:4242")

        val result = transport.fetch(request(graphqlBody = "{}", scanPageShell = false))

        assertTrue("expected Ok, got $result", result is FetchResult.Ok)
        assertEquals(3, server.requestCount) // stream, handshake, POST
        repeat(2) { server.takeRequest() }
        val post = server.takeRequest()
        assertEquals("POST", post.method)
        assertEquals("ajax:4242", post.getHeader("csrf-token"))
    }

    @Test
    fun `a by-id GET refused for csrf loads once the page GET hands out the session`() = runBlocking {
        val wire = LinkedInLikeWire(sessionId = "ajax:5490")
        server.dispatcher = wire

        val result = transport.fetch(zipEdition.request(549))

        assertTrue("expected Ok, got $result", result is FetchResult.Ok)
        assertEquals("ajax:5490", wire.acceptedCsrfToken)
    }

    private val zipEdition = LinkedInEdition(
        gameTypeId = 6,
        day1 = LocalDate.of(2025, 3, 18),
        path = "games/zip/",
        objectName = "trailGamePuzzle",
    )

    /**
     * The wire as LinkedIn serves an anonymous session (F1, research 01 §2): the stream is
     * data-less (unless [streamBody] says otherwise) and sets no `JSESSIONID`; only the plain
     * page GET does, handing out [pageSessionId]; games-by-id refuses any request whose
     * `csrf-token` is not [sessionId], and answers a games-by-id body otherwise.
     */
    private class LinkedInLikeWire(
        private val sessionId: String,
        private val pageSessionId: String = sessionId,
        private val streamBody: String = "2:\"\$Sreact.fragment\"\n5:I[0]",
    ) : Dispatcher() {

        @Volatile
        var acceptedCsrfToken: String? = null
            private set

        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.path.orEmpty()
            return when {
                path.startsWith("/voyager/api/graphql") -> byId(request)
                request.getHeader("x-li-rsc-stream") != null -> MockResponse().setBody(streamBody)
                else -> MockResponse().setBody("<html></html>").addHeader("Set-Cookie", "JSESSIONID=$pageSessionId; Path=/")
            }
        }

        private fun byId(request: RecordedRequest): MockResponse {
            val token = request.getHeader("csrf-token")
            if (token != sessionId) return MockResponse().setResponseCode(403).setBody("CSRF check failed.")
            acceptedCsrfToken = token
            return MockResponse().setBody(
                """{"data":{"identityDashGamesById":{"entityUrn":"urn:li:fsd_game:(guest,6,549)",""" +
                    """"gamePuzzle":{"trailGamePuzzle":{"gridSize":2,"orderedSequence":[0,3]}}}}}""",
            )
        }
    }
}
