# ADR-0012 — Archive-capable daily sources (amends ADR-0002)

Date: 2026-11-05 · Status: accepted · Source: Strands spec A9, A20, A23, A24; goldfish A data-3/4/5, failure-1/4/13, reuse-2

## Context
ADR-0002 left the archive as a future `PuzzleSource` adapter. Strands (NYT) is the first
source that serves any past day by date; the LinkedIn sources' live feed ignores the date
and serves only today (their past days are fetched by date per ADR-0017). Past days are
cache-only end to end (the repository fetches only the calendar's today), so a past
Strands day never opened before is unplayable. History and
the streak treat every cached row as a "captured" day, and history reads load every
payload blob.

## Decision
- `DailySource` declares an optional archive start (`archiveSince: LocalDate?`, default
  null). A non-null start means `fetchDaily(date)` honours `date` for every day from the
  start to the game calendar's today.
- `DailyRepository` gains a by-date read: cache first; on a miss, fetch and persist when
  the source is archive-capable and the day is in range; otherwise a typed outcome
  (not archived, out of range, fetch error). The repository also exposes the archive
  range per game. Replay/slot opening for past days goes through this read.
- Fetches stay lazy: one request when a day is opened. No backfill, no prefetch.
- A cached row is *archived* when it was fetched on a later day (in the game's zone) than
  its own day — derived from the existing fetch time, no new column, schema stays v6.
  Archived rows appear in History; the streak skips them.
- History and puzzle-number reads use a slim projection that never loads payload bytes.

## Consequences
- LinkedIn games are unchanged: no archive start, past days stay cache-only with today's copy.
- A new date-keyed game opts in by declaring its start date; no repository change.
- The archived marker is a derivation: a fetch that crosses midnight in the game's zone
  marks today's row archived (it then misses the streak for that day). Accepted; an
  explicit column is a v7 migration if that ever matters.

## Rejected alternatives
- Fetch-on-miss for every source: LinkedIn would store today's puzzle under a past date.
- A separate archive store/schema: duplicates `puzzle_dailies` for no behavioural gain.
- An explicit `source` column: needs a v7 migration and breaks 0.11.0 downgrade.
- Synthesizing ~977 History rows: breaks the streak and buries other games.
