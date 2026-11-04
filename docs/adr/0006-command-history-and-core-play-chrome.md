# ADR-0006 — Moves are commands; time/hint/result chrome is core, meaning is per game

Date: 2026-11-04 · Status: accepted · Source: user request (Play tab picker, core|game split, command undo)

## Context
Queens ran its own play stack (own route, flow, result, controls) and undid moves by keeping
whole-board snapshots; Zip and Tango each carried a private copy of the timer/hint/side-panel
chrome. The Play tab opened Queens directly, skipping the game picker.

## Decision
1. Every game's moves are `Command<S>` (`apply`/`revert`) over the game's board state;
   `CommandHistory` (immutable done/undone lists) provides undo/redo. Counters, the clock and
   the hint count are not part of the board state, so undo never rewinds them. A no-op move
   records nothing and keeps redo intact. History is persisted in the session codec.
2. Core owns the game-agnostic chrome: `PlayChrome` (timer, hint button + count, undo/redo,
   banner, game-controls slot) and `ResultScreen` (time, hints, record badge, optional game
   label + `gameSection`). Games plug in through the `PlayClock`/`PlayHints`/`PlayUndo` seams
   implemented by their `PlayState`, and own what a hint, a move and a timer mean.
3. All games share one stack: Play tab → picker → `PlayFlow` → `ResultRoute`.

## Consequences
- A new game gets timer/hint/undo/result chrome by implementing three small interfaces.
- Persisted sessions bump codec versions; older versions load with empty history (Queens v2
  snapshots are dropped).
- Zip undo is per drag step, not per gesture (no coalescing); revisit if it feels fine-grained.
