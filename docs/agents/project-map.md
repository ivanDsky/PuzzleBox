# PuzzleBox — project map

pinned: 89c0672567c072a9a14551baa12254d9b62f6b83

## Purpose

One Android app for daily logic puzzles — play official NYT/LinkedIn dailies, build and share
custom games, compete via leaderboards and 1v1. Zip, Tango and Queens play their LinkedIn
dailies through one compete loop (daily fetch → play → completion record → leaderboard
submit). Product frame and versioning policy live in PROFILE.md (semver source of truth; alpha
0.x.y until all five games ship tested). Architecture decisions are ADR-recorded in
`docs/adr/` — ADR-0001 game contracts as orthogonal `GamePlugin`, ADR-0002 the archive as a
future `PuzzleSource` adapter, ADR-0003 per-game editors only after that game ships, ADR-0004
records normalized in `core:data` with `CompletionRecord` emitted by games, ADR-0005 every
persisted artifact self-describes its schema version and rule changes are data
(`Puzzle.variant`), ADR-0006 command history and the shared core play chrome, ADR-0007 the
play session store, ADR-0008 the module graph and its enforced forbidden edges, ADR-0009 the
in-module structure rules (`:konsist`, 12 Konsist-backed rules with shrink-only allowlists;
each failure links to its section).

## Layout

