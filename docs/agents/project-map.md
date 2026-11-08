# PuzzleBox — project map

pinned: 500ae6f7f63dd9190aeda733d043085e01a54c4a

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
ADR-0015 one `games:sudoku` pair hosting two GameIds (`minisudoku` 6x6, `sudoku` 9x9 shipped
unregistered in release), ADR-0016 the persisted-query GraphQL GET rung in `FeedTransport`,
ADR-0017 LinkedIn by-number archive, ADR-0018 custom-puzzle identity, ADR-0019 the editor
seam (`GameEditor` in `core:game`, `GameEditorUi` in `core:game-ui`, one game-agnostic
`feature:create`), ADR-0020 shared puzzles in Firestore, ADR-0021 the tools module family,
ADR-0022 trunk delivery (unfinished work merges dark behind a
`ReleaseFlag`, bound per build type; the flip PR releases), ADR-0023 CI on main only (PRs
run no CI; the local `assembleDebug test` is the merge gate; main tests before releasing).

## Layout

Gradle root `PuzzleBox`; `settings.gradle.kts` is the module list. Dependency direction:
`:app` (composition root, depends on everything) → `:feature:*:impl` → `:feature:*:api` →
`:core:navigation` (features: home, play, history, profile, create); impls also sit on `:core:*` (`:core:designsystem` via the
`puzzlebox.android.feature.impl` plugin). `:games:<x>` → `:core:game` (+ `:core:model`),
`:games:<x>-ui` → `:games:<x>`, `:core:game`, `:core:game-ui`, `:core:designsystem`; games plug in
through Koin. `:core:data` exposes `:core:common`/`:core:game`/`:core:model` and hides
`:core:database`, `:core:datastore`, `:core:network`; `:core:domain` exposes `:core:data`;
`:backend:firebase` implements seams declared in `:core:data`. Every module except app/backend
tests via `testImplementation(projects.core.testing)`. Pure JVM modules
(`puzzlebox.jvm.library`): `:core:model`, `:core:game`, `:core:network`, `:games:zip`,
`:games:tango`, `:games:queens`, `:games:strands`, `:games:sudoku`, plus `:konsist` (test-only `jvm.library`, zero project
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
  `GAME_ORDER`, unlisted set from `buildTypeUnlistedGames`; zip → tango → queens → strands → minisudoku → sudoku; `GamePlayRegistry(getAll())` over every game's
  `GamePlayUi`), `di/BuildTypeReleaseFlagsModule` (`src/debug` `DebugReleaseFlags` with local
  overrides, `src/release` `ShippedReleaseFlags`; ADR-0022), debug-only Catalog: Showkase
  browser (`CatalogDebugBinding`, `ShowkaseBrowserScreen`, `PreviewBackdrop`,
  `BrowserRadioControls`; goldens in `app/snapshots/`), Design settings, Release flags
  (`ReleaseFlagsPanel`). Tests:
  `src/test/.../e2e/` the E2E robot (`shellRobot`/`ShellRobot`: gestures, back and
  predictive back, background/restore/relaunch, spec-notation row assertions; `E2eKoinGraph`:
  production `appModules` with core:testing fakes on the edges; `TabNavigationE2ETest`,
  `HistoryE2ETest`, `ProcessDeathE2ETest`, `StrandsPlayE2ETest`, `StrandsArchiveE2ETest`),
  `nav/PerTabShellTest`, `nav/ShellSessionLifecycleTest`.
- `konsist/` (`:konsist`, test-only `puzzlebox.jvm.library`, no project dependencies) — the
  in-module structure rule suite (ADR-0009): `core/` engine (`SourceTree`, `Allowlist`, `judge`,
  `RuleCheck`, `CensusTest`), `rules/` one `<ID><Name>.kt` + test per rule, resources
  `allowlists/<ID>.txt` and `fixtures/<ID>/` (incl. `add-a-game`). Rules (no S3, ADR-0009):
  S0 no `@Suppress`, S1 one top-level type per file, S2 file length, S4 composable previews,
  S5 no colour literals, S6 no infinite animation, S7 no system night flag outside theme resolution; P1 package root, P2 dispatchers location,
  P3 ViewModel shape, P4 use-case shape, P5 naming, P6 Koin module location, P7 Android-free
  pure modules, P8 NavKey payload.
