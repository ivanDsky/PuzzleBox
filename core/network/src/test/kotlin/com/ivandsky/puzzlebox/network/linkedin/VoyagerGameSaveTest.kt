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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The voyager wire of a post against MockWebServer, built through the public factory so the client and the
 * routing under test are the ones the app runs: a play that carries a voyager board is read and saved over
 * voyager, never retried, and every other play still goes over `updateGameState`.
 */
class VoyagerGameSaveTest {

    private val servers = mutableListOf<MockWebServer>()
    private val post = MiniSudokuVoyagerFixtures.post()
    private val fresh = MiniSudokuVoyagerFixtures.unplayedPage
    private val started = MiniSudokuVoyagerFixtures.inProgressPage
    private val saved = MiniSudokuVoyagerFixtures.winResponse

    @After
    fun tearDown() = servers.forEach { it.shutdown() }

    @Test
    fun `the read of a voyager play is one GET of today's game by type with the session, and parses`() = runBlocking {
        val server = server(json(MiniSudokuVoyagerFixtures.unplayedGame))

        val read = server.poster().gamePage(post)

        assertEquals(LinkedInRead.Ok(fresh), read)
        assertEquals(1, server.requestCount)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals(GAME_PATH, request.path)
        assertRead(request)
    }

    @Test
    fun `a read that is refused, is not JSON or is not the game is a rejection`() {
        assertEquals(pageRejected(Kind.SessionExpired), page(status(401), json(MiniSudokuVoyagerFixtures.unplayedGame)))
        assertEquals(pageRejected(Kind.Challenge), page(html(MiniSudokuVoyagerFixtures.unplayedGame)))
        assertEquals(pageRejected(Kind.Unexpected), page(json("{}")))
    }

    @Test
    fun `a fresh game is saved as the opening and then the win, each one POST of the captured body`() = runBlocking {
        val server = server(json(saved), json(saved))

        val sent = server.poster().send(post, fresh)

        assertEquals(LinkedInSendResult.Posted, sent)
        assertEquals(2, server.requestCount)
        val opening = server.takeRequest()
        val win = server.takeRequest()
        listOf(opening, win).forEach { request ->
            assertEquals("POST", request.method)
            assertEquals(SAVE_PATH, request.path)
            assertEquals("application/json; charset=UTF-8", request.getHeader("Content-Type"))
            assertSave(request)
        }
        assertEquals(MiniSudokuVoyagerFixtures.openingRequest, body(opening))
        assertEquals(MiniSudokuVoyagerFixtures.winRequest, body(win))
    }

    @Test
    fun `each save carries the capture's accept, LinkedIn's origin and the device timezone in x-li-track`() = runBlocking {
        val server = server(json(saved), json(saved))

        server.poster(zone = ZoneId.of("Asia/Kolkata")).send(post, fresh)

        repeat(2) {
            val request = server.takeRequest()
            assertEquals("application/vnd.linkedin.normalized+json", request.getHeader("accept"))
            assertEquals("https://www.linkedin.com", request.getHeader("origin"))
            val track = Json.parseToJsonElement(request.getHeader("x-li-track")!!).jsonObject
            assertEquals("Asia/Kolkata", track.getValue("timezone").jsonPrimitive.content)
            assertEquals("5.5", track.getValue("timezoneOffset").jsonPrimitive.content)
        }
    }

    @Test
    fun `a game already in progress gets the win alone`() = runBlocking {
        val server = server(json(saved), json(saved))

        val sent = server.poster().send(post, started)

        assertEquals(LinkedInSendResult.Posted, sent)
        assertEquals(1, server.requestCount)
        assertEquals(MiniSudokuVoyagerFixtures.winRequest, body(server.takeRequest()))
    }

    @Test
    fun `an opening that is not Posted is returned and no win is sent`() {
        assertEquals(writeRejected(Kind.SessionExpired), open(status(401), json(saved)))
        assertEquals(writeRejected(Kind.RateLimited), open(status(429), json(saved)))
        assertEquals(writeRejected(Kind.Unexpected), open(json(REFUSED), json(saved)))
        val dropped = MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
        assertEquals(LinkedInSendResult.Unknown, open(dropped, json(saved)))
    }

    @Test
    fun `save statuses map to their outcome class`() {
        assertEquals(LinkedInSendResult.Posted, win(json(saved)))
        assertEquals(writeRejected(Kind.SessionExpired), win(status(401)))
        assertEquals(writeRejected(Kind.SessionExpired), win(status(403)))
        assertEquals(writeRejected(Kind.RateLimited), win(status(429)))
        assertEquals(writeRejected(Kind.Challenge), win(status(999)))
        assertEquals(writeRejected(Kind.Challenge), win(status(302).addHeader("Location", "/feed/"), json(saved)))
        assertEquals(writeRejected(Kind.Unexpected), win(status(500)))
        assertEquals(writeRejected(Kind.Unexpected), win(json(REFUSED)))
        assertEquals(writeRejected(Kind.Unexpected), win(html("<html>Sign in</html>")))
    }

