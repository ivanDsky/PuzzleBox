# Firestore rules matrix

Allow/deny cases for `backend/firebase/firestore.rules`, run against the Firebase Rules Test API
(`projects/puzzlebox-f3356:test`) with the local rules file; nothing is deployed. Cases live in
`cases.json`; reads of other documents (`get`, `exists`, `getAfter`) are mocked per case.

Run from the repo root (needs `firebase` logged in and `jq`):

```
bash backend/firebase/rules-matrix/run.sh
```

It exits non-zero when any verdict differs from the expected one. Request time is
`2026-11-07T12:00:00Z`, so "today" in the quota cases is `2026-11-07`. The API refuses a request of more
than about 240 cases, so the script sends them in batches of 200 and prints one joined log.


## puzzles

| Expect | Method | Path | Auth | Case |
|---|---|---|---|---|
| allow | get | `puzzles/K7Q2MX` | — | anonymous get of a puzzle |
| allow | get | `puzzles/K7Q2MX` | bob | signed-in get of a puzzle |
| deny | list | `puzzles/K7Q2MX` | — | anonymous list of puzzles |
| deny | list | `puzzles/K7Q2MX` | bob | signed-in list of puzzles |
| allow | create | `puzzles/K7Q2MX` | alice | create as author with the authors bump (4 -> 5) |
| allow | create | `puzzles/K7Q2MX` | alice | first-ever create starts the authors doc at 1 |
| allow | create | `puzzles/K7Q2MX` | alice | first create of a new UTC day restarts at 1 |
| allow | create | `puzzles/K7Q2MX` | alice | 20th create in a day (19 -> 20) |
| deny | create | `puzzles/K7Q2MX` | alice | 21st create in a day (20 -> 21) |
| deny | create | `puzzles/K7Q2MX` | alice | create without bumping the authors doc |
| deny | create | `puzzles/K7Q2MX` | alice | create bumping the authors doc by two |
| allow | create | `puzzles/K7Q2MX` | alice | create whose authors bump names its code (4 -> 5, last set) |
| allow | create | `puzzles/K7Q2MX` | alice | old authors doc without last, then a create that writes last |
| deny | create | `puzzles/K7Q2MX` | alice | two creates under one bump (last names the other code) |
| allow | create | `puzzles/K7Q2MX` | alice | first create of a new UTC day with last restarts at 1 |
| allow | create | `puzzles/K7Q2MX` | alice | first-ever create with last starts the authors doc at 1 |
| deny | create | `puzzles/K7Q2MX` | alice | create whose authors doc is stamped with another day |
| deny | create | `puzzles/K7Q2MX` | — | signed-out create |
| deny | create | `puzzles/K7Q2MX` | bob | create as another uid |
| allow | create | `puzzles/K7Q2MX` | alice | 60-char title |
| deny | create | `puzzles/K7Q2MX` | alice | 61-char title |
| deny | create | `puzzles/K7Q2MX` | alice | empty title |
| allow | create | `puzzles/K7Q2MX` | alice | 40-char author name |
| deny | create | `puzzles/K7Q2MX` | alice | 41-char author name |
| allow | create | `puzzles/K7Q2MX` | alice | 140-char note |
| deny | create | `puzzles/K7Q2MX` | alice | 141-char note |
| allow | create | `puzzles/K7Q2MX` | alice | 8192-char payload |
| deny | create | `puzzles/K7Q2MX` | alice | 8193-char payload |
| deny | create | `puzzles/K7Q2MX` | alice | create already unpublished |
| deny | create | `puzzles/K7Q2MX` | alice | createdAt other than the server time |
| deny | create | `puzzles/K7Q2MX` | alice | formatVersion 2 |
| deny | create | `puzzles/K7Q2MX` | alice | an extra field |
| deny | create | `puzzles/K7Q2MI` | alice | a code outside the share alphabet |
| allow | update | `puzzles/K7Q2MX` | alice | author flips unpublished false -> true |
| deny | update | `puzzles/K7Q2MX` | bob | another user flips unpublished |
| deny | update | `puzzles/K7Q2MX` | alice | author flips unpublished true -> false |
| deny | update | `puzzles/K7Q2MX` | alice | author edits the title |
| deny | update | `puzzles/K7Q2MX` | alice | author edits the payload |
| deny | update | `puzzles/K7Q2MX` | alice | author flips unpublished and edits the note |
| deny | delete | `puzzles/K7Q2MX` | alice | author deletes a puzzle |

## authors

| Expect | Method | Path | Auth | Case |
|---|---|---|---|---|
| allow | get | `authors/alice` | alice | author reads own authors doc |
| deny | get | `authors/alice` | bob | another user reads an authors doc |
| deny | get | `authors/alice` | — | signed-out read of an authors doc |
| deny | list | `authors/alice` | alice | list of authors |
| allow | update | `authors/alice` | alice | author writes the bump (4 -> 5) |
| allow | create | `authors/alice` | alice | author starts the authors doc at 1 |
| allow | update | `authors/alice` | alice | author restarts at 1 on a new day |
| allow | update | `authors/alice` | alice | author writes the 20th bump (19 -> 20) |
| deny | update | `authors/alice` | alice | author writes the 21st bump (20 -> 21) |
| deny | update | `authors/alice` | alice | author resets the count the same day |
| deny | update | `authors/alice` | alice | author stamps another day |
| deny | create | `authors/alice` | alice | author starts the authors doc above 1 |
| allow | update | `authors/alice` | alice | author writes the bump with last (4 -> 5) |
| allow | create | `authors/alice` | alice | author starts the authors doc at 1 with last |
| deny | update | `authors/alice` | alice | author writes a last that is no share code |
| deny | update | `authors/alice` | alice | author writes an extra field beside last |
| deny | update | `authors/alice` | alice | author writes a last that is not a string |
| deny | update | `authors/alice` | bob | another user writes an authors doc |
| deny | delete | `authors/alice` | alice | author deletes the authors doc |

## puzzle entries

| Expect | Method | Path | Auth | Case |
|---|---|---|---|---|
| allow | create | `puzzles/K7Q2MX/entries/bob` | bob | signed-in friend writes a first entry |
| allow | get | `puzzles/K7Q2MX/entries/bob` | carol | signed-in get of an entry |
| allow | list | `puzzles/K7Q2MX/entries/bob` | carol | signed-in list of a board |
| deny | get | `puzzles/K7Q2MX/entries/bob` | — | signed-out get of an entry |
| deny | list | `puzzles/K7Q2MX/entries/bob` | — | signed-out list of a board |
| deny | update | `puzzles/K7Q2MX/entries/bob` | bob | second entry write (update of an existing entry) |
| deny | create | `puzzles/K7Q2MX/entries/bob` | bob | entry on an unpublished puzzle |
| deny | create | `puzzles/K7Q2MX/entries/alice` | alice | author entry on their own puzzle |
| deny | create | `puzzles/K7Q2MX/entries/bob` | carol | entry under another uid |
| deny | create | `puzzles/K7Q2MX/entries/bob` | bob | entry whose puzzleRef names another game |
| deny | create | `puzzles/K7Q2MX/entries/bob` | bob | entry whose puzzleRef names another code |
| deny | create | `puzzles/K7Q2MX/entries/bob` | bob | entry with timeMs 0 |
| deny | create | `puzzles/K7Q2MX/entries/bob` | bob | entry with negative hints |
| deny | create | `puzzles/K7Q2MX/entries/bob` | bob | entry with formatVersion 2 |
| deny | create | `puzzles/K7Q2MX/entries/bob` | bob | entry with an extra field |
| deny | create | `puzzles/K7Q2MX/entries/bob` | bob | entry on a missing puzzle |
| deny | delete | `puzzles/K7Q2MX/entries/bob` | bob | owner deletes an entry |

## daily leaderboards (unchanged)

| Expect | Method | Path | Auth | Case |
|---|---|---|---|---|
| allow | get | `leaderboards/zip_2026-11-07/entries/bob` | carol | signed-in read of a daily entry |
| allow | list | `leaderboards/zip_2026-11-07/entries/bob` | carol | signed-in list of a daily board |
| deny | get | `leaderboards/zip_2026-11-07/entries/bob` | — | signed-out read of a daily entry |
| allow | create | `leaderboards/zip_2026-11-07/entries/bob` | bob | owner creates a valid daily entry |
| deny | create | `leaderboards/zip_2026-11-07/entries/bob` | carol | daily entry under another uid |
| deny | create | `leaderboards/zip_2026-11-07/entries/bob` | bob | daily entry whose ref names another board |
| deny | create | `leaderboards/zip_2026-11-07/entries/bob` | bob | daily entry with a custom ref |
| deny | create | `leaderboards/zip_2026-11-07/entries/bob` | bob | daily entry with timeMs 0 |
| deny | create | `leaderboards/zip_2026-11-07/entries/bob` | bob | daily entry with formatVersion 2 |
| deny | update | `leaderboards/zip_2026-11-07/entries/bob` | bob | second daily entry write |
| deny | delete | `leaderboards/zip_2026-11-07/entries/bob` | bob | owner deletes a daily entry |

## daily leaderboards (allow-list and day window)

