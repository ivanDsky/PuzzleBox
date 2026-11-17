package com.ivandsky.puzzlebox.network.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInAccountSession
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead.Rejected.Kind
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSessionCookies
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.ResponseBody
import retrofit2.Response

/**
 * One Voyager GraphQL read with the player's session: one GET per call, or none when signed out. The account
 * reads and the voyager save's read of the game both go through here, so they classify an answer the same way.
 *
 * Anything but a 200 carrying JSON of the expected shape is a rejection, never retried here: the caller
 * pauses on the first one. Nothing of the session or of a response body leaves this class except the
 * parsed value.
 */
internal class VoyagerGraphql(
    private val api: LinkedInAccountApi,
    private val session: LinkedInSessionCookies,
) {

    suspend fun <T : Any> read(queryId: String, variables: String, parse: (String) -> T?): LinkedInRead<T> {
        val headers = session.current() ?: return LinkedInRead.SignedOut
        return try {
            // A session no header can carry is as useless as a refused one: it pauses the link, so the player
            // is asked to reconnect instead of every view of the day failing.
            val response = send(headers, variables, queryId) ?: return LinkedInRead.Rejected(Kind.Unexpected)
            classify(response, parse)
        } catch (_: IOException) {
            // OkHttp reports a cancelled call as an IOException; that is cancellation, not a dead network.
            currentCoroutineContext().ensureActive()
            LinkedInRead.Offline
        }
    }

    /** Null when no request could be built from [headers]; nothing was sent then. */
    private suspend fun send(headers: LinkedInAccountSession, variables: String, queryId: String): Response<ResponseBody>? =
        try {
            api.graphql(
                cookie = headers.cookieHeader,
                csrfToken = headers.csrfToken,
                userAgent = headers.userAgent,
                includeWebMetadata = true,
                variables = variables,
                queryId = queryId,
            )
        } catch (_: IllegalArgumentException) {
            // OkHttp quotes a header value it refuses in its message, and redacts only the cookie. The
            // exception is dropped, not kept or chained, so the token cannot ride out of here.
            null
        }

    private fun <T : Any> classify(response: Response<ResponseBody>, parse: (String) -> T?): LinkedInRead<T> {
        val code = response.code()
        if (code != HTTP_OK) {
            response.errorBody()?.close()
            response.body()?.close()
            return LinkedInRead.Rejected(linkedInRejection(code))
        }
        val body = response.body() ?: return LinkedInRead.Rejected(Kind.Unexpected)
        // A login wall or a challenge page answers 200 with HTML.
        if (response.headers()[CONTENT_TYPE]?.contains(JSON, ignoreCase = true) != true) {
            body.close()
            return LinkedInRead.Rejected(Kind.Challenge)
        }
        val value = parse(body.string()) ?: return LinkedInRead.Rejected(Kind.Unexpected)
        return LinkedInRead.Ok(value)
    }

    private companion object {
        const val HTTP_OK = 200
        const val CONTENT_TYPE = "Content-Type"
        const val JSON = "json"
    }
}