    @Test
    fun `a 409 reads the game once more, and a won record of the same puzzle is AlreadyPlayed`() = runBlocking {
        val server = server(status(409), json(MiniSudokuVoyagerFixtures.solvedGame), json(saved))

        val sent = server.poster().send(post, started)

        assertEquals(LinkedInSendResult.AlreadyPlayed, sent)
        assertEquals(2, server.requestCount)
        assertEquals("POST", server.takeRequest().method)
        val reread = server.takeRequest()
        assertEquals("GET", reread.method)
        assertEquals(GAME_PATH, reread.path)
        assertRead(reread)
    }

    @Test
    fun `a 409 on the opening stops there and no win follows`() = runBlocking {
        val server = server(status(409), json(MiniSudokuVoyagerFixtures.solvedGame), json(saved))

        val sent = server.poster().send(post, fresh)

        assertEquals(LinkedInSendResult.AlreadyPlayed, sent)
        assertEquals(listOf("POST", "GET"), List(server.requestCount) { server.takeRequest().method })
    }

    @Test
    fun `a 409 over a record that is not this puzzle won is a rejection, never a post`() {
        val otherPuzzle = MiniSudokuVoyagerFixtures.solvedGame.replace("\"puzzleId\": 463", "\"puzzleId\": 464")
        assertNotEquals(MiniSudokuVoyagerFixtures.solvedGame, otherPuzzle)

        assertEquals(writeRejected(Kind.Unexpected), conflict(json(MiniSudokuVoyagerFixtures.unplayedGame)))
        assertEquals(writeRejected(Kind.Unexpected), conflict(json(MiniSudokuVoyagerFixtures.inProgressGame)))
        assertEquals(writeRejected(Kind.Unexpected), conflict(json(otherPuzzle)))
    }

    @Test
    fun `a 409 whose re-read fails answers as the read and is not read again`() {
        assertEquals(writeRejected(Kind.SessionExpired), conflict(status(401)))
        assertEquals(writeRejected(Kind.Challenge), conflict(html(MiniSudokuVoyagerFixtures.solvedGame)))
        assertEquals(writeRejected(Kind.Unexpected), conflict(json("{}")))
        val dropped = MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
        assertEquals(LinkedInSendResult.Offline, conflict(dropped))
    }

    @Test
    fun `a disconnect after the save was written is Unknown and is not retried`() {
        val dropped = MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)

