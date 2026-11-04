# PuzzleBox — project map

pinned: 31d29fac353126ba7ca6635484adc6110fdebdb3

## Purpose

One Android app for daily logic puzzles — play official NYT/LinkedIn dailies, build and share
custom games, compete via leaderboards and 1v1. Zip shipped first: the compete loop (daily
fetch → play → completion record → leaderboard submit) is live as of v0.5.1. Product frame and
versioning policy live in PROFILE.md (semver source of truth; alpha 0.x.y until all five games
ship tested). Architecture decisions are ADR-recorded in `docs/adr/` — ADR-0001 game contracts
as orthogonal `GamePlugin`, ADR-0002 the archive as a future `PuzzleSource` adapter, ADR-0003
per-game editors only after that game ships, ADR-0004 records normalized in `core/data` with
`CompletionRecord` emitted by games, ADR-0005 every persisted artifact self-describes its
schema version and rule changes are data (`Puzzle.variant`).

## Layout

Gradle root `PuzzleBox`, twelve modules. Dependency direction: `:app` → `:feature:*` →
`:core:*`; `:games:zip`/`:games:zip-ui` plug in through Koin; `:backend:firebase` implements
seams declared in `:core:data`; `:core:model` is the dependency-free base. Three modules are
pure JVM (no Android): `:core:model`, `:core:game`, `:games:zip`.

- `app/` (`:app`) — `PuzzleBoxApplication` (starts Koin, then starts `LeaderboardSubmitWatcher`),
  `MainActivity` (Compose host, theme resolution, edge-to-edge), `PuzzleBoxApp` (Navigation3
  shell: Home/Play/Profile tabs, pushed History/ReplayZip/Result/Catalog entries, SceneCanvas,
  fade-through transitions honoring system reduce-motion, special Result enter transition),
  NavKey routes in `nav/TopLevelDestination.kt`, `auth/GoogleIdTokenProvider` (Credential
  Manager with a typed `Unavailable.Reason` taxonomy), `leaderboard/LeaderboardSubmitWatcher`
  (auto-submits records when signed in), `screens/ProfileScreen` (ProfileViewModel: identity,
  sign-in/out, today's board), `di/AppModule` (app scope, `List<GamePlugin>` aggregation,
  `GameUiRegistry` with Zip's renderer), debug-only Showkase browser (`CatalogDebugBinding`,
  `PreviewBackdrop`, `BrowserRadioControls`; goldens in `app/snapshots/`).
- `core/common/` — `DispatcherProvider` seam (+ default impl), `AppResult`/`AppError` and
  `resultOf` convention (typed errors, never thrown surprises), Koin `commonModule`.
- `core/data/` — Room v4 (`PuzzleBoxDatabase` with the legacy `DailyPuzzleDao`/`DailyPuzzleEntity`
  feed, `PuzzleDailyDao`/`PuzzleDailyEntity` keyed gameId+day carrying `puzzleNumber`,
  `variant`, `formatVersion` for Zip dailies, `CompletionDao`/`CompletionEntity` keyed on the
  encoded puzzle-ref string, schemas exported 1–4, `Migrations.ALL` = 1→2→3→4), network
  (legacy `PuzzleFeedApi` + `AuthHeaderInterceptor`/`AuthHeaderSource`, `LinkedInZipApi`
  stateless-flight GET with `LinkedInSessionCookieJar`, `LinkedInZipClient` fetch ladder,
  `TrailGameExtractor` rotation-tolerant `trailGamePuzzle` scan), repositories
  (`DailyPuzzleRepository` → offline-first (legacy feed), `ZipDailyRepository` →
  `OfflineFirstZipDailyRepository` (`today()`/`cached()`/`observeHistory()`, `ZipToday`
  Fresh/Cached/Unavailable), `CompletionRepository` → `RoomCompletionRepository` +
  `FirstWinPolicy`), `identity/IdentityService` + `AuthResult`/`AuthError`, `leaderboard/`
  (`LeaderboardRef`, `LeaderboardEntry`, `LeaderboardRepository`, `SubmitResult`
  Ok/Deduped/Err, `LeaderboardEntries.fromRecord`), Koin `dataModule`.