- `core/navigation/` (`:core:navigation`) — `NavigationState` (immutable `@Serializable` tab +
  one stack per tab, invariants I1–I4, pure verbs `selectTab`/`push`/`pushOnce`/`pop`/
  `replaceTop`, `reconciledWith`, spec-notation `toString`; `navigationState(vararg tabs)`),
  `Navigator` (snapshot-state holder, one line per verb), and `navKeySavedStateConfiguration`
  (open `NavKey` polymorphism for saved state).
- `feature/{home,play,history,profile,create}/api/` (`:feature:*:api`, plugin
  `puzzlebox.android.feature.api`) — each feature's `@Serializable` NavKeys (`HomeKey`;
  `PlayKey` (games library), `GameKey(gameId, day?)`, `ResultKey(gameId, day, timeMs, hintsUsed)`;
  `HistoryKey`; `ProfileKey`; `CreateKey` (Create tab root), `EditorKey(draftId)`) and a
  `*NavKeys()` polymorphic registration; serial names keep
  the `com.ivandsky.puzzlebox.app.nav.*` prefix by convention, compatibility is versioned by
  `NAVIGATION_STATE_KEY` (ADR-0011).
- `core/common/` (`:core:common`) — `DispatcherProvider` seam (+ `DefaultDispatcherProvider`),
  `AppResult`/`AppError` and `resultOf` convention (typed errors, never thrown surprises),
  `flags/` `ReleaseFlag` enum + `ReleaseFlags` reader + `ShippedReleaseFlags` (ADR-0022), Koin
  `commonModule`.
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
  `databaseModule`, `networkModule`, `datastoreModule`); `DraftRepository` →
  `RoomDraftRepository` (the draft store: `Draft` rows; observeAll/observe/get/upsert/delete)
  and `update/` `AppReleaseRepository` (GitHub releases).
- `core/database/` (`:core:database`) — Room v7 `PuzzleBoxDatabase`: `PuzzleDailyDao`/
  `PuzzleDailyEntity` (`puzzle_dailies`, keyed gameId+day), `CompletionDao`/`CompletionEntity`
  (`completions`, keyed on the encoded puzzle-ref string), `SavedPlayDao`/`SavedPlayEntity`
  (`saved_plays`, keyed gameId+day, `blob` + `updatedAt`, `saveAndPrune`), v7 additions
  `DraftDao`/`DraftEntity` (`drafts`: id, gameId, variant, title, note, blob),
  `CustomSavedPlayDao`/`CustomSavedPlayEntity` (`custom_saved_plays`), `PublishedPuzzleEntity`
  (`published_puzzles`), `ReceivedPuzzleEntity` (`received_puzzles`); schemas 1–7 exported under
  `core/database/schemas/`, `Migrations.ALL` = 1→…→7; Koin `databaseModule`.
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
  `com.ivandsky.puzzlebox.model` (`GameId`, `ThemeMode`, `PlaySlot` (`Daily`/`Custom`)).