        assertEquals(LinkedInSendResult.Unknown, win(dropped, json(saved)))
    }

    @Test
    fun `a refused connection is Offline, nothing was written`() = runBlocking {
        val server = server(json(saved))
        val poster = server.poster()
        server.shutdown()

        assertEquals(LinkedInSendResult.Offline, poster.send(post, fresh))
        assertEquals(LinkedInRead.Offline, poster.gamePage(post))
    }

    @Test
    fun `cancelling a save in flight is cancellation, not Unknown`() = runBlocking {
        val server = server(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val poster = server.poster()
        val returned = AtomicReference<LinkedInSendResult?>()

        val call = async(Dispatchers.Default) { poster.send(post, started).also(returned::set) }
        server.takeRequest()
        call.cancel()

        assertTrue(runCatching { call.await() }.exceptionOrNull() is CancellationException)
        assertNull(returned.get())
    }

    @Test
    fun `no session means no request for the read or the save`() = runBlocking {
        val server = server(json(MiniSudokuVoyagerFixtures.unplayedGame), json(saved))
        val poster = server.poster(session = null)

        assertEquals(LinkedInRead.SignedOut, poster.gamePage(post))
        assertEquals(LinkedInSendResult.SignedOut, poster.send(post, fresh))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a session a header cannot carry is a rejection that quotes nothing and sends nothing`() = runBlocking {
        val server = server(json(MiniSudokuVoyagerFixtures.unplayedGame), json(saved))
        val unsendable = LinkedInAccountSession(SESSION.cookieHeader, "ajax:SENTINEL-CSRF\n", SESSION.userAgent)
        val poster = server.poster(unsendable)

        val read = poster.gamePage(post)
        val sent = poster.send(post, fresh)

        assertEquals(pageRejected(Kind.Unexpected), read)
        assertEquals(writeRejected(Kind.Unexpected), sent)
        assertFalse((read.toString() + sent.toString()).contains("SENTINEL"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `no result keeps the session or a response body`() = runBlocking {
        val server = server(
            status(401).setBody(SECRET_BODY),
            json(SECRET_JSON),
            status(500).setBody(SECRET_BODY),
            json(SECRET_JSON),
            status(409).setBody(SECRET_BODY),
            json(SECRET_JSON),
        )
        val poster = server.poster()

        val results = listOf(
            poster.gamePage(post),
            poster.gamePage(post),
            poster.send(post, started),
            poster.send(post, started),
            poster.send(post, started),
        )

        assertEquals(6, server.requestCount)
        assertFalse(results.toString().contains("SENTINEL"))
    }

    @Test
    fun `a play without a voyager board is still read and sent over updateGameState`() = runBlocking {
        val server = server(
            rsc(LinkedInPostFixtures.text("zip-page-daily-solved.rsc")),
            rsc(LinkedInPostFixtures.text("zip-update-game-state-win.response.rsc")),
        )
        val poster = server.poster()

        val read = poster.gamePage(LinkedInPostFixtures.post())
        val sent = poster.send(LinkedInPostFixtures.post(), LinkedInPostFixtures.dailyPage)

        assertEquals(LinkedInRead.Ok(LinkedInPostFixtures.dailyPage), read)
        assertEquals(LinkedInSendResult.Posted, sent)
        val page = server.takeRequest()
        assertEquals("GET", page.method)
        assertEquals("/flagship-web/games/zip", page.path)
        val write = server.takeRequest()
        assertEquals("POST", write.method)
        assertEquals("/flagship-web/rsc-action/actions/server-request", write.requestUrl?.encodedPath)
    }

    private fun pageRejected(kind: Kind) = LinkedInRead.Rejected(kind)

    private fun writeRejected(kind: Kind) = LinkedInSendResult.Rejected(kind)

    private fun page(vararg responses: MockResponse): LinkedInRead<LinkedInGamePage> = runBlocking {
        val server = server(*responses)
        val result = server.poster().gamePage(post)
        // The rest are bait only a second request would reach.
        assertEquals(1, server.requestCount)
        result
    }

    /** A send over a fresh game whose opening gets the first of [responses]: one request, the rest are bait. */
    private fun open(vararg responses: MockResponse): LinkedInSendResult = send(fresh, 1, *responses)

    /** A send over a game in progress, so the one save is the win. */
    private fun win(vararg responses: MockResponse): LinkedInSendResult = send(started, 1, *responses)

    /** A win refused with a 409 and then [reread]: the save, one read, and no more. */
    private fun conflict(reread: MockResponse): LinkedInSendResult = send(started, 2, status(409), reread, json(saved))

    private fun send(page: LinkedInGamePage, requests: Int, vararg responses: MockResponse): LinkedInSendResult = runBlocking {
        val server = server(*responses)
        val result = server.poster().send(post, page)
        assertEquals(requests, server.requestCount)
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

    /** A voyager read: the session and the three headers of the account reads, and nothing a write adds. */
    private fun assertRead(request: RecordedRequest) {
        assertSession(request)
        assertEquals("application/vnd.linkedin.normalized+json+2.1", request.getHeader("accept"))
        assertOmitted(request, SAVE_ONLY + NEVER_SENT)
    }

    /** A voyager save: the capture's headers whose values are known. The redacted ones are not made up. */
    private fun assertSave(request: RecordedRequest) {
        assertSession(request)
        assertEquals("application/vnd.linkedin.normalized+json", request.getHeader("accept"))
        assertEquals("https://www.linkedin.com", request.getHeader("origin"))
        assertEquals("UTC", Json.parseToJsonElement(request.getHeader("x-li-track")!!).jsonObject.getValue("timezone").jsonPrimitive.content)
        assertOmitted(request, NEVER_SENT)
    }

    private fun assertSession(request: RecordedRequest) {
        assertEquals(listOf(SESSION.cookieHeader), request.headers.values("Cookie"))
        assertEquals(SESSION.csrfToken, request.getHeader("csrf-token"))
        assertEquals(SESSION.userAgent, request.getHeader("User-Agent"))
        assertEquals("2.0.0", request.getHeader("x-restli-protocol-version"))
        assertEquals("en_US", request.getHeader("x-li-lang"))
    }

    private fun assertOmitted(request: RecordedRequest, omitted: Set<String>) {
        val names = request.headers.names().map(String::lowercase)
        assertEquals(emptyList<String>(), names.filter { it.startsWith("sec-") || it in omitted })
    }

    private fun body(request: RecordedRequest): JsonObject = Json.parseToJsonElement(request.body.readUtf8()).jsonObject

    private fun status(code: Int) = MockResponse().setResponseCode(code)

    private fun json(body: String) = MockResponse().setBody(body)
        .setHeader("Content-Type", "application/vnd.linkedin.normalized+json+2.1; charset=UTF-8")

    private fun html(body: String) = MockResponse().setBody(body).setHeader("Content-Type", "text/html; charset=UTF-8")

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
        const val GAME_PATH =
            "/voyager/api/graphql?includeWebMetadata=true&variables=(gameTypeId:7)" +
                "&queryId=voyagerIdentityDashGames.22685dd4999129d6bed79f33e65dcbc5"
        const val SAVE_PATH =
            "/voyager/api/graphql?action=execute&queryId=voyagerIdentityDashGames.f8508525e36bee5f9a5ab6b637854d87"
        const val SECRET_BODY = "SENTINEL-BODY"
        const val SECRET_JSON = """{"marker":"SENTINEL-BODY"}"""

        /** A 200 that carries a GraphQL error and no save result. */
        const val REFUSED = """{"data":{"data":null},"errors":[{"message":"nope"}]}"""
        val SAVE_ONLY = setOf("x-li-track", "origin")
        val NEVER_SENT = setOf("x-li-page-instance", "x-li-pem-metadata", "x-li-anchor-page-key", "referer")
    }
}
