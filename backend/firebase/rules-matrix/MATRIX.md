# Firestore rules matrix

Allow/deny cases for `backend/firebase/firestore.rules`, run against the Firebase Rules Test API
(`projects/puzzlebox-f3356:test`) with the local rules file; nothing is deployed. Cases live in
`cases.json`; reads of other documents (`get`, `exists`, `getAfter`) are mocked per case.

Run from the repo root (needs `firebase` logged in and `jq`):

```
bash backend/firebase/rules-matrix/run.sh
```

It exits non-zero when any verdict differs from the expected one. Request time is
`2026-11-07T12:00:00Z`, so "today" in the quota cases is `2026-11-07`.


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

## deny-all

| Expect | Method | Path | Auth | Case |
|---|---|---|---|---|
| deny | get | `users/bob` | bob | signed-in read of an unmatched path |
| deny | create | `users/bob` | bob | signed-in write of an unmatched path |
| deny | get | `puzzles/K7Q2MX/comments/c1` | bob | signed-in read of a puzzle's unknown subcollection |

## Last run

2026-11-07, rules at the commit that added this file: **80 cases, 80/80 passed**, exit 0.

2026-11-07, re-run after `firebase deploy --only firestore,hosting --project puzzlebox-f3356` released this
same `firestore.rules`: **80/80 passed**, exit 0. The output below is unchanged.

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
PASS  DENY  [deny-all] signed-in read of an unmatched path
PASS  DENY  [deny-all] signed-in write of an unmatched path
PASS  DENY  [deny-all] signed-in read of a puzzle's unknown subcollection
80/80 passed
```
