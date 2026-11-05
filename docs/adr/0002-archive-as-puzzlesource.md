# ADR-0002 — Games archive is a future PuzzleSource adapter

Date: 2026-10-31 · Status: accepted, amended by ADR-0012 (2026-11-05) · Source: F1 §2; user ruling (archive = interface seam only in Phase 1)

## Context
The product vision includes an archive of past games (browse/replay any previous day) and
custom games. Building archive storage now would delay the Zip compete loop for unproven
requirements.

## Decision
`PuzzleSource` is the only archive commitment in Phase 1: `suspend fun fetch(date) →
Puzzle | SealedError`. LinkedInDaily (ticket 12) + in-memory test fake are its two real
adapters today; the archive later implements the same interface as a third, additive
adapter. No archive schema, UI, or tickets exist in Phase 1.

## Consequences
Archive and "custom game" become data sources, not app features with special plumbing;
history UI already consumes cached puzzles through the same seam (A8).
Any archive requirement that does not fit `PuzzleSource` is a design bug against ADR-0001,
filed at archive-phase time, not pre-built now.
