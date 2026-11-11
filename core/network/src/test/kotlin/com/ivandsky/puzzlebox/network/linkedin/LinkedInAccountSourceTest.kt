package com.ivandsky.puzzlebox.network.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInAccountSession
import com.ivandsky.puzzlebox.game.linkedin.LinkedInAccountSource
import com.ivandsky.puzzlebox.game.linkedin.LinkedInOwnResult
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead.Rejected
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead.Rejected.Kind
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSessionCookies
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.util.concurrent.atomic.AtomicReference
import java.util.logging.Logger
import java.util.logging.SimpleFormatter
import java.util.logging.StreamHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The account client's safety rules against MockWebServer, built through the public factory so the client
 * under test is the one the app runs: one GET per call, no retry, no redirect, no cookie replay, no logging.
 */
class LinkedInAccountSourceTest {

    private val servers = mutableListOf<MockWebServer>()

    @After
    fun tearDown() = servers.forEach { it.shutdown() }

    @Test
    fun `each call is one GET to the graphql path with the pinned query and the session headers`() = runBlocking {
        val server = server(json(TODAY), json(fixture("end-page.json")), json(fixture("board.json")))
        val source = server.source()

        assertEquals(LinkedInRead.Ok("ACoAAFAKE0001"), source.check(6))
        assertEquals(
            LinkedInRead.Ok(LinkedInOwnResult.Solved(14, hintFree = true)),
            source.ownResult("ACoAAFAKE0001", 6, 538),
        )
        val board = source.board("ACoAAFAKE0001", 6, 812, 3)
        assertEquals(5, (board as LinkedInRead.Ok).value.rows.size)
        assertEquals(3, server.requestCount)

        // The board is keyed by today's URN (…,6,812) and picks the past day with delta:3.
        val expected = listOf(
            "(gameTypeId:6)" to VoyagerQueries.TODAYS_GAME,
            "(gameUrn:urn%3Ali%3Afsd_game%3A%28ACoAAFAKE0001%2C6%2C538%29)" to VoyagerQueries.END_PAGE,
            "(gameUrn:urn%3Ali%3Afsd_game%3A%28ACoAAFAKE0001%2C6%2C812%29,start:0,count:50,delta:3)" to
                VoyagerQueries.CONNECTIONS,
        )
        expected.forEach { (variables, queryId) ->
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals(
                "/voyager/api/graphql?includeWebMetadata=true&variables=$variables&queryId=$queryId",
                request.path,
            )
            assertEquals(listOf(SESSION.cookieHeader), request.headers.values("Cookie"))
            assertEquals(SESSION.csrfToken, request.getHeader("csrf-token"))
            assertEquals(SESSION.userAgent, request.getHeader("User-Agent"))
            assertEquals("application/vnd.linkedin.normalized+json+2.1", request.getHeader("accept"))
            assertEquals("2.0.0", request.getHeader("x-restli-protocol-version"))
            assertEquals("en_US", request.getHeader("x-li-lang"))
        }
    }

    @Test
    fun `401 and 403 are SessionExpired`() {
        assertEquals(Rejected(Kind.SessionExpired), single(status(401)))
        assertEquals(Rejected(Kind.SessionExpired), single(status(403)))
    }

    @Test
    fun `999 and a 302 are Challenge`() {
        assertEquals(Rejected(Kind.Challenge), single(status(999)))
        // The bait answers at the redirect target: following it would cost a second request and return Ok.
        assertEquals(Rejected(Kind.Challenge), single(status(302).addHeader("Location", "/feed/"), json(TODAY)))
    }

    @Test
    fun `a 200 that is not JSON is Challenge`() {
        val page = MockResponse().setBody(TODAY).setHeader("Content-Type", "text/html; charset=UTF-8")

        assertEquals(Rejected(Kind.Challenge), single(page))
    }

    @Test
    fun `429 is RateLimited`() {
        assertEquals(Rejected(Kind.RateLimited), single(status(429).addHeader("Retry-After", "1"), json(TODAY)))
    }

    @Test
    fun `other 4xx and 5xx are Unexpected`() {
        assertEquals(Rejected(Kind.Unexpected), single(status(404)))
        assertEquals(Rejected(Kind.Unexpected), single(status(500), json(TODAY)))
    }

    @Test
    fun `a 200 whose JSON has the wrong shape is Unexpected`() {
        assertEquals(Rejected(Kind.Unexpected), single(json("{}")) { check(6) })
        assertEquals(Rejected(Kind.Unexpected), single(json("{}")) { ownResult("ACoAAFAKE0001", 6, 538) })
        assertEquals(Rejected(Kind.Unexpected), single(json("{}")) { board("ACoAAFAKE0001", 6, 812, 3) })
    }

    @Test
    fun `a dropped connection is Offline and is not retried`() {
        val dropped = MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)

