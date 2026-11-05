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
   `Screen`), `GamePlayController` (`adoptToday`, `adoptRestored`, `replay`, `isComplete`),
   `PlayEnvironment` (scope, session-changed and won callbacks), `PlayBoardStatus`,
   `PlayCopy`, and `GamePlayRegistry` (GameId → `GamePlayUi`; a duplicate id throws).
   Games' controllers also implement the ADR-0006 `PlayClock`/`PlayHints`/`PlayUndo` seams.
   `core/game-ui` never references `feature/play`.
2. Each `games/*-ui` module ships exactly one controller and one `GamePlayUi`, bound in its
   `*UiModule` as a `GamePlayUi` single qualified by the game id; `app` binds
   `GamePlayRegistry(getAll())` once.
3. `feature/play` is game-agnostic (no `games:*` dependency, no `when` over game ids):
   `PlaySessionStore` (Koin single) owns the live `ActivePlay`s (one per slot since §4's amendment) — fetch the daily, resolve
   the record identity, write the record, derive the badge and result label — and creates a
   new controller per load, dropping callbacks from released controllers. `HubViewModel`,
   `GameViewModel` and `ResultViewModel` expose it to `HubRoute`/`GameRoute`/`ResultRoute` via
   `collectAsStateWithLifecycle` (0.8: `PlayViewModel`/`PlayRoute`).
4. *(Amended 2026-11-04, navigation-ux, ADR-0010.)* Sessions persist on disk and resume.
   The store keeps one `ActivePlay` per `PlaySlot` (game, day), shared by every stack whose
   `GameKey`/`ResultKey` names that slot; `open(key)`, `peek(slot)`, `retain(keys)` and
   `flush()` are its only API. `SlotOpening` decides where a slot's board comes from: the
   saved row (its `SessionBlob` envelope must name the slot's game and decode, else the row
   is deleted), then today's fetch, then a past day's replay build. Each slot's `SlotWriter`
   coalesces saves by trailing throttle (the first change opens a 300 ms window, its end
   writes the latest) into `SavedPlayRepository` (Room `saved_plays`, newest 8 slots); a solve
   discards the row. Saves, deletes and opening reads share one serial `WriteLane`. The shell
   calls `retain(allKeys)` whenever the stacks change (a slot no key names folds its clock,
   saves and is cancelled) and `flush()` at activity stop. After process death a key reopens
   its slot from disk; nothing rides the route's saveable.
   *Originally:* persistence stayed in the route's `rememberSaveable` via `SessionBlob`
   envelopes, with one live play that `end()` dropped.
5. The picker and Home's featured game follow the app-side product order (zip, tango,
   queens); an unlisted game sorts after them by id.

## Consequences
- A new game needs: a `games/<id>` plugin module (ADR-0001) bound under its id qualifier, a
  `games/<id>-ui` module with one `GamePlayController` + one `GamePlayUi` bound under its id
  qualifier, both modules added to `PuzzleBoxApplication.appModules`, and its id in
  `GAME_ORDER`. `feature/play` and `core/game-ui` stay untouched.
- *(Amended 2026-11-04, ADR-0010.)* ViewModels are per entry: the shell decorates every tab's
  stack with Navigation3's per-entry `ViewModelStore`, so `HubViewModel`, `GameViewModel` and
  `ResultViewModel` live and die with their entry, and own only UI state. The play outlives
  them in the store; releasing it is `retain`'s job. *Originally:* ViewModels were
  activity-scoped and the session survived in the saveable.
- Tests of the play layer run against stand-in games; real-game route coverage lives in
  `app`, the only module that sees every game.
