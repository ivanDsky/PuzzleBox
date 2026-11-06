# ADR-0017 — LinkedIn past days by number from games-by-id

Date: 2026-11-05 · Status: accepted · Source: remote-collection research 02 + 07, spec A2–A6, A17, A23 · Amends ADR-0012 (LinkedIn feeds serve only today) · Builds on ADR-0016 (persisted-query rung)

## Context
LinkedIn expires each Zip, Queens, Tango and Mini Sudoku puzzle at midnight PT, and every
LinkedIn feed in the app served only today (ADR-0012). Research 02 found one anonymous
endpoint that serves any past puzzle: the persisted GraphQL query `games-by-id`
(`voyagerIdentityDashGames.f6c16c0fa3fc425ade72fdb9d4e2e133`), variables
`(gameUrn:<percent-encoded urn:li:fsd_game:(<guest member>,<gameTypeId>,<number>)>)`.
It is keyed by puzzle number; LinkedIn's by-date finder answers `RoutingException` in
production. It does not gate by date (tomorrow's number is served), it answers an unknown
number with `gamePuzzle: null`, and its `puzzleId` field holds *today's* number whatever
was asked — only `entityUrn` component 3 names the edition returned.

## Decision
1. **The by-id rung.** A day before the game's today (`DailySource.fetchPast`, ADR-0012
   as amended by ticket 00) is fetched through `FeedTransport` with the ADR-0016 persisted
   query `games-by-id`; its stream GET only seeds the `JSESSIONID` cookie. The request
   accepts any games-by-id body of the game and never a stream body, which is always
   today's puzzle (A5). Today keeps its live path unchanged. The queryId and the guest
   member id `ACoAAAAAAAABWzs7tPEzjbzdTu0bZDhDZpin_Ds` are constants (the hash is data,
   ADR-0016 §3); the urn is percent-encoded because a raw urn answers 400.
2. **Number from date.** Puzzle number = (day − day1) + 1 on the game's PT day. day1:
   Queens 2024-05-01, Tango 2024-10-08, Zip 2025-03-18, Mini Sudoku 2025-08-12. The rule
   matches today's live numbers and every community sweep (research 07), but LinkedIn
   documents no gap-free guarantee: it is an assumption, pinned per game by a test against
   live-captured fixtures. A skipped or doubled day would surface as an identity mismatch
   (Untrusted), never as a wrong puzzle shown under a date.
3. **Identity rule.** The returned edition is `entityUrn` component 3, never `puzzleId`.
   A body whose urn names another number (or no urn of the game) is terminal
   `FetchError.Untrusted` — accepted by the transport so the ladder does not fall through
   to another rung — and stores nothing. A null `gamePuzzle` is `FetchError.NotFound`.
   The game's own parser and trust gate then run as for today (A6, A7). A number above
   today's is refused as `NotFound` before any request (A4).
4. **One shared building block.** `LinkedInEdition` (core:game) holds the day→number rule,
   the games-by-id request and the identity check for one `gameTypeId`; each LinkedIn
   `DailySource` declares one edition and derives `archiveSince` (= day1) and
   `puzzleNumber` from it. It reads bodies by regex, so core:game gains no JSON
   dependency. No new seam or registry: the existing `DailySource` seam and Koin's
   `getAll<DailySource>()` stay the extension point (A8).
5. **Throttle sharing.** Past-day fetches go through the same `FeedTransport` and so share
   LinkedIn's per-IP throttle and its cooldown with today's fetches (A17). Accepted: a
   player browsing the archive can delay today's refresh by one cooldown.
6. **Terms posture.** Fetch only on the user's tap, once per day per device, cached
   forever, no LinkedIn branding, nothing bundled or pre-seeded in the APK (A23,
   research 08). The exposure is accepted on these terms.

## Consequences
- Each LinkedIn game gains an archive from its #1 with one `LinkedInEdition` literal and a
  `fetchPast` override; its parser must accept the by-id wire shape (no `$type` prefixes,
  `RIGHT`/`DOWN` walls, Tango `ZERO`/`ONE`) into the same canonical v1 model.
- A rotated hash or guest member id surfaces as `Unavailable` and degrades to cache.
- The LinkedIn sources read a clock only to bound the number by today's; which fetch runs
  is still the repository's call.

## Rejected alternatives
- **Splitting `LinkedInEdition`** into a numbering rule, a request builder and an identity
  checker (goldfish B arch-4, FS-9): the request and its identity rule always change
  together when LinkedIn rotates the endpoint; one deep module per shared endpoint keeps
  that knowledge in one place.
- **The by-date finder `games-by-games-by-date`:** production refuses it.
- **Taking the edition from `puzzleId`:** it names today's puzzle in every by-id body.
- **A community archive or own backend store:** out of scope (A2).
