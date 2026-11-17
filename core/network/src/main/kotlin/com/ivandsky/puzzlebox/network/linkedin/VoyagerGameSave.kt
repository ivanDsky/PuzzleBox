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
    private val clock: () -> Instant,
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
                response.closeBody()
                conflict(post)
            } else {
                voyagerSaveOutcome(response.code(), response.successText())
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
                track = LinkedInTrack.header(zone(), clock()),
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

    private companion object {
        val JSON = "application/json; charset=UTF-8".toMediaType()
    }
}
