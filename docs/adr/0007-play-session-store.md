# ADR-0007 — One game-agnostic play session store; each game ships one play UI

Date: 2026-11-04 · Status: accepted · Source: P6 refactor (review findings F1, F2, F4–F7, F9)

## Context
ADR-0001 promises "one game module + one registration", but the play feature still named
every game: `PlayFlow` switched over game ids to build a `ZipPlayState` / `TangoPlayState` /
`QueensPlayState`, `feature/play` depended on every `games:*` module, and `GameUiRegistry`
only mapped a game id to a board renderer while the screens, hint machinery and record
emission stayed in `feature/play`. The flow lived in a composition-scoped holder, so a
rotation could leave the session writing into a dead composition's saver (F5), back left the
old play's scope running (F6), and the routes read the flow directly instead of through a
ViewModel (F7).

## Decision
1. `core/game-ui` owns the play contracts: `GamePlayUi` (game id, `createController`,
   `tutorial`, `Board`, `Screen`), `GamePlayController` (`adoptToday`, `adoptRestored`,
   `replay`, `isComplete`), `PlayEnvironment` (scope, session-changed and won callbacks),
   `PlayBoardStatus`, `PlayCopy`, and `GamePlayRegistry` (GameId → `GamePlayUi`; a
   duplicate id throws). Games' controllers also implement the ADR-0006
   `PlayClock`/`PlayHints`/`PlayUndo` seams. `Board` is the game's one chrome-free board:
   its daily `Screen` and the tutorial both draw it. `tutorial` is the game's ordered
   `TutorialStep`s (instruction, puzzle, ref). `TutorialSession` is the step machine
   (lessons → ready, or skipped); `TutorialHost` draws it. `TutorialSandbox` builds each
   lesson's controller with an environment that drops session changes and turns a win into
   the next step, so a lesson never reaches the record emitter or the session store.
   `PlayChrome` takes an optional `onHowToPlay` that shows the "How to play" button.
   `core/game-ui` never references `feature/play`.
2. Each `games/*-ui` module ships exactly one controller and one `GamePlayUi`, bound in its
   `*UiModule` as a `GamePlayUi` single qualified by the game id; `app` binds
   `GamePlayRegistry(getAll())` once.
3. `feature/play` is game-agnostic (no `games:*` dependency, no `when` over game ids):
   `PlaySessionStore` (Koin single) owns the one live `ActivePlay` — fetch the daily, resolve
   the record identity, write the record, derive the badge and result label — and creates a
   new controller per load, dropping callbacks from released controllers. `PlayViewModel` and
   `ResultViewModel` expose it to `PlayRoute`/`ResultRoute` via `collectAsStateWithLifecycle`.
4. Persistence stays in the route's `rememberSaveable`: the store emits `SessionBlob`
   envelopes naming the game, so after process death (when the store single is gone) the
   envelope tells the fresh store which game to restart. Re-entry rebinds the sink and
   backfills the live blob; switching games or `end()` drops the old play's sink and cancels
   its scope.
5. The picker and Home's featured game follow the app-side product order (zip, tango,
   queens); an unlisted game sorts after them by id.

## Consequences
- A new game needs: a `games/<id>` plugin module (ADR-0001) bound under its id qualifier, a
  `games/<id>-ui` module with one `GamePlayController` + one `GamePlayUi` bound under its id
  qualifier, both modules added to `PuzzleBoxApplication.appModules`, and its id in
  `GAME_ORDER`. `feature/play` and `core/game-ui` stay untouched.
- ViewModels are activity-scoped (Navigation3 has no per-entry ViewModel store decorator
  here), which is why the session survives in the saveable rather than in a `SavedStateHandle`.
- Tests of the play layer run against stand-in games; real-game route coverage lives in
  `app`, the only module that sees every game.