        assertEquals(LinkedInRead.Offline, single(dropped, json(TODAY)))
    }

    @Test
    fun `no session means no request`() = runBlocking {
        val server = server(json(TODAY))
        val source = server.source(session = null)

        assertEquals(LinkedInRead.SignedOut, source.check(6))
        assertEquals(LinkedInRead.SignedOut, source.ownResult("ACoAAFAKE0001", 6, 538))
        assertEquals(LinkedInRead.SignedOut, source.board("ACoAAFAKE0001", 6, 812, 3))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `cancelling a call in flight is cancellation, not Offline`() = runBlocking {
        val server = server(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val source = server.source()
        // Awaiting a cancelled job throws whatever its block did, so the block's own outcome is kept too.
        val returned = AtomicReference<LinkedInRead<String>?>()

        val call = async(Dispatchers.Default) { source.check(6).also(returned::set) }
        server.takeRequest()
        call.cancel()

        assertTrue(runCatching { call.await() }.exceptionOrNull() is CancellationException)
        assertNull(returned.get())
    }

    @Test
    fun `a Set-Cookie from LinkedIn is never replayed`() = runBlocking {
        val server = server(json(TODAY).addHeader("Set-Cookie", "JSESSIONID=ROTATED; Path=/"), json(TODAY))
        val source = server.source()

        source.check(6)
        source.check(6)

        server.takeRequest()
        assertEquals(listOf(SESSION.cookieHeader), server.takeRequest().headers.values("Cookie"))
    }

    @Test
    fun `a session a header cannot carry is a rejection that quotes nothing and sends nothing`() = runBlocking {
        val server = server(json(TODAY))
        val unsendable = LinkedInAccountSession(SESSION.cookieHeader, "ajax:SENTINEL-CSRF\n", SESSION.userAgent)

        val read = server.source(unsendable).check(6)

        assertEquals(Rejected(Kind.Unexpected), read)
        assertFalse(read.toString().contains("SENTINEL"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `nothing of the session or a response body is logged or kept in a result`() = runBlocking {
        val server = server(
            status(401).setBody(SECRET_BODY),
            json("""{"marker":"$SECRET_BODY"}"""),
            MockResponse().setBody(SECRET_BODY),
            MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST),
        )
        val source = server.source()
        val results = mutableListOf<LinkedInRead<String>>()

        val output = logged {
            // OkHttp's logging interceptor writes to this logger; the probe proves the capture hears it.
            Logger.getLogger(OkHttpClient::class.java.name).info(PROBE)
            repeat(4) { results += source.check(6) }
        }

        assertEquals(4, server.requestCount)
        assertTrue(output.contains(PROBE))
        assertFalse(output.contains("SENTINEL"))
        assertFalse(results.toString().contains("SENTINEL"))
    }

    /** What one call makes of the first of [responses]; the rest are bait only a second request would reach. */
    private fun single(
        vararg responses: MockResponse,
        call: suspend LinkedInAccountSource.() -> LinkedInRead<*> = { check(6) },
    ): LinkedInRead<*> = runBlocking {
        val server = server(*responses)
        val result = server.source().call()
        assertEquals(1, server.requestCount)
        result
    }

    /** Everything [block] sends to the console streams or to java.util.logging. */
    private suspend fun logged(block: suspend () -> Unit): String {
        val sink = ByteArrayOutputStream()
        val stream = PrintStream(sink, true)
        val handler = StreamHandler(stream, SimpleFormatter())
        val root = Logger.getLogger("")
        val out = System.out
        val err = System.err
        System.setOut(stream)
        System.setErr(stream)
        root.addHandler(handler)
        try {
            block()
        } finally {
            handler.flush()
            root.removeHandler(handler)
            System.setOut(out)
            System.setErr(err)
        }
        return sink.toString()
    }

    private fun server(vararg responses: MockResponse): MockWebServer =
        MockWebServer().also { server ->
            responses.forEach(server::enqueue)
            server.start()
            servers += server
        }

    private fun MockWebServer.source(session: LinkedInAccountSession? = SESSION): LinkedInAccountSource =
        linkedInAccountSource(url("/").toString(), FixedSessionCookies(session))

    private fun status(code: Int) = MockResponse().setResponseCode(code)

    private fun json(body: String) = MockResponse().setBody(body)
        .setHeader("Content-Type", "application/vnd.linkedin.normalized+json+2.1; charset=UTF-8")

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader).getResource("fixtures/linkedin-account/$name")!!.readText()

    private class FixedSessionCookies(private val session: LinkedInAccountSession?) : LinkedInSessionCookies {
        override suspend fun current(): LinkedInAccountSession? = session
        override suspend fun clear() = Unit
    }

    private companion object {
        val SESSION = LinkedInAccountSession(
            cookieHeader = "li_at=SENTINEL-LI-AT; JSESSIONID=\"ajax:SENTINEL-CSRF\"",
            csrfToken = "ajax:SENTINEL-CSRF",
            userAgent = "SENTINEL-UA",
        )
        const val SECRET_BODY = "SENTINEL-BODY"
        const val PROBE = "probe-line-from-the-okhttp-logger"
        const val TODAY =
            """{"data":{"data":{"identityDashGamesByTodaysGame":{"*elements":["urn:li:fsd_game:(ACoAAFAKE0001,6,812)"]}}}}"""
    }
}
