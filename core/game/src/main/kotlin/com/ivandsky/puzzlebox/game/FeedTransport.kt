package com.ivandsky.puzzlebox.game

/**
 * What one game's LinkedIn feed asks of the network (research R-19-1, ADR-0013). The
 * transport walks a fixed ladder and returns the first body [accepts] approves:
 *
 * 1. Streaming screen-load GET of `/<path>?skipStartScreen=true` with the
 *    `x-li-rsc-stream: true` + `x-li-initial-url: /<path>` pair (stateless, csrf-free).
 *    A zero-byte 200 with a `location` header is LinkedIn's per-IP throttle. Its
 *    `JSESSIONID` cookie seeds the session the later rungs sign with.
 * 2. Persisted-query GET of `voyager/api/graphql?variables=<v>&queryId=<id>` when
 *    [persistedQuery] is set (cookie jar + csrf echo; both params verbatim).
 * 3. GraphQL POST of [graphqlBody] when set (session seams: cookie jar + csrf echo).
 * 4. Plain page-shell GET of `/<path>` when [scanPageShell].
 *
 * @param path route relative to the LinkedIn host, e.g. `games/zip/`
 * @param objectName the payload object the game extracts, e.g. `trailGamePuzzle` (diagnostics only)
 * @param accepts the game's extractor verdict: true when the body carries its puzzle
 */
class FeedRequest(
    val path: String,
    val objectName: String,
    val accepts: (body: String) -> Boolean,
    val graphqlBody: String? = null,
    val persistedQuery: PersistedQuery? = null,
    val scanPageShell: Boolean = false,
)

/**
 * The network seam a game's daily source fetches through (pure; core/data binds the
 * Retrofit implementation). Throttle → [FetchError.Throttled]; anything else that yields
 * no accepted body → [FetchError.Unavailable] with the ladder's reasons.
 */
fun interface FeedTransport {
    suspend fun fetch(request: FeedRequest): FetchResult<String>
}
