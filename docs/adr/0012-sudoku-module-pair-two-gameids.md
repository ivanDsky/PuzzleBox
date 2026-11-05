# ADR-0012 — One module pair hosts two Sudoku GameIds; 9x9 is debug-only

Date: 2026-11-05 · Status: accepted · Source: sudoku run spec A1, A2, A19 · Amends ADR-0008 §1 (one pair per game)

## Context
ADR-0008 gives each game one `games:<id>` + `games:<id>-ui` pair. Sudoku comes in two boards
that share every rule, command, solver, hint step and composable, and differ only in geometry
(6x6 with 2x3 boxes against 9x9 with 3x3) and in where the daily comes from. Mini Sudoku (6x6)
is LinkedIn's daily. The 9x9 board has no content source yet: this run ships no generator, and
LinkedIn has no 9x9. It is wanted only so the engine can be exercised at 9x9.

## Decision
1. **One pair, two ids.** `:games:sudoku` (JVM) and `:games:sudoku-ui` (Compose) host
   `minisudoku` ("Mini Sudoku", 6x6) and `sudoku` ("Sudoku", 9x9). Geometry is variant data
   (`sudoku/6x6`, `sudoku/9x9`: size, box rows × box cols). No code path branches on size.
   Every binding (`GamePlugin`, `DailySource`, `GamePlayUi`) is qualified by its own GameId,
   so `GameRegistry` and `GamePlayRegistry` still see one plugin and one UI per id.
2. **Per-id Koin modules.** The pair exposes one Koin module per GameId and side
   (`miniSudokuModule` / `miniSudokuUiModule`, `sudokuModule` / `sudokuUiModule`), so `app`
   can install either id without the other.
3. **9x9 is debug-only.** `app` installs the Mini Sudoku modules in every build. The 9x9
   modules come from a build-type source set (`src/debug` supplies them; `src/release`
   supplies an empty list). Release builds therefore have no `sudoku` plugin, which keeps
   `requireEveryGamePlayable` and `GAME_ORDER` (where unlisted ids are tolerated) valid in
   both builds.
4. **The 9x9 daily is a bundled sample.** Its `DailySource` returns the same public-domain
   easy puzzle every day (Wikimedia L2G-20050714) under a ref with no edition number, so it
   is never leaderboard-eligible. Records are stored locally as for any game.
5. **Forbidden edges are unchanged.** `-ui` depends on its JVM module and `core:game-ui`.
   Neither module depends on another game or on a feature.

## Consequences
- A real 9x9 source later replaces the bundled `DailySource`, and the debug gate moves. The
  engine and UI stay untouched.
- A future size (e.g. 4x4) that is its own GameId is one variant row and a source in the game
  module, plus wiring: a `SudokuGames` entry, the module pair in `appModules`, and a
  `GAME_ORDER` line. In `:games:sudoku-ui` it also needs a `SudokuTutorial` `BOARDS` entry
  (three lesson boards for that size) and, if the `SudokuGrid` stroke weights (one branch for
  sizes up to 6, one above) do not suit it, a new branch in `gridStrokes`.
- A new elimination technique is an `EliminationStep` file, one line in
  `EliminationSteps.DEFAULT` and a `SudokuTechnique` entry. Hint captions name only the single
  ("After ruling out some numbers, ..."), so `SudokuHintCaptions` does not change.
- The 9x9 classes (`BundledSudokuDailySource`, `SudokuSamples`, `sudokuModule`,
  `sudokuUiModule`) live in `main` source sets and ship in release, unregistered. Only the
  module registration is gated by build type.
- The "one pair per game" wording in ADR-0008 now reads "one pair per game family". Tests that
  list game ids (DI graph, E2E seen-sets) list the ids per build type.
