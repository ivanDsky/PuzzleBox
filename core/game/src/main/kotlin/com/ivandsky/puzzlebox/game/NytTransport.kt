package com.ivandsky.puzzlebox.game

/**
 * The NYT network seam (ADR-0014): one GET of `path` relative to the NYT host
 * (`https://www.nytimes.com/`), e.g. `svc/strands/v2/2026-11-05.json`. Pure; core:network
 * binds the HTTP implementation under this type (never as a second [FeedTransport]).
 *
 * Returns the response body of a 2xx, or a typed failure: HTTP 404 → [FetchError.NotFound];
 * offline, timeout, any other status or an unreadable body → [FetchError.Unavailable].
 * The transport never parses game data — the game's daily source owns parsing and the trust gate.
 */
fun interface NytTransport {
    suspend fun get(path: String): FetchResult<String>
}
