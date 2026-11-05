# PuzzleBox — project map

pinned: d1acf404450135d21fd5acd5c548b3756f0e3267

## Purpose

One Android app for daily logic puzzles — play official NYT/LinkedIn dailies, build and share
custom games, compete via leaderboards and 1v1. Zip, Tango and Queens play their LinkedIn
dailies and Strands its NYT daily (any past day by date) through one compete loop (daily
fetch → play → completion record → leaderboard submit). Product frame and versioning policy live in PROFILE.md (semver source of truth; alpha
0.x.y until all five games ship tested). Architecture decisions are ADR-recorded in
`docs/adr/` — ADR-0001 game contracts as orthogonal `GamePlugin`, ADR-0002 the archive as a
future `PuzzleSource` adapter, ADR-0003 per-game editors only after that game ships, ADR-0004
records normalized in `core:data` with `CompletionRecord` emitted by games, ADR-0005 every
persisted artifact self-describes its schema version and rule changes are data
(`Puzzle.variant`), ADR-0006 command history and the shared core play chrome, ADR-0007 the
play session store (amended: slots persist on disk and resume; per-entry ViewModels),
ADR-0008 the module graph and its enforced forbidden edges, ADR-0009 the
in-module structure rules (`:konsist`, 12 Konsist-backed rules with shrink-only allowlists;
each failure links to its section), ADR-0011 per-tab navigation (immutable
`NavigationState` + verb rules, rejected designs, "Adding a tab", serial-name policy),
ADR-0012 archive-capable daily sources (amends 0002: optional `DailySource.archiveSince`,
a by-date repository read that fetches a missed in-range day once, lazily; rows fetched after
their day are "archived" — in History, skipped by the streak; schema stays v6), ADR-0013
rank order is per game (`ScoreModel` declares `RankOrder`; Strands ranks hints then time;
leaderboard stays time-first and Zip-only), ADR-0014 the NYT transport (`core:game`
`NytTransport`, implemented in `core:network` with its own client, never a second
`FeedTransport`; parsing and the trust gate stay in the game).

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
`:games:tango`, `:games:queens`, `:games:strands`, plus `:konsist` (test-only `jvm.library`, zero project
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
  `MainActivity` (Compose host, theme resolution, edge-to-edge), `PuzzleBoxApp` (bar or rail
  over the shell; highlight = `navigator.state.tab`; Play tab badge while
  `ObserveTodaySet` reports any of today's games in progress), `nav/` the shell
  (ADR-0011): `TopLevelDestination.kt` (`topLevelDestinations` tab registry: key, label, icon,
  test tag), `PuzzleBoxNavigationState.kt` (fresh state, Home = start tab),
  `RememberNavigator.kt` (`rememberNavigator`, saved under `NAVIGATION_STATE_KEY`
  `"puzzlebox.navigation.v1"`, restored via `reconciledWith`; foreign/undecodable → fresh),
  `TabEntries.kt` (`rememberTabEntries`, the only Navigation3 adapter: per-tab saveable +
  per-entry `ViewModelStore` + `SceneCanvas` decorators, `OWNER_TAB`/`ENTRY_KEY` metadata),
  `PuzzleBoxNavShell.kt` (`NavDisplay`, `onBack` = `Navigator.pop`, `PlaySessionStore.retain`
  on stack change and `flush` at ON_STOP), `NavTransitions.kt` (`NavMotion` values and the
  push/pop/tab-switch/Result/reduced-motion chooser), `TopLevelNavBar.kt` (bar + rail,
  `badged` keys), `PuzzleBoxEntryProvider.kt` (route table from every feature's `*Entry`
  builder plus `CatalogKey`), `CatalogKey.kt`, `PuzzleBoxNavKeys.kt` (`puzzleBoxNavKeys`:
  polymorphic `SavedStateConfiguration` over every feature's keys),
  `leaderboard/LeaderboardSubmitWatcher` (auto-submits records when signed in),
  `di/AppModule` (app scope; `GameRegistry` over `getAll<GamePlugin>()` sorted by the app-side
  `GAME_ORDER` zip → tango → queens → strands; `GamePlayRegistry(getAll())` over every game's
  `GamePlayUi`), debug-only Showkase browser (`CatalogDebugBinding`, `ShowkaseBrowserScreen`,
  `PreviewBackdrop`, `BrowserRadioControls`; goldens in `app/snapshots/`). Tests:
  `src/test/.../e2e/` the E2E robot (`shellRobot`/`ShellRobot`: gestures, back and
  predictive back, background/restore/relaunch, spec-notation row assertions; `E2eKoinGraph`:
  production `appModules` with core:testing fakes on the edges; `TabNavigationE2ETest`,
  `HistoryE2ETest`, `ProcessDeathE2ETest`, `StrandsPlayE2ETest`, `StrandsArchiveE2ETest`),
  `nav/PerTabShellTest`, `nav/ShellSessionLifecycleTest`.
- `konsist/` (`:konsist`, test-only `puzzlebox.jvm.library`, no project dependencies) — the
  in-module structure rule suite (ADR-0009): `core/` engine (`SourceTree`, `Allowlist`, `judge`,
  `RuleCheck`, `CensusTest`), `rules/` one `<ID><Name>.kt` + test per rule, resources
  `allowlists/<ID>.txt` and `fixtures/<ID>/` (incl. `add-a-game`).
- `core/navigation/` (`:core:navigation`) — `NavigationState` (immutable `@Serializable` tab +
  one stack per tab, invariants I1–I4, pure verbs `selectTab`/`push`/`pushOnce`/`pop`/
  `replaceTop`, `reconciledWith`, spec-notation `toString`; `navigationState(vararg tabs)`),
  `Navigator` (snapshot-state holder, one line per verb), and `navKeySavedStateConfiguration`
  (open `NavKey` polymorphism for saved state).
- `feature/{home,play,history,profile}/api/` (`:feature:*:api`, plugin
  `puzzlebox.android.feature.api`) — each feature's `@Serializable` NavKeys (`HomeKey`;
  `PlayKey` (hub), `GameKey(gameId, day?)`, `ResultKey(gameId, day, timeMs, hintsUsed)`;
  `HistoryKey`; `ProfileKey`) and a `*NavKeys()` polymorphic registration; serial names keep
  the `com.ivandsky.puzzlebox.app.nav.*` prefix by convention, compatibility is versioned by
  `NAVIGATION_STATE_KEY` (ADR-0011).
- `core/common/` (`:core:common`) — `DispatcherProvider` seam (+ `DefaultDispatcherProvider`),
  `AppResult`/`AppError` and `resultOf` convention (typed errors, never thrown surprises),
  Koin `commonModule`.
- `core/data/` (`:core:data`) — game-agnostic repositories: `DailyRepository` →
  `OfflineFirstDailyRepository` (`today(game)`/`cached(game, day)`/`observeHistory(game)`,
  by-date `day(game, day)` → `DailyDay` (cache first; on a miss fetches and persists only for an
  archive-capable source within its range), `archiveRange(game)` (ADR-0012),
  `DailyToday` Fresh/Cached/Unavailable, `DailyEntry`; decodes through `GameRegistry`, fetches
  through every game's `DailySource`), `SavedPlayRepository` → `RoomSavedPlayRepository`
  (one session blob per `PlaySlot`; save prunes to the newest 8 slots in one transaction;
  `observeInProgress`), `calendar/GameCalendar` → `CadenceGameCalendar` (each game's today in
  its cadence zone; `observeToday` re-emits at that midnight), `CompletionRepository` → `RoomCompletionRepository` +
  `FirstWinPolicy` (best read under the game's `RankOrder`), `UserPreferencesRepository` → `OfflineUserPreferencesRepository`;
  `model/` entity mappings + `PuzzleRefKeys`; `identity/` `IdentityService` + `AuthResult`/
  `AuthError`; `leaderboard/` (`LeaderboardRef`, `LeaderboardEntry`, `LeaderboardRepository`,
  `SubmitResult` Ok/Deduped/Err, `LeaderboardEntries.fromRecord`); Koin `dataModule` (includes
  `databaseModule`, `networkModule`, `datastoreModule`).
- `core/database/` (`:core:database`) — Room v6 `PuzzleBoxDatabase`: `PuzzleDailyDao`/
  `PuzzleDailyEntity` (`puzzle_dailies`, keyed gameId+day), `CompletionDao`/`CompletionEntity`
  (`completions`, keyed on the encoded puzzle-ref string), `SavedPlayDao`/`SavedPlayEntity`
  (`saved_plays`, keyed gameId+day, `blob` + `updatedAt`, `saveAndPrune`); schemas 1–6
  exported under `core/database/schemas/`, `Migrations.ALL` = 1→2→3→4→5→6; Koin `databaseModule`.
- `core/network/` (`:core:network`, pure JVM) — the one LinkedIn client: internal
  `LinkedInGamesApi` (Retrofit), `LinkedInSessionCookieJar` + `LinkedInCsrfInterceptor`
  (anonymous session), `LinkedInFeedTransport` implementing `core:game`'s `FeedTransport`;
  the NYT client (ADR-0014): internal `NytApi` + `NytHttpTransport` implementing `core:game`'s
  `NytTransport` (own sessionless client, call timeout); Koin `networkModule`.
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
  `com.ivandsky.puzzlebox.model` (`GameId`, `ThemeMode`, `PlaySlot(gameId, day)`).
- `core/domain/` (`:core:domain`) — use cases (package `com.ivandsky.puzzlebox.domain`, Koin
  `domainModule`): `ObserveGameHistory` (records UNION every registered game's captured days →
  `GameHistoryDay`), `GetFeaturedGame` (registry's first game as `GameSummary`),
  `ComputeStreak`, `LoadPastDay` (a past day by date — cached, or fetched once for an archive game — → the game's session blob as
  `PastDayOutcome`), `ObserveTodayLeaderboard` (identity-gated featured-game board:
  `TodayLeaderboard`), `ObserveTodaySet` (saved plays + records + each game's calendar day →
  registry-order `TodayEntry` per game: Solved/InProgress(elapsed)/NotStarted; `nextUnsolved`). Android library.
- `core/game/` (`:core:game`, pure JVM) — the orthogonal game contracts (ADR-0001):
  `GameDefinition`, `GameRules<P>`, `GameSession` + `GameSessionCodec`, `ScoreModel<S>` →
  `CompletionRecord?`, `PuzzleSource` (`fetch(date)` → `FetchResult`; `FetchError`), composed
  per game as `GamePlugin<P, S, M>`; `GameRegistry` (the sole GameId → `GamePlugin` lookup);
  `DailySource : PuzzleSource` (per-game feed adapter, `fetchDaily` → `DailyFetch`, optional
  `archiveSince`) over `FeedTransport`/`FeedRequest` (LinkedIn) or `NytTransport` (NYT);
  `RankOrder` (TimeThenHints/HintsThenTime, on `ScoreModel`); `Command`/`CommandHistory` (undo/redo); `SessionBlob`;
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
  `FakeCompletionRepository`, `FakeIdentityService`, `FakeLeaderboardRepository`,
  `FakeSavedPlayRepository`, `FakeUserPreferencesRepository`), `calendar/FakeGameCalendar`,
  `network/FakeNytTransport`, `game/TestGames` (stand-in registry), `rule/MainDispatcherRule`.
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
- `games/strands/` (`:games:strands`, pure JVM) — `strandsPlugin` + `strandsModule` (binds
  `NytStrandsDailySource(NytTransport)` as `DailySource`, `archiveSince` = `StrandsNumber.EPOCH`);
  model `StrandsPuzzle`/`StrandsWord`/`StrandsState`/`StrandsSession` + `StrandsPuzzleRef`;
  `codec/` `StrandsCodec`, `StrandsSessionCodec`, `Varint`; `feed/` `StrandsWireParser` →
  `StrandsTrustGate` (the trust gate: ordered `feed/gate/` `StrandsTrustCheck`s — fields,
  print date, rectangular, letters, word paths, tiling, spangram edges; a rejected day is
  `FetchError.Untrusted`); `engine/` `StrandsRules`, `StrandsAccept`, `StrandsHints`/`StrandsHint`,
  `StrandsSpend`, `StrandsVerdict`; `StrandsScoreModel` (`HintsThenTime`); `render/`
  `StrandsRendererModel` → `StrandsCell` with sealed `CellState` (Idle/FoundTheme/
  FoundSpangram/HintOutlined/HintOrderRevealed) + `StrandsLink`/`LinkKind`.
- `games/zip-ui/`, `games/tango-ui/`, `games/queens-ui/`, `games/strands-ui/` (`:games:<x>-ui`) — each game's play
  UI: one `*PlayController : GamePlayController`, one `*PlayUi : GamePlayUi`, the board
  composables, and `*UiModule` (the game's `GamePlayUi`). Strands: `StrandsPlayController`,
  `StrandsScreen`, `board/` (`StrandsCellDrawing` maps each `CellState` to its
  `StrandsBoardSpec` — `IdleSpec`, `FoundThemeSpec`, `FoundSpangramSpec`, `HintOutlinedSpec`,
  `HintOrderRevealedSpec` — plus `TraceOverlaySpec`, gesture/hit-test/sizing), `chrome/`
  (`StrandsHintRow`, `StrandsHintControl`, `HintMeter`, `ThemeCard`, `WordSlotLine`,
  `FoundCounter`, `HintModal`).
- `feature/home/impl/` (`:feature:home:impl`) — `navigation/HomeEntry`, `HomeRoute` +
  `HomeViewModel` (today's set via `ObserveTodaySet`, identity, cache-only history teaser via
  `ObserveGameHistory`; `di/HomeModule`), `HomeScreen` with `HomeListPane`/`HomeDetailPane`/
  `HomeTodayCard` (one row per game, "n of N done")/`HomeCards`, `ThemeDialog`, `HomeLayout` +
  `homeLayoutFor` pure pane policy.
- `feature/play/impl/` (`:feature:play:impl`) — the one game-agnostic play surface (ADR-0006,
  ADR-0007; no `games:*` dependency): `PlaySessionStore` (Koin single: one `ActivePlay` per
  `PlaySlot`, `open(GameKey)`/`peek(slot)`/`retain(keys)`/`flush()`, `playable` games, pins a
  null-day key to today on first open), `ActivePlay` (controller, board/record flows, record
  write, A10 verdict, slot saves with elapsed ms), `SlotOpening` (saved row → today's fetch → past-day replay build
  → Unavailable; undecodable row deleted), `SlotWriter` (per-slot 300 ms trailing-throttle
  saves, `discard` on solve), `WriteLane` (one Mutex-ordered lane for every saved-play I/O),
  `HubRoute` + `HubViewModel` (Play tab's games hub: today's status line and Play/Continue/
  Replay per game), `GameRoute` + `GameViewModel` (one `GameKey`'s board; first-play tutorial
  and How to play via `TutorialGate`), `ResultRoute` + `ResultViewModel` (verdict + previous
  best live, Play again, Next unsolved), `GamePicker`, `di/PlayModule`; `navigation/PlayEntries` (`playEntry`,
  `resultEntry`).
- `feature/history/impl/` (`:feature:history:impl`) — `navigation/HistoryEntry`,
  `HistoryRoute`/`HistoryScreen` with `HistoryListPane`/`HistoryDetailPane` over
  `HistoryViewModel` (`di/HistoryModule`), `HistoryPaging` (`historyPager`,
  `HistoryPagingSource`), `HistoryLayout` + `historyLayoutFor`; a history row pushes that
  day's `GameKey` on Home's stack, which resumes or replays the slot.
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
- `docs/adr/` — ADR-0001…0014, summarized under Purpose.

## Entry points

- `PuzzleBoxApplication.onCreate` — `startKoin` with `appModules` = `commonModule` +
  `dataModule` + `firebaseBackendModule` + `domainModule` + `homeModule` + `historyModule` +
  `playModule` + `profileModule(R.string.default_web_client_id)` + each game's `*Module` and
  `*UiModule` (zip, queens, tango, strands) + `appModule`, then `LeaderboardSubmitWatcher.start()`.
- `MainActivity` — sole activity; holds composition until the persisted `ThemeMode` arrives
  (no light flash), resolves dark theme against the system setting, keeps system bar icons in
  step, then hosts `PuzzleBoxApp` with the width size class, `ThemeMode`, and the setter.
- `PuzzleBoxApp` route table (`puzzleBoxEntryProvider`, entries contributed by each feature
  impl's `*Entry` builder): tab roots HomeKey → `HomeRoute`, PlayKey → `HubRoute`,
  ProfileKey → `ProfileRoute`. Pushed on the current tab's stack: HistoryKey →
  `HistoryRoute`, GameKey(game, day?) → `GameRoute` (from the hub or a history row),
  ResultKey → `ResultRoute` (replaces the game on solve; distinct enter transition),
  CatalogKey → the debug-only Showkase browser (`CatalogDebugBinding`, FLAG_DEBUGGABLE-gated;
  release gets a placeholder). Each tab keeps its own stack; reselecting a tab pops it to its
  root; back at a Play/Profile root returns to Home; back at the Home root exits
  (ADR-0011). Bottom bar on compact widths, rail on expanded-width landscape
  (`homeLayoutFor`/`playLayoutFor`/`historyLayoutFor`).
- No deep links, widgets, or services.

## Seams

- **Koin DI graph** — `commonModule` + `dataModule` (repositories; includes `databaseModule`
  Room + DAOs, `networkModule` Json/cookie jar/`LinkedInGamesApi`/`FeedTransport`,
  `datastoreModule` DataStore + `PuzzleBoxPreferencesDataSource`) + `firebaseBackendModule` +
  `domainModule` (the six use cases) + feature modules + per game `*Module` (plugin +
  `DailySource`) and `*UiModule` (`GamePlayUi`) + `appModule` (`CoroutineScope`,
  `LeaderboardSubmitWatcher`, `GameRegistry`, `GamePlayRegistry`). Completeness asserted by
  `DiGraphTest` (app) and `DataModuleGraphTest` (core:data) under Robolectric.
- **`GamePlugin`/`GameRegistry`** (ADR-0001, `core:game`) — onboarding a game is one module
  pair + Koin registrations + a `GAME_ORDER` slot; nothing outside `app` references a concrete
  game. Evolution stays inside the game (ADR-0005: versioned codecs, `Puzzle.variant`,
  `RecordStats`).
- **`DailySource` / `FeedTransport` / `NytTransport`** — each game adapts its feed
  (`DailySource`); `core:network` supplies the transports: LinkedIn `FeedTransport` and the NYT
  `NytTransport` (contract in `core:game`, `NytHttpTransport` internal to `core:network`,
  `FakeNytTransport` in `core:testing`); `DailyRepository` consumes all sources by id.
  `FetchError` distinguishes Unavailable (degrade to cache) / Throttled / Untrusted.
- **`DailySource.archiveSince` + `DailyRepository.day`** (ADR-0012) — a non-null start makes the
  source archive-capable; the by-date read is the one path for past days (`archiveRange`
  bounds it). **`LoadPastDay`** (`core:domain`) wraps it for `feature:play:impl`'s
  `SlotOpening` past-day build and `feature:history:impl`'s `HistoryDetailPane`.
- **`RankOrder`** (`core:game`, ADR-0013) — per-game `ScoreModel.rankOrder`; read by
  `RoomCompletionRepository` best queries and the Result verdict.
- **`NavigationState` / `Navigator`** (`core:navigation`, ADR-0011) — pure per-tab reducer
  plus its snapshot holder; features get a `Navigator`, Navigation3 stays in `app/nav/TabEntries`.
- **`PlaySessionStore` slots** (`feature:play:impl`, ADR-0007 §4) — `open`/`peek`/`retain`/
  `flush` over `SlotOpening`, `SlotWriter` and one `WriteLane`; the shell drives `retain`/`flush`.
- **`SavedPlayRepository`** (`core:data`) — Room (`saved_plays`) + `FakeSavedPlayRepository`,
  one shared contract test (`SavedPlayRepositoryContract`).
- **`GameCalendar`** (`core:data`) — `CadenceGameCalendar` + `FakeGameCalendar`; the one
  "today for game X", used by dailies, the store and `ObserveTodaySet`.
- **E2E robot** (`app` tests, `e2e/`) — `shellRobot { … }` over `E2eKoinGraph` drives the real
  shell row by row in spec notation.
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
  `FakeSavedPlayRepository`, `FakeUserPreferencesRepository`, `FakeGameCalendar`, `TestGames`.
- **Pure layout policies** — `homeLayoutFor`, `playLayoutFor`, `historyLayoutFor`.
- **`Migrations.ALL`** (`core:database`) — every schema change bumps the Room version (now 6),
  commits the schema JSON, and registers a migration (1→2 puzzle_dailies, 2→3 completions,
  3→4 daily_puzzles rebuild, 4→5 drops the legacy daily_puzzles, 5→6 adds saved_plays); no destructive fallback.
- **Module graph guard** — `checkModuleGraph` (see Layout) fails the build on a forbidden edge.
- **Roborazzi** — plugin `puzzlebox.android.roborazzi` (app, designsystem, zip-ui, tango-ui,
  queens-ui, strands-ui) unifies the switch: `-Proborazzi.verify=true` verifies, default records.
  Committed goldens: `app/snapshots/` (14 pngs, `ShowkaseBrowserScreenshotTest`),
  `games/queens-ui/snapshots/` (36, `QueensGoldenScreenshots`),
  `games/strands-ui/snapshots/` (24, `StrandsGoldenScreenshots`, the real 2026-11-06 NYT day
  through the production source), `games/tango-ui/src/test/resources/goldens/` (3,
  `TangoBoardGoldenTest`); zip-ui's
  `VariantMatrixScreenshotTest` writes review evidence to `.scratch`, not goldens.

## Working test commands

Counts are `@Test` annotation counts over each module's `src/test*/` trees in the working
tree at the pin (1158 total) — not executions. Robolectric where device APIs are touched; `:app` needs `ANDROID_HOME`
or `local.properties` (SDK at `~/Library/Android/sdk` here); Gradle toolchain pinned to JVM 25
via `gradle/gradle-daemon-jvm.properties`. Android modules run `testDebugUnitTest`, pure-JVM
modules run `test`. Full suite:

`./gradlew assembleDebug testDebugUnitTest :core:game:test :games:zip:test :games:queens:test
:games:tango:test :games:strands:test :core:model:test :core:network:test -Proborazzi.verify=true --continue` —
the 0.9.0 gate.

Structure rules (ADR-0009): `./gradlew :konsist:test` — runs the 13 in-module rules, the
census and the engine tests over the whole repo's sources (not in the count above; reruns
when any source, build script or allowlist changes).

- `:app:testDebugUnitTest` — 112 (DI graph, nav routes, saved-state restore, per-tab shell,
  E2E robot rows, submit watcher, browser screenshots; at the old pin) — plus the Strands
  E2Es `e2e/StrandsPlayE2ETest` and `e2e/StrandsArchiveE2ETest`
- `:backend:firebase:testDebugUnitTest` — 32
- `:core:common:testDebugUnitTest` — 8
- `:core:data:testDebugUnitTest` — 49
- `:core:database:testDebugUnitTest` — 15 (DAOs + migrations)
- `:core:datastore:testDebugUnitTest` — 2
- `:core:designsystem:testDebugUnitTest` — 34
- `:core:domain:testDebugUnitTest` — 29
- `:core:game:test` — 16
- `:core:game-ui:testDebugUnitTest` — 38
- `:core:navigation:testDebugUnitTest` — 29
- `:core:network:test` — 10
- `:core:model`, `:core:testing`, `:feature:*:api` — 0
- `:feature:home:impl:testDebugUnitTest` — 28
- `:feature:history:impl:testDebugUnitTest` — 25
- `:feature:play:impl:testDebugUnitTest` — 67
- `:feature:profile:impl:testDebugUnitTest` — 28
- `:games:zip:test` — 155; `:games:zip-ui:testDebugUnitTest` — 70
- `:games:tango:test` — 114; `:games:tango-ui:testDebugUnitTest` — 53
- `:games:queens:test` — 163; `:games:queens-ui:testDebugUnitTest` — 81
- `:games:strands:test` — 82; `:games:strands-ui:testDebugUnitTest` — 125 (executed `tests`
  totals from the last `build/test-results/` XML, not annotation counts)
- `./gradlew checkModuleGraph` — forbidden-edge guard (also run by `check`)

## Diagram

[docs/agents/project-map.html](project-map.html) — module-graph diagram regenerated from
`.archify/project-map/architecture-candidate.json` at `89c0672` (pre-0.9): the modules and
edges listed under Layout, with the forbidden-edge rules of ADR-0008. It predates the
per-tab navigation seams and the Strands modules above; this file is authoritative where they
differ. The Strands addition (app → features → `:games:strands-ui` → `:games:strands` →
`:core:game`, `NytHttpTransport`, by-date `DailyRepository`, trust gate, `CellState` specs) has
its own diagram at `.scratch/strands/review/archify/strands-architecture.html` (gitignored).

## Drift rule

If `git rev-parse HEAD` differs from `pinned:`, run `git diff --stat <pin>..HEAD` and
refresh only the sections the touched paths hit (layout, entry points, seams, tests), then
bump the pin — never edit the map in conversation.
