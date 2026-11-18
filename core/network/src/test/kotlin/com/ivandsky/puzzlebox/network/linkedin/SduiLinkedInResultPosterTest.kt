package com.ivandsky.puzzlebox.network.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInAccountSession
import com.ivandsky.puzzlebox.game.linkedin.LinkedInGamePage
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead.Rejected.Kind
import com.ivandsky.puzzlebox.game.linkedin.LinkedInResultPoster
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSendResult
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSessionCookies
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The post adapter against MockWebServer, built through the public factory so the client under test is the
 * one the app runs: one request per method, never retried, no redirect followed, nothing of the session kept.
 */
class SduiLinkedInResultPosterTest {

    private val servers = mutableListOf<MockWebServer>()
    private val pageBody = LinkedInPostFixtures.text("zip-page-daily-solved.rsc")
    private val winResponse = LinkedInPostFixtures.text("zip-update-game-state-win.response.rsc")
    private val openingResponse = LinkedInPostFixtures.text("zip-update-game-state-daily-first-move.response.rsc")
    private val capturedPageHeaders = headersOf("zip-page-daily-unsolved.request-headers.json")
    private val capturedWriteHeaders = headersOf("zip-update-game-state-daily-win.request-headers.json")

    @After
    fun tearDown() = servers.forEach { it.shutdown() }

    @Test
    fun `the page is one GET of the game's slug with the session, and parses`() = runBlocking {
        val server = server(rsc(pageBody))

        val read = server.poster().gamePage(LinkedInPostFixtures.post())

        assertEquals(LinkedInRead.Ok(LinkedInPostFixtures.dailyPage), read)
        assertEquals(1, server.requestCount)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/flagship-web/games/zip", request.path)
        assertEquals(listOf(SESSION.cookieHeader), request.headers.values("Cookie"))
        assertEquals(SESSION.csrfToken, request.getHeader("csrf-token"))
        assertEquals(SESSION.userAgent, request.getHeader("User-Agent"))
        PAGE_HEADERS.forEach { name -> assertEquals(name, capturedPageHeaders.getValue(name), request.getHeader(name)) }
        assertEquals("https://www.linkedin.com/games/zip", request.getHeader("referer"))
        assertNotNull(request.getHeader("accept-language"))
        assertNone(request)
    }

    @Test
    fun `the slug is the last segment of the game path, with or without a trailing slash`() = runBlocking {
        val server = server(rsc(pageBody), rsc(pageBody))
        val poster = server.poster()

        poster.gamePage(LinkedInPostFixtures.post().copy(gamePath = "games/queens/"))
        poster.gamePage(LinkedInPostFixtures.post().copy(gamePath = "games/tango"))

        assertEquals("/flagship-web/games/queens", server.takeRequest().path)
        assertEquals("/flagship-web/games/tango", server.takeRequest().path)
    }

    @Test
    fun `a page missing a required state is Rejected Unexpected`() {
        val drifted = pageBody.replace("\"id\":\"gameBoardPuzzleId\"}}},\"value\"", "\"id\":\"renamed\"}}},\"value\"")

        assertEquals(pageRejected(Kind.Unexpected), page(rsc(drifted)))
    }

    @Test
    fun `a page over the 1 MB cap is Rejected Unexpected`() {
        val oversize = pageBody + "\n" + "x".repeat(OVER_CAP)

        assertEquals(pageRejected(Kind.Unexpected), page(rsc(oversize)))
    }

    @Test
    fun `an oversize page of unknown length is Rejected Unexpected too`() {
        val chunked = MockResponse().setChunkedBody(pageBody + "\n" + "x".repeat(OVER_CAP), 8192)

        assertEquals(pageRejected(Kind.Unexpected), page(chunked))
    }

    @Test
    fun `page statuses follow the shared table and a redirect is not followed`() {
        assertEquals(pageRejected(Kind.SessionExpired), page(status(401)))
        assertEquals(pageRejected(Kind.SessionExpired), page(status(403)))
        assertEquals(pageRejected(Kind.Challenge), page(status(999)))
        assertEquals(pageRejected(Kind.Challenge), page(status(302).addHeader("Location", "/feed/"), rsc(pageBody)))
        assertEquals(pageRejected(Kind.RateLimited), page(status(429)))
        assertEquals(pageRejected(Kind.Unexpected), page(status(500).setBody(pageBody)))
    }

