# ADR-0016 — Persisted-query GraphQL GET rung in the LinkedIn feed transport

Date: 2026-11-05 · Status: accepted · Source: sudoku run research 01, spec A3–A4 · Amends the FeedRequest ladder (research R-19-1)

## Context
Mini Sudoku (LinkedIn `gameTypeId` 7) is served by the legacy Voyager web app, not by the
SDUI stream that Zip, Tango and Queens use. The stream GET of `games/mini-sudoku/` returns
a guest launch page with no puzzle, though it does set `JSESSIONID`. The puzzle comes from a
*persisted* GraphQL query:
`GET voyager/api/graphql?variables=(gameTypeId:7)&queryId=voyagerIdentityDashGames.<hash>`,
which works anonymously when `csrf-token` equals the `JSESSIONID` cookie. The existing
GraphQL rung POSTs a raw body to bare `voyager/api/graphql`, and LinkedIn answers that with
403 for any body (verified live 2026-11-05). So no game can fetch Mini Sudoku through the
current seam.

> **Amended (F1, d7fcb31):** the stream GET sets no `JSESSIONID` — only the plain page GET
> of the game's route does (research 01 §2). Without it the csrf echo is missing and
> LinkedIn answers 403 `CSRF check failed.`

## Decision
1. **`FeedRequest.persistedQuery: PersistedQuery?`** (`core:game`) carries `queryId` and the
   Rest.li `variables` string (e.g. `(gameTypeId:7)`), both sent verbatim.
2. **A new rung in `LinkedInFeedTransport`.** After the stream GET (which seeds the cookie
   jar), and before the POST and page rungs, the transport issues
   `GET voyager/api/graphql?variables=<v>&queryId=<id>`. The query params are encoded as-is,
   so the parentheses and colon reach LinkedIn unescaped. The existing csrf interceptor signs
   it. The response is accepted by the game's extractor like every other rung's.
   Throttle and failure accounting are the same as for the other rungs.

   > **Amended (F1, d7fcb31):** the stream does not seed the jar. Before the first session
   > rung (this GET or the POST), and only while the jar holds no `JSESSIONID`, the
   > transport sends one plain page GET as the session handshake; a 403 on the rung is an
   > `Unavailable` naming it, never retried. A request may skip the stream
   > (`FeedRequest.stream = false`), as ADR-0017's past-day request does.
3. **The hash is data.** Each game keeps its `queryId` as one constant. A rotated hash
   surfaces as `Unavailable` and degrades to cache. There is no guessing and no seed fallback.
4. **Queens' bare-POST GraphQL body is not changed.** It already 403s. The stream rung still
   serves Queens, and migrating Queens to a persisted query is follow-up work.

## Consequences
- `LinkedInGamesApi` gains one `@GET` method. The POST rung and its test stay as they are,
  and a new test pins the GET URL and its order after the stream.
- Any future Voyager-only game reuses the rung by setting `persistedQuery`.
