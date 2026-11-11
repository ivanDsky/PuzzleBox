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
Legacy (today's hint, which Classic Sudoku keeps) or Stepwise (one deduction per press), chosen per game where the game is registered.
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

**Import**:
A completion written from the player's own LinkedIn result, stored with `source = LinkedIn`; it follows first win like any solve.

**Ghost**:
A LinkedIn connection's row for a day, ranked with the PuzzleBox rows on one combined board when it has a time (hidden times stay unranked); part of a saved board.

**Saved board**:
One LinkedIn connections' board for a game and day, kept on the phone for good and, when signed in, in the player's cloud space; Disconnect keeps it, Delete saved boards removes it.

**Cloud space**:
The signed-in account's own owner-only Firestore area holding its synced kinds; nobody else can read it.
_Avoid_: backup, cloud save

**Synced kind**:
One kind of data cloud sync carries (completions, drafts, published, received, saved boards, replays), each with its own merge rule.

**Owner**:
The account a local synced row belongs to; a row made while signed out has none until the next sign-in adopts it.

**Tombstone**:
A synced marker that a draft or received puzzle was deleted, so the delete reaches every device.

**Pause**:
The persisted latch set by the first LinkedIn rejection; every account read waits until the player taps Resume, Reconnect or Disconnect.
