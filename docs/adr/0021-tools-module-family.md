# ADR-0021 — The `tools:*` module family

Date: 2026-11-06 · Status: accepted · Source: editor spec A23, A27, S14; intake A12,
cli_bridge; goldfish A persona-flows-17/18 · Amends ADR-0008 §6 (forbidden edges) and its
"new module families need a rule" consequence

## Context
An AI agent writes puzzles as text and needs the app's own verdict before a human imports
them. The verdict comes from pure JVM code: each game's solver, hint ladder, publish gates
and notation parser behind `GameEditor` (ADR-0019). ADR-0008 knows the families `app`,
`core`, `feature`, `games`, `backend` and `konsist`. A new top-level family needs its own
arms in `forbiddenEdgeReason` [ev: build-logic/.../ModuleGraphGuard.kt:53-88;
ADR-0008 Consequences]. The convention says nothing outside `app` names a concrete game
[ev: docs/agents/project-map.md:292-294].

## Decision
1. **`tools:*` holds runnable pure-JVM developer tools that ship nowhere.** The first is
   `:tools:puzzle-cli` (`puzzlebox.jvm.library` plus Gradle's `application` plugin, run via
   `installDist`). It provides:
   - `validate <file>`, which prints a versioned JSON verdict (`"schema": 1`) and exits 0
     only for Unique and, on ladder games, not needs-guessing;
   - `solve <file>`, which also prints the solution.

   The tool holds no credentials and makes no network calls.
2. **What a tool may depend on.** A tool may depend on `:core:game`, on any non-UI
   `games:<id>`, and on `:core:testing` from test configurations only. The new arms reject
   tool edges to `feature:*`, `backend:*`, `core:data`, `core:game-ui`, `core:designsystem`,
   any `games:*-ui` and every Android module.
3. **Nothing depends on a tool.** A new arm rejects any edge from outside `tools:*` into it,
   in every configuration. Both arms sit next to the `:konsist` arms, before the generic
   family arms.
4. **A tool is a composition root.** Like `app`, a tool may name concrete games. Its `main`
   builds a `GameRegistry` from the non-UI games' plugins and reaches every game through
   `GamePlugin.editor`, so adding a game's editor adds it to the tool with one registration.
   No tool code branches on a game id.
5. **One verdict, two callers.** The CLI and the app's `ImportDraftFromText` (ADR-0019)
   call the same `parse` and `analyze`. One parity test per game runs both over the shared
   notation fixtures in `docs/puzzle-notation.md`.

## Consequences
- `ModuleGraphGuard` gains the two arms and their unit cases. ADR-0008 §6's list and the
  project map gain the family.
- The Konsist census and the jvm-library rule (no `android.*`) cover `tools/**` like any
  JVM module. The CLI's `main` file is the one place in the family that names games.
- `build/install/puzzle-cli/bin/puzzle-cli validate f.txt` runs without Gradle once it is
  installed.
- A future tool (a notation formatter, a batch validator) is a new `tools:<name>` module
  under the same arms.

## Rejected alternatives
- The CLI inside `app` or as an instrumented entry point: it needs a device or the Android
  runtime, and an agent cannot run it offline.
- A Gradle task (`./gradlew validatePuzzle`): every run pays Gradle configuration, and the
  build logic would depend on game modules.
- A CLI in `core:*`: `core` may not depend on `games:*` (ADR-0008 §6), and a tool must name
  games.
- Re-implementing solvers in a script: the tool's verdict would drift from the app's.
