# ADR-0020 — Shared puzzles and their boards on Firestore (Spark plan)

Date: 2026-11-06 · Status: accepted · Source: editor spec A14–A21, A25; intake A5–A7, A11,
share_auth/open_auth; round 3 (strip the solution, rules tests); round 5 (cap, solves count,
board after unpublish); spec checkpoint 2026-11-06 (OQ1 board reads) · Amends ADR-0013
(leaderboard stays Zip-only and time-first) · Amended 2026-11-16 by the nav rethink
(`.scratch/nav-rethink/spec.md` C1, C2, C3: decisions 1 and 2 and the rejected public `list`;
public puzzles can be listed, see the amendment at the end)

## Context
Today Firestore holds only the daily Zip boards. Rules deny everything else, and an entry's
ref must match a Zip date ref [ev: backend/firebase/firestore.rules:19-24,42]. The project is
on the Spark plan: no Cloud Functions, no server render. Writes already use a
create-if-absent transaction [ev: backend/firebase/.../LeaderboardStore.kt:52]. Friends must
open a puzzle signed out, a published puzzle must not hand its answer to anyone who reads the
document, and an author must not be able to flood the project.

## Decision
1. **`puzzles/{code}` holds the puzzle without its solution.** The document is
   `{formatVersion: 1, gameId, variant, shareCodecVersion, payload, title, authorName, note,
   authorUid, authorTimeMs, authorHints, createdAt, unpublished}`. `payload` is the game's
   versioned share codec (`GameEditor.encodeShare`, ADR-0019), base64, with the solution
   stripped. The recipient's device decodes it, re-solves at limit 2 and accepts only
   `Unique`, then rebuilds the full `Puzzle` through the game's existing codec.
2. **Public reads are get-only.** On `puzzles/{code}`: `get` is public and `list` is false.
   `create` requires the author's uid, a valid shape, the text caps and the quota.
   `update` is author-only and can only flip `unpublished` from false to true. `delete` is
   false. A code is never reused.
3. **Quota in rules.** `authors/{uid}` = `{day (UTC yyyy-MM-dd), count, last?}`, written in the
   same transaction as the create. The rule checks `getAfter` with `count ≤ 20`, and the count
   resets when `day` changes. `last` is the code the bump is for; when present, the create must
   be for that code, so one bump covers one create. It is optional until a later rules-only
   change requires it.
4. **One board per puzzle.** `puzzles/{code}/entries/{uid}` mirrors the daily `validEntry`
   shape [ev: firestore.rules:33-43] with `ref == 'custom/' + gameId + '/' + code`. Only a
   signed-in non-author may create an entry, and only while the puzzle is published.
   `update` and `delete` are false. `read` needs sign-in, as for dailies (checkpoint OQ1).
   Signed out, the card and the result show the pinned author row, your local time and a
   sign-in prompt. The query honours the game's `RankOrder` (ADR-0013), and Strands gets a
   hints-then-time index.
5. **The code and an idempotent publish.** Codes are 6 characters of Crockford base32, shown
   `K7Q-2MX`; input ignores case, dashes and spaces. The code is saved on the draft before
   the create-if-absent transaction. On retry, an existing doc with `authorUid == me` counts
   as success, and any other doc means generate a new code, so one draft never yields two
   codes.
6. **Links.** `https://puzzlebox-f3356.web.app/p/<CODE>` is a verified App Link. Hosting
   serves `assetlinks.json` and a static code page with a generic OG card. `MainActivity`
   becomes `singleTask`. It applies a link once, as `pushOnce(FriendPuzzleKey(code))` on
   the Play stack, and pops nothing [ev: MainActivity.kt:30-66].
7. **Seams.** `SharedPuzzleRepository` (publish, unpublish, fetch by code) and
   `CustomBoardRepository` are owned by `core:data`, with fakes in `core:testing`. Their
   Firestore adapters live in `backend:firebase` beside `FirestoreLeaderboardStore`.
   `CustomBoardSubmitter` posts custom records. After 3 rejections it gives up and marks the
   record `LocalOnly`. The daily watcher is untouched.
8. **The daily block stays byte-identical.** The `leaderboards` rules and the deny-all
   default do not change.

## Consequences
- Rules billing: each entry write costs one billed `get()` and each publish costs one.
  Opening a puzzle costs one public get. The author's "{k} solves" is a `count()`
  aggregation, cached in `published_puzzles` and refreshed at most every 10 minutes.