- `core/domain/` (`:core:domain`) — use cases (package `com.ivandsky.puzzlebox.domain`, Koin
  `domainModule`): `ObserveGameHistory` (records UNION every registered game's captured days →
  `GameHistoryDay`), `GetFeaturedGame` (registry's first game as `GameSummary`),
  `ComputeStreak`, `LoadPastDay` (a past day by date — cached, or fetched once for an archive game — → the game's session blob as
  `PastDayOutcome`), `ObserveLeaderboard` (identity-gated board for one game and day),
  `ObserveTodayLeaderboard` (the featured game's today board via `ObserveLeaderboard`:
  `TodayLeaderboard`), `ObservePlayStats` (one slot's tablet side-panel stats: streak, week,
  today progress, times → `PlayStats`), `ObserveTodaySet` (saved plays + records + each game's calendar day →
  registry-order `TodayEntry` per game: Solved/InProgress(elapsed)/NotStarted; `nextUnsolved`). Android library.
- `core/game/` (`:core:game`, pure JVM) — the orthogonal game contracts (ADR-0001):
  `GameDefinition`, `GameRules<P>`, `GameSession` + `GameSessionCodec`, `ScoreModel<S>` →
  `CompletionRecord?`, `PuzzleSource` (`fetch(date)` → `FetchResult`; `FetchError`), composed
  per game as `GamePlugin<P, S, M>`; `GameRegistry` (the sole GameId → `GamePlugin` lookup; `get` resolves listed + unlisted, `all` = listed only and feeds every surface, `unlisted` = debug-only 9x9 opened from Catalog → Design settings);
  `DailySource : PuzzleSource` (per-game feed adapter, `fetchDaily` → `DailyFetch`, optional
  `archiveSince`) over `FeedTransport`/`FeedRequest` (LinkedIn) or `NytTransport` (NYT);
  `LinkedInEdition` (ADR-0017: one LinkedIn game's day→number rule, games-by-id `FeedRequest`, `entityUrn` identity check);
  `RankOrder` (TimeThenHints/HintsThenTime, on `ScoreModel`); `Command`/`CommandHistory` (undo/redo); `SessionBlob`;
  supporting `Puzzle`, `DecodeResult`/`DecodeError`, `VariantId`, `RecordStats`,
  `CompletionRecord`. `GamePlugin` carries an optional `editor`. `editor/` (ADR-0019):
  `GameEditor<D>` (per-game draft contract: variants, `newDraft`, versioned draft codec,
  `analyze(draft, Budget)` → `Verdict`, `publishBlocks`, `layoutOf`), `EditorDraft<D>` (the
  type-erased draft + `CommandHistory` the UI holds: apply/undo/redo/resize, `canTestSolve`,
  `toPuzzle`), `Verdict` (sealed solvability/uniqueness/`Difficulty` outcome), `DraftState`,
  `DraftExport`, `Budget`, `PublishBlock`, `IncompleteReason`, `LadderRung`, `Solution`.
- `core/game-ui/` (`:core:game-ui`) — the Compose mapping layer keyed by `GameId` (no Compose in
  `:core:game`): play contracts (ADR-0007) `GamePlayUi` (`createController(PlayEnvironment)`,
  `Screen`), `GamePlayController` (+ `PlayClock`/`PlayHints`/`PlayUndo`), `GamePlayRegistry`
  (GameId → `GamePlayUi`; a duplicate id throws), `PlayBoardStatus` + `PlayBoardFallback`,
  `PlayCopy`, `BoardGrid` + `EdgeOverlay`, `DragTrace`, `GridGeometry`, `HintButton`,
  `TimerBar` + `formatClock`, `PlayScaffold` (`playLayoutFor`), `PlayChrome`, `ResultScreen`,
  `CompletionRecordEmitter`. `editor/`: `GameEditorUi` (per-game `sizeLabel`,
  `createController`, `Board`), `GameEditorController` (draft `StateFlow`, `adopt`, overlay
  hooks), `GameEditorRegistry` (GameId → `GameEditorUi`), `EditorOverlay` (witness display).
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
  `LotkaWireParser` (LinkedIn's `lotkaGamePuzzle`, stream + by-id shapes), `TangoSessionCodec` v2;
  `feed/TangoGameExtractor`; `TangoScoreModel`.
- `games/queens/` (`:games:queens`, pure JVM) — `queensPlugin` + `queensModule` (binds
  `LinkedInQueensDailySource` as `DailySource`); `engine/` rules (`QueensRules`,
  `QueensVerifier`, `NoTouchAdjacencyRule`) and `solve/` (`QueensSolver`/`QueensSearch`);
  `codec/` `QueensCodec`, `QueensWireParser`, `QueensSessionCodec` v3; `feed/`
  `QueensGameExtractor` + `QueensUniquenessGate`; `QueensScoreModel`; `engine/hints/ladder/`
  (`QueensLadder` of rungs: singles, line lock, pigeonhole, look-ahead). `editor/` (the
  `QueensEditor` domain): `QueensDraft` + `QueensDraftCommand` + `QueensDraftCodec`,
  `QueensDraftAnalyzer` (verdict), `QueensPublishGates`, `QueensContiguity`, `QueensCrowns`,
  `QueensClashLocator`, `QueensAnswerFit`.
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
- `games/sudoku/` (`:games:sudoku`, pure JVM, ADR-0015) — one engine for two ids:
  `SudokuModules` (`miniSudokuModule` binds `LinkedInMiniSudokuDailySource`; `sudokuModule`
  binds `BundledSudokuDailySource`), `SudokuPlugin`, `SudokuGames`, geometry as variant data
  (`SudokuVariants`: `sudoku/6x6`, `sudoku/9x9`); `engine/rules/` `SudokuConflicts`,
  `engine/solve/SudokuSolver` (MRV bitmask), `engine/hints/` (`SudokuHints` ladder over
  singles, intersections, subsets, reveal fallback); `codec/` `SudokuCodec`,
  `SudokuSessionCodec` v1 + `SudokuCommandCodec`; `feed/` `MiniSudokuGameExtractor`,
  `MiniSudokuWireParser`, `SudokuUniquenessGate`; `SudokuScoreModel`.
- `games/zip-ui/`, `games/tango-ui/`, `games/queens-ui/`, `games/strands-ui/`, `games/sudoku-ui/` (`:games:<x>-ui`) — each game's play
  UI: one `*PlayController : GamePlayController`, one `*PlayUi : GamePlayUi`, the board
  composables, and `*UiModule` (the game's `GamePlayUi`). Strands: `StrandsPlayController`,
  `StrandsScreen`, `board/` (`StrandsCellDrawing` maps each `CellState` to its
  `StrandsBoardSpec` — `IdleSpec`, `FoundThemeSpec`, `FoundSpangramSpec`, `HintOutlinedSpec`,
  `HintOrderRevealedSpec` — plus `TraceOverlaySpec`, gesture/hit-test/sizing), `chrome/`
  (`StrandsHintRow`, `StrandsHintControl`, `HintMeter`, `ThemeCard`, `WordSlotLine`,
  `FoundCounter`, `HintModal`). `queens-ui/editor/`: `QueensEditorUi : GameEditorUi` +
  `QueensEditorController`, `QueensEditorBoard` (crown/ghost/selection layers, gestures),
  mode toggle, coach line, fix list, probe strip, diagnostics; `QueensUiModule` binds both
  `GamePlayUi` and `GameEditorUi`.
  `sudoku-ui` serves both ids
  (`SudokuUiModules`: `miniSudokuUiModule`, `sudokuUiModule`): `SudokuGrid` + `SudokuKeypad`,
  `SudokuBoardState`, pure `SudokuSizing`/`SudokuLayout`, `SudokuMotion`, `SudokuHaptics`,
  `SudokuTutorial`.
  Extending Sudoku (ADR-0015): a new board size is a `SudokuVariants` row and a source, a
  `SudokuTutorial` `BOARDS` entry, and a `gridStrokes` branch in `SudokuGrid` if neither
  stroke weight suits it; as its own GameId it also needs a `SudokuGames` entry, its module
  pair in `appModules` and a `GAME_ORDER` line. A new elimination technique is an
  `EliminationStep` file, a line in `EliminationSteps.DEFAULT` and a `SudokuTechnique` entry;
  captions name only the single, so `SudokuHintCaptions` stays unchanged.
- `feature/home/impl/` (`:feature:home:impl`) — `navigation/HomeEntry`, `HomeRoute` +
  `HomeViewModel` (today's set via `ObserveTodaySet`, stats via `ObserveGameStats`, the 7-day
  week, `GetNextRollover`, identity, cache-only history teaser via `ObserveGameHistory`;
  `di/HomeModule`), `HomeScreen` with `HomeListPane`/`HomeDetailPane` stacking
  `HomeTodaySection` (`HomeTodaySummary` card with `TodayBadge`s + one CTA / rollover countdown)/`HomeStreakSection`/
  `HomeRecentSection`/`HomeCards`, `HomeLayout` + `homeLayoutFor` pure pane policy.
- `feature/play/impl/` (`:feature:play:impl`) — the one game-agnostic play surface (ADR-0006,
  ADR-0007; no `games:*` dependency): `PlaySessionStore` (Koin single: one `ActivePlay` per
  `PlaySlot`, `open(GameKey)`/`peek(slot)`/`retain(keys)`/`flush()`, `playable` games, pins a
  null-day key to today on first open), `ActivePlay` (controller, board/record flows, record
  write, A10 verdict, slot saves with elapsed ms), `SlotOpening` (saved row → today's fetch → past-day replay build
  → Unavailable; undecodable row deleted), `SlotWriter` (per-slot 300 ms trailing-throttle
  saves, `discard` on solve), `WriteLane` (one Mutex-ordered lane for every saved-play I/O),
  `LibraryRoute` + `LibraryViewModel` (Play tab's games library: a row per game with today's status;
  two-pane shows the list beside the selected game's `GamePage` — `ArchiveDayCard` (today, or the
  picked past day → `GameKey(game, day)`), `ArchiveMonthCalendar` (`ArchiveMonthNav` ‹ › + jumps,
  `ArchiveDayDisc` per day from `ObserveArchiveMonth`), `GameStatsCard`, `HowToPlayCard` (rules + Open the lesson); a phone pushes the
  page over the list), `GameRoute` + `GameViewModel` (one `GameKey`'s board; first-play tutorial
  and How to play via `TutorialGate`), `ResultRoute` + `ResultViewModel` (verdict + previous
  best live, Play again, Next unsolved), `di/PlayModule`; `navigation/PlayEntries` (`playEntry`,
  `resultEntry`).
- `feature/history/impl/` (`:feature:history:impl`) — `navigation/HistoryEntry`,
  `HistoryRoute`/`HistoryScreen` with `HistoryListPane`/`HistoryDetailPane` over
  `HistoryViewModel` (`di/HistoryModule`), `HistoryPaging` (`historyPager`,
  `HistoryPagingSource`), `HistoryLayout` + `historyLayoutFor`; the All timeline only (A28:
  streak and stats, week/month calendar, every game's rows; any `HistoryKey` opens it); a
  history row pushes that day's `GameKey` on Home's stack, which resumes or replays the slot.
- `feature/profile/impl/` (`:feature:profile:impl`) — packages kept from the app:
  `app/screens/` (`ProfileRoute`, `ProfileScreen`, `ProfileLeaderboard`, `ProfileViewModel`
  over `ObserveTodayLeaderboard` + `ObserveGameStats`/`ObserveGameHistory` (the record) +
  `UserPreferencesRepository` (Appearance), `ProfileUiState`), `profile/ProfileRecord`
  (header, lifetime grid, per-game rows opening `HistoryKey(game)`), `profile/AppearanceRow`,
  `app/auth/GoogleIdTokenProvider` (Credential Manager, typed `Unavailable.Reason`),
  `profile/di/ProfileModule` (`profileModule(serverClientIdRes)`), `profile/navigation/ProfileEntry`.
- `feature/create/impl/` (`:feature:create:impl`, ADR-0019; no `games:*` dependency) —
  `navigation/CreateEntries` (`CreateKey`, `EditorKey`), `CreateRoute`/`CreateScreen` +
  `CreateViewModel` (draft list over `DraftRepository`, `NewPuzzleSheet`, `DraftActionsSheet`),
  `editor/` `EditorRoute` + `EditorViewModel` (one draft through `GameEditorRegistry`;
  `DraftAutosave`, `EditHistory`), `StudioScreen` (`StudioLayout`, top bar, `ToolStrip`, side
  pane, `ActionArea`, `VerdictCard`/`VerdictPill`), `VerdictPipeline` (debounced
  `analyze` → `Verdict` flow), `TestSolveScreen` + `TestSolveSession` (play the draft before
  publish); `di/CreateModule`. Use cases in `core:domain`: `CreateDraft`, `ValidatePublish`.
- `backend/firebase/` (`:backend:firebase`) — implements the `core:data` seams:
  `FirebaseIdentityService` over an internal `AuthGateway`/`FirebaseAuthGateway`,
  `FirestoreLeaderboardStore` + `FirestoreLeaderboardRepository` (dedupe on (uid, puzzleRef)),
  `LeaderboardDocCodec`, `BackendModule` (`firebaseBackendModule`). Rules and indexes live
  alongside (`firebase.json`, `firestore.rules`, `firestore.indexes.json`).
- `PROFILE.md` — product frame, ranked care-axes, deploy targets, testing style.
- `docs/adr/` — ADR-0001…0022, summarized under Purpose.

## Entry points

- `PuzzleBoxApplication.onCreate` — `startKoin` with `appModules` = `commonModule` +
  `dataModule` + `firebaseBackendModule` + `domainModule` + `homeModule` + `historyModule` +
  `playModule` + `profileModule(R.string.default_web_client_id)` + `createModule` + each game's
  `*Module` and `*UiModule` (zip, queens, tango, strands, minisudoku) + `appModule`, plus
  `buildTypeGameModules`
  (`BuildTypeGameModules.kt` per source set: debug adds `sudokuModule` + `sudokuUiModule`,
  release is empty; `testRelease/ReleaseGameModulesTest`), then `LeaderboardSubmitWatcher.start()`.
- `MainActivity` — sole activity; holds composition until the persisted `ThemeMode` arrives
  (no light flash), resolves dark theme against the system setting, keeps system bar icons in
  step, then hosts `PuzzleBoxApp` with the width size class, `ThemeMode`, and the setter.
- `PuzzleBoxApp` route table (`puzzleBoxEntryProvider`, entries contributed by each feature
  impl's `*Entry` builder): tab roots HomeKey → `HomeRoute`, PlayKey → `LibraryRoute`,
  CreateKey → `CreateRoute` (Create tab), ProfileKey → `ProfileRoute`. Pushed on the current
  tab's stack: EditorKey(draftId) → `EditorRoute` (the Studio; swaps to `TestSolveScreen` while
  test-solving), HistoryKey →
  `HistoryRoute`, GameKey(game, day?) → `GameRoute` (from the library's day card or a history row),
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
  `LeaderboardSubmitWatcher`, `GameRegistry`, `GamePlayRegistry`, `GameEditorRegistry`). Completeness asserted by
  `DiGraphTest` (app) and `DataModuleGraphTest` (core:data) under Robolectric.
- **`GamePlugin`/`GameRegistry`** (ADR-0001, `core:game`) — onboarding a game is one module
  pair per game family (ADR-0015: one pair may host several ids) + Koin registrations
  (per build type via `BuildTypeGameModules` when an id is debug-only) + a `GAME_ORDER` slot; nothing outside `app` references a concrete
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
- **Editor seam** (ADR-0019) — `GamePlugin.editor` (`GameEditor`, `core:game`) + a
  `GameEditorUi` binding (`core:game-ui`, collected into `GameEditorRegistry`); the Studio
  holds an `EditorDraft` and feeds `VerdictPipeline`, so adding an editor never touches
  `feature:create`. Queens is the only editor today.
- **`DraftRepository`** (`core:data`) — the draft store over Room `drafts`; drafts persist
  as the editor's versioned blob, autosaved by `DraftAutosave`.
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
- **`Migrations.ALL`** (`core:database`) — every schema change bumps the Room version (now 7),
  commits the schema JSON, and registers a migration (1→2 puzzle_dailies, 2→3 completions,
  3→4 daily_puzzles rebuild, 4→5 drops the legacy daily_puzzles, 5→6 adds saved_plays, 6→7
  adds drafts, custom_saved_plays, published_puzzles, received_puzzles); no destructive fallback.
- **Module graph guard** — `checkModuleGraph` (ADR-0008, see Layout) fails the build on a
  forbidden edge; `feature:create:impl` reaches games only through the registries.
- **Structure rules** — `:konsist` S0–S7 / P1–P8 (see Layout); allowlists only shrink.
- **Roborazzi** — plugin `puzzlebox.android.roborazzi` (app, designsystem, zip-ui, tango-ui,
  queens-ui, strands-ui, sudoku-ui) unifies the switch: default compares (never fails on drift, never writes goldens),
  `-Proborazzi.verify=true` fails on drift, `-Proborazzi.record=true` records.
  Committed goldens: `app/snapshots/` (14 pngs, `ShowkaseBrowserScreenshotTest`),
  `games/queens-ui/snapshots/` (36, `QueensGoldenScreenshots`),
  `games/strands-ui/snapshots/` (24, `StrandsGoldenScreenshots`, the real 2026-11-06 NYT day
  through the production source), `games/sudoku-ui/snapshots/` (26, `SudokuGoldenScreenshots`),
  `games/tango-ui/src/test/resources/goldens/` (3,
  `TangoBoardGoldenTest`); zip-ui's
  `VariantMatrixScreenshotTest` writes review evidence to `.scratch`, not goldens.

## Working test commands

Counts are `@Test` annotation counts over each module's `src/test*/` trees at the pin
(2419 total excl. `:konsist`) — not executions. Robolectric where device APIs are touched; `:app` needs `ANDROID_HOME`
or `local.properties` (SDK at `~/Library/Android/sdk` here); Gradle toolchain pinned to JVM 25
via `gradle/gradle-daemon-jvm.properties`. Android modules run `testDebugUnitTest`, pure-JVM
modules run `test`. Full suite:

`./gradlew assembleDebug testDebugUnitTest :core:game:test :games:zip:test :games:queens:test
:games:tango:test :games:strands:test :games:sudoku:test :core:model:test :core:network:test -Proborazzi.verify=true --continue` —
the 0.13.0 gate.

Structure rules (ADR-0009): `./gradlew :konsist:test` — runs the 15 in-module rules (S0–S7 without S3, P1–P8; 127 tests), the
census and the engine tests over the whole repo's sources (not in the count above; reruns
when any source, build script or allowlist changes).

- `:app:testDebugUnitTest` — 144 (DI graph, nav routes, saved-state restore, per-tab shell,
  E2E robot rows, submit watcher, browser screenshots)
- `:backend:firebase:testDebugUnitTest` — 32
- `:core:common:testDebugUnitTest` — 8
- `:core:data:testDebugUnitTest` — 101
- `:core:database:testDebugUnitTest` — 18 (DAOs + migrations through 6→7)
- `:core:datastore:testDebugUnitTest` — 2
- `:core:designsystem:testDebugUnitTest` — 54
- `:core:domain:testDebugUnitTest` — 125
- `:core:game:test` — 34
- `:core:game-ui:testDebugUnitTest` — 111
- `:core:navigation:testDebugUnitTest` — 29
- `:core:network:test` — 39
- `:core:model`, `:core:testing`, `:feature:*:api` — 0
- `:feature:home:impl:testDebugUnitTest` — 61
- `:feature:history:impl:testDebugUnitTest` — 50
- `:feature:play:impl:testDebugUnitTest` — 121
- `:feature:profile:impl:testDebugUnitTest` — 36
- `:feature:create:impl:testDebugUnitTest` — 63
- `:games:zip:test` — 174; `:games:zip-ui:testDebugUnitTest` — 83
- `:games:tango:test` — 140; `:games:tango-ui:testDebugUnitTest` — 67
- `:games:queens:test` — 294; `:games:queens-ui:testDebugUnitTest` — 178
- `:games:sudoku:test` — 140; `:games:sudoku-ui:testDebugUnitTest` — 107
- `:games:strands:test` — 94; `:games:strands-ui:testDebugUnitTest` — 114
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
