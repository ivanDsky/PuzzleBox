# ADR-0005 — Artifacts self-describe version; variants are data; open stats slot

Date: 2026-10-31 · Status: accepted · Source: user evolution ruling 2026-10-31; F1 §2.1; spec A18/A19

## Context
User feedback after release will add rules, special cases, mechanics, and metrics to
shipped games. Old puzzles, in-progress sessions, and history must survive every such
change; editors (ADR-0003) and the archive (ADR-0002) must parse everything ever saved.

## Decision
1. Every persisted artifact — encoded puzzle bytes, serialized session state, record
   stats blob — starts with a schema version field. Parsers are forward-compatible:
   unknown fields are preserved or dropped, never reinterpreted.
2. Rule changes are DATA: `Puzzle.variant: VariantId` selects among variant definitions
   shipped inside the game's module. Old puzzles pin their variant and stay valid; new
   rules never mutate old ones. Zip's daily sizes (n=5/6/7) are the first real variants.
3. `CompletionRecord.stats` is the open per-game metric slot; A3 indexed fields
   (time + hints) never change meaning.

## Consequences
Evolving a shipped game never edits `:core:game` (zero-core-edit guarantee, F1 §2.1);
archive/editors stay lossless across versions. Cost: every codec carries a version byte
and a forward-compatible reader from day one — a small, always-consumed price.

## Rejected alternative
Schema-less free-for-all (no versions): the first rule change would corrupt every stored
puzzle and session.