- Code guessing is bounded by get-only reads and the Spark read quota. App Check is out of
  scope (spec O7).
- Unpublish is a tombstone. Players who already opened the puzzle keep playing locally, and
  its board stays readable but closed to new entries.
- Each deploy of rules, indexes or Hosting is followed by a scripted allow/deny matrix run
  by hand.
- ADR-0013's "a Strands leaderboard later needs its own query/index and a rules change" is
  realised here for custom boards only. Daily boards stay Zip-only and time-first.

## Rejected alternatives
- The solution in the document: anyone who can `get` the document reads the answer, and
  the recipient can derive it anyway.
- Public `list` or public board reads: they let anyone enumerate codes and boards, and the
  user ruled that boards stay behind sign-in (checkpoint OQ1).
- Cloud Functions for quota, OG render or verification: they need the Blaze plan.
- Deleting a puzzle: a delete would let the code be reused and would orphan boards that
  other players have entries on.
- An emulator-backed rules suite: the user chose the manual matrix (round 3).
- Entries written by the author: the author's time is pinned from the test solve and never
  ranks.

*(Amended 2026-11-07 by ADR-0024: daily boards are no longer Zip-only or time-first, and decision 8 "the daily block stays byte-identical" no longer holds. The `leaderboards` rules now take the five-id allow-list and a day window [server UTC date −2, +1]; first-write-stands, immutability, board-id consistency and the custom-board rules in this ADR are unchanged.)*

## Amendment 2026-11-16: community puzzles (nav rethink C1, C2, C3)
An author now chooses at publish whether a puzzle is public or link only, and public puzzles
show in a community list in Games › Custom. That changes decisions 1 and 2 and narrows one
rejected alternative. Everything else above stands: the quota, who may edit what, the boards,
the code and the links.

1. **Two optional fields on `puzzles/{code}` (decision 1).** `visibility` is `public` or
   `link`. A document without it is link only, so every puzzle published before this change
   stays link only. `difficulty` is `easy`, `medium`, `hard` or `expert`, and is absent when
   the puzzle needs guessing or the game has no ladder. Both are set at create and never
   change: `update` still admits only the `unpublished` flip. The app writes `visibility`
   only for a public puzzle, so a link-only document is the one older app versions write.
   Older versions read the new documents, since they ignore fields they do not know.
   `formatVersion` stays 1.
2. **`list` is allowed for public, still-published puzzles only (decision 2).** The rule is
   `resource.data.visibility == 'public' && resource.data.unpublished == false &&
   request.query.limit <= 30`. Firestore refuses a query whole unless its own constraints
   guarantee that every result passes, so a query must filter on both fields and carry a
   limit of at most 30. Like `get`, it needs no sign-in. Nobody can enumerate link-only
   puzzles, their author included. `get` stays public for every puzzle: the code is a
   shortcut to any puzzle, never a gate.
3. **Indexes.** Four composite indexes on `puzzles`, each starting `visibility, unpublished`
   and ending `createdAt` descending: those three alone, with `gameId`, with `difficulty`,
   and with both. They serve "new today", newest first, filtered by game and by difficulty.
4. **Reports.** `puzzles/{code}/reports/{uid}` is `{formatVersion: 1, uid, createdAt}`, one
   per puzzle and reporter. A signed-in player who is not the author creates it, and the
   puzzle must exist. No client can read, list, change or delete a report, so a second
   report by the same player is refused. Spark has no server, so a report does two things
   only. It hides the puzzle on the reporter's device, and it leaves a record for the project
   owner to review in the Firebase console, who can then unpublish the puzzle as admin.
5. **The hide set stays on the device.** The puzzles a player reported or hid are kept
   locally and are not synced.

Consequences:
- Public titles, notes and author names cannot be moderated server-side. The mitigations are
  the report record, the text caps, author unpublish and the 20-a-day quota (C1).
- The rejection of "public `list` or public board reads" now holds for link-only puzzles and
  for boards. Boards stay behind sign-in.
- A list bills one read per puzzle returned, at most 30 a query. A report costs one billed
  `get()` in rules.
- No "popular" order: a counter without a server can be faked (C2).
- The community stands in for a generated Hard daily, so no code changes for that. The Hard
  daily is revisited if the community stays thin (C3).
- Where Custom sits in Games is ADR-0027's amendment, not this one.
- The matrix gains cases for the two fields, the list rule and reports. It can only mock a
  query's limit, so an emulator test (`FirestoreCommunityListEmulatorTest`) checks which real
  queries are allowed and which are refused.
