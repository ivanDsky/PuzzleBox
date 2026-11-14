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
A LinkedIn connection's row for a day. On a Friends board it is ranked with the PuzzleBox rows and takes a friend place when it has a time (hidden times rank last); Global boards hold none. Part of a saved board, and never stored in a shared document.

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

**Solve replay** (short: replay):
The playback of one recorded solve on the game's own board, read-only. In code "replay" means only this.
_Avoid_: using it for "play the puzzle again"

**Play again**:
A fresh session of the same puzzle; visible copy says "Play again" and "Practice". Formerly called replay in code.

**Move**:
One recorded player action: place, remove, mark, clear, undo, redo, a counted hint, a setting flip. Not a hint Step.
_Avoid_: step

**Move log**:
The ordered moves of one solve, each stamped with its play time.

**Play time**:
Elapsed solve time with pause and background excluded; never wall clock.

**Lead**:
Who is ahead at a moment of a compare: fewer hints, then more correct pieces, else Level.

**No replay**:
The solve has no usable replay, for any cause. On the daily result and the History day a stored solve shows the disabled Watch replay row with the reason; elsewhere the section is not drawn.

**Replay mark** (kind):
The kind of one timeline tick: Normal, Undo, Hint or Mistake. A merged tap cycle takes the highest kind of its marks.

**Hint badge**:
The corner disc on a replay board cell (Strands: a word) that a replayed hint revealed.

**Twin**:
The compare layout with both boards side by side, at Expanded width.

**Flip+peek**:
The compare layout with one board at a time in a pager and the other peeking at the edge, at Compact and Medium width.

**Streak freeze** (short: freeze):
A held token that covers one missed puzzle day so the streak survives; derived from the solved-day history, never stored.
_Avoid_: shield, life

**Frozen day**:
A missed puzzle day a freeze covered; it keeps the streak count and adds nothing to it.
_Avoid_: skipped day, rest day

**Freeze ladder**:
The run lengths that earn a freeze (7, then 14, then 28 for every later step) up to 5 held; each spent freeze moves one step back.
_Avoid_: freeze timer

**Freeze moment**:
The one line on today's Result that says a freeze was used or earned by this solve.
_Avoid_: freeze toast, freeze notification

**Ranks**:
The top-level tab that replaces History: one leaderboard, re-ranked in place, with your own row in view. It opens on Friends by default; Settings → Ranks switches the default between Friends and Global, and the game-page row, Result chip and Today row follow it.
_Avoid_: History tab

**Overall**:
The hub board that sums a player's percentile points across every daily game in a day, week or month window. On Friends the points come from places among the viewer, friends and saved LinkedIn connections; on Global from the whole board.
_Avoid_: total, global board

**Percentile points**:
What a counted solve earns on a daily board: `100 × (N − rank + 1) / N` for N entries; a player past the top 100 earns the floor of place 101.
_Avoid_: score

**Final board**:
A daily board whose day has passed the write window, so it can never change; fetched once and kept in the local cache `final_boards.db`.
_Avoid_: saved board

**Official**:
An entry posted inside its day's client submit window; the server board accepted it, so it earns points.
_Avoid_: settled

**Unofficial**:
A local solve or LinkedIn result for a day whose submit window has passed: device-only, owner-only, marked, with no points and no effect on anyone's place.
_Avoid_: late

**Visibility tier**:
One rung of the ladder that sets how much of a player others see: Hidden, On leaderboards (default), Challengeable, or Replays watchable; each includes the ones below.
_Avoid_: privacy setting
