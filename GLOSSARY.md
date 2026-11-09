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
Legacy (today's hint, which Classic Sudoku keeps) or Stepwise (one deduction per press), chosen per game at registration; read from the `HintParity` flag while it exists.
_Avoid_: hint type

**Match** (visible word: duel):
One 1v1 best-of-N contest.
_Avoid_: game, battle

**Leg**:
One puzzle of a match. Code only; visible copy says "puzzle".
_Avoid_: round

**Seat claim**:
The first non-challenger accept, which sets the opponent.
_Avoid_: join

**Settled**:
Both players have a result on every leg, or the match has closed.

**Sealed**:
An opponent's leg result stays unreadable until your own exists or the match closes.

**Reveal**:
The once-per-device animated outcome sequence that ends on the summary.

**Late / LocalOnly**:
The two failed post states: rejected because the match had closed, or rejected three times.
