package com.ivandsky.puzzlebox.network.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInAccountSession
import com.ivandsky.puzzlebox.game.linkedin.LinkedInGamePage
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPost
import com.ivandsky.puzzlebox.game.linkedin.LinkedInProgress
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead.Rejected.Kind
import com.ivandsky.puzzlebox.game.linkedin.LinkedInResultPoster
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSendResult
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSessionCookies
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response

/**
 * Posts the player's result with their session: a page read is one request; a send is the opening play and then
 * the win when the game has an opening and the page holds no saved play, else the win alone, and nothing at all when signed out. A send stops at an
 * opening that is not `Posted` and returns it. Never retries; the caller decides what a failure means. A page
 * the stream cannot be read from, and any answer but the exact success, is a rejection that pauses the link. Nothing of the session, a request
 * body or a response body leaves this class except the parsed value.
 */
internal class SduiLinkedInResultPoster(
    private val api: LinkedInPostApi,
    private val session: LinkedInSessionCookies,
    private val clock: () -> Instant,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : LinkedInResultPoster {

    override suspend fun gamePage(post: LinkedInPost): LinkedInRead<LinkedInGamePage> {
        val gamePath = post.gamePath
        val headers = session.current() ?: return LinkedInRead.SignedOut
        return try {
            // A session no header can carry is as useless as a refused one: it is a rejection, not a retry.
            val response = fetchPage(headers, gamePath) ?: return LinkedInRead.Rejected(Kind.Unexpected)
            readPage(response)
        } catch (_: IOException) {
            // OkHttp reports a cancelled call as an IOException; that is cancellation, not a dead network.
            currentCoroutineContext().ensureActive()
            LinkedInRead.Offline
        }
    }

    override suspend fun send(post: LinkedInPost, page: LinkedInGamePage): LinkedInSendResult {
        val headers = session.current() ?: return LinkedInSendResult.SignedOut
        // A page that already holds a saved play is what the web client reloads to after a failed win: it sends the win alone.
        val opening = if (page.progress == LinkedInProgress.Started) null else UpdateGameStateBody.opening(post, page)
        if (opening != null) {
            val opened = write(headers, post, opening)
            if (opened != LinkedInSendResult.Posted) return opened
        }
        return write(headers, post, UpdateGameStateBody.build(post, page))
    }

    /** One POST of [json]: its answer, Unknown once the body is out and the call fails, Offline before. */
    private suspend fun write(headers: LinkedInAccountSession, post: LinkedInPost, json: String): LinkedInSendResult {
        val body = json.toByteArray().toRequestBody(JSON)
        val written = AtomicBoolean(false)
        return try {
            val response = request(headers, post.gamePath, body, written)
                ?: return LinkedInSendResult.Rejected(Kind.Unexpected)
            serverRequestOutcome(response.code(), response.successText())
        } catch (_: IOException) {
            currentCoroutineContext().ensureActive()
            // Once the body is out the write may have landed; before that nothing was sent.
            if (written.get()) LinkedInSendResult.Unknown else LinkedInSendResult.Offline
        }
    }

    /** Null when no request could be built from [headers]; nothing was sent then. */
    private suspend fun fetchPage(headers: LinkedInAccountSession, gamePath: String): Response<ResponseBody>? =
        try {
            val slug = slug(gamePath)
            api.gamePage(
                slug = slug,
                cookie = headers.cookieHeader,
                csrfToken = headers.csrfToken,
                userAgent = headers.userAgent,
                referer = "$ORIGIN/${gamePath.trim('/')}",
                track = track(),
            )
        } catch (_: IllegalArgumentException) {
            // OkHttp quotes a header value it refuses in its message, and redacts only the cookie. The
            // exception is dropped, not kept or chained, so the token cannot ride out of here.
            null
        }

    /** Null when no request could be built from [headers]; nothing was sent then. */
    private suspend fun request(
        headers: LinkedInAccountSession,
        gamePath: String,
        body: RequestBody,
        written: AtomicBoolean,
    ): Response<ResponseBody>? {
        val slug = slug(gamePath)
        return try {
            api.serverRequest(
                sduiid = UPDATE_GAME_STATE,
                cookie = headers.cookieHeader,
                csrfToken = headers.csrfToken,
                userAgent = headers.userAgent,
                referer = "$ORIGIN/${gamePath.trimStart('/')}",
                anchorPageKey = "d_flagship3_games_${slug}_gameboard",
                track = track(),
                body = body,
                written = written,
            )
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun readPage(response: Response<ResponseBody>): LinkedInRead<LinkedInGamePage> {
        val code = response.code()
        if (code != HTTP_OK) {
            response.closeBody()
            return LinkedInRead.Rejected(linkedInRejection(code))
        }
        val text = response.body()?.cappedText() ?: return LinkedInRead.Rejected(Kind.Unexpected)
        val page = SduiGamePageParser.parse(text) ?: return LinkedInRead.Rejected(Kind.Unexpected)
        return LinkedInRead.Ok(page)
    }

    private fun track(): String = LinkedInTrack.header(zone(), clock())

    /** `games/zip/` is the `zip` page: the last segment of the game's LinkedIn route. */
    private fun slug(gamePath: String): String = gamePath.trim('/').substringAfterLast('/')

    private companion object {
        const val ORIGIN = "https://www.linkedin.com"
        const val UPDATE_GAME_STATE = "updateGameState"
        val JSON = "application/json".toMediaType()
    }
}
