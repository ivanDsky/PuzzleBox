# ADR-0006 — Moves are commands; time/hint/result chrome is core, meaning is per game

Date: 2026-11-04 · Updated: 2026-11-05 (A14 shared play chrome) · Status: accepted · Source: user request (Play tab picker, core|game split, command undo)

## Context
Queens ran its own play stack (own route, flow, result, controls) and undid moves by keeping
whole-board snapshots; Zip and Tango each carried a private copy of the timer/hint/side-panel
chrome. The Play tab opened Queens directly, skipping the game picker.

## Decision
1. Every game's moves are `Command<S>` (`apply`/`revert`) over the game's board state;
   `CommandHistory` (immutable done/undone lists) provides undo/redo. Counters, the clock and
   the hint count are not part of the board state, so undo never rewinds them. A no-op move
   records nothing and keeps redo intact. History is persisted in the session codec.
2. Core owns the game-agnostic chrome: `PlayChrome` (degrade banner, the fixed caption slot,
   and the bottom `PlayActionBar`: Undo · Redo · Hint · Clear) and `ResultScreen` (time,
   hints, record badge, optional game label + `gameSection`). Games plug in through the
   `PlayClock`/`PlayHints`/`PlayUndo` seams implemented by their `PlayState`, and own what a
   hint, a move and a timer mean.
3. All games share one stack: Play tab → picker → `PlayFlow` → `ResultRoute`.
4. All three games share one play layout (A14). The route's top bar (`GameRoute`, through
   `CompactTopBar`) carries Back, the game's `GameGlyph` badge, its name and number, the
   tabular timer, How to play, and an overflow menu; a game's `Screen` no longer draws a
   title or a how-to button. The board sits under it in `playBoardPane()`, top-aligned
   with a fixed top inset and horizontal-only gutters, so every board's top y matches at
   a given window size (pinned by app's `PlayBoardPositionTest`).
   - `GamePlayUi.clearFor(controller): PlayClear?` is the Clear seam: Zip empties its
     trace as one undo step (`ZipTraceEdits`), Queens clears the whole board, and Tango
     returns null, which hides the button.
   - `GamePlayUi.overflowFor(controller): List<PlayOverflowItem>` carries a game's
     toggles into the overflow menu; Queens' Auto-cross lives only there.
   - `PlaySolveFrame` wraps the board: a solve made in play runs `SolveSweep` in the
     game's accent, then the glyph `Stamp`, then hands off to Result once. Reduced motion
     shows only the outline. A board that is already solved when it first draws hands off
     at once.
   - The per-game control rows (`HintButton`, `UndoRedoRow`, `QueensPlayControls`, the
     game-controls slot) are gone.

## Consequences
- A new game gets timer/hint/undo/result chrome by implementing three small interfaces.
- Persisted sessions bump codec versions; older versions load with empty history (Queens v2
  snapshots are dropped).
- Zip undo is per drag step, not per gesture (no coalescing); revisit if it feels fine-grained.
- The solve moment delays the hand-off to Result by the sweep plus a short hold
  (about 0.8 s at default motion); a restored, already-solved board still hands off at once.