Ticket 03 (spec A5, A6). Entries land only for `zip | tango | queens | strands | minisudoku` and only when
the puzzle day is server UTC today −2, −1, 0 or +1. Relative to the fixed request time that is
`2026-11-05` … `2026-11-08`; `2026-11-04` (day −3) and `2026-11-09` (day +2) are denied. The board id,
the ref and the day always agree, so each row's path day is the ref day.

| Expect | Method | Path | Auth | Case |
|---|---|---|---|---|
| allow | create | `leaderboards/zip_2026-11-07/entries/bob` | bob | zip entry for today |
| allow | create | `leaderboards/tango_2026-11-07/entries/bob` | bob | tango entry for today |
| allow | create | `leaderboards/queens_2026-11-07/entries/bob` | bob | queens entry for today |
| allow | create | `leaderboards/strands_2026-11-07/entries/bob` | bob | strands entry for today |
| allow | create | `leaderboards/minisudoku_2026-11-07/entries/bob` | bob | minisudoku entry for today |
| allow | create | `leaderboards/zip_2026-11-05/entries/bob` | bob | zip entry two days back (window start) |
| allow | create | `leaderboards/zip_2026-11-06/entries/bob` | bob | zip entry for yesterday |
| allow | create | `leaderboards/zip_2026-11-08/entries/bob` | bob | zip entry one day ahead (window end) |
| allow | create | `leaderboards/tango_2026-11-05/entries/bob` | bob | tango entry two days back |
| allow | create | `leaderboards/strands_2026-11-08/entries/bob` | bob | strands entry one day ahead |
| deny | create | `leaderboards/sudoku_2026-11-07/entries/bob` | bob | sudoku entry (debug-only game) |
| deny | create | `leaderboards/custom_2026-11-07/entries/bob` | bob | custom as a daily game id |
| deny | create | `leaderboards/chess_2026-11-07/entries/bob` | bob | unknown game id |
| deny | create | `leaderboards/Zip_2026-11-07/entries/bob` | bob | game id in another case |
| deny | create | `leaderboards/zip_2026-11-04/entries/bob` | bob | zip entry three days back |
| deny | create | `leaderboards/zip_2026-11-09/entries/bob` | bob | zip entry two days ahead |
| deny | create | `leaderboards/tango_2026-11-04/entries/bob` | bob | tango entry three days back |
| deny | create | `leaderboards/strands_2026-11-09/entries/bob` | bob | strands entry two days ahead |
| deny | create | `leaderboards/minisudoku_2025-11-07/entries/bob` | bob | minisudoku entry a year back |
| deny | create | `leaderboards/queens_2026-11-31/entries/bob` | bob | queens entry for a day that does not exist |

## daily leaderboards (closed keys and caps)

Ticket 25 (spec A21). A new entry carries only the seven fields every client has written since the first
release (`formatVersion`, `uid`, `displayName`, `puzzleRef`, `timeMs`, `hints`, `completedAtMs`), with
`displayName` and `puzzleRef` at most 64 characters. The check is on writes only: an entry that already
carries something else is still read.

| Expect | Method | Path | Auth | Case |
|---|---|---|---|---|
| deny | create | `leaderboards/zip_2026-11-07/entries/bob` | bob | daily entry with an extra field |
| allow | create | `leaderboards/zip_2026-11-07/entries/bob` | bob | 64-char display name |
| deny | create | `leaderboards/zip_2026-11-07/entries/bob` | bob | 65-char display name |
| deny | create | `leaderboards/zip_2026-11-07/entries/bob` | bob | empty display name |
| allow | create | `leaderboards/zip_2026-11-07/entries/bob` | bob | 64-char puzzleRef |
| deny | create | `leaderboards/zip_2026-11-07/entries/bob` | bob | 65-char puzzleRef |
| allow | get | `leaderboards/zip_2026-11-07/entries/bob` | carol | signed-in read of an older entry that carries an extra field |

## visibility

Ticket 25 (spec A16, A17). `visibility/{uid}` = `{tier, v}` with `tier` one of `hidden | board | duel | replays`
and `v` 1. Any signed-in player gets one tier by id; only its owner creates or changes it; nobody lists or
deletes. A list would hand out the uid of every player, the hidden ones included, and the client only ever
reads one tier by id. No other document is read, so no mocks.

| Expect | Method | Path | Auth | Case |
|---|---|---|---|---|
| allow | get | `visibility/bob` | carol | another signed-in player gets a tier |
| allow | get | `visibility/bob` | bob | owner gets own tier |
| deny | get | `visibility/bob` | — | signed-out get of a tier |
| deny | list | `visibility/bob` | carol | signed-in list of tiers |
| allow | create | `visibility/bob` | bob | owner sets tier hidden |
| allow | create | `visibility/bob` | bob | owner sets tier board |
| allow | create | `visibility/bob` | bob | owner sets tier duel |
| allow | create | `visibility/bob` | bob | owner sets tier replays |
| allow | update | `visibility/bob` | bob | owner raises the tier (board -> replays) |
| allow | update | `visibility/bob` | bob | owner lowers the tier (replays -> hidden) |
| deny | create | `visibility/bob` | carol | tier under another uid |
| deny | create | `visibility/bob` | — | signed-out tier write |
| deny | create | `visibility/bob` | bob | a tier off the ladder |
| deny | create | `visibility/bob` | bob | tier as a number |
| deny | create | `visibility/bob` | bob | v 2 |
| deny | create | `visibility/bob` | bob | tier doc without v |
| deny | create | `visibility/bob` | bob | tier doc without a tier |
| deny | create | `visibility/bob` | bob | tier doc with an extra field |
| deny | update | `visibility/bob` | carol | another user changes a tier |
| deny | delete | `visibility/bob` | bob | owner deletes the tier |

## daily replays

Ticket 25 (spec A18–A20). `leaderboards/{boardId}/replays/{uid}` = `{uid, blob, formatVersion, createdAtMs}`,
beside the entry and never on it. A get passes only while the owner's tier doc exists and says `replays`; the
tier is read on every request (`exists` then `get` of `visibility/bob`, mocked per case), so lowering it hides
the replay at once, from its owner too. No tier doc is the default tier, `board`, so it denies. Written once by
its owner; the owner may delete it, for any day; nobody lists or rewrites.

| Expect | Method | Path | Auth | Case |
|---|---|---|---|---|
| allow | get | `leaderboards/zip_2026-11-07/replays/bob` | carol | signed-in get while the owner's tier is replays |
| allow | get | `leaderboards/zip_2026-11-07/replays/bob` | bob | owner gets own replay while their tier is replays |
| deny | get | `leaderboards/zip_2026-11-07/replays/bob` | carol | get while the owner's tier is duel |
| deny | get | `leaderboards/zip_2026-11-07/replays/bob` | carol | get while the owner's tier is board |
| deny | get | `leaderboards/zip_2026-11-07/replays/bob` | carol | get while the owner's tier is hidden |
| deny | get | `leaderboards/zip_2026-11-07/replays/bob` | carol | get while the owner has no tier doc (the default, board) |
| deny | get | `leaderboards/zip_2026-11-07/replays/bob` | bob | owner gets own replay after lowering the tier to board |
| deny | get | `leaderboards/zip_2026-11-07/replays/bob` | carol | get while the owner's tier doc has no tier |
| deny | get | `leaderboards/zip_2026-11-07/replays/bob` | — | signed-out get while the owner's tier is replays |
| deny | list | `leaderboards/zip_2026-11-07/replays/bob` | carol | signed-in list of a board's replays |
| deny | create | `leaderboards/zip_2026-11-07/replays/bob` | bob | replay whose blob is a string |
| deny | create | `leaderboards/zip_2026-11-07/replays/bob` | — | signed-out replay write |
| deny | create | `leaderboards/zip_2026-11-07/replays/bob` | carol | replay under another uid |
| deny | update | `leaderboards/zip_2026-11-07/replays/bob` | bob | owner rewrites a replay |
| allow | delete | `leaderboards/zip_2026-11-07/replays/bob` | bob | owner deletes own replay |
| allow | delete | `leaderboards/zip_2026-10-01/replays/bob` | bob | owner deletes own replay of a board past the entry window |
| deny | delete | `leaderboards/zip_2026-11-07/replays/bob` | carol | another user deletes a replay |
| deny | delete | `leaderboards/zip_2026-11-07/replays/bob` | — | signed-out delete of a replay |

