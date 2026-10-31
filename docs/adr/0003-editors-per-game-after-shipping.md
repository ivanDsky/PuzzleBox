# ADR-0003 — Editors: per-game phase, only after that game ships tested

Date: 2026-10-31 · Status: accepted · Source: user ruling 2026-10-31 (F1 inputs §0)

## Context
Each game will eventually get a puzzle editor. Editors compete with the compete loop for
time and would force premature codec/UX decisions if designed per-game too early.

## Decision
Editors are delivered in a dedicated phase PER GAME, sequenced only after that game has
shipped and been tested. Phase-1 designs keep the prerequisites in place — symmetric
versioned codecs (A19), reusable validation rules (rules-doc §4 editor constraints),
reusable board components — so an editor never forces `:core:game` changes.

## Consequences
No editor code or tickets before its game ships; editor work reuses the game's own
validation and components by construction.
