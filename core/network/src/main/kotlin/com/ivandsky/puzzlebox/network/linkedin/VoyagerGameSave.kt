package com.ivandsky.puzzlebox.network.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInAccountSession
import com.ivandsky.puzzlebox.game.linkedin.LinkedInGamePage
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPost
import com.ivandsky.puzzlebox.game.linkedin.LinkedInProgress
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead.Rejected.Kind
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSendResult
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSessionCookies
import com.ivandsky.puzzlebox.game.linkedin.LinkedInVoyagerPlay
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Response

/**
 * The voyager wire of a post, for a game LinkedIn has not moved to `updateGameState` (ADR-0032 §11): the read
 * of today's game with the member's stored record, and the save of the opening and then the win, or the win
 * alone over a record already in progress. A send stops at an opening that is not `Posted` and returns it.
 *
 * Never retries a save. A 409 says the stored record moved on under this one, so the game is read once more
 * to learn what is there: a won record of the same puzzle is `AlreadyPlayed`, anything else is a rejection.
 * Nothing of the session, a request body or a response body leaves this class except the parsed value.
 */
internal class VoyagerGameSave(
    private val api: LinkedInPostApi,
    private val graphql: VoyagerGraphql,
    private val session: LinkedInSessionCookies,
    private val zone: () -> ZoneId,
) {

    /** One GET of today's game of [post]'s type. A game with no readable stored record is Rejected(Unexpected). */
    suspend fun game(post: LinkedInPost): LinkedInRead<LinkedInGamePage> = graphql.read(
        VoyagerQueries.TODAYS_GAME_RECORD,
        VoyagerQueries.todaysGameVariables(post.gameTypeId),
        VoyagerGamePageParser::parse,
    )

    suspend fun send(post: LinkedInPost, play: LinkedInVoyagerPlay, page: LinkedInGamePage): LinkedInSendResult {
        val headers = session.current() ?: return LinkedInSendResult.SignedOut
        // A record already in progress is what the web client reloads to after a failed win: it saves the win alone.
        if (page.progress != LinkedInProgress.Started) {
            val opened = write(headers, post, VoyagerSaveGameBody.opening(post, play, page))
            if (opened != LinkedInSendResult.Posted) return opened
        }
        return write(headers, post, VoyagerSaveGameBody.win(post, play, page))
    }

    /** One POST of [json]: its answer, Unknown once the body is out and the call fails, Offline before. */
    private suspend fun write(headers: LinkedInAccountSession, post: LinkedInPost, json: String): LinkedInSendResult {
        val body = json.toByteArray().toRequestBody(JSON)
        val written = AtomicBoolean(false)
        return try {
            val response = request(headers, body, written) ?: return LinkedInSendResult.Rejected(Kind.Unexpected)
            if (response.code() == HTTP_CONFLICT) {
                close(response)
                conflict(post)
            } else {
                voyagerSaveOutcome(response.code(), answer(response))
            }
        } catch (_: IOException) {
            // OkHttp reports a cancelled call as an IOException; that is cancellation, not a dead network.
            currentCoroutineContext().ensureActive()
            // Once the body is out the write may have landed; before that nothing was sent.
            if (written.get()) LinkedInSendResult.Unknown else LinkedInSendResult.Offline
        }
    }

    /** Null when no request could be built from [headers]; nothing was sent then. */
    private suspend fun request(headers: LinkedInAccountSession, body: RequestBody, written: AtomicBoolean): Response<ResponseBody>? =
        try {
            api.saveGame(
                action = VoyagerQueries.EXECUTE,
                queryId = VoyagerQueries.SAVE_GAME,
                cookie = headers.cookieHeader,
                csrfToken = headers.csrfToken,
                userAgent = headers.userAgent,
                track = LinkedInTrack.header(zone(), Instant.now()),
                body = body,
                written = written,
            )
        } catch (_: IllegalArgumentException) {
            // OkHttp quotes a header value it refuses in its message, and redacts only the cookie. The
            // exception is dropped, not kept or chained, so the token cannot ride out of here.
            null
        }

    /** What a refused save left behind, read back: only a won record of [post]'s own puzzle is the result already there. */
    private suspend fun conflict(post: LinkedInPost): LinkedInSendResult = when (val read = game(post)) {
        is LinkedInRead.Ok ->
            if (read.value.puzzleId == post.puzzleNumber.toLong() && read.value.progress == LinkedInProgress.Won) {
                LinkedInSendResult.AlreadyPlayed
            } else {
                LinkedInSendResult.Rejected(Kind.Unexpected)
            }
        is LinkedInRead.Rejected -> LinkedInSendResult.Rejected(read.kind)
        LinkedInRead.SignedOut -> LinkedInSendResult.SignedOut
        // The save was refused, so nothing of it landed: the dead network is the read's, and the post can be tried again.
        LinkedInRead.Offline -> LinkedInSendResult.Offline
    }

    /** The body of a 200, or empty: any other status is classified without reading it. */
    private fun answer(response: Response<ResponseBody>): String {
        if (response.code() != HTTP_OK) {
            close(response)
            return ""
        }
        return response.body()?.let(::capped).orEmpty()
    }

    private fun close(response: Response<ResponseBody>) {
        response.errorBody()?.close()
        response.body()?.close()
    }

    /** The body as text, or null when it is longer than [BODY_CAP_BYTES]. Always closes the body. */
    private fun capped(body: ResponseBody): String? = body.use {
        if (it.contentLength() > BODY_CAP_BYTES) return@use null
        val source = it.source()
        // Asking for one byte past the cap buffers it only when the body is over: the cap is never crossed unseen.
        source.request(BODY_CAP_BYTES + 1)
        if (source.buffer.size > BODY_CAP_BYTES) null else source.readUtf8()
    }

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_CONFLICT = 409
        const val BODY_CAP_BYTES = 1_048_576L
        val JSON = "application/json; charset=UTF-8".toMediaType()
    }
}