- `core/designsystem/` — `PuzzleBoxTheme` composition root, token vocabulary (`ColorTokens`
  light/dark, `Type` Baloo 2, `Shape`, `Spacing`, `MotionTokens` + named motion patterns),
  `BoardTokens`/`BoardColors` (board palette + wall/grid weights, contrast-tuned per light/dark),
  the interactive `PuzzleBoard` reference board (drag/tap with haptics), components (buttons,
  chips, dialogs, top bars, states) carrying `@ShowkaseComposable` metadata, `Previews.kt`
  Light/Dark `@Preview` pairs, `ShowkaseTokens` color/typography registrations; the browser
  runtime is debug-only (`ShowkaseBrowserModule` `@ShowkaseRoot` here; the browser screen lives
  in `app/src/debug`, goldens in `app/snapshots/`). Font license in `licenses/`.
- `core/model/` — dependency-free shared types in package `com.ivandsky.puzzlebox.model`
  (`GameId`, the `DailyPuzzle` envelope: gameId/day/payload bytes/fetchedAtMs, `ThemeMode`);
  pure JVM.
- `core/game/` — the five orthogonal game contracts (ADR-0001): `GameDefinition`
  (id/displayName/`Cadence`/symmetric `decode`/`encode`), `GameRules<P>`
  (`validate`/`isComplete`/`hint`), `GameSession` + `GameSessionCodec`, `ScoreModel<S>` →
  `CompletionRecord?`, `PuzzleSource` (`fetch(date)` → `FetchResult`; `FetchError` =
  Unavailable/Throttled/Untrusted), composed per game as `GamePlugin<P, S, M>` (the one type
  `app` aggregates). Supporting types: `Puzzle` (gameId+variant+formatVersion+payload bytes),
  `DecodeResult`/`DecodeError` (UnsupportedVersion/UnknownVariant/VariantSizeMismatch/Malformed),
  `VariantId`, `RecordStats` (versioned open stats slot), `CompletionRecord` (puzzleRef + time +
  hints + stats; `firstCompletedAt`/`isRecord` stamped by core/data).
- `core/testing/` — test doubles, consumed ONLY via `testImplementation(projects.core.testing)`
  (package `com.ivandsky.puzzlebox.testing`): `repository/` fakes of the core:data interfaces
  (`FakeDailyRepository`, `FakeCompletionRepository`, `FakeIdentityService`,
  `FakeLeaderboardRepository`, `FakeUserPreferencesRepository`), `game/` (`TestGames` stand-in
  registry, `FakeDailySource`, `InMemoryPuzzleSource`), `rule/MainDispatcherRule`.
- `core/game-ui/` — the Compose mapping layer, keyed by `GameId` (ADR-0001: no Compose in
  `:core:game`): `GameUiRegistry` (GameId → `GameBoardRenderer`, `Board()` renders loading/
  error/empty via `BoardStates`), `BoardGrid` + `EdgeOverlay` (walls), `DragTrace`
  (pointer-drag → cell events), `GridGeometry`, `HintController`/`HintButton`, `TimerBar` +
  `formatClock`, `PlayScaffold` (`playLayoutFor` SinglePane/TwoPane policy, 552dp board cap),
  `CompletionRecordEmitter` (one record per PuzzleRef, `AppResult` write).
- `games/zip/` — the first game plugin (pure JVM). `ZipDefinition` + `zipPlugin`
  (GamePlugin<ZipState, ZipSession, ZipRendererModel>, Koin-registered under the `"zip"`
  qualifier); model `ZipPuzzle`/`ZipBoard` (n, wall set, ordered waypoints) + `ZipPuzzleRef`;
  engine: `ZipRules` validating through an ordered `ZipRule` pipeline (`ZipRuleId` START_CELL →
  END_AT_LAST_WAYPOINT), `ZipVerifier` oracle, `ZipSolver`/`ZipSearch` (bounded solution-count
  search, `longestSolvablePrefix` for hints), `ZipHints`, `ZipVariants` (zip/n5–n8; BRIDGE/
  TELEPORT features reserved); `codec/`: `ZipCodec` v1 (byte format, canonical re-encode for
  storage), `ZipWireParser` (structural `trailGamePuzzle` JSON → typed puzzle, untrusted until
  the A16 oracle gate), `ZipSessionCodec` v2 (session serialize/restore incl. the undo/redo history of
  `ZipCommand`s Extend/RetractTo/Reset; still reads v1); `ZipScoreModel`
  emits `RecordStats` v1 {variant, mistakes, backtracks}; `render/ZipRendererModel`.