Gradle root `PuzzleBox`; `settings.gradle.kts` is the module list. Dependency direction:
`:app` (composition root, depends on everything) → `:feature:*:impl` → `:feature:*:api` →
`:core:navigation`; impls also sit on `:core:*` (`:core:designsystem` via the
`puzzlebox.android.feature.impl` plugin). `:games:<x>` → `:core:game` (+ `:core:model`),
`:games:<x>-ui` → `:games:<x>`, `:core:game`, `:core:game-ui`, `:core:designsystem`; games plug in
through Koin. `:core:data` exposes `:core:common`/`:core:game`/`:core:model` and hides
`:core:database`, `:core:datastore`, `:core:network`; `:core:domain` exposes `:core:data`;
`:backend:firebase` implements seams declared in `:core:data`. Every module except app/backend
tests via `testImplementation(projects.core.testing)`. Pure JVM modules
(`puzzlebox.jvm.library`): `:core:model`, `:core:game`, `:core:network`, `:games:zip`,
`:games:tango`, `:games:queens`, plus `:konsist` (test-only `jvm.library`, zero project
edges; reads the repo's sources as files).

Forbidden edges (ADR-0008, [docs/adr/0008-module-graph.md](../adr/0008-module-graph.md)),
checked over ALL configurations incl. test by `./gradlew checkModuleGraph` (build-logic
`ModuleGraphGuard.kt`, registered by the application / android.library / jvm.library
convention plugins; `check`, `preBuild` and `classes` depend on it, so `./gradlew check` runs
it): anything → `:app`; `:core:testing` from a non-test configuration; `core:*` → non-core;
`feature:*` → `games:*`; `feature:*:api` → anything but `:core:navigation`/`:core:model`;
`feature:a:impl` → `feature:b:impl`; `games:*` → `feature:*`; `games:x` → `games:y` except
`games:x-ui` → `games:x`. Two more arms guard the verification module `:konsist` (ADR-0009):
`:konsist` → any module, and any module → `:konsist` (test configurations too).

- `app/` (`:app`) — `PuzzleBoxApplication` (starts Koin, then starts `LeaderboardSubmitWatcher`),
  `MainActivity` (Compose host, theme resolution, edge-to-edge), `PuzzleBoxApp` (Navigation3
  shell: Home/Play/Profile tabs over one `NavBackStack` driven through a `Navigator`, the
  feature-contributed entries plus the app-owned Catalog page, `SceneCanvas` as a
  `NavEntryDecorator`, fade-through transitions honoring system reduce-motion, special Result
  enter transition), `nav/TopLevelDestination.kt` (tab table over the features' keys),
  `nav/CatalogKey.kt`, `nav/PuzzleBoxNavKeys.kt` (`puzzleBoxNavKeys`: the back stack's
  polymorphic `SavedStateConfiguration` over every feature's keys),
  `leaderboard/LeaderboardSubmitWatcher` (auto-submits records when signed in),
  `di/AppModule` (app scope; `GameRegistry` over `getAll<GamePlugin>()` sorted by the app-side
  `GAME_ORDER` zip → tango → queens; `GamePlayRegistry(getAll())` over every game's
  `GamePlayUi`), debug-only Showkase browser (`CatalogDebugBinding`, `ShowkaseBrowserScreen`,
  `PreviewBackdrop`, `BrowserRadioControls`; goldens in `app/snapshots/`).
- `konsist/` (`:konsist`, test-only `puzzlebox.jvm.library`, no project dependencies) — the
  in-module structure rule suite (ADR-0009): `core/` engine (`SourceTree`, `Allowlist`, `judge`,
  `RuleCheck`, `CensusTest`), `rules/` one `<ID><Name>.kt` + test per rule, resources
  `allowlists/<ID>.txt` and `fixtures/<ID>/` (incl. `add-a-game`).
- `core/navigation/` (`:core:navigation`) — `Navigator` (`currentKey`, `navigate`,
  `navigateOnce` double-tap guard, `navigateToTopLevel` tab reset, `goBack`) over the app's
  single `NavBackStack<NavKey>`, and `navKeySavedStateConfiguration` (open `NavKey`
  polymorphism for back-stack saved state).
- `feature/{home,play,history,profile}/api/` (`:feature:*:api`, plugin
  `puzzlebox.android.feature.api`) — each feature's `@Serializable` NavKeys (`HomeKey`;
  `PlayKey`, `ResultKey`, `ReplayKey`; `HistoryKey`; `ProfileKey`) and a `*NavKeys()`
  polymorphic registration; `@SerialName`s stay pinned to the pre-split
  `com.ivandsky.puzzlebox.app.nav.*` class names so older saved stacks restore.
- `core/common/` (`:core:common`) — `DispatcherProvider` seam (+ `DefaultDispatcherProvider`),
  `AppResult`/`AppError` and `resultOf` convention (typed errors, never thrown surprises),
  Koin `commonModule`.
- `core/data/` (`:core:data`) — game-agnostic repositories: `DailyRepository` →
  `OfflineFirstDailyRepository` (`today(game)`/`cached(game, day)`/`observeHistory(game)`,
  `DailyToday` Fresh/Cached/Unavailable, `DailyEntry`; decodes through `GameRegistry`, fetches
  through every game's `DailySource`), `CompletionRepository` → `RoomCompletionRepository` +
  `FirstWinPolicy`, `UserPreferencesRepository` → `OfflineUserPreferencesRepository`;
  `model/` entity mappings + `PuzzleRefKeys`; `identity/` `IdentityService` + `AuthResult`/
  `AuthError`; `leaderboard/` (`LeaderboardRef`, `LeaderboardEntry`, `LeaderboardRepository`,
  `SubmitResult` Ok/Deduped/Err, `LeaderboardEntries.fromRecord`); Koin `dataModule` (includes
  `databaseModule`, `networkModule`, `datastoreModule`).
- `core/database/` (`:core:database`) — Room v5 `PuzzleBoxDatabase`: `PuzzleDailyDao`/
  `PuzzleDailyEntity` (`puzzle_dailies`, keyed gameId+day), `CompletionDao`/`CompletionEntity`
  (`completions`, keyed on the encoded puzzle-ref string); schemas 1–5 exported under
  `core/database/schemas/`, `Migrations.ALL` = 1→2→3→4→5; Koin `databaseModule`.
- `core/network/` (`:core:network`, pure JVM) — the one LinkedIn client: internal
  `LinkedInGamesApi` (Retrofit), `LinkedInSessionCookieJar` + `LinkedInCsrfInterceptor`
  (anonymous session), `LinkedInFeedTransport` implementing `core:game`'s `FeedTransport`;
  Koin `networkModule`.
- `core/datastore/` (`:core:datastore`) — `PuzzleBoxPreferencesDataSource` over a Preferences
  DataStore (`PuzzleBoxPreferences`); Koin `datastoreModule`.
- `core/designsystem/` (`:core:designsystem`) — `PuzzleBoxTheme` composition root, token
  vocabulary (`ColorTokens` light/dark, `Type` Baloo 2, `Shape`, `Spacing`, `MotionTokens` +
  named motion patterns), `BoardTokens`/`BoardColors` (board palette + wall/grid weights,
  contrast-tuned per light/dark), the interactive `PuzzleBoard` reference board, components
  (buttons, chips, dialogs, top bars, states) carrying `@ShowkaseComposable` metadata,
  `ShowkaseTokens` color/typography registrations; the browser runtime is debug-only
  (`ShowkaseBrowserModule` `@ShowkaseRoot` in `src/debug`; the browser screen lives in
  `app/src/debug`). Font license in `licenses/`.
- `core/model/` (`:core:model`, pure JVM) — dependency-free shared types in package
  `com.ivandsky.puzzlebox.model` (`GameId`, `ThemeMode`).
- `core/domain/` (`:core:domain`) — use cases (package `com.ivandsky.puzzlebox.domain`, Koin
  `domainModule`): `ObserveGameHistory` (records UNION every registered game's captured days →
  `GameHistoryDay`), `GetFeaturedGame` (registry's first game as `GameSummary`),
  `ComputeStreak`, `GetReplaySession` (cached puzzle → the game's replay blob as
  `ReplayOutcome`), `ObserveTodayLeaderboard` (identity-gated featured-game board:
  `TodayLeaderboard`), `ObserveTodaySet` (registry-order `TodayEntry` per game: Solved/InProgress/NotStarted on its cadence day). Android library.
- `core/game/` (`:core:game`, pure JVM) — the orthogonal game contracts (ADR-0001):
  `GameDefinition`, `GameRules<P>`, `GameSession` + `GameSessionCodec`, `ScoreModel<S>` →
  `CompletionRecord?`, `PuzzleSource` (`fetch(date)` → `FetchResult`; `FetchError`), composed
  per game as `GamePlugin<P, S, M>`; `GameRegistry` (the sole GameId → `GamePlugin` lookup);
  `DailySource : PuzzleSource` (per-game feed adapter, `fetchDaily` → `DailyFetch`) over
  `FeedTransport`/`FeedRequest`; `Command`/`CommandHistory` (undo/redo); `SessionBlob`;
  supporting `Puzzle`, `DecodeResult`/`DecodeError`, `VariantId`, `RecordStats`,
  `CompletionRecord`.
- `core/game-ui/` (`:core:game-ui`) — the Compose mapping layer keyed by `GameId` (no Compose in
  `:core:game`): play contracts (ADR-0007) `GamePlayUi` (`createController(PlayEnvironment)`,
  `Screen`), `GamePlayController` (+ `PlayClock`/`PlayHints`/`PlayUndo`), `GamePlayRegistry`
  (GameId → `GamePlayUi`; a duplicate id throws), `PlayBoardStatus` + `PlayBoardFallback`,
  `PlayCopy`, `BoardGrid` + `EdgeOverlay`, `DragTrace`, `GridGeometry`, `HintButton`,
  `TimerBar` + `formatClock`, `PlayScaffold` (`playLayoutFor`), `PlayChrome`, `ResultScreen`,
  `CompletionRecordEmitter`.
- `core/testing/` (`:core:testing`) — test doubles, test configurations only (package
  `com.ivandsky.puzzlebox.testing`): `repository/` (`FakeDailyRepository`,
  `FakeCompletionRepository`, `FakeIdentityService`, `FakeLeaderboardRepository`), `game/`
  (`TestGames` stand-in registry, `FakeDailySource`), `rule/MainDispatcherRule`.
- `games/zip/` (`:games:zip`, pure JVM) — `zipPlugin` + Koin `zipModule` (binds
  `LinkedInZipDailySource` as `DailySource`); model `ZipPuzzle`/`ZipBoard` + `ZipPuzzleRef`;
  `engine/` `ZipRules` over an ordered `ZipRule` pipeline (`ZipRuleId`), `ZipVerifier`,
  `ZipSolver`/`ZipSearch` (`longestSolvablePrefix` for hints), `ZipHints`, `ZipVariants`;
  `codec/` `ZipCodec`, `ZipWireParser`, `ZipSessionCodec` v2 (`ZipCommand` history);
  `feed/` `TrailGameExtractor`; `ZipScoreModel`; `render/ZipRendererModel`.
- `games/tango/` (`:games:tango`, pure JVM) — `tangoPlugin` + `tangoModule` (binds
  `TangoSeedBackedSource(LinkedInTangoDailySource)` as `DailySource`); `engine/` `TangoRules`
  (triple/count/marker rules, `TangoUniqueness`, `TangoHint`); `codec/` `TangoCodec`,
  `TangoWireParser`, `TangoSessionCodec` v2; `feed/TangoGameExtractor`; `TangoScoreModel`.
- `games/queens/` (`:games:queens`, pure JVM) — `queensPlugin` + `queensModule` (binds
  `LinkedInQueensDailySource` as `DailySource`); `engine/` rules (`QueensRules`,
  `QueensVerifier`, `NoTouchAdjacencyRule`) and `solve/` (`QueensSolver`/`QueensSearch`);
  `codec/` `QueensCodec`, `QueensWireParser`, `QueensSessionCodec` v3; `feed/`
  `QueensGameExtractor` + `QueensUniquenessGate`; `QueensScoreModel`.
- `games/zip-ui/`, `games/tango-ui/`, `games/queens-ui/` (`:games:<x>-ui`) — each game's play
  UI: one `*PlayController : GamePlayController`, one `*PlayUi : GamePlayUi`, the board
  composables, and `*UiModule` (the game's `GamePlayUi`).
- `feature/home/impl/` (`:feature:home:impl`) — `navigation/HomeEntry`, `HomeRoute` +
  `HomeViewModel` (featured game, identity, cache-only history teaser via
  `ObserveGameHistory`; `di/HomeModule`), `HomeScreen` with `HomeListPane`/`HomeDetailPane`/
  `HomeCards`, `ThemeDialog`, `HomeLayout` + `homeLayoutFor` pure pane policy.
- `feature/play/impl/` (`:feature:play:impl`) — the one game-agnostic play surface (ADR-0006,
  ADR-0007; no `games:*` dependency): `PlaySessionStore` (Koin single: the live `ActivePlay`,
  `start`/`restore`/`end`, emits `SessionBlob` envelopes), `ActivePlay`, `PlayViewModel` +
  `ResultViewModel`, `PlayRoute`, `GamePicker`, `ResultRoute`, `di/PlayModule`;
  `navigation/PlayEntries` and `navigation/PlayBackStack` (`solveToResult`,
  `playAgainFromResult`, `leavePlay` as `Navigator` extensions).
- `feature/history/impl/` (`:feature:history:impl`) — `navigation/HistoryEntry`,
  `HistoryRoute`/`HistoryScreen` with `HistoryListPane`/`HistoryDetailPane` over
  `HistoryViewModel` (`HistoryUiState`, one-shot `HistoryEvent` replay via `GetReplaySession`;
  `di/HistoryModule`), `HistoryPaging` (`historyPager`, `HistoryPagingSource`),
  `HistoryLayout` + `historyLayoutFor`; replay rides the same `PlayRoute` restored.
- `feature/profile/impl/` (`:feature:profile:impl`) — packages kept from the app:
  `app/screens/` (`ProfileRoute`, `ProfileScreen`, `ProfileLeaderboard`, `ProfileViewModel`
  over `ObserveTodayLeaderboard`, `ProfileUiState`), `app/auth/GoogleIdTokenProvider`
  (Credential Manager, typed `Unavailable.Reason`), `profile/di/ProfileModule`
  (`profileModule(serverClientIdRes)`), `profile/navigation/ProfileEntry`.
- `backend/firebase/` (`:backend:firebase`) — implements the `core:data` seams:
  `FirebaseIdentityService` over an internal `AuthGateway`/`FirebaseAuthGateway`,
  `FirestoreLeaderboardStore` + `FirestoreLeaderboardRepository` (dedupe on (uid, puzzleRef)),
  `LeaderboardDocCodec`, `BackendModule` (`firebaseBackendModule`). Rules and indexes live
  alongside (`firebase.json`, `firestore.rules`, `firestore.indexes.json`).
- `PROFILE.md` — product frame, ranked care-axes, deploy targets, testing style.
- `docs/adr/` — ADR-0001…0008, summarized under Purpose.

## Entry points

- `PuzzleBoxApplication.onCreate` — `startKoin` with `appModules` = `commonModule` +
  `dataModule` + `firebaseBackendModule` + `domainModule` + `homeModule` + `historyModule` +
  `playModule` + `profileModule(R.string.default_web_client_id)` + each game's `*Module` and
  `*UiModule` (zip, queens, tango) + `appModule`, then `LeaderboardSubmitWatcher.start()`.
- `MainActivity` — sole activity; holds composition until the persisted `ThemeMode` arrives
  (no light flash), resolves dark theme against the system setting, keeps system bar icons in
  step, then hosts `PuzzleBoxApp` with the width size class, `ThemeMode`, and the setter.
- `PuzzleBoxApp` route table (Navigation3 `NavDisplay`, entries contributed by each feature
  impl's `*Entry` builder): tabs HomeKey → `HomeRoute`, PlayKey → `PlayRoute`, ProfileKey →
  `ProfileRoute`. Pushed: HistoryKey → `HistoryRoute`, ReplayKey(session bytes) → `PlayRoute`
  restored, ResultKey → `ResultRoute` (distinct enter transition), CatalogKey → the debug-only
  Showkase browser (`CatalogDebugBinding`, FLAG_DEBUGGABLE-gated; release gets a placeholder).
  Tab selection resets to the tab root (`Navigator.navigateToTopLevel`);
  `Navigator.navigateOnce` guards double-push. Bottom bar on compact widths, rail on
  expanded-width landscape (`homeLayoutFor`/`playLayoutFor`/`historyLayoutFor`).
- No deep links, widgets, or services.

## Seams

- **Koin DI graph** — `commonModule` + `dataModule` (repositories; includes `databaseModule`
  Room + DAOs, `networkModule` Json/cookie jar/`LinkedInGamesApi`/`FeedTransport`,
  `datastoreModule` DataStore + `PuzzleBoxPreferencesDataSource`) + `firebaseBackendModule` +
  `domainModule` (the five use cases) + feature modules + per game `*Module` (plugin +
  `DailySource`) and `*UiModule` (`GamePlayUi`) + `appModule` (`CoroutineScope`,
  `LeaderboardSubmitWatcher`, `GameRegistry`, `GamePlayRegistry`). Completeness asserted by
  `DiGraphTest` (app) and `DataModuleGraphTest` (core:data) under Robolectric.
- **`GamePlugin`/`GameRegistry`** (ADR-0001, `core:game`) — onboarding a game is one module
  pair + Koin registrations + a `GAME_ORDER` slot; nothing outside `app` references a concrete
  game. Evolution stays inside the game (ADR-0005: versioned codecs, `Puzzle.variant`,
  `RecordStats`).
- **`DailySource` / `FeedTransport`** — each game adapts the LinkedIn feed (`DailySource`),
  `core:network` supplies the one transport; `DailyRepository` consumes all sources by id.
  `FetchError` distinguishes Unavailable (degrade to cache) / Throttled / Untrusted.
- **`GamePlayRegistry`** (`core:game-ui`) — the game → UI seam; `:feature:play:impl` drives any
  game through `GamePlayController`, so adding a game never touches Play.
- **Repository interfaces** (`core:data`) — `DailyRepository.today()` is network-first
  (`DailyToday.Cached` carries the fetch error), `cached()`/`observeHistory()` never touch the
  network; `CompletionRepository` stamps ADR-0004 first-win semantics via `FirstWinPolicy`
  keyed by `PuzzleRefKeys`; `UserPreferencesRepository` — `themeMode` Flow + setter.
- **`IdentityService` / `LeaderboardRepository`** (`core:data`, Firebase impl) — identity is a
  `StateFlow<Identity?>`; `submit` returns `SubmitResult`, never throws. Backend = module swap.
- **`LeaderboardSubmitWatcher`** (app) — signed in, every record converts via
  `LeaderboardEntries.fromRecord` and submits once per (uid, puzzleRef).
- **`DispatcherProvider`** — dispatcher injection keeps callers testable.
- **Handwritten fakes** in `core:testing` (test-scope only): `FakeDailyRepository`,
  `FakeCompletionRepository`, `FakeIdentityService`, `FakeLeaderboardRepository`,
  `TestGames`/`FakeDailySource`.
- **Pure layout policies** — `homeLayoutFor`, `playLayoutFor`, `historyLayoutFor`.
- **`Migrations.ALL`** (`core:database`) — every schema change bumps the Room version (now 5),
  commits the schema JSON, and registers a migration (1→2 puzzle_dailies, 2→3 completions,
  3→4 daily_puzzles rebuild, 4→5 drops the legacy daily_puzzles); no destructive fallback.
- **Module graph guard** — `checkModuleGraph` (see Layout) fails the build on a forbidden edge.
- **Roborazzi** — plugin `puzzlebox.android.roborazzi` (app, designsystem, zip-ui, tango-ui,
  queens-ui) unifies the switch: `-Proborazzi.verify=true` verifies, default records.
  Committed goldens: `app/snapshots/` (14 pngs, `ShowkaseBrowserScreenshotTest`),
  `games/queens-ui/snapshots/` (36, `QueensGoldenScreenshots`),
  `games/tango-ui/src/test/resources/goldens/` (3, `TangoBoardGoldenTest`); zip-ui's
  `VariantMatrixScreenshotTest` writes review evidence to `.scratch`, not goldens.

## Working test commands

Counts are `@Test` annotation counts over each module's `src/test*/` trees at the pin (1018
total) — not executions. Robolectric where device APIs are touched; `:app` needs `ANDROID_HOME`
or `local.properties` (SDK at `~/Library/Android/sdk` here); Gradle toolchain pinned to JVM 25
via `gradle/gradle-daemon-jvm.properties`. Android modules run `testDebugUnitTest`, pure-JVM
modules run `test`. Full suite:

`./gradlew assembleDebug testDebugUnitTest :core:game:test :games:zip:test :games:queens:test
:games:tango:test :core:model:test :core:network:test -Proborazzi.verify=true --continue` —
P9 full-suite run: 1036 tests, 0 failures.

Structure rules (ADR-0009): `./gradlew :konsist:test` — runs the 12 in-module rules, the
census and the engine tests over the whole repo's sources (not in the count above; reruns
when any source, build script or allowlist changes).

- `:app:testDebugUnitTest` — 39 (DI graph, nav routes, saved-state restore, submit watcher,
  browser screenshots)
- `:backend:firebase:testDebugUnitTest` — 32
- `:core:common:testDebugUnitTest` — 8
- `:core:data:testDebugUnitTest` — 39
- `:core:database:testDebugUnitTest` — 8 (DAOs + migrations)
- `:core:datastore:testDebugUnitTest` — 2
- `:core:designsystem:testDebugUnitTest` — 34
- `:core:domain:testDebugUnitTest` — 22
- `:core:game:test` — 16
- `:core:game-ui:testDebugUnitTest` — 35
- `:core:navigation:testDebugUnitTest` — 4
- `:core:network:test` — 10
- `:core:model`, `:core:testing`, `:feature:*:api` — 0
- `:feature:home:impl:testDebugUnitTest` — 21
- `:feature:history:impl:testDebugUnitTest` — 28
- `:feature:play:impl:testDebugUnitTest` — 57
- `:feature:profile:impl:testDebugUnitTest` — 28
- `:games:zip:test` — 155; `:games:zip-ui:testDebugUnitTest` — 69
- `:games:tango:test` — 114; `:games:tango-ui:testDebugUnitTest` — 53
- `:games:queens:test` — 163; `:games:queens-ui:testDebugUnitTest` — 81
- `./gradlew checkModuleGraph` — forbidden-edge guard (also run by `check`)

## Diagram

[docs/agents/project-map.html](project-map.html) — module-graph diagram regenerated from
`.archify/project-map/architecture-candidate.json` at the pinned revision: the modules and
edges listed under Layout, with the forbidden-edge rules of ADR-0008.

## Drift rule

If `git rev-parse HEAD` differs from `pinned:`, run `git diff --stat <pin>..HEAD` and
refresh only the sections the touched paths hit (layout, entry points, seams, tests), then
bump the pin — never edit the map in conversation.
