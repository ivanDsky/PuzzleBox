# ADR-0013 — Rank order is per game

Date: 2026-11-05 · Status: accepted · Source: Strands spec A25–A27; user ruling (Strands score = hints, then time); goldfish A reuse-5, data-6/7, verify-1, best-12

## Context
A3 ("lower is better", time then hints) is shared by every ranking surface: the
game's best, the Result best verdict (time only), the leaderboard sort, the Firestore
query and index. The user ruled that Strands ranks by hints, then time. Firestore rules
accept only Zip refs, so no non-Zip game reaches a leaderboard today.

## Decision
- `ScoreModel` declares the game's `RankOrder` (`TimeThenHints` default, `HintsThenTime`).
- The completion repository reads a game's best under its order (time and hints, not
  time alone).
- `RecordVerdict` compares the current solve against that best under the order; for
  time-first games the comparison is exactly today's.
- The leaderboard (local sort, Firestore query, index, rules) is unchanged and stays
  time-first and Zip-only. Nothing is deployed.

## Consequences
- Zip, Queens and Tango behave exactly as before.
- A Strands leaderboard later needs its own query/index ordered by the game's order and
  a rules change; that is a backend deploy and its own decision.
- Strands records are submitted by the watcher and rejected by rules, the same harmless
  path Queens and Tango already take.

## Rejected alternatives
- Keep A3 for Strands: contradicts the user ruling.
- Flip A3 for every game: changes three shipped games' records semantics.
- Order on `GameDefinition`: identity/wire concerns; ranking is a score concern.
