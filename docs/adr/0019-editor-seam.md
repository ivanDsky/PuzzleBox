# ADR-0019 — The editor seam: `GameEditor` on the plugin, `GameEditorUi` in a registry

Date: 2026-11-06 · Status: accepted · Source: editor spec A3–A10, A22–A23, A28; prototype
handoffs 01–09; spec checkpoint 2026-11-06 (OQ3 Queens ladder, OQ4 share target) ·
Supersedes ADR-0003 (per-game editor phase) · Amends ADR-0008 §1/§3 (plugin and registry
seams)

## Context
ADR-0003 planned one editor phase per game, each after its game shipped. All five games now
ship play (Zip, Queens, Tango, Mini Sudoku, Strands; 9x9 Sudoku in debug, ADR-0015), and the
user asked for every editor in one run. Play already has a two-part seam: a pure
`GamePlugin` in `core:game`, found through `GameRegistry` (ADR-0008 §1)
[ev: GamePlugin.kt:32-41], and a `GamePlayUi` in `core:game-ui`, found through
`GamePlayRegistry`, which throws on a duplicate id [ev: GamePlay.kt:17-24,111-121]. Features
may not name a game (ADR-0008 §6 `feature:* → games:*`) [ev: ModuleGraphGuard.kt:70-71].
Three callers need the editor logic without UI: `core:data` (decoding drafts and shares),
`core:domain` (import) and `:tools:puzzle-cli` (ADR-0021).

## Decision
1. **A pure `GameEditor<D>` contract in `core:game`, one implementation per `games:x`.** Its
   members are:
   - `variants` and `newDraft(variant)`;
   - `apply`/`undo`/`redo` through the existing `CommandHistory`
     [ev: core/game/.../Command.kt:18-31];
   - a versioned `encodeDraft`/`decodeDraft` (draft codec v1; partial boards allowed);
   - `analyze(draft, budget): Verdict`;
   - `publishBlocks(draft)`;
   - `toPuzzle(draft, solution)` through the game's existing codec;
   - `encodeShare`/`decodeShare` (ADR-0020);
   - notation `parse`/`print`.
2. **It is reached through the plugin, not a second registry.** `GamePlugin` gains
   `editor: GameEditor<*>?` (null = no editor in this build). `GameRegistry` stays the one
   `GameId` lookup for pure code. Create's New-puzzle sheet lists exactly the games whose
   plugin has an editor.
3. **`GameEditorUi` + `GameEditorRegistry(List<GameEditorUi>)` live in `core:game-ui`.** Each
   `games:x-ui` binds one `GameEditorUi` under its id qualifier, built on that game's
   existing board composables. `app` builds the registry once, and a duplicate id throws,
   exactly like `GamePlayRegistry`. `feature:create:impl` drives any game through these two
   seams and names none.
4. **`Verdict` is one sealed type for every game.** `Incomplete(reason)` | `Checking` |
   `Unique(solution, difficulty?, needsGuessing)` | `Multiple(a, b, differingCells)` |
   `None(conflicts)` | `Undetermined`. Every solver counts to a limit of 2 and returns both
   witnesses. Every solver checks a cooperative `Budget` in its search loop and names no
   dispatcher (Konsist P7). The UI debounces about 150 ms, runs on the injected
   `DispatcherProvider.default`, cancels a stale solve on each edit and ends a solve that
   runs past about 1.5 s in `Undetermined`. `Checking` and `Undetermined` keep Publish
   disabled.
5. **Publish gates sit outside the solver.** `publishBlocks` returns:
   - non-contiguous Queens regions, an empty title, text over caps and Strands blocklist
     hits;
   - **needs guessing** on the ladder games (Queens, Tango, Sudoku): the game's own hint
     ladder must solve the puzzle from its givens.

   Difficulty is the hardest rung that solve needs. Queens' ladder grows to four rungs
   (singles, line lock, pigeonhole, look-ahead) in `games:queens`, one step file per rung.
   The gate, the label and play hints share it, so daily Queens hints gain the new reasons.
6. **The test solve runs in a sandbox.** The editor hosts the game's real `GamePlayUi.Board`
   on a controller whose `PlayEnvironment.onWon` record [ev: GamePlay.kt:78-81] is read for
   time and hints and then dropped. A test solve never becomes a `CompletionRecord`. Any
   board edit clears it, and undoing that edit restores it.
7. **One notation per game, one import use case.** The parser and printer live in
   `games:x`, so the app and the CLI share them byte for byte. `ImportDraftFromText` in
   `core:domain` parses with a 64 KB cap, returns a typed error with line and column, and
   saves an `imported` draft. It has two callers: the clipboard Import sheet and the Android
   `ACTION_SEND` `text/plain` share target, which `MainActivity` hands to Create. The text
   never rides in a NavKey (Konsist P8).
8. **Run phasing.** Queens goes first as the walking skeleton (seam, Create tab, studio
   chrome, local-only loop). The other four editors follow in parallel once that skeleton is
   accepted at its device check.

## Consequences
- Adding an editor for a game means new files in `games:x` and `games:x-ui`, one `editor =`
  argument on its plugin and one `GameEditorUi` binding. Create, import, sharing and the CLI
  pick it up by `GameId` without edits.
- Zip, Tango and Sudoku gain a "return up to 2 solutions" entry point, as Queens already has
  [ev: QueensSolver.kt:28-37].
- The draft codec is separate from the play codecs, which reject incomplete boards
  [ev: SudokuCodec.kt:14-18]. It carries the bounded undo history, so undo survives process
  death.
- Changing the Queens ladder changes daily hint behaviour. Characterization tests pin
  today's hints before the change.
- A new import source (file picker, `ACTION_VIEW`) is one more caller of
  `ImportDraftFromText`.
- The seam has two adapters from the first ticket: `QueensEditor`/`QueensEditorUi` in
  production, and `FakeGameEditor`/`FakeGameEditorUi`/`FakeEditorController` in
  `feature:create:impl` tests (`FakeEditor.kt`). Feature tests never depend on Queens rules,
  and a seam member that only Queens could answer shows up as a fake that cannot implement it.
- A seam interface has a stable core plus capabilities. `GameEditor` and `GameEditorUi` keep
  the members every game must answer from their first ticket. A later member is either
  defaulted (`publishBlocks`, `ToolHint`, `Issues`) or a separate capability interface the game
  object also implements and callers find with `as?` (`DraftExport`, `EditorShareCodec`,
  `EditorNotation`), so a landed implementer never breaks. This supersedes the flat member list
  in Decision 1: `toPuzzle`, the share codec and notation are capabilities, undo is
  `ReplaceDraft` plus each game's commands, and `00-interfaces.md` §2.1 is the source of truth.

## Rejected alternatives
- A per-game editor phase after each game ships (ADR-0003): every game has shipped, and the
  user asked for all five editors in one run.
- A separate `GameEditorRegistry` in `core:game`: a second `GameId` lookup for pure code,
  which ADR-0008 §1 forbids. The plugin field keeps one lookup.
- Editing inside the play UI (`GamePlayUi` gains edit modes): it mixes two lifecycles in
  one controller, and play has no undoable resize or verdict pipeline.
- An editor-only Queens grader: a friend could hit reason-less hints on a puzzle the gate
  called hint-solvable (checkpoint OQ3).
- The wire JSON as the notation (goldfish reuse-5): those are feed shapes with a date and a
  mandatory solution, which an agent cannot write.
- Clipboard-only import (the pre-checkpoint recommendation): the user asked for the share
  target too (checkpoint OQ4).
