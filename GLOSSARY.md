# PuzzleBox

The domain language of PuzzleBox's puzzle games and their hints.

## Language

**Spotlight**:
The dim, evidence, target and callout view of one hint step.
_Avoid_: highlight, overlay

**Step**:
One human-statable deduction, mistake or reveal that one press shows.
_Avoid_: move

**Culprit**:
The wrong entry a mistake step marks; the next press removes it.
_Avoid_: error cell

**Ledger**:
The per-play record of which steps have been paid for.
_Avoid_: counter

**Hint mode**:
Legacy (today's hint, which Classic Sudoku keeps) or Stepwise (one deduction per press), chosen per game while the `HintParity` flag exists.
_Avoid_: hint type
