package com.ivandsky.puzzlebox.network.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInAccountSession
import com.ivandsky.puzzlebox.game.linkedin.LinkedInAccountSource
import com.ivandsky.puzzlebox.game.linkedin.LinkedInBoard
import com.ivandsky.puzzlebox.game.linkedin.LinkedInOwnResult
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead.Rejected.Kind
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSessionCookies
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.ResponseBody
import retrofit2.Response

/**
 * Reads the player's own LinkedIn account with their session: one GET per call, or none when signed out.
 *
 * Anything but a 200 carrying JSON of the expected shape is a rejection, never retried here: the caller
 * pauses on the first one. Nothing of the session or of a response body leaves this class except the
 * parsed value.
 */
internal class VoyagerLinkedInAccountSource(
    private val api: LinkedInAccountApi,
    private val session: LinkedInSessionCookies,
) : LinkedInAccountSource {

    override suspend fun check(gameTypeId: Int): LinkedInRead<String> =
        read(VoyagerQueries.TODAYS_GAME, VoyagerQueries.todaysGameVariables(gameTypeId), VoyagerTodayParser::profileId)

    override suspend fun ownResult(
        profileId: String,
        gameTypeId: Int,
        puzzleNumber: Int,
    ): LinkedInRead<LinkedInOwnResult> {
        val urn = VoyagerQueries.gameUrn(profileId, gameTypeId, puzzleNumber)
        return read(VoyagerQueries.END_PAGE, VoyagerQueries.endPageVariables(urn)) {
            VoyagerOwnResultParser.parse(it, urn)
        }
    }

    override suspend fun board(
        profileId: String,
        gameTypeId: Int,
        todayNumber: Int,
        delta: Int,
    ): LinkedInRead<LinkedInBoard> {
        // Today's URN even for a past day: the delta selects the day, and a past URN returns today's board.
        val todayUrn = VoyagerQueries.gameUrn(profileId, gameTypeId, todayNumber)
        return read(VoyagerQueries.CONNECTIONS, VoyagerQueries.connectionsVariables(todayUrn, delta), VoyagerBoardParser::parse)
    }

    private suspend fun <T : Any> read(queryId: String, variables: String, parse: (String) -> T?): LinkedInRead<T> {
        val headers = session.current() ?: return LinkedInRead.SignedOut
        return try {
            classify(send(headers, variables, queryId), parse)
        } catch (_: IOException) {
            // OkHttp reports a cancelled call as an IOException; that is cancellation, not a dead network.
            currentCoroutineContext().ensureActive()
            LinkedInRead.Offline
        }
    }

    private suspend fun send(headers: LinkedInAccountSession, variables: String, queryId: String): Response<ResponseBody> =
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
            // original is dropped, not chained, so the token cannot ride an exception out of here.
            throw IllegalArgumentException(UNSENDABLE)
        }

    private fun <T : Any> classify(response: Response<ResponseBody>, parse: (String) -> T?): LinkedInRead<T> {
        val code = response.code()
        if (code != HTTP_OK) {
            response.errorBody()?.close()
            response.body()?.close()
            return LinkedInRead.Rejected(rejection(code))
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

    private fun rejection(code: Int): Kind = when (code) {
        HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> Kind.SessionExpired
        LINKEDIN_BLOCKED, in REDIRECTS -> Kind.Challenge
        HTTP_TOO_MANY_REQUESTS -> Kind.RateLimited
        else -> Kind.Unexpected
    }

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_TOO_MANY_REQUESTS = 429

        /** LinkedIn's own status for a request it takes for automation. */
        const val LINKEDIN_BLOCKED = 999
        val REDIRECTS = 300..399
        const val CONTENT_TYPE = "Content-Type"
        const val JSON = "json"
        const val UNSENDABLE = "The LinkedIn request could not be built; the cause is withheld because it can quote the session"
    }
}