    @Test
    fun `a dropped page connection is Offline and is not retried`() {
        assertEquals(LinkedInRead.Offline, page(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST), rsc(pageBody)))
    }

    @Test
    fun `no session means no request for either method`() = runBlocking {
        val server = server(rsc(pageBody))
        val poster = server.poster(session = null)

        assertEquals(LinkedInRead.SignedOut, poster.gamePage(LinkedInPostFixtures.post()))
        assertEquals(LinkedInSendResult.SignedOut, poster.send(LinkedInPostFixtures.post(), LinkedInPostFixtures.dailyPage))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `the write is one POST with the capture's headers, the pinned sduiid and the built body`() = runBlocking {
        val server = server(rsc(winResponse))
        val post = LinkedInPostFixtures.post()
        val page = LinkedInPostFixtures.dailyPage

        assertEquals(LinkedInSendResult.Posted, server.poster().send(post, page))

        assertEquals(1, server.requestCount)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/flagship-web/rsc-action/actions/server-request", request.requestUrl!!.encodedPath)
        // P12 does not read the value, so this is where `updateGameState` is pinned.
        assertEquals("sduiid=updateGameState", request.requestUrl!!.encodedQuery)
        assertEquals(UpdateGameStateBody.build(post, page), request.body.readUtf8())

        STATIC_HEADERS.forEach { name -> assertEquals(name, capturedWriteHeaders.getValue(name), request.getHeader(name)) }
        assertNotNull(request.getHeader("accept-language"))
        assertNone(request)
        assertEquals("application/json", request.getHeader("Content-Type"))
        assertEquals("https://www.linkedin.com/games/zip/", request.getHeader("referer"))
        assertEquals("d_flagship3_games_zip_gameboard", request.getHeader("x-li-anchor-page-key"))
        assertEquals(listOf(SESSION.cookieHeader), request.headers.values("Cookie"))
        assertEquals(SESSION.csrfToken, request.getHeader("csrf-token"))
        assertEquals(SESSION.userAgent, request.getHeader("User-Agent"))
    }

    @Test
    fun `a game with an opening sends the opening and then the win, both with the capture's headers`() = runBlocking {
        val server = server(rsc(openingResponse), rsc(winResponse))
        val post = LinkedInPostFixtures.dailyPost()
        val page = LinkedInPostFixtures.unsolvedDailyPage

        assertEquals(LinkedInSendResult.Posted, server.poster().send(post, page))

        assertEquals(2, server.requestCount)
        val first = server.takeRequest()
        val second = server.takeRequest()
        assertEquals(UpdateGameStateBody.opening(post, page), first.body.readUtf8())
        assertEquals(UpdateGameStateBody.build(post, page), second.body.readUtf8())
        listOf(first, second).forEach { request ->
            assertEquals("POST", request.method)
            assertEquals("/flagship-web/rsc-action/actions/server-request", request.requestUrl!!.encodedPath)
            assertEquals("sduiid=updateGameState", request.requestUrl!!.encodedQuery)
            STATIC_HEADERS.forEach { name -> assertEquals(name, capturedWriteHeaders.getValue(name), request.getHeader(name)) }
        }
    }

    @Test
    fun `a page that holds a saved play gets the win alone`() = runBlocking {
        val server = server(rsc(winResponse), rsc(winResponse))
        val post = LinkedInPostFixtures.dailyPost()
        val page = LinkedInPostFixtures.inProgressDailyPage

        assertEquals(LinkedInSendResult.Posted, server.poster().send(post, page))

        assertEquals(1, server.requestCount)
        assertEquals(UpdateGameStateBody.build(post, page), server.takeRequest().body.readUtf8())
    }

    @Test
    fun `an opening that is not Posted is returned and no win is sent`() {
        val outcomes = mapOf(
            status(409) to LinkedInSendResult.AlreadyPlayed,
            status(401) to writeRejected(Kind.SessionExpired),
            status(429) to writeRejected(Kind.RateLimited),
            status(500) to writeRejected(Kind.Unexpected),
            rsc(openingResponse.replace("\"errors\":[]", "\"errors\":[{}]")) to writeRejected(Kind.Unexpected),
        )
        outcomes.forEach { (response, expected) ->
            runBlocking {
                // The second response is bait only a win would reach.
                val server = server(response, rsc(winResponse))

                assertEquals(expected, server.poster().send(LinkedInPostFixtures.dailyPost(), LinkedInPostFixtures.unsolvedDailyPage))
                assertEquals(1, server.requestCount)
            }
        }
    }

    @Test
    fun `a failure after the opening was written is Unknown and no win is sent`() = runBlocking {
        val server = server(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST), rsc(winResponse))

        assertEquals(LinkedInSendResult.Unknown, server.poster().send(LinkedInPostFixtures.dailyPost(), LinkedInPostFixtures.unsolvedDailyPage))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `the win after a posted opening answers as the win`() = runBlocking {
        val server = server(rsc(openingResponse), status(409))

        assertEquals(LinkedInSendResult.AlreadyPlayed, server.poster().send(LinkedInPostFixtures.dailyPost(), LinkedInPostFixtures.unsolvedDailyPage))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `x-li-track carries the device timezone and offset`() = runBlocking {
        val server = server(rsc(winResponse))

        server.poster(zone = ZoneId.of("Asia/Kolkata")).send(LinkedInPostFixtures.post(), LinkedInPostFixtures.dailyPage)

        val track = Json.parseToJsonElement(server.takeRequest().getHeader("x-li-track")!!).jsonObject
        assertEquals("Asia/Kolkata", track.getValue("timezone").jsonPrimitive.content)
        assertEquals("5.5", track.getValue("timezoneOffset").jsonPrimitive.content)
    }

    @Test
    fun `the referer and anchor page key follow the post's game`() = runBlocking {
        val server = server(rsc(winResponse))

        server.poster().send(LinkedInPostFixtures.post().copy(gamePath = "games/queens/"), LinkedInPostFixtures.dailyPage)

        val request = server.takeRequest()
        assertEquals("https://www.linkedin.com/games/queens/", request.getHeader("referer"))
        assertEquals("d_flagship3_games_queens_gameboard", request.getHeader("x-li-anchor-page-key"))
    }

    @Test
    fun `write statuses map to their outcome class`() {
        assertEquals(LinkedInSendResult.Posted, write(rsc(winResponse)))
        assertEquals(LinkedInSendResult.AlreadyPlayed, write(status(409)))
        assertEquals(writeRejected(Kind.SessionExpired), write(status(401)))
        assertEquals(writeRejected(Kind.SessionExpired), write(status(403)))
        assertEquals(writeRejected(Kind.RateLimited), write(status(429)))
        assertEquals(writeRejected(Kind.Challenge), write(status(999)))
        assertEquals(writeRejected(Kind.Challenge), write(status(302).addHeader("Location", "/feed/"), rsc(winResponse)))
        assertEquals(writeRejected(Kind.Unexpected), write(status(500)))
        assertEquals(writeRejected(Kind.Unexpected), write(rsc(winResponse.replace("\"errors\":[]", "\"errors\":[{}]"))))
        assertEquals(writeRejected(Kind.Unexpected), write(rsc("<html>Sign in</html>")))
    }

    @Test
    fun `a disconnect after the request was written is Unknown and is not retried`() {
        val dropped = MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)

        assertEquals(LinkedInSendResult.Unknown, write(dropped, rsc(winResponse)))
    }

    @Test
    fun `a body cut off mid-response after the request was written is Unknown`() {
        val cut = rsc(winResponse).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)

        assertEquals(LinkedInSendResult.Unknown, write(cut, rsc(winResponse)))
    }

    @Test
    fun `a refused connection is Offline, nothing was written`() = runBlocking {
        val server = server(rsc(winResponse))
        val poster = server.poster()
        server.shutdown()

        assertEquals(LinkedInSendResult.Offline, poster.send(LinkedInPostFixtures.post(), LinkedInPostFixtures.dailyPage))
        assertEquals(LinkedInRead.Offline, poster.gamePage(LinkedInPostFixtures.post()))
    }

    @Test
    fun `cancelling a write in flight is cancellation, not Unknown`() = runBlocking {
        val server = server(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val poster = server.poster()
        val returned = AtomicReference<LinkedInSendResult?>()

        val call = async(Dispatchers.Default) {
            poster.send(LinkedInPostFixtures.post(), LinkedInPostFixtures.dailyPage).also(returned::set)
        }
        server.takeRequest()
        call.cancel()

        assertTrue(runCatching { call.await() }.exceptionOrNull() is CancellationException)
        assertNull(returned.get())
    }

    @Test
    fun `a Set-Cookie from LinkedIn is never re-sent`() = runBlocking {
        val server = server(
            rsc(pageBody).addHeader("Set-Cookie", "JSESSIONID=ROTATED; Path=/"),
            rsc(winResponse),
        )
        val poster = server.poster()

        poster.gamePage(LinkedInPostFixtures.post())
        poster.send(LinkedInPostFixtures.post(), LinkedInPostFixtures.dailyPage)

        server.takeRequest()
        assertEquals(listOf(SESSION.cookieHeader), server.takeRequest().headers.values("Cookie"))
    }

    @Test
    fun `a session a header cannot carry is a rejection that quotes nothing and sends nothing`() = runBlocking {
        val server = server(rsc(pageBody), rsc(winResponse))
        val unsendable = LinkedInAccountSession(SESSION.cookieHeader, "ajax:SENTINEL-CSRF\n", SESSION.userAgent)
        val poster = server.poster(unsendable)

        val read = poster.gamePage(LinkedInPostFixtures.post())
        val sent = poster.send(LinkedInPostFixtures.post(), LinkedInPostFixtures.dailyPage)

        assertEquals(pageRejected(Kind.Unexpected), read)
        assertEquals(writeRejected(Kind.Unexpected), sent)
        assertFalse((read.toString() + sent.toString()).contains("SENTINEL"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `no result keeps the session or a response body`() = runBlocking {
        val server = server(status(401).setBody(SECRET_BODY), rsc(SECRET_BODY), status(500).setBody(SECRET_BODY))
        val poster = server.poster()

        val results = listOf(
            poster.gamePage(LinkedInPostFixtures.post()),
            poster.gamePage(LinkedInPostFixtures.post()),
            poster.send(LinkedInPostFixtures.post(), LinkedInPostFixtures.dailyPage),
        )

        assertFalse(results.toString().contains("SENTINEL"))
    }

    private fun pageRejected(kind: Kind) = LinkedInRead.Rejected(kind)

    private fun writeRejected(kind: Kind) = LinkedInSendResult.Rejected(kind)

    private fun page(vararg responses: MockResponse): LinkedInRead<LinkedInGamePage> = runBlocking {
        val server = server(*responses)
        val result = server.poster().gamePage(LinkedInPostFixtures.post())
        // The rest are bait only a second request would reach.
        assertEquals(1, server.requestCount)
        result
    }

    private fun write(vararg responses: MockResponse): LinkedInSendResult = runBlocking {
        val server = server(*responses)
        val result = server.poster().send(LinkedInPostFixtures.post(), LinkedInPostFixtures.dailyPage)
        assertEquals(1, server.requestCount)
        result
    }

    private fun server(vararg responses: MockResponse): MockWebServer =
        MockWebServer().also { server ->
            responses.forEach(server::enqueue)
            server.start()
            servers += server
        }

    private fun MockWebServer.poster(
        session: LinkedInAccountSession? = SESSION,
        zone: ZoneId = ZoneId.of("UTC"),
    ): LinkedInResultPoster = linkedInResultPoster(url("/").toString(), FixedSessionCookies(session), clock = Instant::now) { zone }

    private fun headersOf(name: String): Map<String, String> =
        Json.parseToJsonElement(LinkedInPostFixtures.text(name)).jsonObject.mapValues { it.value.jsonPrimitive.content }

    /** The trace and instance headers are redacted in the capture, and `sec-*` only a browser sends: none is made up. */
    private fun assertNone(request: RecordedRequest) {
        val names = request.headers.names().map(String::lowercase)
        assertEquals(emptyList<String>(), names.filter { it.startsWith("sec-") || it in OMITTED })
    }

    private fun status(code: Int) = MockResponse().setResponseCode(code)

    private fun rsc(body: String) = MockResponse().setBody(body).setHeader("Content-Type", "text/x-component")

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
        const val OVER_CAP = 1_048_576 + 1
        val STATIC_HEADERS = listOf("accept", "origin", "x-li-rsc-stream", "x-li-application-version", "x-li-track", "x-li-anchor-page-key")
        val PAGE_HEADERS = listOf("accept", "x-li-rsc-stream", "x-li-application-version", "x-li-track", "x-li-anchor-page-key")
        val OMITTED = setOf(
            "x-li-traceparent", "x-li-tracestate", "x-li-page-instance", "x-li-page-instance-tracking-id",
            "x-li-application-instance", "x-li-pageforestid", "x-li-initial-url", "x-li-layout-tree",
        )
    }
}
