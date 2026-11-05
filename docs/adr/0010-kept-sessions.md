# ADR-0010 — Kept sessions: leaving a half-played game keeps it

Date: 2026-11-04 · Status: superseded by ADR-0011 §7 (0.11) · Source: UX P1/P2 ticket 02 (spec A3–A6, A14, A20) · Amends ADR-0007 §3, §4

> Superseded in 0.11: per-slot saved plays (`saved_plays`, with the displayed elapsed ms)
> replace the kept-session store; the `kept_sessions` DataStore file is left on disk unread.
> The picker statuses below live on in the Play hub and Home via `ObserveTodaySet`.

## Context
ADR-0007 gave the play feature one live `ActivePlay` and persisted it only through the
route's `rememberSaveable`. Back (`end()`) cancelled the play and dropped its session, so
leaving a half-played daily lost the board and the clock, and switching games did the same to
the game left behind. The picker could not say which games were started, solved or untouched.

## Decision
1. **Kept-session store.** `KeptSessionRepository` (core:data) holds at most one
   `KeptSession` per game id: the `SessionBlob` envelope, the game's cadence day, and the
   displayed elapsed ms. It is backed by its own Preferences DataStore file
   (`kept_sessions`), owned by one data source, so the theme-gating preferences file stays
   single-owner and low-churn. No Room table and no migration. Home and Play both read it,
   through `ObserveTodaySet` (core:domain) for today's status.
2. **Amends ADR-0007 §3: one live play, plus kept bytes.** `PlaySessionStore` still holds
   exactly one live `ActivePlay` (one controller, one scope). Every other started game is
   held only as kept bytes. `end()` is gone; `leave()` folds the clock, keeps the session and
   then releases the play (scope cancelled, sink dropped). Opening another game leaves the
   live one first, so switching games keeps both, one per game.
3. **Amends ADR-0007 §4: persistence.**
   - Writes happen on `leave()`, on `background()` (the play route's `ON_STOP`, plus the
     route leaving composition — a tab switch never reaches `ON_STOP`; process death
     always follows `ON_STOP`), when another game's play replaces the live one, and when
     a restore replaces the live session with one it does not already hold (a same-game
     history replay over today's play).
     They never happen per move. They run in the store's own keep scope, because leave
     cancels the play's scope.
   - A write first folds the clock through the controller's `PlayClock.onBackground()`.
     That is the same hook `PlayChrome` calls on `ON_PAUSE`; folding an already-paused
     clock banks nothing, so the two folds never double the time. The kept bytes are the
     newest envelope the store published (which includes the fold), and the elapsed is
     `displayedElapsedMs()`.
   - Only an unfinished, started session of the game's cadence today is kept. A board
     never started (0 ms, nothing published), a finished session, and a replay of another
     day keep nothing. So a history replay can't overwrite today's kept session.
   - A solve discards the game's kept session.
   - The store owns the day filter. `pick()` reads the kept sessions (it waits for the
     first DataStore emission, so a kept session is never missed after a process
     restart). A session of today is re-adopted through the existing `restore` path
     before `active` publishes, so the route never sees a needs-load play and never
     fetches. A session of another day is discarded and the game starts fresh.
   - Precedence: the kept-session store is the source of truth across entries. The
     route's saveable covers only the same entry's rotation (and the process death that
     follows it), as before.
4. **Picker.** The play tab's fresh-entry state is today's set (`ObserveTodaySet` over
   `GameRegistry.all`, registry order). Each row reads "Not started", "In progress · m:ss"
   or "Solved · m:ss ✓". Solved beats a kept session, so a replay never downgrades the row.
   The row follows the completion and kept-session flows live. The rows are seeded from the
   registry with no status, so the first frame lists every game before Room and DataStore
   answer. A row shows its status line once today's set emits.

## Consequences
- Resume restores what each game's session codec carries: the board, undo history where
  the codec stores it, and the banked clock. Transient UI (hint cooldown, flash) resets.
- A new game gets kept sessions for free when its controller implements `PlayClock` and
  publishes its session on a background fold. A game without a clock is kept only when it
  has published a session.
- Picking a solved game still opens a fresh replay of today (A6). If that replay is left
  half-played it is kept, and picking the game again resumes it.
- The store's default keep scope uses `Dispatchers.Main.immediate`. Plain-JVM tests pass
  their own scope.
