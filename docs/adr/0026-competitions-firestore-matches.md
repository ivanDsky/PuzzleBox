# ADR-0026 — Competitions: Firestore match documents

Date: 2026-11-08 · Status: accepted · Source: `.scratch/competitions/spec.md` (A3, A6, A7, A18, A21, A23)

## Context
Competitions adds async 1v1 matches. Firebase runs on the Spark plan: no Cloud Functions, so nothing server-side can
compute an outcome, enforce a deadline by job, or hide data except through rules. ADR-0020 owns `puzzles/{code}` and
`authors/{uid}`; ADR-0024 owns daily boards. Both stay unchanged.

## Decision
1. **`matches/{code}`**: 6-char Crockford code in its own namespace. Create-if-absent; the creating client skips codes
   present in `puzzles/{code}`. Fields: `formatVersion: 1, createdAt == request.time, deadlineHours in [24, 48],
   challengerUid/Name/Photo, invitedUid|null, opponentUid|null, opponentName/Photo, participants: [uids],
   legs: [{ref, authorUid|null}] (1..10), includeFriends, cancelled: false, declined: false, rematchOf|null,
   progress: {uid: [legIndex]}`. `participants` at create is `[challengerUid]`, or `[challengerUid, invitedUid]` for a
   rematch. Leg `ref` uses the board puzzleRef formats; daily refs only for `dailyGameIds()`.
   Rules cannot loop, so legs are validated unrolled for indices 0..9.
2. **Time**: deadline = `createdAt + deadlineHours`; close = deadline + 6 h. Clients store hours, never a client
   deadline. Rules are the authority; device clocks drive countdown UI only.
3. **Reads**: `get: if true` (signed-out invite card); `list` only when `request.auth.uid in resource.data.participants`.
4. **Claim** (update): a non-challenger, `opponentUid == null`, not cancelled, before the deadline, and `invitedUid`
   unset or equal to the caller. Affected keys only: opponent fields, `participants`, `claimedAt == request.time`.
   Set once. **Cancel**: challenger, unclaimed, `cancelled` false → true only. **Decline**: `invitedUid`, unclaimed,
   `declined` false → true only. Delete is never allowed (tombstones).
5. **Results** `matches/{code}/results/{legIndex}_{uid}` = `{formatVersion, uid, legIndex, timeMs, hints,
   completedAtMs, gaveUp}`. Create only, by that participant, `legIndex < legs.size()`, `request.time < closesAt`,
   `completedAtMs <= deadline`. Written in one batch with `progress.<uid>` arrayUnion on the match; the progress rule
   requires `existsAfter(result)` and changes only the writer's own key. Update and delete are never allowed.
6. **Sealing** (A7): a result `get` is allowed for its owner, for a participant whose own result for that leg
   exists, or for any participant once `request.time >= closesAt`. Never `list`. Cost: one billed get + exists per
   sealed read.
7. **Quota**: `matchQuota/{uid}` `{day, count, last}`, 20 creates per UTC day, `last == code` required. The
   publish-quota rule stays `isPublishQuotaBump`; a sibling `isMatchQuotaBump` carries the match cap, so the
   existing literal-cap test keeps working and asserts both bounds.
8. **Outcome** is derived on each device by one pure function (`ComputeMatchOutcome`); nothing server-side writes it.

## Consequences
- Rules ship live regardless of the release flag; deploys wait for the user with the matrix green.
- Trust between friends: results are client-reported (A6). No anti-cheat.
- Unknown `formatVersion` renders "update the app".
- Docs are permanent (no deletion), like boards.

## Rejected alternatives
- Reusing `/p/` and `puzzles/{code}` for invites: code ambiguity across collections (A28).
- UI-only sealing: data would be on the wire (A27).
- Storing an absolute deadline written by the client: unverifiable without functions.
- A server-written outcome: impossible on Spark.
