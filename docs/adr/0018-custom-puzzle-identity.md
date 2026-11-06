# ADR-0018 — A shared puzzle's identity is its share code

Date: 2026-11-06 · Status: accepted · Source: editor spec A11–A13, A24; intake A8; goldfish A
reuse-6, persona-flows-11; spec checkpoint 2026-11-06 · Amends ADR-0003 (editors never force
`:core:game` changes) and ADR-0004 (`puzzleRef` identity)

## Context
Play, records, resume and the leaderboard are keyed by day. `PuzzleRef` is `{gameId, date,
puzzleNumber?}` [ev: core/game/.../Records.kt:18-26], `PlaySlot` is `(gameId, day)`
[ev: core/model/.../PlaySlot.kt:6], and `GamePlugin.refOf(date, n)` and
`replaySession(cached, day, n)` rebuild refs from a day [ev: GamePlugin.kt:39-40].
`PuzzleRefKeys` stores refs as `zip/2026-10-31/592` and decodes them by parsing an ISO date
[ev: core/data/.../PuzzleRefKeys.kt:19-32]. A friend's puzzle has no date. Reusing a day key
would collide with the dailies and leak into Today, History, streak and best. Editor mode
therefore needs a second identity kind, and that changes `:core:game`, which ADR-0003 said
an editor never forces.

## Decision
1. **The share code is the identity.** A published puzzle is identified by its 6-character
   code (ADR-0020). Codes are immutable and never reused, even after unpublish, so the code
   is a stable id.
2. **`PuzzleRef` carries a sealed key.** `PuzzleRef` keeps `gameId` and replaces
   `date`/`puzzleNumber` with `key: PuzzleKey`, sealed as `Daily(date, puzzleNumber?)` |
   `Custom(puzzleId)`. Each game's typed ref carries the key, because controllers hard-cast
   it [ev: QueensPlayController.kt:75; ZipPlayController.kt:126]. `GamePlugin.refOf(date, n)`
   becomes `refOf(key)`, and `replaySession(cached, day, n)` becomes
   `replaySession(cached, key)`.
3. **One string form per kind.** `PuzzleRefKeys` writes a custom ref as
   `custom/<gameId>/<code>`, and `custom` becomes a reserved `GameId`. The string never
   matches the `"$gameId/"` best prefix or the ISO-date parse, so every existing daily query
   misses it. `LeaderboardRef.fromPuzzleRefKey` rejects it [ev: Leaderboard.kt:13-45], so it
   never reaches a daily board.
4. **`PlaySlot` is sealed too.** `Daily(gameId, day)` | `Custom(gameId, puzzleId)`. A custom
   slot opens from its saved row first, then from `received_puzzles.puzzleBytes`, and
   otherwise reports Unavailable. Custom saves live in `custom_saved_plays` with their own
   cap of 8, so a friend play never evicts a daily save
   [ev: RoomSavedPlayRepository.kt:13-15].
5. **Session codecs gain a version that writes the key tag.** Every game's session codec
   gets one new version. Older versions still decode, always as `Daily` (ADR-0005).
6. **Daily reads stay Daily-only by construction.** `ObserveTodaySet`, `ObserveGameHistory`,
   `ComputeStreak`, best-by-game and `LeaderboardSubmitWatcher` select the `"$gameId/"`
   prefix and the `Daily` key. A friend-puzzle win writes one first-win record (ADR-0004)
   under its custom ref. A new `CompletionRepository.observeCustom(code)` serves From
   friends and the custom result. History gets no Custom section in this run (checkpoint
   OQ2).
7. **The `completions` table is unchanged.** It is keyed by the encoded ref string
   [ev: CompletionDao.kt:36-49], so the new key form needs no column.

## Consequences
- The change touches `core:game`, `core:model`, `core:data`, `core:domain`, `feature:play`
  and every game's ref and session codec. It lands once, in M2, before any editor work.
- Old session blobs decode as `Daily` forever, and the old-version decode branches stay.
- Strands scoring accepts a custom key without a puzzle number
  [ev: StrandsScoreModel.kt:12-13,21].
- New play nav keys (`FriendPuzzleKey(code)`, `CustomGameKey`, `CustomResultKey`) sit beside
  `GameKey`/`ResultKey`, and those two keys are untouched.
- A future identity kind is one more `PuzzleKey` case, plus its string form and its slot.

## Rejected alternatives
- Content hash of the board as the identity (intake A8 option): two authors who publish the
  same grid would share a board and records, and the code is already unique.
- A synthetic date (e.g. epoch day plus code) inside the existing `Daily` shape: it would
  pass the daily prefix and date filters and leak into streak and History.
- A separate records table for friend puzzles: it breaks ADR-0004's one normalized record
  path, and every record read would need two code paths.
