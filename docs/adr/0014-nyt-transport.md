# ADR-0014 — NYT transport beside the LinkedIn feed transport

Date: 2026-11-05 · Status: accepted · Source: Strands spec A19; user ruling (generic NYT transport in core:network); goldfish A reuse-7, failure-7/8, verify-7

## Context
`FeedTransport`/`FeedRequest` are LinkedIn-shaped (stream/graphql/page ladder) and bound
once, unqualified, in Koin. Strands reads a stateless public JSON endpoint on the NYT
host. Game modules may only depend on `core:game` contracts, never on `core:network`.

## Decision
- `core:game` declares `NytTransport`: a GET of a path relative to the NYT host,
  returning the response body or a typed `FetchError` (HTTP status incl. 404, offline,
  timeout, unreadable body). No game parsing.
- `core:network` implements it with its own HTTP client and a call timeout, bound in Koin
  under the `NytTransport` type — never a second unqualified `FeedTransport`.
- Strands' daily source is the first consumer and owns all parsing and the trust gate.

## Consequences
- The next NYT game (Connections, Mini...) is a new daily source over the same transport.
- Two adapters exist from day one: the HTTP one and the in-memory test fake.
- Anything more general (auth, rate-limit policy, caching) waits for a second consumer.

## Rejected alternatives
- Reshape `FeedRequest` to carry a host: leaks LinkedIn ladder semantics into NYT.
- Strands-private Retrofit inside `games:strands`: breaks the games → core:game-only rule.