- `games/zip-ui/` — `ZipBoardRenderer : GameBoardRenderer` (BoardGrid + walls + waypoint dots +
  path trace + hint highlight) and `zipUiModule` (factory binding for the renderer).
- `feature/home/` — `HomeRoute` (DI-bound entry: observes `ZipDailyRepository.observeHistory()`
  — the cache-only puzzle_dailies store — plus `List<GamePlugin>` for the featured game; never
  the network), `HomeScreen` (single/two-pane, play/history destinations, appearance via
  `ThemeDialog`), `HomeLayout` + `homeLayoutFor` pure pane policy.
- `feature/play/` — the one play surface for every game (ADR-0006): `PlayRoute` (game picker →
  restore-adopt-load; `BackHandler` ends the flow), `PlayFlow` (per-entry scope around one
  `ActivePlay`: `ZipPlayState` / `TangoPlayState` / `QueensPlayState`; rebindable session
  saver so rotation then process death keeps the live session), per-game `*PlayState`
  (screen states, hint machinery, record emission) implementing the core seams
  `PlayClock`/`PlayHints`/`PlayUndo`, `*PlayScreen` (board area + `PlayChrome`),
  `ResultRoute` (shared `ResultScreen` + the active state's `resultLabel`), `di/PlayModule`.
- `core/game/` `Command`/`CommandHistory` — undo/redo as per-move commands over the game's
  board state; `core/game-ui/` `PlayChrome` (title, timer, hint button+count, undo/redo,
  game-controls slot), `HintButton`, `ResultScreen` (label + `gameSection` slot).
- Session codecs persist the command history: `ZipSessionCodec` v2, `TangoSessionCodec` v2,
  `QueensSessionCodec` v3 (each still reads its previous version with empty history).
- `feature/history/` — `HistoryRoute`/`HistoryScreen` (paged day list with streak header),
  `HistoryPaging` (`historyDays` PagingData flow, PAGE_SIZE 20), `HistoryLayout` +
  `historyLayoutFor`, `Streak.streakEndingAtNewest`, `ReplayLaunch` (cached puzzle + entry →
  `ZipSessionCodec` session bytes as Ready/Missing/Unreadable — replay rides the same
  PlayRoute with a restored session).
- `backend/firebase/` — implements the `core/data` seams: `FirebaseIdentityService`
  (`IdentityService`) over an internal `AuthGateway`/`FirebaseAuthGateway` (Google ID-token
  sign-in), `FirestoreLeaderboardStore` + `FirestoreLeaderboardRepository`
  (`LeaderboardRepository`; dedupe on (uid, puzzleRef) — first write stands; retries transient
  snapshot errors), `LeaderboardDocCodec`, `BackendModule` bindings. Deployed rules and
  indexes live alongside the module (`firebase.json`, `firestore.rules`, `firestore.indexes.json`).
- `PROFILE.md` — product frame, ranked care-axes, deploy targets, testing style.
- `docs/adr/` — the five accepted ADRs summarized under Purpose.

## Entry points

- `PuzzleBoxApplication.onCreate` — `startKoin` with `appModules` = `commonModule` +
  `dataModule` + `firebaseBackendModule` + `playModule` + `zipModule` + `zipUiModule` +
  `appModule` (the whole object graph), then `LeaderboardSubmitWatcher.start()`.
- `MainActivity` — sole activity; holds composition until the persisted `ThemeMode` arrives
  (no light flash), resolves dark theme against the system setting, keeps system bar icon
  appearance in step with the resolved in-app theme, then hosts `PuzzleBoxApp` with the width
  size class, `ThemeMode`, and the set-theme callback.
- `PuzzleBoxApp` route table (Navigation3 `NavDisplay`): tabs Home → `HomeRoute`, Play →
  `PlayRoute`, Profile → `ProfileRoute` (real identity + leaderboard screen now). Pushed
  entries: History → `HistoryRoute` (Home's history teaser), ReplayZip(session bytes) →
  `PlayRoute` with a restored session, Result(timeMs, hintsUsed) → `ResultRoute` (solved only
  from Play, distinct enter transition), Catalog → the debug-only Showkase browser (reflective
  `CatalogDebugBinding` registration, FLAG_DEBUGGABLE-gated; non-debuggable builds get the
  cutover placeholder). Tab selection resets to the tab root; `pushOnce` guards double-push.
  Bottom bar on compact widths, navigation rail on expanded-width landscape (the
  `homeLayoutFor`/`playLayoutFor`/`historyLayoutFor` decisions drive nav shape and each
  screen's panes).
- No deep links, widgets, or services.

## Seams

- **Koin DI graph** — `commonModule` (`DispatcherProvider`) + `dataModule` (Json,
  `AuthHeaderSource`→`NONE`, OkHttp, two Retrofit instances → `PuzzleFeedApi` and
  `LinkedInZipApi` (cookie jar), `LinkedInZipClient`, `LinkedInZipDailySource` bound as both
  `ZipDailySource` and `PuzzleSource` (ADR-0002's first adapter), `OfflineFirstZipDailyRepository`,
  Room v4 + all three DAOs, `CompletionRepository`→`RoomCompletionRepository`,
  `DailyPuzzleRepository`→offline-first, DataStore + `UserPreferencesRepository`) +
  `firebaseBackendModule` (`AuthGateway`, `IdentityService`, `LeaderboardStore`,
  `LeaderboardRepository`) + `playModule` + `zipModule` (`zipPlugin`) + `zipUiModule`
  (`ZipBoardRenderer`) + `appModule` (`CoroutineScope`, `GoogleIdTokenProvider`,
  `ProfileViewModel`, `LeaderboardSubmitWatcher`, `List<GamePlugin>` via `getAll`,
  `GameUiRegistry` with Zip's renderer). Graph completeness is asserted by `DiGraphTest`
  under Robolectric.
- **`GamePlugin`/`GameDefinition` contracts** (ADR-0001, `core/game`) — onboarding a game is
  one module + one Koin registration; `app` aggregates `List<GamePlugin<*, *, *>>` and never
  references a concrete game. The zero-`core:game`-edit guarantee covers onboarding AND
  evolution (ADR-0005: versioned codecs, `Puzzle.variant` rule-as-data, `RecordStats` open slot).
- **`PuzzleSource`** — where puzzles come from; implementations live outward of core
  (`LinkedInZipDailySource` today, `InMemoryPuzzleSource` in core/testing, the archive later per
  ADR-0002). `FetchError` distinguishes Unavailable (degrade to cache) from Throttled
  (LinkedIn soft-redirect cooldown, retryable) from Untrusted (oracle gate failed).
- **`GameUiRegistry`** (core/game-ui) — GameId → `GameBoardRenderer` is the game→UI seam:
  games ship a pure `RendererModel` factory; the Compose mapping registers by GameId
  (`registerZipBoard()`), so a game's UI is swappable without touching Play.
- **Repository interfaces** (live in core/data, features inject them):
  `ZipDailyRepository` — `today()` is network-first (`ZipToday.Cached` carries the fetch error
  so UI can banner degradation), `cached()`/`observeHistory()` never touch the network.
  `CompletionRepository` — `onCompleted` stamps ADR-0004 semantics via `FirstWinPolicy`
  (first win is the record, replays kept) under a Room transaction keyed by
  `PuzzleRefKeys.encode(puzzleRef)` (`gameId/day[/n]` strings; a game ships its ref type only
  together with this registration). `UserPreferencesRepository` — `themeMode` Flow + setter.
  `DailyPuzzleRepository` remains as the legacy feed stack.
- **`IdentityService` / `LeaderboardRepository`** (core/data interfaces, Firebase impl in
  `backend/firebase`) — identity is a `StateFlow<Identity?>`; leaderboard `submit` returns
  `SubmitResult` (Ok/Deduped/Err, never throws; A7). Swapping backends is a module swap.
- **`LeaderboardSubmitWatcher`** (app) — combines `observeRecords()` with identity: signed
  out = local-only play; signed in, every record converts via `LeaderboardEntries.fromRecord`
  and submits once per (uid, puzzleRef) with in-process dedup and quiet-failure retry.
- **`DispatcherProvider`** — dispatcher injection keeps callers testable.
- **`AuthHeaderSource`** — fun interface where LinkedIn session headers plug in; currently
  `NONE` (the Zip client rides `LinkedInSessionCookieJar` instead).
- **Handwritten fakes** in `core/testing` (test-scope only): `FakeDailyRepository`,
  `FakeCompletionRepository`, `FakeIdentityService` (scriptable auth outcomes),
  `FakeLeaderboardRepository` (records submissions), `FakeUserPreferencesRepository`,
  `TestGames`/`FakeDailySource`, `InMemoryPuzzleSource` — the tdd-fakes style PROFILE.md names.
- **Pure layout policies** — `homeLayoutFor`, `playLayoutFor`, `historyLayoutFor`:
  width+orientation → pane arrangement; tested without a device.
- **`Migrations.ALL`** — every schema change bumps the Room version (now 4), commits an
  exported schema JSON, and registers a migration (1→2 puzzle_dailies, 2→3 completions,
  3→4 daily_puzzles rebuild); destructive fallbacks disallowed.
- **Roborazzi screenshot harness** — committed goldens are the debug browser's
  (`app/snapshots/`, 14 pngs from `ShowkaseBrowserScreenshotTest`); `feature/play`'s
  `VariantMatrixScreenshotTest` renders its board/variant matrix into the `.scratch` review
  tree. `-Proborazzi.verify` remains the verify-vs-record switch in the designsystem and play
  build files.

## Working test commands

Counts below are the `@Test`-annotated methods in each module's unit-test sources at the
pinned sha (500 total), read from the test trees — not executed in this pass. Robolectric
sdk 34 where device APIs are touched; `:app` requires `ANDROID_HOME` or `local.properties` —
SDK lives at `~/Library/Android/sdk` here; Gradle toolchain is pinned to JVM 25 via
`gradle/gradle-daemon-jvm.properties`. Android modules run `testDebugUnitTest`; the pure-JVM
modules (`:core:game`, `:games:zip`) run plain `test`; `:core:model` has no test sources.

- `./gradlew :app:testDebugUnitTest` — 61 tests (DI graph, nav routes, auth reason mapping,
  submit watcher, profile screen/viewmodel, 16 browser screenshots)
- `./gradlew :backend:firebase:testDebugUnitTest` — 32 tests (identity service, leaderboard
  repository, doc codec)
- `./gradlew :core:common:testDebugUnitTest` — 8 tests (AppResult/resultOf)
- `./gradlew :core:data:testDebugUnitTest` — 81 tests (offline-first repos, DAOs + migration,
  DataStore prefs, auth interceptor, zip client/extractor/source, leaderboard entries,
  first-win policy, fakes)
- `./gradlew :core:designsystem:testDebugUnitTest` — 32 tests (token checks, board mapping,
  component and puzzle-board interaction)
- `./gradlew :core:game:test` — 6 tests (contract-conformance suite)
- `./gradlew :core:game-ui:testDebugUnitTest` — 37 tests (grid geometry, drag trace, registry,
  clock formatting, layout policy, record emitter, compose component checks)
- `./gradlew :feature:home:testDebugUnitTest` — 14 tests (layout policy, home screen/route)
- `./gradlew :feature:history:testDebugUnitTest` — 30 tests (paging, layout policy, streak,
  replay launch, screen)
- `./gradlew :feature:play:testDebugUnitTest` — 75 tests (play state machine, screens,
  33-case board/variant matrix)
- `./gradlew :games:zip:test` — 122 tests (rule pipeline, property tests, codec fixtures +
  property + golden snapshot, solver uniqueness/benchmark, generator, session, plugin, score)
- `./gradlew :games:zip-ui:testDebugUnitTest` — 2 tests (board renderer)

## Diagram

[docs/agents/project-map.html](project-map.html) — archify architecture candidate
(finalized at the pinned revision; candidate at
`.archify/project-map/architecture-candidate.json`). Archify added per-topic diagrams on
2026-10-31 (`arch-modules`, `flow-zip-fetch`, `seq-zip-move` under `.archify/`, plus
`.archify/project-map/review-2`). Note: archify's repository-evidence gate requires a git
remote identity and the repo had none when the candidate was built (origin
`github.com:ivanDsky/PuzzleBox` was added later), so per-node source citations were omitted
and the pinned revision is recorded in the diagram's Contracts card; the topology, labels,
and cards are evidence-derived from the modules above.

## Drift rule

If `git rev-parse HEAD` differs from `pinned:`, run `git diff --stat <pin>..HEAD` and
refresh only the sections the touched paths hit (layout, entry points, seams, tests), then
bump the pin — never edit the map in conversation.