`blob` must be bytes of at most 12 288 (the cap of a duel result's replay), and the Rules Test API cannot send
bytes (see `results (create)`), so every create above is a deny. The create rule and the tier read against real
documents were checked on the local Firestore emulator instead (2026-11-13, REST, unsigned tokens, no network;
request day `2026-11-13`, bob the owner, carol the reader, board `zip_<day>` unless the row names one):

| Expect | Case |
|---|---|
| allow | create with a `blob` of 12 288 bytes |
| allow | create with a `blob` of 1 byte |
| allow | create with a `blob` of 0 bytes |
| deny | create with a `blob` of 12 289 bytes |
| deny | create with `blob` as a string |
| deny | create with `blob` as an int |
| deny | create with an extra field |
| deny | create whose `uid` field names another player |
| deny | create by another uid under bob |
| deny | signed-out create |
| deny | create with `formatVersion` 2 |
| deny | create with `createdAtMs` 0 |
| deny | create with `createdAtMs` as a string |
| deny | create without `blob`, without `uid`, without `formatVersion`, without `createdAtMs` (four rows) |
| allow | create on the board two days back (window start) |
| allow | create on the board one day ahead (window end) |
| deny | create on the board three days back |
| deny | create on the board two days ahead |
| allow | create on today's `tango`, `queens`, `strands`, `minisudoku` board (four rows) |
| deny | create on a `sudoku` board (debug-only game) |
| deny | create on a `chess` board (unknown game) |
| deny | create on board id `zip_<day>_x` |
| deny | create on board id `zip` |
| deny | owner rewrites a published replay |
| deny | get while the owner has no tier doc (a real missing doc), by carol and by bob |
| deny | get after bob creates tier `duel` |
| allow | get after bob raises the tier to `replays`, by carol and by bob |
| deny | signed-out get while the tier is `replays` |
| deny | list of the board's replays while the tier is `replays` |
| deny | get right after bob lowers the tier to `board`, by carol and by bob |
| deny | delete by carol, signed-out delete |
| allow | delete by bob while the tier is `board` |

The same emulator run gave the expected verdict for the `visibility` rows (owner create, raise and lower
allowed; get by another player allowed; signed-out get, list, an off-ladder tier, `v` 2, an extra field, another
user's update and create, and the owner's delete denied) and for a daily entry (seven fields allowed and read;
an extra field and a 65-char `puzzleRef` denied). A get of a tier doc that does not exist answers not-found,
not permission-denied, which is what lets a client read "no doc" as the default tier.

## matches (create)

Ticket 12 (ADR-0026, spec SC7). A create must bump `matchQuota/alice` in the same write with `last` set to
the match code; the quota reads are mocked. `D7K2QX` is the match code, `R3M4CH` an earlier one.

| Expect | Method | Path | Auth | Case |
|---|---|---|---|---|
| allow | create | `matches/D7K2QX` | alice | open match with the matchQuota bump (3 -> 4, last = code) |
| allow | create | `matches/D7K2QX` | alice | rematch inviting a player (participants = [challenger, invited]) |
| allow | create | `matches/D7K2QX` | alice | first-ever match starts the matchQuota doc at 1 |
| deny | create | `matches/D7K2QX` | alice | 0 legs |
| allow | create | `matches/D7K2QX` | alice | 1 leg |
| allow | create | `matches/D7K2QX` | alice | 10 legs |
| allow | create | `matches/D7K2QX` | alice | 10 custom legs with authors on a rematch (expression budget) |
| deny | create | `matches/D7K2QX` | alice | 11 legs |
| deny | create | `matches/D7K2QX` | alice | deadlineHours 23 |
| allow | create | `matches/D7K2QX` | alice | deadlineHours 24 |
| allow | create | `matches/D7K2QX` | alice | deadlineHours 48 |
| deny | create | `matches/D7K2QX` | alice | deadlineHours 72 |
| deny | create | `matches/D7K2QX` | alice | empty challenger name |
| allow | create | `matches/D7K2QX` | alice | 64-char challenger name |
| deny | create | `matches/D7K2QX` | alice | 65-char challenger name |
| deny | create | `matches/D7K2QX` | alice | an extra field |
| deny | create | `matches/D7K2QX` | alice | challengerUid of another user |
| deny | create | `matches/D7K2QX` | alice | opponent already set |
| deny | create | `matches/D7K2QX` | alice | daily leg ref numbered 0 |
| deny | create | `matches/D7K2QX` | alice | custom leg ref with a code outside the share alphabet |
| deny | create | `matches/D7K2QX` | alice | daily leg ref for a game without a daily board |
| deny | create | `matches/D7K2QX` | alice | 2049-char challenger photo |
| deny | create | `matches/D7K2QX` | alice | invited player missing from participants |
| deny | create | `matches/D7K2QX` | alice | createdAt other than the server time |
| deny | create | `matches/D7K2QX` | alice | create without bumping matchQuota |
| deny | create | `matches/D7K2QX` | alice | matchQuota bump whose last names another code |
| deny | create | `matches/D7K2QX` | — | signed-out create |

## matches (claim)

The fixture match was created at `2026-11-07T00:00:00Z` with 24 h, so its deadline is `2026-11-08T00:00:00Z`;
the late match was created at `2026-11-06T11:00:00Z` (deadline an hour before the request time).

| Expect | Method | Path | Auth | Case |
|---|---|---|---|---|
| allow | update | `matches/D7K2QX` | bob | stranger claims an open match |
| allow | update | `matches/D7K2QX` | bob | invited player claims the rematch |
| deny | update | `matches/D7K2QX` | alice | challenger claims own match |
| deny | update | `matches/D7K2QX` | carol | another player claims a match that invites bob |
| deny | update | `matches/D7K2QX` | carol | claim of a taken seat (set once) |
| deny | update | `matches/D7K2QX` | bob | claim of a cancelled match |
| deny | update | `matches/D7K2QX` | bob | claim of a declined match |
| deny | update | `matches/D7K2QX` | bob | claim after the deadline |
| deny | update | `matches/D7K2QX` | bob | claim that also changes includeFriends |
| deny | update | `matches/D7K2QX` | bob | claim seating another uid |
| deny | update | `matches/D7K2QX` | bob | claim with claimedAt other than the server time |
| deny | update | `matches/D7K2QX` | bob | claim that leaves the caller out of participants |
| deny | update | `matches/D7K2QX` | bob | claim without opponentName |
| deny | update | `matches/D7K2QX` | bob | claim with a null opponentName |
| deny | update | `matches/D7K2QX` | bob | claim with a non-string opponentName |
| deny | update | `matches/D7K2QX` | bob | claim with an empty opponentName |
| allow | update | `matches/D7K2QX` | bob | claim with a 64-char opponentName |
| deny | update | `matches/D7K2QX` | bob | claim with a 65-char opponentName |

## matches (cancel and decline)

| Expect | Method | Path | Auth | Case |
|---|---|---|---|---|
| allow | update | `matches/D7K2QX` | alice | challenger cancels an unclaimed match |
| deny | update | `matches/D7K2QX` | alice | challenger cancels after the claim |
| deny | update | `matches/D7K2QX` | bob | opponent cancels |
| deny | update | `matches/D7K2QX` | alice | challenger cancels and edits a leg |
| allow | update | `matches/D7K2QX` | bob | invited player declines |
| deny | update | `matches/D7K2QX` | carol | stranger declines an invite |
| deny | update | `matches/D7K2QX` | bob | invited player declines after claiming |

## matches (read)

| Expect | Method | Path | Auth | Case |
|---|---|---|---|---|
| allow | get | `matches/D7K2QX` | — | anonymous get of a match (invite card) |
| allow | list | `matches/D7K2QX` | bob | participant lists own matches |
| deny | list | `matches/D7K2QX` | carol | non-participant lists matches |
| deny | delete | `matches/D7K2QX` | alice | challenger deletes a match |

## matches (progress)

| Expect | Method | Path | Auth | Case |
|---|---|---|---|---|
| allow | update | `matches/D7K2QX` | alice | own key grows by one with its result |
| allow | update | `matches/D7K2QX` | bob | opponent's first progress entry with its result |
| deny | update | `matches/D7K2QX` | alice | writes the other player's key |
| deny | update | `matches/D7K2QX` | alice | own key grows by two |
| deny | update | `matches/D7K2QX` | alice | own key grows without its result |
| deny | update | `matches/D7K2QX` | alice | own key rewrites its prefix (reorder) |
| deny | update | `matches/D7K2QX` | alice | own key grows on a cancelled match |
| deny | update | `matches/D7K2QX` | carol | non-participant writes own key |

## results (create)

The match is read before and after the write (`get` / `getAfter`); the after state carries the poster's progress.
The grace rows use a match whose deadline is `2026-11-07T08:00:00Z` and whose close is `14:00:00Z`; the closed
match closed at `2026-11-07T06:00:00Z`.

| Expect | Method | Path | Auth | Case |
|---|---|---|---|---|
| allow | create | `matches/D7K2QX/results/1_bob` | bob | opponent posts a leg in time with its progress |
| allow | create | `matches/D7K2QX/results/1_alice` | alice | challenger posts a leg in time with its progress |
| allow | create | `matches/D7K2QX/results/1_bob` | bob | opponent gives up a leg (time 0) |
| deny | create | `matches/D7K2QX/results/1_carol` | carol | non-participant posts |
| deny | create | `matches/D7K2QX/results/1_bob` | bob | rematch invitee posts before claiming |
| deny | update | `matches/D7K2QX/results/1_bob` | bob | second write of the same leg |
| deny | create | `matches/D7K2QX/results/1_bob` | bob | post after close |
| allow | create | `matches/D7K2QX/results/1_bob` | bob | post in grace, completed before the deadline |
| allow | create | `matches/D7K2QX/results/1_bob` | bob | post in grace, completed exactly at the deadline |
| deny | create | `matches/D7K2QX/results/1_bob` | bob | post in grace, completed after the deadline |
| deny | create | `matches/D7K2QX/results/3_bob` | bob | legIndex past the last leg |
| deny | create | `matches/D7K2QX/results/2_bob` | bob | id names another leg |
| deny | create | `matches/D7K2QX/results/1_bob` | bob | gave up with a non-zero time |
| deny | create | `matches/D7K2QX/results/1_bob` | bob | solved with time 0 |
| deny | create | `matches/D7K2QX/results/1_bob` | bob | negative hints |
| deny | create | `matches/D7K2QX/results/1_bob` | bob | completedAtMs 0 |
| deny | create | `matches/D7K2QX/results/1_bob` | bob | post without its progress in the same write |
| deny | create | `matches/D7K2QX/results/1_bob` | bob | post on a cancelled match |
| deny | create | `matches/D7K2QX/results/1_bob` | bob | an extra field |
| deny | create | `matches/D7K2QX/results/1_bob` | bob | replay as a string |
| deny | delete | `matches/D7K2QX/results/1_bob` | bob | owner deletes a result |

`replay` is optional bytes of at most 12 288. The Rules Test API takes request data as JSON and cannot send a
bytes value: a probe rule `request.resource.data.replay is bytes` answered FAILURE for each of these eight
encodings of `replay`: `"QUJD"`, `{"bytesValue":"QUJD"}`, `{"__bytes":"QUJD"}`, `{"$bytes":"QUJD"}`,
`{"type":"bytes","value":"QUJD"}`, `{"@type":"type.googleapis.com/google.protobuf.BytesValue","value":"QUJD"}`,
`{"bytes":"QUJD"}`, `{"blob":"QUJD"}`. So the bytes rows are checked on the Firestore emulator instead
(2026-11-11, seven fields plus `replay`, one commit with the progress write, bob; the seven-field allow is the
existing `results (create)` row):

| Expect | Case |
|---|---|
| allow | `replay` of 12 288 bytes |
| allow | `replay` of 0 bytes |
| deny | `replay` of 12 289 bytes |
| deny | `replay` as null |
| deny | `replay` as an int |
| deny | `replay` of 10 bytes beside another unknown key |

## results (sealed read)

`results/1_alice` is read; the match, the reader's own result for leg 1 and `1_alice` itself are mocked
(the absent rows mock `1_alice` as missing and send no resource).

| Expect | Method | Path | Auth | Case |
|---|---|---|---|---|
| allow | get | `matches/D7K2QX/results/1_alice` | alice | owner reads own result |
| deny | get | `matches/D7K2QX/results/1_alice` | bob | opponent reads before posting own leg |
| allow | get | `matches/D7K2QX/results/1_alice` | bob | opponent reads after posting own leg |
| allow | get | `matches/D7K2QX/results/1_alice` | bob | opponent reads after close without own leg |
| deny | get | `matches/D7K2QX/results/1_alice` | carol | non-participant reads after close |
| allow | get | `matches/D7K2QX/results/1_alice` | bob | opponent gets an absent result before posting own leg |
| allow | get | `matches/D7K2QX/results/1_alice` | bob | opponent gets an absent result after posting own leg |
| deny | get | `matches/D7K2QX/results/1_alice` | carol | non-participant gets an absent result |
| deny | get | `matches/D7K2QX/results/1_alice` | — | signed-out get of an absent result |
| deny | list | `matches/D7K2QX/results/1_alice` | bob | opponent lists results |

## matchQuota

| Expect | Method | Path | Auth | Case |
|---|---|---|---|---|
| allow | create | `matchQuota/alice` | alice | first bump starts at 1 |
| allow | update | `matchQuota/alice` | alice | 20th match of the day (19 -> 20) |
| deny | update | `matchQuota/alice` | alice | 21st match of the day (20 -> 21) |
| allow | update | `matchQuota/alice` | alice | first match of a new UTC day restarts at 1 |
| deny | update | `matchQuota/alice` | alice | bump without last |
| allow | get | `matchQuota/alice` | alice | owner reads own quota |
| deny | get | `matchQuota/alice` | bob | another user reads the quota |
| deny | delete | `matchQuota/alice` | alice | owner deletes the quota |

## account space

`users/{uid}/{kind}/{docId}` (ADR-0029): owner-only, closed over six kinds, `updatedAt` must be the server time,
per-kind key lists and size caps. Request time is `2026-11-07T12:00:00Z`; no other document is read, so no mocks.
Drafts, received puzzles and replays carry bytes (`blob`, `bytes`, `replay`), which the Rules Test API's JSON cannot express, so their
live shapes are covered only on the deny side (a string where bytes belong); their allow side, the 64 KiB,
8 KiB and 12 288-byte replay bounds, the replay sync-id doc id shape and a merge-set tombstone over a live doc were checked on the local Firestore emulator instead.
Tombstones (`deleted`, `deletedAtMs`) stand for the draft and received kinds in the allow and name-cap cases.

| Expect | Method | Path | Auth | Case |
|---|---|---|---|---|
| allow | create | `users/alice/completions/2026-11` | alice | owner creates a completions month doc |
| allow | update | `users/alice/completions/2026-11` | alice | owner overwrites a completions month doc |
| allow | create | `users/alice/completions/2026-11-2` | alice | owner creates an overflow completions doc |
| allow | create | `users/alice/published/K7Q2MX` | alice | owner creates a published doc |
| allow | create | `users/alice/boards/zip_2026-11-07_0123456789abcdef` | alice | owner creates a board doc |
| allow | create | `users/alice/drafts/3f2b8c1e-5d4a-4b7e-9c1d-0a1b2c3d4e5f` | alice | owner writes a draft tombstone |
| allow | create | `users/alice/received/K7Q2MX` | alice | owner writes a received tombstone |
| allow | get | `users/alice/published/K7Q2MX` | alice | owner gets own doc |
| allow | list | `users/alice/published/K7Q2MX` | alice | owner lists own collection |
| allow | get | `users/alice/replays/sync-1` | alice | owner gets a replays doc |
| deny | create | `users/alice/published/K7Q2MX` | bob | other uid creates in the space |
| deny | update | `users/alice/completions/2026-11` | bob | other uid overwrites in the space |
| deny | get | `users/alice/published/K7Q2MX` | bob | other uid gets a doc |
| deny | list | `users/alice/published/K7Q2MX` | bob | other uid lists the space |
| deny | create | `users/alice/published/K7Q2MX` | — | signed-out create |
| deny | get | `users/alice/published/K7Q2MX` | — | signed-out get |
| deny | list | `users/alice/published/K7Q2MX` | — | signed-out list |
| deny | list | `users/alice/drafts/3f2b8c1e-5d4a-4b7e-9c1d-0a1b2c3d4e5f` | bob | list of another account's draft collection |
| deny | create | `users/alice/secrets/s1` | alice | create under an unknown kind |
| deny | get | `users/alice/secrets/s1` | alice | get under an unknown kind |
| deny | list | `users/alice/secrets/s1` | alice | list under an unknown kind |
| deny | delete | `users/alice/secrets/s1` | alice | delete under an unknown kind |
| deny | create | `users/alice/replays/0123456789abcdef0123456789abcdef` | alice | replays doc without its replay |
| deny | create | `users/alice/replays/0123456789abcdef0123456789abcdef` | alice | replays doc whose replay is a string |
| deny | create | `users/alice/published/K7Q2MX` | alice | updatedAt other than the server time |
| deny | create | `users/alice/published/K7Q2MX` | alice | updatedAt missing |
| deny | create | `users/alice/published/K7Q2MX` | alice | formatVersion 2 |
| deny | create | `users/alice/completions/2026-11` | alice | completions doc with an extra key |
| deny | create | `users/alice/published/K7Q2MX` | alice | published doc with an extra key |
| deny | create | `users/alice/drafts/3f2b8c1e-5d4a-4b7e-9c1d-0a1b2c3d4e5f` | alice | draft tombstone with an extra key |
| deny | create | `users/alice/received/K7Q2MX` | alice | received tombstone with an extra key |
| deny | create | `users/alice/boards/zip_2026-11-07_0123456789abcdef` | alice | board doc with a session key |
| deny | create | `users/alice/completions/2026-11` | alice | completions doc without records |
| deny | create | `users/alice/published/K7Q2MX` | alice | published doc missing a key |
| deny | create | `users/alice/boards/zip_2026-11-07_0123456789abcdef` | alice | board doc without rows |
| deny | create | `users/alice/drafts/3f2b8c1e-5d4a-4b7e-9c1d-0a1b2c3d4e5f` | alice | draft doc with neither shape |
| deny | create | `users/alice/drafts/3f2b8c1e-5d4a-4b7e-9c1d-0a1b2c3d4e5f` | alice | draft tombstone with a text deletedAtMs |
| deny | create | `users/alice/drafts/3f2b8c1e-5d4a-4b7e-9c1d-0a1b2c3d4e5f` | alice | draft doc whose blob is a string |
| deny | create | `users/alice/received/K7Q2MX` | alice | received doc whose bytes are a string |
| deny | create | `users/alice/published/K7Q2MX` | alice | published doc with a text unpublished |
| deny | create | `users/alice/completions/2026-13` | alice | completions doc id outside the month shape |
| deny | create | `users/alice/published/k7q2mx` | alice | published doc id outside the share alphabet |
| allow | create | `users/alice/published/K7Q2MX` | alice | 64-char published name |
| deny | create | `users/alice/published/K7Q2MX` | alice | 65-char published name |
| allow | create | `users/alice/drafts/3f2b8c1e-5d4a-4b7e-9c1d-0a1b2c3d4e5f` | alice | 64-char draft name |
| deny | create | `users/alice/drafts/3f2b8c1e-5d4a-4b7e-9c1d-0a1b2c3d4e5f` | alice | 65-char draft name |
| allow | create | `users/alice/received/K7Q2MX` | alice | 64-char received sender name |
| deny | create | `users/alice/received/K7Q2MX` | alice | 65-char received sender name |
| allow | create | `users/alice/boards/zip_2026-11-07_0123456789abcdef` | alice | 51 board rows |
| deny | create | `users/alice/boards/zip_2026-11-07_0123456789abcdef` | alice | 52 board rows |
| allow | create | `users/alice/completions/2026-11` | alice | 1000 completion records |
| deny | create | `users/alice/completions/2026-11` | alice | 1001 completion records |
| allow | delete | `users/alice/drafts/3f2b8c1e-5d4a-4b7e-9c1d-0a1b2c3d4e5f` | alice | owner deletes a draft |
| allow | delete | `users/alice/boards/zip_2026-11-07_0123456789abcdef` | alice | owner deletes a board |
| allow | delete | `users/alice/replays/sync-1` | alice | owner deletes a replays doc |
| deny | delete | `users/alice/drafts/3f2b8c1e-5d4a-4b7e-9c1d-0a1b2c3d4e5f` | bob | other uid deletes a draft |
| deny | delete | `users/alice/boards/zip_2026-11-07_0123456789abcdef` | bob | other uid deletes a board |
| deny | delete | `users/alice/drafts/3f2b8c1e-5d4a-4b7e-9c1d-0a1b2c3d4e5f` | — | signed-out delete |

## account space (hardening)

Ticket 44 tightens the same block. Every other case here, and every case above, signs in with the Google
provider (`run.sh` sends `sign_in_provider: google.com` unless a case sets `"provider"`). The rules now also
require that provider, pin each kind's doc id, cap a board's game, day and profile key, restrict a board's rows to
the codec's keys, require a completions doc's record keys to be sync ids, and admit the new optional fields
(`reservedCode` on drafts; `variant` and `authorTimeMs` on published docs). The rules language cannot loop over a
map's values or evaluate more than 1000 expressions per request, so a completions record's value and a board
row's value types and lengths are not checked beyond the 1000-record and 51-row caps. So the cases below cover
a row's keys and the caps, and a completions record's key, and nothing more.

| Expect | Method | Path | Auth | Case |
|---|---|---|---|---|
| deny | get | `users/alice/published/K7Q2MX` | alice (anonymous) | anonymous sign-in gets a doc |
| deny | list | `users/alice/published/K7Q2MX` | alice (anonymous) | anonymous sign-in lists its collection |
| deny | delete | `users/alice/drafts/3f2b8c1e-5d4a-4b7e-9c1d-0a1b2c3d4e5f` | alice (anonymous) | anonymous sign-in deletes a draft |
| deny | create | `users/alice/published/K7Q2MX` | alice (anonymous) | anonymous sign-in creates a published doc |
| deny | create | `users/alice/completions/2026-11` | alice (anonymous) | anonymous sign-in creates a completions doc |
| deny | create | `users/alice/boards/zip_2026-11-07_0123456789abcdef` | alice (anonymous) | anonymous sign-in creates a board doc |
| deny | create | `users/alice/drafts/3f2b8c1e-5d4a-4b7e-9c1d-0a1b2c3d4e5f` | alice (anonymous) | anonymous sign-in creates a draft tombstone |
| deny | create | `users/alice/received/K7Q2MX` | alice (anonymous) | anonymous sign-in creates a received tombstone |
| deny | create | `users/alice/published/K7Q2MX` | alice (password) | password sign-in creates a published doc |
| allow | create | `users/alice/published/K7Q2MX` | alice (google.com) | google sign-in creates a published doc |
| allow | get | `users/alice/published/K7Q2MX` | alice (google.com) | google sign-in gets a doc |
| deny | create | `users/alice/drafts/d1` | alice | draft doc id is not a UUID |
| deny | create | `users/alice/drafts/3F2B8C1E-5D4A-4B7E-9C1D-0A1B2C3D4E5F` | alice | draft doc id is an uppercase UUID |
| deny | create | `users/alice/drafts/3f2b8c1e5d4a4b7e9c1d0a1b2c3d4e5f` | alice | draft doc id is a UUID without dashes |
| deny | create | `users/alice/drafts/3f2b8c1e-5d4a-4b7e-9c1d-0a1b2c3d4e5f-2` | alice | draft doc id is a UUID with a suffix |
| deny | create | `users/alice/published/K7Q2M` | alice | published doc id is too short |
| deny | create | `users/alice/published/K7Q2MXA` | alice | published doc id is too long |
| deny | create | `users/alice/received/K7Q2MI` | alice | received doc id is outside the share alphabet |
| deny | create | `users/alice/received/k7q2mx` | alice | received doc id is lowercase |
| deny | create | `users/alice/boards/zip_2026-11-07` | alice | board doc id is a game and day only |
| deny | create | `users/alice/boards/Zip_2026-11-07_0123456789abcdef` | alice | board doc id has an uppercase game |
| deny | create | `users/alice/boards/zip_2026-11-07_0123456789abcde` | alice | board doc id has a 15-char profile key |
| deny | create | `users/alice/boards/zip_2026-11-08_0123456789abcdef` | alice | board doc id names another day than the doc |
| deny | create | `users/alice/boards/tango_2026-11-07_0123456789abcdef` | alice | board doc id names another game than the doc |
| deny | create | `users/alice/boards/zip_2026-11-07_fedcba9876543210` | alice | board doc id names another profile than the doc |
| deny | create | `users/alice/boards/zip_2026-11-7_0123456789abcdef` | alice | board doc day is not a date shape |
| allow | create | `users/alice/boards/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa_2026-11-07_0123456789abcdef` | alice | board doc game of 32 chars |
| deny | create | `users/alice/boards/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa_2026-11-07_0123456789abcdef` | alice | board doc game of 33 chars |
| allow | create | `users/alice/boards/minisudoku_2026-07-19_93b1ec6d3a151ecb` | alice | board doc of a minisudoku day, as already in the cloud |
| allow | create | `users/alice/boards/queens_2026-08-01_93b1ec6d3a151ecb` | alice | board doc of a queens day, as already in the cloud |
| allow | create | `users/alice/boards/tango_2026-09-30_93b1ec6d3a151ecb` | alice | board doc of a tango day, as already in the cloud |
| allow | create | `users/alice/boards/zip_2026-10-14_93b1ec6d3a151ecb` | alice | board doc of a zip day, as already in the cloud |
| allow | create | `users/alice/boards/zip_2026-11-07_0123456789abcdef` | alice | board row without a time (hidden) |
| allow | create | `users/alice/boards/zip_2026-11-07_0123456789abcdef` | alice | board row with a member id of 64 chars |
| deny | create | `users/alice/boards/zip_2026-11-07_0123456789abcdef` | alice | board row with an extra key |
| deny | create | `users/alice/boards/zip_2026-11-07_0123456789abcdef` | alice | board row that is a string |
| deny | create | `users/alice/boards/zip_2026-11-07_0123456789abcdef` | alice | 51st board row with an extra key |
| deny | create | `users/alice/boards/zip_2026-11-07_0123456789abcdef` | alice | board with a bad row among 51 |
| allow | create | `users/alice/boards/zip_2026-11-07_0123456789abcdef` | alice | 51 well-formed board rows including own and ref |
| allow | create | `users/alice/boards/zip_2026-11-07_0123456789abcdef` | alice | empty board |
| deny | create | `users/alice/completions/2026-11` | alice | completions record keyed by a non-sync id |
| deny | create | `users/alice/completions/2026-11` | alice | completions record keyed by 31 hex chars |
| deny | create | `users/alice/completions/2026-11` | alice | completions record keyed by uppercase hex |
| deny | create | `users/alice/completions/2026-11` | alice | completions doc with one bad key among good ones |
| allow | create | `users/alice/completions/2026-11` | alice | completions doc with two records |
| allow | create | `users/alice/completions/2026-11` | alice | completions doc with no records |
| allow | create | `users/alice/drafts/3f2b8c1e-5d4a-4b7e-9c1d-0a1b2c3d4e5f` | alice | draft tombstone with a reservedCode |
| deny | create | `users/alice/drafts/3f2b8c1e-5d4a-4b7e-9c1d-0a1b2c3d4e5f` | alice | draft with a lowercase reservedCode |
| deny | create | `users/alice/drafts/3f2b8c1e-5d4a-4b7e-9c1d-0a1b2c3d4e5f` | alice | draft with a 5-char reservedCode |
| deny | create | `users/alice/drafts/3f2b8c1e-5d4a-4b7e-9c1d-0a1b2c3d4e5f` | alice | draft with a reservedCode outside the share alphabet |
| deny | create | `users/alice/drafts/3f2b8c1e-5d4a-4b7e-9c1d-0a1b2c3d4e5f` | alice | draft with a numeric reservedCode |
| allow | create | `users/alice/drafts/3f2b8c1e-5d4a-4b7e-9c1d-0a1b2c3d4e5f` | alice | draft without a reservedCode (legacy) |
| allow | create | `users/alice/published/K7Q2MX` | alice | published doc with a variant |
| allow | create | `users/alice/published/K7Q2MX` | alice | published doc with a 32-char variant |
| deny | create | `users/alice/published/K7Q2MX` | alice | published doc with a 33-char variant |
| deny | create | `users/alice/published/K7Q2MX` | alice | published doc with a numeric variant |
| allow | create | `users/alice/published/K7Q2MX` | alice | published doc with an authorTimeMs |
| allow | create | `users/alice/published/K7Q2MX` | alice | published doc with a zero authorTimeMs |
| deny | create | `users/alice/published/K7Q2MX` | alice | published doc with a negative authorTimeMs |
| deny | create | `users/alice/published/K7Q2MX` | alice | published doc with a text authorTimeMs |
| deny | create | `users/alice/published/K7Q2MX` | alice | published doc with a fractional authorTimeMs |
| allow | create | `users/alice/published/K7Q2MX` | alice | published doc with both new fields |
| allow | update | `users/alice/published/K7Q2MX` | alice | published doc without the new fields (legacy) |
| deny | create | `users/alice/published/K7Q2MX` | alice | published doc with an unlisted extra key |

## deny-all

| Expect | Method | Path | Auth | Case |
|---|---|---|---|---|
| deny | get | `users/bob` | bob | signed-in read of an unmatched path |
| deny | create | `users/bob` | bob | signed-in write of an unmatched path |
| deny | get | `puzzles/K7Q2MX/comments/c1` | bob | signed-in read of a puzzle's unknown subcollection |

## Last run

2026-11-13, local `firestore.rules` with the hardened `account space` block (not deployed; the run uses the
local source on the Rules Test API), two batches: **380 cases, 380/380 passed**, exit 0.

The `account space` group's 58 cases were rewritten for the Google-only rule and the doc id patterns (draft ids
are UUIDs, the completions fixture and the 1000/1001-record cases carry sync-id keys), and the new
`account space (hardening)` group adds 64 cases. The byte cases stay emulator-only (2026-11-11: draft blob
65,536 bytes allowed, 65,537 denied; received bytes 8,192 allowed, 8,193 denied; a merge-set tombstone over a
live draft allowed; a collection-group query over a kind, a list across all accounts, denied; the Test API
cannot express one). So do the `daily replays` create rows above: 62 emulator checks on 2026-11-13, all with
the expected verdict.

