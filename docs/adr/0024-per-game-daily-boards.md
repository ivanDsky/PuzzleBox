# ADR-0024 — Per-game daily boards: allow-list, day window, rank-ordered queries, recent-day submission

Date: 2026-11-07 · Status: accepted · Source: per-game leaderboard spec A1–A8, A20; grilling ruling 2026-11-07 (recent days only, enforced by rules); goldfish A data-1, data-2, reuse-7 · Amends ADR-0013 and ADR-0020 (daily boards were Zip-only and time-first)

## Context
Firestore holds one board per official daily: `leaderboards/{gameId}_{day}/entries/{uid}`. The rules
accepted a daily entry only for a Zip ref, so a Tango, Queens, Strands or Mini Sudoku solve was
rejected quietly by the submit watcher. Every board sorted time first, though Strands ranks by
hints, then time (ADR-0013). Entries can never be deleted (first write stands, A4 record
semantics, rules `allow update, delete: if false`), so whatever the rules admit stays.

The user ruled out historical backfill: only recent solves post. Release 0.17.0 clients are
already out; they still try to submit every Zip record.

## Decision
1. **Explicit allow-list.** `leaderboards/{boardId}/entries/{uid}` accepts an entry only when the
   puzzle ref's game id is in `dailyGameIds()` of `backend/firebase/firestore.rules`:
   `zip | tango | queens | strands | minisudoku`. The ref shape, the `<game>_<day>` board-id
   consistency check, first-write-stands and immutability are unchanged. The debug-only 9x9
   `sudoku`, `custom` (custom boards live under `puzzles/{code}/entries`) and any unknown id are
   rejected. Custom-board and `authors` rules are untouched.
2. **Day window.** The ref's day must be one of the server's UTC date −2, −1, 0, +1 (`request.time`;
   a client clock never decides). The rule formats each of the four days with the quota rule's
   existing `utcDay()` and tests membership with `in`. It does not string-compare a range: a range
   across a month end would also admit days that do not exist (`2026-02-30`).
3. **Rank-ordered queries.** `LeaderboardRepository.observe(board, order)` takes the game's
   `RankOrder` and the Firestore query orders by that order's board fields, the mechanism custom
   boards already use (ADR-0020 decision 4). Both composite indexes on the `entries` collection id
   already exist and cover daily boards, so there is no index change. Every local sort uses the same
   comparator.
4. **Recent-day submission.** The submit watcher posts a record only when its puzzle day is the
   game's today or yesterday on the game's calendar (`GameCalendar`, the game's cadence zone).
   Older local records are never submitted. This applies to Zip too: a Zip solve that stays offline
   for more than about a day never posts.
5. **Invariant: watcher days ⊆ rules window.** The watcher's days (game today and yesterday, in every
   cadence zone) must always lie inside the rules window, or the watcher would retry forever a
   write the rules reject. A cadence zone sits between UTC−12 and UTC+14, so the game's today is
   UTC date −1, 0 or +1 and its yesterday is −2, −1 or 0. The window [−2, +1] covers exactly that
   union. Today's zones (America/Los_Angeles for the shipped dailies) only reach −2…0, and the +1
   edge is the grace for a client whose zone is ahead of UTC. Widening what the watcher submits, or
   adding a zone beyond those bounds, needs the window revisited in the same change.
6. **A new game is one rules line.** A sixth daily game needs its registry entry plus its id in
   `dailyGameIds()` and a rules deploy. `FirestoreRulesAllowListTest` (`:app` JVM test) builds the
   registry, reads `firestore.rules` and fails when a listed game id is missing from the allow-list,
   so the line cannot be forgotten. The rules-matrix (`backend/firebase/rules-matrix`) carries allow
   rows for the five ids inside the window and deny rows for `sudoku`, `custom`, an unknown id, day
   −3 and day +2.
7. **Deploy is separate.** Landing this file deploys nothing. Rules are deployed by hand, right
   before the flip that shows the boards (ticket 13), after a clean matrix run.

## Consequences
- Old 0.17.0 clients keep posting Zip entries inside the window; their old-day backfill is rejected,
  which is the intended behavior. They never post other games, as before.
- Boards for the four newly admitted games start empty on the deploy day and fill from recent
  solves only. Past-day boards are not browsable and not backfilled.
- Old boards are never cleaned up: entries are immutable and undeletable, and growth is accepted at
  the current user count.
- The window is a server-time rule: a device with a wrong clock can still submit, because the
  server date, not the device date, bounds the day.
- Until the deploy, the new surfaces' non-Zip entries are rejected by the deployed (old) rules, the
  same harmless path as before. Rules, index state and the watcher filter ship independently of the
  release flag.

## Rejected alternatives
- **Generic id pattern** (`^[a-z]+/…`, admit any id): entries cannot be deleted, so a typo-id or
  probing client could create boards that stay forever. A sixth game is rare and gets one line, with
  a test.
- **Client-only filter** (watcher submits recent days, rules unchanged): old clients and any
  hand-built client would still write any day of any Zip board, and the "no backfill" ruling would
  rest on client goodwill.
- **Full backfill** (admit any day, submit all history): the grilling ruling excludes it; it would
  also create one board per game per past day that nothing displays.
- **String range on the day** (`day >= lower && day <= upper`): admits non-existent dates at month
  ends; four exact days via `utcDay()` do not.
