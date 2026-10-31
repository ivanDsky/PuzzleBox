# ADR-0001 — Game contracts as orthogonal GamePlugin

Date: 2026-10-31 · Status: accepted · Source: F1 design-it-twice (`.scratch/puzzlebox/design/F1-foundations.md` §2)

## Context
Five games must onboard without core changes, and post-release feedback must evolve a
shipped game without bending shared contracts (A18). The load-bearing seam is "how a
game plugs in".

## Decision
Five orthogonal pure-Kotlin contracts in `:core:game` — `GameDefinition`, `GameRules`,
`GameSession`, `ScoreModel`, `PuzzleSource` — composed per game as a `GamePlugin`
(registered in Koin; `app` collects `List<GamePlugin>`). `:core:game-ui` components
consume only `:core:game` types.

## Consequences
Each concern (editor, archive, scoring) evolves independently; a game implements only
what it needs; onboarding = one module + one registration line.
Zero-`core:game`-edit guarantee covers onboarding AND evolution (F1 §2.1/§5).

## Rejected alternative
One fat `Game` interface: fails the deletion test — editor stubs would exist for every
game, and every new concern grows the type for all games.