```
PASS  ALLOW  [puzzles] anonymous get of a puzzle
PASS  ALLOW  [puzzles] signed-in get of a puzzle
PASS  DENY  [puzzles] anonymous list of puzzles
PASS  DENY  [puzzles] signed-in list of puzzles
PASS  ALLOW  [puzzles] create as author with the authors bump (4 -> 5)
PASS  ALLOW  [puzzles] first-ever create starts the authors doc at 1
PASS  ALLOW  [puzzles] first create of a new UTC day restarts at 1
PASS  ALLOW  [puzzles] 20th create in a day (19 -> 20)
PASS  DENY  [puzzles] 21st create in a day (20 -> 21)
PASS  DENY  [puzzles] create without bumping the authors doc
PASS  DENY  [puzzles] create bumping the authors doc by two
PASS  ALLOW  [puzzles] create whose authors bump names its code (4 -> 5, last set)
PASS  ALLOW  [puzzles] old authors doc without last, then a create that writes last
PASS  DENY  [puzzles] two creates under one bump (last names the other code)
PASS  ALLOW  [puzzles] first create of a new UTC day with last restarts at 1
PASS  ALLOW  [puzzles] first-ever create with last starts the authors doc at 1
PASS  DENY  [puzzles] create whose authors doc is stamped with another day
PASS  DENY  [puzzles] signed-out create
PASS  DENY  [puzzles] create as another uid
PASS  ALLOW  [puzzles] 60-char title
PASS  DENY  [puzzles] 61-char title
PASS  DENY  [puzzles] empty title
PASS  ALLOW  [puzzles] 40-char author name
PASS  DENY  [puzzles] 41-char author name
PASS  ALLOW  [puzzles] 140-char note
PASS  DENY  [puzzles] 141-char note
PASS  ALLOW  [puzzles] 8192-char payload
PASS  DENY  [puzzles] 8193-char payload
PASS  DENY  [puzzles] create already unpublished
PASS  DENY  [puzzles] createdAt other than the server time
PASS  DENY  [puzzles] formatVersion 2
PASS  DENY  [puzzles] an extra field
PASS  DENY  [puzzles] a code outside the share alphabet
PASS  ALLOW  [puzzles] author flips unpublished false -> true
PASS  DENY  [puzzles] another user flips unpublished
PASS  DENY  [puzzles] author flips unpublished true -> false
PASS  DENY  [puzzles] author edits the title
PASS  DENY  [puzzles] author edits the payload
PASS  DENY  [puzzles] author flips unpublished and edits the note
PASS  DENY  [puzzles] author deletes a puzzle
PASS  ALLOW  [authors] author reads own authors doc
PASS  DENY  [authors] another user reads an authors doc
PASS  DENY  [authors] signed-out read of an authors doc
PASS  DENY  [authors] list of authors
PASS  ALLOW  [authors] author writes the bump (4 -> 5)
PASS  ALLOW  [authors] author starts the authors doc at 1
PASS  ALLOW  [authors] author restarts at 1 on a new day
PASS  ALLOW  [authors] author writes the 20th bump (19 -> 20)
PASS  DENY  [authors] author writes the 21st bump (20 -> 21)
PASS  DENY  [authors] author resets the count the same day
PASS  DENY  [authors] author stamps another day
PASS  DENY  [authors] author starts the authors doc above 1
PASS  ALLOW  [authors] author writes the bump with last (4 -> 5)
PASS  ALLOW  [authors] author starts the authors doc at 1 with last
PASS  DENY  [authors] author writes a last that is no share code
PASS  DENY  [authors] author writes an extra field beside last
PASS  DENY  [authors] author writes a last that is not a string
PASS  DENY  [authors] another user writes an authors doc
PASS  DENY  [authors] author deletes the authors doc
PASS  ALLOW  [puzzle entries] signed-in friend writes a first entry
PASS  ALLOW  [puzzle entries] signed-in get of an entry
PASS  ALLOW  [puzzle entries] signed-in list of a board
PASS  DENY  [puzzle entries] signed-out get of an entry
PASS  DENY  [puzzle entries] signed-out list of a board
PASS  DENY  [puzzle entries] second entry write (update of an existing entry)
PASS  DENY  [puzzle entries] entry on an unpublished puzzle
PASS  DENY  [puzzle entries] author entry on their own puzzle
PASS  DENY  [puzzle entries] entry under another uid
PASS  DENY  [puzzle entries] entry whose puzzleRef names another game
PASS  DENY  [puzzle entries] entry whose puzzleRef names another code
PASS  DENY  [puzzle entries] entry with timeMs 0
PASS  DENY  [puzzle entries] entry with negative hints
PASS  DENY  [puzzle entries] entry with formatVersion 2
PASS  DENY  [puzzle entries] entry with an extra field
PASS  DENY  [puzzle entries] entry on a missing puzzle
PASS  DENY  [puzzle entries] owner deletes an entry
PASS  ALLOW  [daily leaderboards (unchanged)] signed-in read of a daily entry
PASS  ALLOW  [daily leaderboards (unchanged)] signed-in list of a daily board
PASS  DENY  [daily leaderboards (unchanged)] signed-out read of a daily entry
PASS  ALLOW  [daily leaderboards (unchanged)] owner creates a valid daily entry
PASS  DENY  [daily leaderboards (unchanged)] daily entry under another uid
PASS  DENY  [daily leaderboards (unchanged)] daily entry whose ref names another board
PASS  DENY  [daily leaderboards (unchanged)] daily entry with a custom ref
PASS  DENY  [daily leaderboards (unchanged)] daily entry with timeMs 0
PASS  DENY  [daily leaderboards (unchanged)] daily entry with formatVersion 2
PASS  DENY  [daily leaderboards (unchanged)] second daily entry write
PASS  DENY  [daily leaderboards (unchanged)] owner deletes a daily entry
PASS  ALLOW  [daily leaderboards (allow-list and day window)] zip entry for today
PASS  ALLOW  [daily leaderboards (allow-list and day window)] tango entry for today
PASS  ALLOW  [daily leaderboards (allow-list and day window)] queens entry for today
PASS  ALLOW  [daily leaderboards (allow-list and day window)] strands entry for today
PASS  ALLOW  [daily leaderboards (allow-list and day window)] minisudoku entry for today
PASS  ALLOW  [daily leaderboards (allow-list and day window)] zip entry two days back (window start)
PASS  ALLOW  [daily leaderboards (allow-list and day window)] zip entry for yesterday
PASS  ALLOW  [daily leaderboards (allow-list and day window)] zip entry one day ahead (window end)
PASS  ALLOW  [daily leaderboards (allow-list and day window)] tango entry two days back
PASS  ALLOW  [daily leaderboards (allow-list and day window)] strands entry one day ahead
PASS  DENY  [daily leaderboards (allow-list and day window)] sudoku entry (debug-only game)
PASS  DENY  [daily leaderboards (allow-list and day window)] custom as a daily game id
PASS  DENY  [daily leaderboards (allow-list and day window)] unknown game id
PASS  DENY  [daily leaderboards (allow-list and day window)] game id in another case
PASS  DENY  [daily leaderboards (allow-list and day window)] zip entry three days back
PASS  DENY  [daily leaderboards (allow-list and day window)] zip entry two days ahead
PASS  DENY  [daily leaderboards (allow-list and day window)] tango entry three days back
PASS  DENY  [daily leaderboards (allow-list and day window)] strands entry two days ahead
PASS  DENY  [daily leaderboards (allow-list and day window)] minisudoku entry a year back
PASS  DENY  [daily leaderboards (allow-list and day window)] queens entry for a day that does not exist
PASS  DENY  [daily leaderboards (closed keys and caps)] daily entry with an extra field
PASS  ALLOW  [daily leaderboards (closed keys and caps)] 64-char display name
PASS  DENY  [daily leaderboards (closed keys and caps)] 65-char display name
PASS  DENY  [daily leaderboards (closed keys and caps)] empty display name
PASS  ALLOW  [daily leaderboards (closed keys and caps)] 64-char puzzleRef
PASS  DENY  [daily leaderboards (closed keys and caps)] 65-char puzzleRef
PASS  ALLOW  [daily leaderboards (closed keys and caps)] signed-in read of an older entry that carries an extra field
PASS  ALLOW  [visibility] another signed-in player gets a tier
PASS  ALLOW  [visibility] owner gets own tier
PASS  DENY  [visibility] signed-out get of a tier
PASS  DENY  [visibility] signed-in list of tiers
PASS  ALLOW  [visibility] owner sets tier hidden
PASS  ALLOW  [visibility] owner sets tier board
PASS  ALLOW  [visibility] owner sets tier duel
PASS  ALLOW  [visibility] owner sets tier replays
PASS  ALLOW  [visibility] owner raises the tier (board -> replays)
PASS  ALLOW  [visibility] owner lowers the tier (replays -> hidden)
PASS  DENY  [visibility] tier under another uid
PASS  DENY  [visibility] signed-out tier write
PASS  DENY  [visibility] a tier off the ladder
PASS  DENY  [visibility] tier as a number
PASS  DENY  [visibility] v 2
PASS  DENY  [visibility] tier doc without v
PASS  DENY  [visibility] tier doc without a tier
PASS  DENY  [visibility] tier doc with an extra field
PASS  DENY  [visibility] another user changes a tier
PASS  DENY  [visibility] owner deletes the tier
PASS  ALLOW  [daily replays] signed-in get while the owner's tier is replays
PASS  ALLOW  [daily replays] owner gets own replay while their tier is replays
PASS  DENY  [daily replays] get while the owner's tier is duel
PASS  DENY  [daily replays] get while the owner's tier is board
PASS  DENY  [daily replays] get while the owner's tier is hidden
PASS  DENY  [daily replays] get while the owner has no tier doc (the default, board)
PASS  DENY  [daily replays] owner gets own replay after lowering the tier to board
PASS  DENY  [daily replays] get while the owner's tier doc has no tier
PASS  DENY  [daily replays] signed-out get while the owner's tier is replays
PASS  DENY  [daily replays] signed-in list of a board's replays
PASS  DENY  [daily replays] replay whose blob is a string
PASS  DENY  [daily replays] signed-out replay write
PASS  DENY  [daily replays] replay under another uid
PASS  DENY  [daily replays] owner rewrites a replay
PASS  ALLOW  [daily replays] owner deletes own replay
PASS  ALLOW  [daily replays] owner deletes own replay of a board past the entry window
PASS  DENY  [daily replays] another user deletes a replay
PASS  DENY  [daily replays] signed-out delete of a replay
PASS  ALLOW  [matches (create)] open match with the matchQuota bump (3 -> 4, last = code)
PASS  ALLOW  [matches (create)] rematch inviting a player (participants = [challenger, invited])
PASS  ALLOW  [matches (create)] first-ever match starts the matchQuota doc at 1
PASS  DENY  [matches (create)] 0 legs
PASS  ALLOW  [matches (create)] 1 leg
PASS  ALLOW  [matches (create)] 10 legs
PASS  ALLOW  [matches (create)] 10 custom legs with authors on a rematch (expression budget)
PASS  DENY  [matches (create)] 11 legs
PASS  DENY  [matches (create)] deadlineHours 23
PASS  ALLOW  [matches (create)] deadlineHours 24
PASS  ALLOW  [matches (create)] deadlineHours 48
PASS  DENY  [matches (create)] deadlineHours 72
PASS  DENY  [matches (create)] empty challenger name
PASS  ALLOW  [matches (create)] 64-char challenger name
PASS  DENY  [matches (create)] 65-char challenger name
PASS  DENY  [matches (create)] an extra field
PASS  DENY  [matches (create)] challengerUid of another user
PASS  DENY  [matches (create)] opponent already set
PASS  DENY  [matches (create)] daily leg ref numbered 0
PASS  DENY  [matches (create)] custom leg ref with a code outside the share alphabet
PASS  DENY  [matches (create)] daily leg ref for a game without a daily board
PASS  DENY  [matches (create)] 2049-char challenger photo
PASS  DENY  [matches (create)] invited player missing from participants
PASS  DENY  [matches (create)] createdAt other than the server time
PASS  DENY  [matches (create)] create without bumping matchQuota
PASS  DENY  [matches (create)] matchQuota bump whose last names another code
PASS  DENY  [matches (create)] signed-out create
PASS  ALLOW  [matches (claim)] stranger claims an open match
PASS  ALLOW  [matches (claim)] invited player claims the rematch
PASS  DENY  [matches (claim)] challenger claims own match
PASS  DENY  [matches (claim)] another player claims a match that invites bob
PASS  DENY  [matches (claim)] claim of a taken seat (set once)
PASS  DENY  [matches (claim)] claim of a cancelled match
PASS  DENY  [matches (claim)] claim of a declined match
PASS  DENY  [matches (claim)] claim after the deadline
PASS  DENY  [matches (claim)] claim that also changes includeFriends
PASS  DENY  [matches (claim)] claim seating another uid
PASS  DENY  [matches (claim)] claim with claimedAt other than the server time
PASS  DENY  [matches (claim)] claim that leaves the caller out of participants
PASS  DENY  [matches (claim)] claim without opponentName
PASS  DENY  [matches (claim)] claim with a null opponentName
PASS  DENY  [matches (claim)] claim with a non-string opponentName
PASS  DENY  [matches (claim)] claim with an empty opponentName
PASS  ALLOW  [matches (claim)] claim with a 64-char opponentName
PASS  DENY  [matches (claim)] claim with a 65-char opponentName
PASS  ALLOW  [matches (cancel and decline)] challenger cancels an unclaimed match
PASS  DENY  [matches (cancel and decline)] challenger cancels after the claim
PASS  DENY  [matches (cancel and decline)] opponent cancels
PASS  DENY  [matches (cancel and decline)] challenger cancels and edits a leg
PASS  ALLOW  [matches (cancel and decline)] invited player declines
PASS  DENY  [matches (cancel and decline)] stranger declines an invite
PASS  DENY  [matches (cancel and decline)] invited player declines after claiming
PASS  ALLOW  [matches (read)] anonymous get of a match (invite card)
PASS  ALLOW  [matches (read)] participant lists own matches
PASS  DENY  [matches (read)] non-participant lists matches
PASS  DENY  [matches (read)] challenger deletes a match
PASS  ALLOW  [matches (progress)] own key grows by one with its result
PASS  ALLOW  [matches (progress)] opponent's first progress entry with its result
PASS  DENY  [matches (progress)] writes the other player's key
PASS  DENY  [matches (progress)] own key grows by two
PASS  DENY  [matches (progress)] own key grows without its result
PASS  DENY  [matches (progress)] own key rewrites its prefix (reorder)
PASS  DENY  [matches (progress)] own key grows on a cancelled match
PASS  DENY  [matches (progress)] non-participant writes own key
PASS  ALLOW  [results (create)] opponent posts a leg in time with its progress
PASS  ALLOW  [results (create)] challenger posts a leg in time with its progress
PASS  ALLOW  [results (create)] opponent gives up a leg (time 0)
PASS  DENY  [results (create)] non-participant posts
PASS  DENY  [results (create)] rematch invitee posts before claiming
PASS  DENY  [results (create)] second write of the same leg
PASS  DENY  [results (create)] post after close
PASS  ALLOW  [results (create)] post in grace, completed before the deadline
PASS  ALLOW  [results (create)] post in grace, completed exactly at the deadline
PASS  DENY  [results (create)] post in grace, completed after the deadline
PASS  DENY  [results (create)] legIndex past the last leg
PASS  DENY  [results (create)] id names another leg
PASS  DENY  [results (create)] gave up with a non-zero time
PASS  DENY  [results (create)] solved with time 0
PASS  DENY  [results (create)] negative hints
PASS  DENY  [results (create)] completedAtMs 0
PASS  DENY  [results (create)] post without its progress in the same write
PASS  DENY  [results (create)] post on a cancelled match
PASS  DENY  [results (create)] an extra field
PASS  DENY  [results (create)] replay as a string
PASS  DENY  [results (create)] owner deletes a result
PASS  ALLOW  [results (sealed read)] owner reads own result
PASS  DENY  [results (sealed read)] opponent reads before posting own leg
PASS  ALLOW  [results (sealed read)] opponent reads after posting own leg
PASS  ALLOW  [results (sealed read)] opponent reads after close without own leg
PASS  DENY  [results (sealed read)] non-participant reads after close
PASS  ALLOW  [results (sealed read)] opponent gets an absent result before posting own leg
PASS  ALLOW  [results (sealed read)] opponent gets an absent result after posting own leg
PASS  DENY  [results (sealed read)] non-participant gets an absent result
PASS  DENY  [results (sealed read)] signed-out get of an absent result
PASS  DENY  [results (sealed read)] opponent lists results
PASS  ALLOW  [matchQuota] first bump starts at 1
PASS  ALLOW  [matchQuota] 20th match of the day (19 -> 20)
PASS  DENY  [matchQuota] 21st match of the day (20 -> 21)
PASS  ALLOW  [matchQuota] first match of a new UTC day restarts at 1
PASS  DENY  [matchQuota] bump without last
PASS  ALLOW  [matchQuota] owner reads own quota
PASS  DENY  [matchQuota] another user reads the quota
PASS  DENY  [matchQuota] owner deletes the quota
PASS  ALLOW  [account space] owner creates a completions month doc
PASS  ALLOW  [account space] owner overwrites a completions month doc
PASS  ALLOW  [account space] owner creates an overflow completions doc
PASS  ALLOW  [account space] owner creates a published doc
PASS  ALLOW  [account space] owner creates a board doc
PASS  ALLOW  [account space] owner writes a draft tombstone
PASS  ALLOW  [account space] owner writes a received tombstone
PASS  ALLOW  [account space] owner gets own doc
PASS  ALLOW  [account space] owner lists own collection
PASS  ALLOW  [account space] owner gets a replays doc
PASS  DENY  [account space] other uid creates in the space
PASS  DENY  [account space] other uid overwrites in the space
PASS  DENY  [account space] other uid gets a doc
PASS  DENY  [account space] other uid lists the space
PASS  DENY  [account space] signed-out create
PASS  DENY  [account space] signed-out get
PASS  DENY  [account space] signed-out list
PASS  DENY  [account space] list of another account's draft collection
PASS  DENY  [account space] create under an unknown kind
PASS  DENY  [account space] get under an unknown kind
PASS  DENY  [account space] list under an unknown kind
PASS  DENY  [account space] delete under an unknown kind
PASS  DENY  [account space] replays doc without its replay
PASS  DENY  [account space] replays doc whose replay is a string
PASS  DENY  [account space] updatedAt other than the server time
PASS  DENY  [account space] updatedAt missing
PASS  DENY  [account space] formatVersion 2
PASS  DENY  [account space] completions doc with an extra key
PASS  DENY  [account space] published doc with an extra key
PASS  DENY  [account space] draft tombstone with an extra key
PASS  DENY  [account space] received tombstone with an extra key
PASS  DENY  [account space] board doc with a session key
PASS  DENY  [account space] completions doc without records
PASS  DENY  [account space] published doc missing a key
PASS  DENY  [account space] board doc without rows
PASS  DENY  [account space] draft doc with neither shape
PASS  DENY  [account space] draft tombstone with a text deletedAtMs
PASS  DENY  [account space] draft doc whose blob is a string
PASS  DENY  [account space] received doc whose bytes are a string
PASS  DENY  [account space] published doc with a text unpublished
PASS  DENY  [account space] completions doc id outside the month shape
PASS  DENY  [account space] published doc id outside the share alphabet
PASS  ALLOW  [account space] 64-char published name
PASS  DENY  [account space] 65-char published name
PASS  ALLOW  [account space] 64-char draft name
PASS  DENY  [account space] 65-char draft name
PASS  ALLOW  [account space] 64-char received sender name
PASS  DENY  [account space] 65-char received sender name
PASS  ALLOW  [account space] 51 board rows
PASS  DENY  [account space] 52 board rows
PASS  ALLOW  [account space] 1000 completion records
PASS  DENY  [account space] 1001 completion records
PASS  ALLOW  [account space] owner deletes a draft
PASS  ALLOW  [account space] owner deletes a board
PASS  ALLOW  [account space] owner deletes a replays doc
PASS  DENY  [account space] other uid deletes a draft
PASS  DENY  [account space] other uid deletes a board
PASS  DENY  [account space] signed-out delete
PASS  DENY  [account space (hardening)] anonymous sign-in gets a doc
PASS  DENY  [account space (hardening)] anonymous sign-in lists its collection
PASS  DENY  [account space (hardening)] anonymous sign-in deletes a draft
PASS  DENY  [account space (hardening)] anonymous sign-in creates a published doc
PASS  DENY  [account space (hardening)] anonymous sign-in creates a completions doc
PASS  DENY  [account space (hardening)] anonymous sign-in creates a board doc
PASS  DENY  [account space (hardening)] anonymous sign-in creates a draft tombstone
PASS  DENY  [account space (hardening)] anonymous sign-in creates a received tombstone
PASS  DENY  [account space (hardening)] password sign-in creates a published doc
PASS  ALLOW  [account space (hardening)] google sign-in creates a published doc
PASS  ALLOW  [account space (hardening)] google sign-in gets a doc
PASS  DENY  [account space (hardening)] draft doc id is not a UUID
PASS  DENY  [account space (hardening)] draft doc id is an uppercase UUID
PASS  DENY  [account space (hardening)] draft doc id is a UUID without dashes
PASS  DENY  [account space (hardening)] draft doc id is a UUID with a suffix
PASS  DENY  [account space (hardening)] published doc id is too short
PASS  DENY  [account space (hardening)] published doc id is too long
PASS  DENY  [account space (hardening)] received doc id is outside the share alphabet
PASS  DENY  [account space (hardening)] received doc id is lowercase
PASS  DENY  [account space (hardening)] board doc id is a game and day only
PASS  DENY  [account space (hardening)] board doc id has an uppercase game
PASS  DENY  [account space (hardening)] board doc id has a 15-char profile key
PASS  DENY  [account space (hardening)] board doc id names another day than the doc
PASS  DENY  [account space (hardening)] board doc id names another game than the doc
PASS  DENY  [account space (hardening)] board doc id names another profile than the doc
PASS  DENY  [account space (hardening)] board doc day is not a date shape
PASS  ALLOW  [account space (hardening)] board doc game of 32 chars
PASS  DENY  [account space (hardening)] board doc game of 33 chars
PASS  ALLOW  [account space (hardening)] board doc of a minisudoku day, as already in the cloud
PASS  ALLOW  [account space (hardening)] board doc of a queens day, as already in the cloud
PASS  ALLOW  [account space (hardening)] board doc of a tango day, as already in the cloud
PASS  ALLOW  [account space (hardening)] board doc of a zip day, as already in the cloud
PASS  ALLOW  [account space (hardening)] board row without a time (hidden)
PASS  ALLOW  [account space (hardening)] board row with a member id of 64 chars
PASS  DENY  [account space (hardening)] board row with an extra key
PASS  DENY  [account space (hardening)] board row that is a string
PASS  DENY  [account space (hardening)] 51st board row with an extra key
PASS  DENY  [account space (hardening)] board with a bad row among 51
PASS  ALLOW  [account space (hardening)] 51 well-formed board rows including own and ref
PASS  ALLOW  [account space (hardening)] empty board
PASS  DENY  [account space (hardening)] completions record keyed by a non-sync id
PASS  DENY  [account space (hardening)] completions record keyed by 31 hex chars
PASS  DENY  [account space (hardening)] completions record keyed by uppercase hex
PASS  DENY  [account space (hardening)] completions doc with one bad key among good ones
PASS  ALLOW  [account space (hardening)] completions doc with two records
PASS  ALLOW  [account space (hardening)] completions doc with no records
PASS  ALLOW  [account space (hardening)] draft tombstone with a reservedCode
PASS  DENY  [account space (hardening)] draft with a lowercase reservedCode
PASS  DENY  [account space (hardening)] draft with a 5-char reservedCode
PASS  DENY  [account space (hardening)] draft with a reservedCode outside the share alphabet
PASS  DENY  [account space (hardening)] draft with a numeric reservedCode
PASS  ALLOW  [account space (hardening)] draft without a reservedCode (legacy)
PASS  ALLOW  [account space (hardening)] published doc with a variant
PASS  ALLOW  [account space (hardening)] published doc with a 32-char variant
PASS  DENY  [account space (hardening)] published doc with a 33-char variant
PASS  DENY  [account space (hardening)] published doc with a numeric variant
PASS  ALLOW  [account space (hardening)] published doc with an authorTimeMs
PASS  ALLOW  [account space (hardening)] published doc with a zero authorTimeMs
PASS  DENY  [account space (hardening)] published doc with a negative authorTimeMs
PASS  DENY  [account space (hardening)] published doc with a text authorTimeMs
PASS  DENY  [account space (hardening)] published doc with a fractional authorTimeMs
PASS  ALLOW  [account space (hardening)] published doc with both new fields
PASS  ALLOW  [account space (hardening)] published doc without the new fields (legacy)
PASS  DENY  [account space (hardening)] published doc with an unlisted extra key
PASS  DENY  [deny-all] signed-in read of an unmatched path
PASS  DENY  [deny-all] signed-in write of an unmatched path
PASS  DENY  [deny-all] signed-in read of a puzzle's unknown subcollection
380/380 passed
```
