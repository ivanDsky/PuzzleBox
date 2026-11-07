# ADR-0020 — Shared puzzles and their boards on Firestore (Spark plan)

Date: 2026-11-06 · Status: accepted · Source: editor spec A14–A21, A25; intake A5–A7, A11,
share_auth/open_auth; round 3 (strip the solution, rules tests); round 5 (cap, solves count,
board after unpublish); spec checkpoint 2026-11-06 (OQ1 board reads) · Amends ADR-0013
(leaderboard stays Zip-only and time-first)

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
3. **Quota in rules.** `authors/{uid}` = `{day (UTC yyyy-MM-dd), count}`, written in the same
   transaction as the create. The rule checks `getAfter` with `count ≤ 20`, and the count
   resets when `day` changes.
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
