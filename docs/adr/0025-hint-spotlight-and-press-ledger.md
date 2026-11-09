# ADR-0025 — Hint Spotlight and press ledger are game-agnostic core modules

Date: 2026-11-08 · Status: accepted · Source: hint-parity run (spec A12–A20, A24)

## Context
Queens, Tango and Mini Sudoku are moving from "one hint = the answer" to "one press = one deduction, drawn in a Spotlight". Three games need the same dim, sequence and callout drawing, the same count-once and erase-culprit rules, and the same off-main, drop-if-stale compute. ADR-0001 forbids core from interpreting a hint; ADR-0008 fixes the module graph; ADR-0022 requires a release flag for unfinished trunk features.

## Decision
1. **One press flow.** `StepHints<S, K>` in `core:game-ui` owns the hint search, the ledger of paid-for step keys and the Spotlight state for every stepwise game. Each game supplies only a small `StepHints.Game` adapter: capture the board, key a step, say whether a step is a culprit, build the Spotlight, and erase the culprit as one undo unit. There is no per-game press-flow class. `StepHints` never reads a game's step type (ADR-0001).
2. **Spotlight glyphs are shape-only.** A `Spotlight` carries its glyphs. `SpotlightGlyphs` is a shape-only seam: the game draws its native glyph shape, and colour, alpha, sequence and badge belong to the overlay. So `core:designsystem` still has no game type and ADR-0001 holds. A `Spotlight` never carries a step, and steps never carry roles.
3. **Solution cached once per puzzle.** Steppers take a solution that the game adapter caches once per puzzle. There is no constrained solve per press. The solver budget still bounds any work that runs (A19).
4. **Queens rungs state their own proof.** A Queens rung emits `QueensProof`, the rung's own statement of its proof. Nothing rebuilds evidence afterwards.
5. **The flag is bound once.** `HintModeSource` (`fun interface`, `current(): HintMode`) is bound once, in the app's Koin module where the release flags are in reach. No UI module reads a flag.
6. **Overlay bounds equal the grid.** The overlay's bounds equal the cell grid's bounds (rows × cols uniform cells). Callers mount it inside any frame padding.
7. **Classic Sudoku keeps today's hint.** Classic Sudoku is not wired to stepwise and keeps today's hint for good (A24). The callout carries the hint text, so the game's caption in the shared slot is empty while a Spotlight shows, and the game's violation line is suppressed (A11).

## Consequences
+ A new deduction is one step class added to one game's catalog list. A new game is one mapper from its step type to roles, plus one `StepHints.Game` adapter (A18).
+ Flag off is today's code path, byte-for-byte. The `HintParity` flag and the app's `HintModeSource` binding are removed by the cleanup tickets (37a / 37b Queens, 38a / 38b Tango, 39 flag), which also remove the Queens and Tango legacy hint code.
+ `HintMode.Legacy` and Sudoku's legacy hint path stay for Classic.
− `core:designsystem` grows a Spotlight drawing module and tokens.
− Two ladders per game exist until cleanup. Queens keeps its default ladder for grading and drafting, and adds a stepwise ladder for hints. The two-ladder situation in Queens ends only if a later ADR converges them.

## Rejected alternatives
- **Per-game drawing with shared tokens only.** Three copies of the dim, sequence and callout logic drift apart, which is the bug A12 and A13 exist to prevent.
- **One press-flow class per game.** Three copies of the search, ledger and culprit state. The adapter carries the only per-game difference.
- **Press ledger in the session or codec.** Breaks the no-format-change rule (A21). The ledger is per live play and in-memory by design (A9).
- **Constrained solve on every press.** Costs latency on the press path. The solution is cached once per puzzle instead (decision 3).
- **Steps carrying roles, or a Spotlight carrying a step.** Couples the engine to drawing and the drawing to the engine. Roles belong to the mapper, outside both.
- **`lastMove` or marks through `GameRules.hint`.** Grows a contract that Zip and Strands share.
- **`Dispatchers.Default` in controllers.** Compute runs on an injected dispatcher from `DispatcherProvider` (konsist rule P2).
- **Reading the flag in UI modules.** Spreads the release decision across modules. The app binds `HintModeSource` once.

Respects ADR-0001, 0008 (no new forbidden edge), 0009, 0022, 0023. Contradicts none.
