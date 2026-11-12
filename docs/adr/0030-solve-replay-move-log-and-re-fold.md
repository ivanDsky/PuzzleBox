# ADR-0030 — Solve replay: move log and re-fold

Date: 2026-11-12 · Status: accepted · Source: `.scratch/detailed-result/spec.md` (A1–A23, D1–D9) · Amends ADR-0005, -0006, -0007, -0008, -0026 (each carries a dated note)

## Context
After a solve the player sees one time and a hint count, not how the solve went. A duel reveal shows two figures, not how the opponent played. Command history cannot supply a replay: `CommandHistory` drops the redo branch on `record`, `replaceLast` rewrites the newest command, Zip trims at 1 000 entries, Strands has no history at all, and hints and settings never enter it (ADR-0006). The per-game stats slot is a game-authored string (ADR-0005); the saved play is one envelope per play (ADR-0007); a duel result is one document per leg (ADR-0026).

## Decision
1. **A move log, separate from undo history.** A *move* is what the game's reducer was told to do after the UI resolved the gesture: place, remove, mark, clear, undo, redo, a counted hint, a setting flip (Queens auto-cross). Undone moves stay in the log. No-op input, refused drags, selection and in-flight traces are not moves. `MoveLog` (`core:game`) is a journal of `(playTimeMs, ReplayMove)`; a game controller changes its session only through `MoveLog.play`/`setting`, which apply the move through `GameReplay.step` and append iff the session changed. Live play and replay therefore share one `step`.
2. **Play time, not wall time.** Every stamp comes from `playTimeMs(bankedMs, runningSince, now)` (`core:game`), fed by the controller's injected clock: banked elapsed plus the live window, background excluded. Stamps never decrease and at seal are clamped to the end time.
3. **Replay is a re-fold.** `GamePlugin.replay: GameReplay<S>?` (null = the game has none) adapts a game: `step(session, move, atMs)` (same instance back = nothing changed, null = move not understood, never throws) and `facts(session)` (hints, correct pieces, total, solved). `GamePlugin.openTimeline(puzzle, bytes)` decodes, folds once from `initialSession(puzzle)` and returns an immutable `Timeline` with in-memory checkpoints; `Timeline.at(tMs)` gives a `ReplayFrame` (session, the session before that move, facts, `sinceMoveMs`, `ended`). A move records its **outcome**, never the press: a hint stores what it revealed or which moves it made, so `step` never asks a hint engine or solver again. Not replayed: hint Spotlight, selection, traces, rejection flashes, ghosts, placement animations; the Tango settle is derived from the stamps. Drift guard: outcome Solved whose fold does not end solved is "no replay".
4. **Complete or absent.** A log never starts mid-solve. A resumed save with no log section voids the log for the rest of that solve; a play-again solve starts a fresh one. A log that passes a cap is void, never truncated: a truncated log would fold to a wrong board. A zero-move replay is a replay.
5. **The byte format is part of the contract for a new game.** One envelope and one generic `(kind, args)` move payload (`ReplayCodec`, `core:game`), raw-deflated behind a `u8` format version; the game maps typed moves to `(kind, args)` and writes no bytes. Layout before deflate: `u8` format version, `u8` game-id length and ASCII id, `u8` outcome (Open, Solved, GaveUp), varint end time, varint move count, then per move a varint delta time, `u8 kind<<3 | argc` (argc 7 = a varint argc follows) and varint zig-zag args. The same codec writes the in-progress snapshot (Open) and the sealed replay. The envelope:
   - move kind 0..31 (`MAX_KIND`);
   - at most 64 argument values per move (`MAX_ARGS`);
   - at most 4 096 moves (`MAX_MOVES`);
   - at most 32 KiB inflated (`MAX_INFLATED_BYTES`);
   - at most 12 KiB sealed (`SolveReplay.MAX_BYTES`, in `core:model`, checked at construction).

   Decode returns a typed `DecodeResult` and never throws; counts are checked against caps before any allocation; a game id that is not the puzzle's, or an unknown format version, is "no replay". A game that needs more than the envelope allows amends this ADR; it does not widen the codec in place. Move kind numbers are append-only.
