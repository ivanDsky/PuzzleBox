# ADR-0004 — Records/scoring normalized in core/data; games emit CompletionRecord

Date: 2026-10-31 · Status: accepted · Source: F1 §2; spec A3/A4

## Context
Scores must be comparable across games (leaderboards, future cross-game aggregates) while
each game measures its own play in its own terms.

## Decision
`ScoreModel` (per game) translates raw play into the normalized `CompletionRecord`
(**`puzzleRef` identity** + indexed fields: time, hints, firstCompletedAt, isRecord — A3
semantics; A4 first-win-is-record enforced in `core/data`). `puzzleRef` added 2026-10-31
at the F2 gate (finding F2-3, user-approved): record semantics are per-puzzle, so a
record without puzzle identity cannot exist. An open `stats` slot carries per-game extras
so new metrics land without schema churn; promoting one to an indexed column is an
explicit one-time `core/data` migration. Games never touch records persistence directly.

## Rejected alternative
Per-game score tables: cross-game queries become per-game code; A3's "time + hints only"
contract would fragment.