6. **Storage.**
   - A finished solve's replay lives in the side table `completion_replays(completionId → completions.id ON DELETE CASCADE, replay BLOB)`, written in the completion's insert transaction (`CompletionRepository.onCompleted(record, replay)`; reads `recordReplay(ref)` and `latestReplay(ref)`). Only the record solve and the newest solve of a puzzle keep one. `statsBlob` is untouched.
   - The in-progress log rides in the saved-play envelope (`SessionBlob` version 2, ADR-0007), not in any game session; no per-game session codec bump.
   - A duel leg's replay is the nullable column `match_results.replay`, written in the leg's own `enqueue`, so the post never reads the completion row (a gave-up leg has none, and the completion is written after the outbox row). On the wire it is the optional `replay` bytes field of the result document, `formatVersion` unchanged; rules accept the old seven-field shape or that shape plus `replay` (bytes, at most 12 288). A decode that finds it missing, mistyped or oversized yields the result with no replay. The opponent's replay is read online and never cached.
   - One additive Room migration, `MIGRATION_11_12`, creates the table and adds the column.
7. **Two flag gates, no more** (`ReleaseFlag.FullReplay`, ADR-0022): the play feature builds a recording log only with the flag on (`PlaySessionStore`), and `LoadSolveReplay` (`core:domain`) returns no replay with it off. With the flag off nothing new is recorded, stored, posted or shown, and saved plays are written byte-identical to before (`SessionBlob` writes version 1 when there is no log).
8. **Advisory only.** A replay never changes time, hints, rank or a duel outcome. A rejected post that carried a replay is posted again at once without it, and the retry does not count toward the attempt limit; the replay never costs the result.
9. **One read entry, one destination per kind.** `LoadSolveReplay(SolveReplayRef)` (`Record`, `Latest`, `MyLeg`, `TheirLeg`) opens a replay for every surface and folds off the main thread. Full-screen replay is one destination, `ReplayKey`, and a compare is one destination, `ReplayCompareKey`; keys carry ids only, never bytes. Results enter through `WatchReplayRow`; History lists "First solve" and "Latest" rows plus a Compare button.
10. **Lead and compare.** `Lead.of(mine, theirs)`: fewer hints at t, then more correct pieces, else Level; `Lead.ended` decides after both sides end. `compareLayoutFor(width)` picks Twin at Expanded width and Flip+peek otherwise. The hint flash plays only during playback; a seek never flashes.

## Consequences
- Adding a game gains two optional steps, `GamePlugin.replay` and `GamePlayUi.replayBoard` (`GameReplayBoard`); still no `core:*` or `feature:*` edit (ADR-0008).
- A reducer change can orphan old replays: a stored replay that no longer folds (a `step` returning null, or a Solved log that does not end solved) shows "no replay". "No replay" is one state for every cause: pre-feature row, LinkedIn import, flag off, over a cap, corrupt, unknown version, puzzle no longer loadable. Surfaces hide the section; there is no placeholder.
- A solved duel leg stores its replay twice, in the outbox and in `completion_replays`; at most 12 KiB each.
- Downgrading to a build that predates `SessionBlob` version 2 after the flag is on drops an in-progress save as unreadable; accepted for alpha.
- Live changes wait for the user: the Room migration with the envelope version, the Firestore rules deploy, the flag flip.

## Rejected alternatives
- Session snapshots per move: size grows with board area times moves, the format changes with each game's session codec, and five codecs would bump.
- Replay inside the stats slot (`statsBlob`): the slot is a game-authored string, so binary needs base64; every `SELECT *` on `completions` would drag the bytes into History lists; core would need a per-game extractor for core-owned bytes; five `statsVersion` bumps for data that is not a stat.
- Time-stamped `CommandHistory` commands: not a journal (redo branch dropped, newest command rewritten, Zip cap, no Strands history), and commands are inverse deltas rather than intent.
- A per-game move byte codec: five formats, five fuzz surfaces, five version counters, to save about one byte per move before deflate.
- A nullable BLOB column on `completions`: the same list-read cost as the stats slot.
- Joining the completion row at post time: fails for a gave-up leg, for a kill between the two writes, and has no leg key.
