# PuzzleBox — project map

pinned: e6e0a0c6ce427e08e3254424f684ce455aeea99b

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
in-module structure rules (`:konsist`, 16 Konsist-backed rules with shrink-only allowlists;
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
run no CI; the local `assembleDebug test` is the merge gate; main tests before releasing), ADR-0024 per-game daily boards (rules allow-list, day window, rank-ordered queries; amends ADR-0013 and ADR-0020), ADR-0025 Spotlight and press ledger (one generic `StepHints` press flow, shape-only glyph seam, `HintMode` supplied per game at registration; hint-parity seams). ADR-0026 Competitions (match documents in Firestore, sealed results, device-derived outcome, a Duels tab in Games). ADR-0027 navigation placement (one owner per content type, the placement table and Konsist P9, chrome types `Bar`/`Immersive`, the shell's one avatar and Profile's gear, one flag-on convention (the NavV2 flag shipped in 0.24.0 and was removed); amends ADR-0011). ADR-0028 LinkedIn account reads (the player's own result and connections' boards through the logged-in WebView session; amends ADR-0027's owner table).

## Layout

Gradle root `PuzzleBox`; `settings.gradle.kts` is the module list. Dependency direction:
`:app` (composition root, depends on everything) → `:feature:*:impl` → `:feature:*:api` →
`:core:navigation` (features: home, play, history, profile, create, compete); impls also sit on `:core:*` (`:core:designsystem` via the
`puzzlebox.android.feature.impl` plugin). `:games:<x>` → `:core:game` (+ `:core:model`),
`:games:<x>-ui` → `:games:<x>`, `:core:game`, `:core:game-ui`, `:core:designsystem`; games plug in
through Koin. `:core:data` exposes `:core:common`/`:core:game`/`:core:model` and hides
`:core:database`, `:core:datastore`, `:core:network`; `:core:domain` exposes `:core:data`;
`:backend:firebase` implements seams declared in `:core:data`. `:core:auth` (Android library, no project
edges) holds the Google ID-token seam. Every module except app/backend
tests via `testImplementation(projects.core.testing)`. Pure JVM modules
(`puzzlebox.jvm.library`): `:core:model`, `:core:game`, `:core:network`, `:games:zip`,
`:games:tango`, `:games:queens`, `:games:strands`, `:games:sudoku`, `:tools:puzzle-cli` (ADR-0021; the
one `tools` family module, adds Gradle's `application` plugin), plus `:konsist` (test-only `jvm.library`, zero project
edges; reads the repo's sources as files). Game UI modules: `:games:zip-ui`, `:games:tango-ui`, `:games:queens-ui`,
`:games:strands-ui`, `:games:sudoku-ui`. Feature api modules: `:feature:home:api`, `:feature:play:api`,
`:feature:history:api`, `:feature:profile:api`, `:feature:create:api`, `:feature:compete:api`.

Forbidden edges (ADR-0008, [docs/adr/0008-module-graph.md](../adr/0008-module-graph.md)),
checked over ALL configurations incl. test by `./gradlew checkModuleGraph` (build-logic
`ModuleGraphGuard.kt`, registered by the application / android.library / jvm.library
convention plugins; `check`, `preBuild` and `classes` depend on it, so `./gradlew check` runs
it): anything → `:app`; `:core:testing` from a non-test configuration; `core:*` → non-core;
`feature:*` → `games:*`; `feature:*:api` → anything but `:core:navigation`/`:core:model`;
`feature:a:impl` → `feature:b:impl`; `games:*` → `feature:*`; `games:x` → `games:y` except
`games:x-ui` → `games:x`. Two more arms guard the verification module `:konsist` (ADR-0009):
`:konsist` → any module, and any module → `:konsist` (test configurations too). Two more arms
guard the `tools` family (ADR-0021): `tools:*` → anything but `:core:game`, a non-UI `games:*`
and (test configurations only) `:core:testing`; and any module outside `tools:*` → a tool.

- `app/` (`:app`) — `PuzzleBoxApplication` (starts Koin, then starts `LeaderboardSubmitWatcher`,
  `CustomBoardSubmitter` and `MatchResultPoster`),
  `MainActivity` (Compose host, theme resolution, edge-to-edge), `PuzzleBoxApp` (bar or rail
  over the shell; highlight = `navigator.state.tab`; Games tab badge while
  `ObserveTodaySet` reports any of today's games in progress or a duel needs the player;
  holds the Enter-code draft and the one-shot `LibraryRequests`; the rail carries the avatar), `nav/` the shell
  (ADR-0011, ADR-0027): `TopLevelDestination.kt` (`topLevelDestinations` tab registry: key, label, icon,
  test tag, `Tab`; the four-tab set Today, Games, History, Create),
  `PuzzleBoxNavigationState.kt` (fresh state, start tab first),
  `RememberNavigator.kt` (`rememberNavigator`, saved under `NAVIGATION_STATE_KEY`
  `"puzzlebox.navigation.v1"`, restored via `reconciledWith`; foreign/undecodable → fresh),
  `Placement.kt` (the placement table: `Placement`, `Home`, `Chrome` `Bar`/`Immersive`, `Tab`,
  `placementOf`; one row per `NavKey`, guarded by Konsist P9) and `Immersive.kt` (`isImmersive`),
  `TabEntries.kt` (`rememberTabEntries`, the only Navigation3 adapter: per-tab saveable +
  per-entry `ViewModelStore` + `SceneCanvas` decorators, and `shellChromeDecorator`; `OWNER_TAB`/`ENTRY_KEY` metadata),
  `ShellChrome.kt` (`shellChromeDecorator`, `ShellAvatar`, `ShellAvatarSize`),
  `PuzzleBoxNavShell.kt` (`NavDisplay`, `onBack` = `Navigator.pop`, `PlaySessionStore.retain`
  on stack change and `flush` at ON_STOP; `withHintCooldown` wraps the entry provider and provides
  `LocalHintCooldown` = `HINT_COOLDOWN` per entry), `NavTransitions.kt` (`NavMotion` values and the
  push/pop/tab-switch/Result/reduced-motion chooser), `ChromeMotion.kt` (bar and rail enter and
  exit, `TabRef`, `chromePadding`), `TopLevelNavBar.kt` (bar + rail,
  `badged` keys; the rail is 96dp wide with centred tabs and an avatar slot), `OpenOwner.kt`
  (`Navigator.openOwner`), `LibraryRequests.kt`, `PlayTabBadge.kt` (`playTabBadged`),
  `ApplyFriendLink.kt`/`FriendLink.kt` (friend and duel links) and `ApplySharedText.kt`/`SharedText.kt`,
  `PuzzleBoxEntryProvider.kt` (route table from every feature's `*Entry`
  builder plus `CatalogKey`; the flag-on callbacks are passed here), `CatalogKey.kt`, `PuzzleBoxNavKeys.kt` (`puzzleBoxNavKeys`:
  polymorphic `SavedStateConfiguration` over every feature's keys),
  `leaderboard/LeaderboardSubmitWatcher` (auto-submits records when signed in) and
  `leaderboard/CustomBoardSubmitter` (friend puzzle wins),
  `match/AndroidNetworkStatus` (the Android side of `NetworkStatus`),
  `di/AppModule` (app scope; `GameRegistry` over `getAll<GamePlugin>()` sorted by the app-side
  `GAME_ORDER`, unlisted set from `buildTypeUnlistedGames`; zip → tango → queens → strands → minisudoku → sudoku; `GamePlayRegistry(getAll())` over every game's
  `GamePlayUi`; `PreferencesQueensSettings` and `PreferencesSudokuHighlightSettings` adapt `GameSettingsRepository`),
  `di/BuildTypeReleaseFlagsModule` (`src/debug` `DebugReleaseFlags` with local
  overrides, `src/release` `ShippedReleaseFlags`; ADR-0022), debug-only Showkase catalog route and flat Settings debug sections (`DebugSettingsSections`):
  browser (`CatalogDebugBinding`, `ShowkaseBrowserScreen`, `PreviewBackdrop`,
  `BrowserRadioControls`; goldens in `app/snapshots/`), Release flags
  (`ReleaseFlagsPanel`). Tests:
  the app's `e2e` test package, the E2E robot (`shellRobot`/`ShellRobot`: gestures, back and
  predictive back, background/restore/relaunch, spec-notation row assertions; `E2eKoinGraph`:
  production `appModules` with core:testing fakes on the edges; `TabNavigationE2ETest`,
  `HistoryE2ETest`, `ProcessDeathE2ETest`, `ShellTabsE2ETest`, `ShellSummariesE2ETest`,
  `StrandsPlayE2ETest`, `StrandsArchiveE2ETest`, `DuelFlowE2ETest`),
  `nav/PerTabShellTest`, `nav/ShellSessionLifecycleTest`, `nav/PlacementTest`, `nav/ShellChromeTest`.
- `konsist/` (`:konsist`, test-only `puzzlebox.jvm.library`, no project dependencies) — the
  in-module structure rule suite (ADR-0009): `core/` engine (`SourceTree`, `Allowlist`, `judge`,
  `RuleCheck`, `CensusTest`), `rules/` one `<ID><Name>.kt` + test per rule, resources
  `allowlists/<ID>.txt` and `fixtures/<ID>/` (incl. `add-a-game`). Rules (no S3, ADR-0009):
  S0 no `@Suppress`, S1 one top-level type per file, S2 file length, S4 composable previews,
  S5 no colour literals, S6 no infinite animation, S7 no system night flag outside theme resolution; P1 package root, P2 dispatchers location,
  P3 ViewModel shape, P4 use-case shape, P5 naming, P6 Koin module location, P7 Android-free
  pure modules, P8 NavKey payload, P9 every NavKey has a placement row (ADR-0027; `P9NavKeyPlacement`, fixtures under `fixtures/P9/`).
- `tools/puzzle-cli/` (`:tools:puzzle-cli`, pure JVM `application`, ADR-0021; ships nowhere) —
  `PuzzleCli.kt` (`main`, the one file that names games: a `GameRegistry` over every non-UI
  plugin; `validate <file>` / `solve <file>` print one `CliVerdict` JSON line, exit codes in
  `CliExit`), `CliVerdicts`/`CliVerdict`/`CliError`. It reaches games only through
  `NotationText` and `GamePlugin.editor`, so a game's editor joins the tool by one registration.
  Tests: one parity test per game (CLI vs the app's import path over the shared fixtures),
  `NotationFixtures` (loader for `games/*/src/test/resources/notation/*.txt`) and
  `NotationDocTest`, which pins `docs/puzzle-notation.md` to those fixtures and to the lines
  `PuzzleCli` prints. Run it from the `puzzle-cli` script `installDist` writes under the module's build directory.
- `core/navigation/` (`:core:navigation`) — `NavigationState` (immutable `@Serializable` tab +
  one stack per tab, invariants I1–I4, pure verbs `selectTab`/`push`/`pushOnce`/`pop`/
  `replaceTop`, `reconciledWith`, spec-notation `toString`; `navigationState(vararg tabs)`),
  `Navigator` (snapshot-state holder, one line per verb), and `navKeySavedStateConfiguration`
  (open `NavKey` polymorphism for saved state).
- `feature/{home,play,history,profile,create,compete}/api/` (`:feature:*:api`, plugin
  `puzzlebox.android.feature.api`) — each feature's `@Serializable` NavKeys (`HomeKey`;
  `PlayKey` (games library), `GameKey(gameId, day?)`, `ResultKey(gameId, day, timeMs, hintsUsed)`,
  `ReplayKey(gameId, kind, day, puzzleNumber, puzzleId, code, legIndex)` (the replay page; `kind` is record, latest, myLeg or theirLeg; build it with `ReplayKey.daily`/`custom`/`leg`),
  `FriendPuzzleKey`, `CustomGameKey`, `CustomResultKey`, `MatchLegKey`, `MatchLegResultKey`;
  `HistoryKey` and `HistoryRootKey` (the History tab root); `ProfileKey` and `SettingsKey`;
  `CreateKey` (Create tab root), `EditorKey(draftId)`, `PublishedPuzzleKey`; compete's `DuelKey`
  and `DuelRevealKey`) and a
  `*NavKeys()` polymorphic registration; serial names keep
  the `com.ivandsky.puzzlebox.app.nav.*` prefix by convention, compatibility is versioned by
  `NAVIGATION_STATE_KEY` (ADR-0011).
- `core/common/` (`:core:common`) — `DispatcherProvider` seam (+ `DefaultDispatcherProvider`),
  `AppResult`/`AppError` and `resultOf` convention (typed errors, never thrown surprises),
  `flags/` `ReleaseFlag` enum (TextImport, ShareTarget, Competitions, LinkedInAccount) + `ReleaseFlags` reader + `ShippedReleaseFlags` (ADR-0022), Koin `commonModule`.
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
  and `update/` `AppReleaseRepository` (GitHub releases); `repository/SharedTextStore` →
  `InMemorySharedTextStore` (text another app shared: `pending` flow, `put`, atomic `take`;
  in memory only, never persisted or in a NavKey).
  `linkedin/` (ADR-0028: `LinkedInLink` → `DataStoreLinkedInLink`, `LinkedInFetchPolicy` +
  `LinkedInCallBudget`, `LinkedInGhost` + `LinkedInGhostWriter`, `LinkedInImport` (an own result →
  a `CompletionRecord`), `LinkedInSync`, `LinkedInDayData`, `LinkedInOwnState`); `match/` (ADR-0026:
  `Match`, `MatchRepository` the remote port, `MatchLocalRepository` → `RoomMatchLocalRepository`,
  `MatchResultOutbox` → `RoomMatchResultOutbox`, `MatchResultPoster` + `MatchResultSync`,
  `LegRef`, `PuzzleCodeCheck`); `network/NetworkStatus` (the port the poster waits on);
  `repository/GameSettingsRepository` → `OfflineGameSettingsRepository` (per-game boolean settings).
- `core/database/` (`:core:database`) — Room v12 `PuzzleBoxDatabase`: `PuzzleDailyDao`/
  `PuzzleDailyEntity` (`puzzle_dailies`, keyed gameId+day), `CompletionDao`/`CompletionEntity`
  (`completions`, keyed on the encoded puzzle-ref string), `SavedPlayDao`/`SavedPlayEntity`
  (`saved_plays`, keyed gameId+day, `blob` + `updatedAt`, `saveAndPrune`), v7 additions
  `DraftDao`/`DraftEntity` (`drafts`: id, gameId, variant, title, note, blob),
  `CustomSavedPlayDao`/`CustomSavedPlayEntity` (`custom_saved_plays`), `PublishedPuzzleEntity`
  (`published_puzzles`), `ReceivedPuzzleEntity` (`received_puzzles`), v8 duel storage
  `LegSavedPlayDao`/`LegSavedPlayEntity` (`leg_saved_plays`), `MatchLocalDao`/`MatchLocalEntity` (`matches_local`),
  `MatchResultDao`/`MatchResultEntity` (`match_results`) and nullable `completions.matchCode`, v9
  `completions.source`; schemas 1–12
  exported under `core/database/schemas/`, `Migrations.ALL` = 1→…→12; v12 `CompletionReplayDao`/
  `CompletionReplayEntity` (`completion_replays`, one sealed replay per record or latest completion,
  cascade-deleted with it) and nullable `match_results.replay`; `linkedin/`
  `LinkedInDatabase` (a separate Room file, v1: `GhostEntity`/`GhostDao`, `FetchLogEntity`/`FetchLogDao`); Koin `databaseModule`.
- `core/network/` (`:core:network`, pure JVM) — the one LinkedIn client: internal
  `LinkedInGamesApi` (Retrofit), `LinkedInSessionCookieJar` + `LinkedInCsrfInterceptor`
  (anonymous session), `LinkedInFeedTransport` implementing `core:game`'s `FeedTransport`;
  the NYT client (ADR-0014): internal `NytApi` + `NytHttpTransport` implementing `core:game`'s
  `NytTransport` (own sessionless client, call timeout); `linkedin/` (`VoyagerQueries`,
  `VoyagerTodayParser`, `VoyagerOwnResultParser`: the account reads' request shapes and parsers); Koin `networkModule`.
- `core/datastore/` (`:core:datastore`) — `PuzzleBoxPreferencesDataSource` over a Preferences
  DataStore (`PuzzleBoxPreferences`); `LinkedInLinkDataSource` over a DataStore file of its own
  (`LinkedInLinkPrefs`: linked profile, consent time, pause latch; nothing secret); Koin `datastoreModule`.
- `core/designsystem/` (`:core:designsystem`) — `PuzzleBoxTheme` composition root, token
  vocabulary (`ColorTokens` light/dark, `Type` Baloo 2, `Shape`, `Spacing`, `MotionTokens` +
  named motion patterns), `BoardTokens`/`BoardColors` (board palette + wall/grid weights,
  contrast-tuned per light/dark), the interactive `PuzzleBoard` reference board, components
  (buttons, chips, dialogs, top bars, states) carrying `@ShowkaseComposable` metadata,
  `ShowkaseTokens` color/typography registrations; the browser runtime is debug-only
  (`ShowkaseBrowserModule` `@ShowkaseRoot` in `src/debug`; the browser screen lives in
  `app/src/debug`). Font license in `licenses/`. `spotlight/` (ADR-0025): `Spotlight` (cell
  `roles` by `SpotlightRole` TARGET/EVIDENCE/EXCLUDED/CULPRIT, optional `SpotlightCallout`, `SpotlightGlyphs`),
  the shape-only `SpotlightGlyphs` seam each game implements, `SpotlightOverlay` (mounted over
  the cell grid; `SpotlightSequence`, `SpotlightLayout`, `SpotlightMarks`, `SpotlightTokens`);
  `CooldownRing.kt`: `Modifier.cooldownRing(progress, accent)` draws a Hint button's cooldown fill.
  Shell seams (ADR-0027): `ShellActions.kt` (`LocalShellActions`; `LargeTopBar` and `CompactTopBar` in
  `TopBars.kt` draw it after the screen's own actions), `SummaryLinkRow.kt`, `TabReselect.kt`
  (`LocalTabReselect`). `DuelTone.kt` is the duel colour vocabulary.
- `core/model/` (`:core:model`, pure JVM) — dependency-free shared types in package
  `com.ivandsky.puzzlebox.model` (`GameId`, `ThemeMode`, `PlaySlot` (`Daily`/`Custom`), `GameSetting`).
- `core/domain/` (`:core:domain`) — use cases (package `com.ivandsky.puzzlebox.domain`, Koin
  `domainModule`): `ObserveGameHistory` (records UNION every registered game's captured days →
  `GameHistoryDay`), `GetFeaturedGame` (registry's first game as `GameSummary`),
  `ComputeStreak`, `LoadPastDay` (a past day by date — cached, or fetched once for an archive game — → the game's session blob as
  `PastDayOutcome`), `ObserveLeaderboard` (identity-gated board for one game and day, or `today(game)` on the game's calendar day:
  `TodayLeaderboard`), `ObserveLatestSolvedToday` (the game whose numbered daily was solved most recently on its own today, or null),
  `ObservePlayStats` (one slot's tablet side-panel stats: streak, week,
  today progress, times → `PlayStats`), `ObserveTodaySet` (saved plays + records + each game's calendar day →
  registry-order `TodayEntry` per game: Solved/InProgress(elapsed)/NotStarted; `nextUnsolved`),
  `ImportDraftFromText` (whole-text import: `NotationText.parse` on `dispatchers.default`, and
  only a parsed draft is stored, via `CreateDraft` + one `DraftRepository.upsert`, as an
  imported row → `ImportResult`). Duel use cases (ADR-0026): `CreateMatch`, `ObserveMyDuels`,
  `ObserveDuel`/`ObserveMatch`, `ComputeMatchLegs`/`ComputeMatchOutcome`, `FinishLeg`,
  `UpdateMatchSeat` (claim, cancel, decline), `RecordRevealSeen`, `RevealReady`. Android library.
  Replay use cases (ADR-0030): `LoadSolveReplay` over `SolveReplayRef` (`Record`/`Latest`/`MyLeg`/`TheirLeg`), which asks
  `LoadDailyReplay`, `LoadCustomReplay` and `LoadLegReplay` in turn and opens the source through the game's plugin.
- `core/game/` (`:core:game`, pure JVM) — the orthogonal game contracts (ADR-0001):
  `GameDefinition`, `GameRules<P>`, `GameSession` + `GameSessionCodec`, `ScoreModel<S>` →
  `CompletionRecord?`, `PuzzleSource` (`fetch(date)` → `FetchResult`; `FetchError`), composed
  per game as `GamePlugin<P, S, M>`; `GameRegistry` (the sole GameId → `GamePlugin` lookup; `get` resolves listed + unlisted, `all` = listed only and feeds every surface, `unlisted` = debug-only 9x9 opened from Settings → Debug games);
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
  `EditorNotation<D>` (opt-in capability of a `GameEditor`: `parse`/`print`/`columns`,
  `notationVersion`, the `puzzlebox <game> v<n>` text of `docs/puzzle-notation.md`) and
  `NotationText` (the one whole-text entry: 64 KiB cap → header → `registry[id].editor as?
  EditorNotation` → version → parse → `NotationParse`), shared by `ImportDraftFromText` and
  the CLI. `CompletionSource` (`App` or `LinkedIn`) tags where a completion came from; `linkedin/`
  (ADR-0028) holds the account ports `LinkedInAccountSource` and `LinkedInSessionCookies` and their value types.
  `replay/` (ADR-0030, the solve replay): `ReplayMove` + `GameReplay<S>` (a game's pure `step` and `facts`),
  `MoveLog` (the journal of one play), `ReplayCodec` (envelope and caps), `ReplayRecord`, `Timeline` +
  `ReplayFrame` (`Timeline.at(t)`, re-folded), `Lead`, `ReplayFacts`, `ReplayOutcome`; `playTimeMs` in
  `PlayTime.kt`; `GamePlugin.replay` (optional) and `GamePlugin.openTimeline`. `SolveReplay` (the sealed bytes) is in `:core:model`.
- `core/game-ui/` (`:core:game-ui`) — the Compose mapping layer keyed by `GameId` (no Compose in
  `:core:game`): play contracts (ADR-0007) `GamePlayUi` (`createController(PlayEnvironment)`,
  `Screen`), `GamePlayController` (+ `PlayClock`/`PlayHints`/`PlayUndo`), `GamePlayRegistry`
  (GameId → `GamePlayUi`; a duplicate id throws), `PlayBoardStatus` + `PlayBoardFallback`,
  `PlayCopy`, `BoardGrid` + `EdgeOverlay`, `DragTrace`, `GridGeometry`, `HintButton`,
  `TimerBar` + `formatClock`, `PlayScaffold` (`playLayoutFor`), `PlayChrome`, `ResultHeroScreen` (daily result), `ResultScreen` (friend result),
  `CompletionRecordEmitter`. `editor/`: `GameEditorUi` (per-game `sizeLabel`,
  `createController`, `Board`), `GameEditorController` (draft `StateFlow`, `adopt`, overlay
  hooks), `GameEditorRegistry` (GameId → `GameEditorUi`), `EditorOverlay` (witness display).
  `hint/` (ADR-0025, the one stepwise press flow; never interprets a step): `StepHints<S, K>`
  (press → search off the main thread → ledger → current `Spotlight`; `StepHints.Game` is the
  per-game adapter: `capture`, `keyOf`, `spotlight`), `HintLedger` (a step key counts once per play, in memory,
  saturating at `MAX_HINTS_USED`), `HintSearch` (stale results dropped), `HintMode`
  (`Legacy` / `Stepwise(compute)`), `HintModeSource` (supplied per game at registration; UI modules never read a flag),
  `OncePerPuzzle<P, S>` (the solution memo, last puzzle only). Beside `HintButton`:
  `HintCooldown` + `rememberHintCooldown` (the Hint button's wait; `LocalHintCooldown` null = none).
  Duel kit: `DuelCompare`/`DuelSide`/`DuelSideState`, `PlayerAvatar` (the signed-in photo or the
  profile icon; the shell avatar uses it), `LocalRouteCaption` (a game's own caption wins),
  `PlayBoardPane`, and `ResultHeroActions`/`ResultHeroChip` (the result hero takes action and chip slots).
  `replay/` (ADR-0030): `ReplayPlayback` (the headless state machine: advance by frame time, speed, skip idle,
  seek, step), `ReplayPlayer`/`ReplayPage`, `ReplayCompare`/`ReplayComparePage` with `CompareLayout`
  (`compareLayoutFor(width)`: `Twin` at Expanded, `FlipPeek` otherwise), `WatchReplayRow` (the result's entry),
  `ReplayLeadLine`, and the per-game `GameReplayBoard` contract.
- `core/testing/` (`:core:testing`) — test doubles and contract tests, test configurations only (package
  `com.ivandsky.puzzlebox.testing`): `repository/` (`FakeDailyRepository`,
  `FakeCompletionRepository`, `FakeIdentityService`, `FakeLeaderboardRepository`,
  `FakeSavedPlayRepository`, `FakeUserPreferencesRepository`, `FakeMatchRepository`,
  `FakeMatchLocalRepository`, `FakeMatchResultOutbox`, `FakeGameSettingsRepository`), `calendar/FakeGameCalendar`,
  `network/FakeNytTransport` and `FakeNetworkStatus`, `linkedin/` (`FakeLinkedInAccountSource`,
  `FakeLinkedInLink`, `FakeLinkedInSessionCookies`), `contract/` (`MatchRepositoryContract`,
  `MatchResultsContract`), `game/TestGames` (stand-in registry), `rule/MainDispatcherRule`.
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
  (`SudokuVariants`: the 6x6 and 9x9 variant ids); `engine/rules/` `SudokuConflicts`,
  `engine/solve/SudokuSolver` (MRV bitmask), `engine/hints/` (`SudokuHints` ladder over
  singles, intersections, subsets, reveal fallback); `codec/` `SudokuCodec`,
  `SudokuSessionCodec` v1 + `SudokuCommandCodec`; `feed/` `MiniSudokuGameExtractor`,
  `MiniSudokuWireParser`, `SudokuUniquenessGate`; `SudokuScoreModel`.
- `games/zip-ui/`, `games/tango-ui/`, `games/queens-ui/`, `games/strands-ui/`, `games/sudoku-ui/` (`:games:<x>-ui`) — each game's play
  UI: one `*PlayController : GamePlayController`, one `*PlayUi : GamePlayUi`, the board
  composables, and `*UiModule` (the game's `GamePlayUi`). Strands: `StrandsPlayController`,
  `StrandsScreen`, `board/` (`StrandsCellDrawing` maps each `CellState` to its
  `StrandsBoardSpec` — `IdleSpec`, `FoundThemeSpec`, `FoundSpangramSpec`, `HintOutlinedSpec`,
  `HintOrderRevealedSpec` — plus `TraceOverlaySpec`, gesture/hit-test/sizing;
  `StrandsTraceEnd` + `StrandsTraceEndTimeline` play the path-local refusal and the neutral
  trace fade-out from the controller's `traceEnd`), `chrome/` (`StrandsHintRow`,
  `StrandsHintControl` with `HintDots`/`HintBadge`/`HintBonus`/`HintFace`, `ThemeCard`,
  `WordSlotLine`,
  `FoundCounter`, `HintModal`). The `editor` package of queens-ui: `QueensEditorUi : GameEditorUi` +
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
  `EliminationStep` file, a line in `EliminationSteps.DEFAULT` (Classic's ladder) and a
  `SudokuTechnique` entry; Mini's stepwise hint also needs a rung in `SudokuEliminations`
  (`engine/hints/step/`) and a `SudokuStepCopy` entry in sudoku-ui's `hint` package. Legacy captions
  name only the single, so `SudokuHintCaptions` stays unchanged.
- `games/{queens,tango,sudoku}-ui/…/hint/` — each game's stepwise-hint adapter over core's
  `StepHints` (Queens, Tango and Mini Sudoku; Classic keeps the legacy hint, A24): `*SpotlightMapper` (step
  → roles, no drawing), `*StepCopy` (the callout line), `*SpotlightGlyphs` (the game's
  shape-only `SpotlightGlyphs`), `*StepGame : StepHints.Game` (capture, key, spotlight;
  Queens and Tango keep their solution in `OncePerPuzzle`), plus `tangoStepHints`. Steps come from
  each engine's `engine/…/step` package (`QueensStepper`, `TangoStepper`, `SudokuStepper`).
- `feature/home/impl/` (`:feature:home:impl`) — `navigation/HomeEntry`, `HomeRoute` +
  `HomeViewModel` (today's set via `ObserveTodaySet`, stats via `ObserveGameStats`, the 7-day
  week, `GetNextRollover`, identity, cache-only history teaser via `ObserveGameHistory`;
  `di/HomeModule`), `HomeScreen` with `HomeListPane`/`HomeDetailPane` stacking
  `HomeTodaySection` (`HomeTodaySummary` card with `TodayBadge`s + one CTA / rollover countdown)/`HomeStreakSection`/
  `HomeRecentSection`, `HomeLayout` + `homeLayoutFor` pure pane policy. Home is the Today
  tab: `HomeSummaries` (rank callback + `HomeDuelsSummary`) adds the rank and duels `SummaryLinkRow`s, and its recent days link
  "See all" to the History tab root (`onOpenHistory`).
- `feature/play/impl/` (`:feature:play:impl`) — the one game-agnostic play surface (ADR-0006,
  ADR-0007; no `games:*` dependency): `PlaySessionStore` (Koin single: one `ActivePlay` per
  `PlaySlot`, `open(GameKey)`/`peek(slot)`/`retain(keys)`/`flush()`, `playable` games, pins a
  null-day key to today on first open; `SolvedDays` holds which days are solved, `OpenDecision`
  what opening a key shows), `ActivePlay` (controller, board/record flows, record
  write, A10 verdict, slot saves with elapsed ms), `SlotOpening` (saved row → today's fetch → past-day replay build
  → Unavailable; undecodable row deleted), `SlotWriter` (per-slot 300 ms trailing-throttle
  saves, `discard` on solve), `WriteLane` (one Mutex-ordered lane for every saved-play I/O),
  `LibraryRoute` + `LibraryViewModel` (the Games screen: `GamesTabs` pills over `GamesTab`
  Dailies, Friends and Duels, the Duels body handed in as a `DuelsSlot`; the Dailies tab is a row per game with today's status;
  two-pane shows the list beside the selected game's `LibraryGamePage` — `ArchiveDayCard` (today, or the
  picked past day → `GameKey(game, day)`), `ArchiveMonthCalendar` (`ArchiveMonthNav` ‹ › + jumps,
  `ArchiveDayDisc` per day from `ObserveArchiveMonth`), `GameStatsCard`, `HowToPlayCard` (rules + Open the lesson); with the
  app's flag-on arguments it adds `GamePageBoard` (the game's leaderboard: a Today / Yesterday
  toggle, the top five and the player's own row pinned beneath) and a "Your history" `SummaryLinkRow`; a phone pushes the
  page over the list; a one-shot `LibraryRequest` opens a page or a section), `GameRoute` + `GameViewModel` (one `GameKey`'s board; first-play tutorial
  and How to play via `TutorialGate`), `ResultRoute` + `ResultViewModel` (verdict + previous
  best live, Play again, Next unsolved; an optional "Full board" row), duel legs
  (`MatchLegRoute`/`MatchLegViewModel`, `MatchLegResultRoute`, `LegCompletionRepository`
  tagging a leg solve with its match), `friend/` (a friend's puzzle: `FriendsTabBody`,
  `EnterCodeSheet`, `FriendPuzzleRoute`, `CustomResultRoute`), `di/PlayModule`; `navigation/PlayEntries` (`playEntry`,
  `resultEntry`) and `navigation/GameEntry`.
- `feature/history/impl/` (`:feature:history:impl`) — `navigation/HistoryEntry` (the pushed
  `HistoryKey` with a back arrow and the tab root `HistoryRootKey` without one),
  `HistoryRoute`/`HistoryScreen` with `HistoryListPane`/`HistoryDetailPane` over
  `HistoryViewModel` (`di/HistoryModule`), `HistoryPaging` (`historyPager`,
  `HistoryPagingSource`), `HistoryLayout` + `historyLayoutFor`; the All timeline only (A28:
  streak and stats, week/month calendar, every game's rows; any history key opens it); a
  history row pushes that day's `GameKey` on the current stack, which resumes or replays the slot.
- `feature/profile/impl/` (`:feature:profile:impl`) — packages kept from the app:
  `app/screens/` (`ProfileRoute`, `ProfileScreen`, `ProfileViewModel(identityService,
  idTokenProvider, observeGameStats)`: the account from `IdentityService.current` and the
  player's overall `playingSince`; `ProfileUiState` is `account` + `playingSince`),
  `profile/ProfileRecord` (header with avatar, name and "Playing since"), `profile/AppearanceRow`
  (drawn by Settings), `profile/settings/` (`SettingsRoute`, `SettingsScreen`,
  `SettingsViewModel`, `SettingsUiState`: Appearance, About with `AppBuild.versionName`, and a
  Developer link to the catalog in debug builds), `profile/di/ProfileModule` (`profileModule`),
  `profile/navigation/ProfileEntry` (`profileEntry` and the Settings entry; `onBack` draws
  the back icon and `onOpenSettings` the gear, both required). The Google ID-token provider moved
  to `:core:auth`.
- `feature/create/impl/` (`:feature:create:impl`, ADR-0019; no `games:*` dependency; `published/` hosts `PublishedPuzzleRoute`) —
  `navigation/CreateEntries` (`CreateKey`, `EditorKey`, `PublishedPuzzleKey`), `CreateRoute`/`CreateScreen` +
  `CreateViewModel` (draft list over `DraftRepository`, `NewPuzzleSheet`, `DraftActionsSheet`),
  `editor/` `EditorRoute` + `EditorViewModel` (one draft through `GameEditorRegistry`;
  `DraftAutosave`, `EditHistory`), `StudioScreen` (`StudioLayout`, top bar, `ToolStrip`, side
  pane, `ActionArea`, `VerdictCard`/`VerdictPill`), `VerdictPipeline` (debounced
  `analyze` → `Verdict` flow), `TestSolveScreen` + `TestSolveSession` (play the draft before
  publish); `di/CreateModule`. `importtext/`: `ImportTextState` (the Import-from-text sheet's
  holder: open/edit/submit, clipboard offer, `onImported(draftId)`; one per screen that offers
  the sheet — Create root and Studio) over `ImportDraftFromText`, with `ImportSheetActions`
  (its callbacks). `CreateViewModel.importSharedText` imports text another app shared.
  Use cases in `core:domain`: `CreateDraft`, `ValidatePublish`, `ImportDraftFromText`.
- `feature/compete/` (`:feature:compete:api`, `:feature:compete:impl`, ADR-0026; plugin
  `puzzlebox.android.feature.api` / `.feature.impl` + roborazzi) — api: `DuelKey`,
  `DuelRevealKey`, `competeNavKeys()`. Impl: `tab/` (`DuelsTab` + `DuelsTabViewModel`, the
  challenge card, my duels, `DuelInviteRows` with inline Accept and Decline, `DuelsNeedYou` →
  the Games Duels badge and Today's row), `create/` (`NewDuelSheet`, `DuelCustomizePanel`,
  `DuelSignInStep`), `share/` (`DuelCreatedSheet`, `InviteShare`), `duel/` (`DuelRoute` +
  `DuelViewModel` over one `ObserveDuel` flow, `DuelLegs`, `DuelInviteCard`, `DuelLegCard`),
  `reveal/` (`RevealRoute` + `RevealViewModel` driving `RevealMachine`, `RevealStage`,
  `RevealSummary`, haptics, `RematchSheet`), `navigation/CompeteEntries`, `di/CompeteModule`.
  Depends on compete:api, play:api, core:auth, data, domain, game and game-ui.
- `backend/firebase/` (`:backend:firebase`) — implements the `core:data` seams:
  `FirebaseIdentityService` over an internal `AuthGateway`/`FirebaseAuthGateway`,
  `FirestoreLeaderboardStore` + `FirestoreLeaderboardRepository` (dedupe on (uid, puzzleRef)),
  `FirestoreMatchRepository` over `FirestoreMatchStore` + `MatchDocCodec` (duels, ADR-0026;
  `MatchStore` is the internal seam),
  `LeaderboardDocCodec`, `BackendModule` (`firebaseBackendModule`). Rules and indexes live
  alongside (`firebase.json`, `firestore.rules`, `firestore.indexes.json`); `hosting/` is the
  share-link page (`p/code.js`, `p/index.html`, `.well-known/assetlinks.json`), `m/` the duel-link landing page (`m/index.html`, `m/code.js`),
  `hosting-test/` its Node test (`code.test.mjs` over `share-code-vectors.json`).
- `PROFILE.md` — product frame, ranked care-axes, deploy targets, testing style.
- `docs/puzzle-notation.md` — the `puzzlebox <game> v<n>` text format agents write (spec A23);
  every sample is pinned by `NotationDocTest` in `:tools:puzzle-cli`.
- `docs/adr/` — ADR-0001…0028; 0001–0028 are summarized under Purpose.

## Entry points

- `PuzzleBoxApplication.onCreate` — `startKoin` with `appModules` = `commonModule` +
  `dataModule` + `firebaseBackendModule` + `domainModule` + `homeModule` + `historyModule` +
  `playModule` + `createModule` + `authModule(R.string.default_web_client_id)` + `profileModule` +
  `competeModule` + each game's
  `*Module` and `*UiModule` (zip, queens, tango, strands, minisudoku) + `appModule` +
  `buildTypeReleaseFlagsModule`, plus `buildTypeGameModules`
  (`BuildTypeGameModules.kt` per source set: debug adds `sudokuModule` + `sudokuUiModule`,
  release is empty; `ReleaseGameModulesTest` in the testRelease source set), then starts `LeaderboardSubmitWatcher`,
  `CustomBoardSubmitter` and `MatchResultPoster`.
- `MainActivity` — sole activity; holds composition until the persisted `ThemeMode` arrives
  (no light flash), resolves dark theme against the system setting, keeps system bar icons in
  step, then hosts `PuzzleBoxApp` with the width size class, `ThemeMode`, and the setter.
- `PuzzleBoxApp` route table (`puzzleBoxEntryProvider`, entries contributed by each feature
  impl's `*Entry` builder; placement of every key is a row in `Placement.kt`, ADR-0027). Tab
  roots: HomeKey → `HomeRoute` (Today), PlayKey → `LibraryRoute` (Games, with
  its Dailies, Friends and Duels tabs), HistoryRootKey → `HistoryRoute` (History, no back arrow),
  CreateKey → `CreateRoute`. Shell screens pushed on the current tab's stack: ProfileKey → `ProfileRoute`
  (slim, with a back icon and the gear) and SettingsKey → `SettingsRoute`. Pushed on a tab:
  EditorKey(draftId) → `EditorRoute` (the Studio; swaps to `TestSolveScreen` while
  test-solving), PublishedPuzzleKey → `PublishedPuzzleRoute`, HistoryKey →
  `HistoryRoute`, GameKey(game, day?) → `GameRoute` (from the library's day card or a history row),
  ResultKey → `ResultRoute` (replaces the game on solve; distinct enter transition),
  ReplayKey → `ReplayRoute` (opened from the result's Watch replay row),
  FriendPuzzleKey, CustomGameKey and CustomResultKey (a friend's puzzle), DuelKey →
  `DuelRoute`, MatchLegKey → `MatchLegRoute`, MatchLegResultKey → `MatchLegResultRoute`,
  DuelRevealKey → `RevealRoute`, CatalogKey → the debug-only Showkase browser (`CatalogDebugBinding`, FLAG_DEBUGGABLE-gated;
  release gets a placeholder). Boards, results, a duel leg's board and result and the reveal are
  immersive (no bar or rail). Each tab keeps its own stack; reselecting a tab pops it to its
  root; back at a non-start root returns to the start tab; back at the start root exits
  (ADR-0011). Bottom bar on compact widths, rail on expanded-width landscape
  (`homeLayoutFor`/`playLayoutFor`/`historyLayoutFor`); the rail also carries the avatar.
- Share target (`ShareTarget` flag, debug builds only): the debug manifest
  (`app/src/debug/AndroidManifest.xml`) adds a plain-text `SEND` intent filter to
  `MainActivity`; M5-22 moves it to the main manifest. `MainActivity` (new intent or fresh
  start, gated by the flag) → `SharedText.of` → `SharedTextStore.put` → `PuzzleBoxApp`'s
  `pendingShare` → `Navigator.applySharedText()` (select Create, pop to its root, from any
  tab) → the Create root's `CreateViewModel.importSharedText` takes the text and imports it
  while RESUMED.
- `puzzle-cli` — `PuzzleCliKt.main` (`:tools:puzzle-cli`), run from `installDist`, never
  from the app: `puzzle-cli validate|solve <file>` → one JSON verdict line, exit code `CliExit`.
- No widgets or services. The main manifest's other filter is the `https` VIEW for share links
  (host `puzzlebox-f3356.web.app`, path prefix p); `MainActivity` hands it to `PuzzleBoxApp` as `pendingLink`.

## Seams

- **Koin DI graph** — `commonModule` + `dataModule` (repositories; includes `databaseModule`
  Room + DAOs, `networkModule` Json/cookie jar/`LinkedInGamesApi`/`FeedTransport`,
  `datastoreModule` DataStore + `PuzzleBoxPreferencesDataSource`) + `firebaseBackendModule` +
  `domainModule` (use cases) + `authModule` + feature modules (incl. `competeModule`) + per game `*Module` (plugin +
  `DailySource`) and `*UiModule` (`GamePlayUi`) + `appModule` (`CoroutineScope`,
  `LeaderboardSubmitWatcher`, `CustomBoardSubmitter`, `NetworkStatus`, `GameRegistry`,
  `GamePlayRegistry`, `GameEditorRegistry`) + `buildTypeReleaseFlagsModule`. Completeness asserted by
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
- **Placement table** (ADR-0027, `app/nav/Placement.kt`) — one row per `NavKey`: home (`TabRoot`
  of a tab, `PushedOn` a tab, `Shell`, or `DebugOnly`) and chrome (`Bar` or `Immersive`);
  `placementOf` throws for a key with no row. `isImmersive` (`Immersive.kt`) derives only the
  bar-hiding predicate from it. Konsist P9 fails a production `NavKey` without a row. The
  per-key table is never copied into docs.
- **Tab set** (ADR-0027) — `topLevelDestinations` is the one registry: Today, Games, History, Create.
  Features never read a flag for it: each entry takes nullable callbacks or a slot, and the app
  passes them (ADR-0027 §10).
- **Shell actions and the avatar** (ADR-0027) — `ShellAvatar` (`app/nav/ShellChrome.kt`) is the
  one avatar; it pushes `ProfileKey` with `pushOnce`. The tablet rail shows it at its bottom
  (`TopLevelNavRail` `bottomActions`, 96dp rail). The phone shows it in Today's header only,
  through `shellChromeDecorator` providing `LocalShellActions` (`core:designsystem`) to
  `HomeKey` entries; only `LargeTopBar` and `CompactTopBar` read the slot. Profile's top-right
  gear (`profile.settings`) opens `SettingsKey`; the shell has no gear.
- **`SummaryLinkRow`** (`core:designsystem`) — the one summary card: Today's rank and duels
  rows (`HomeSummaries`), the game page's "Your history" row, Result's "Full board" row.
- **`LocalTabReselect`** (`core:designsystem`) — the shell tells tab roots that the selected
  tab was tapped again; Today, History, the Games list and Create's lists scroll to the top.
- **`openOwner`** (`app/nav/OpenOwner.kt`) and **`LibraryRequests`** (`app/nav/LibraryRequests.kt`)
  — "go to owner" composes `selectTab` and `push`; the app holds a one-shot `LibraryRequest`
  (open a game's page, or a Games section such as Duels) and the Games entry takes it once.
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
- **Solve replay** (ADR-0030) — `GamePlugin.replay` (`GameReplay`, `core:game`) and `GamePlayUi.replayBoard`
  (`GameReplayBoard`, `core:game-ui`) are the two optional per-game seams. `LoadSolveReplay(SolveReplayRef)`
  (`core:domain`) is the one read entry; `ReplayKey` and `ReplayCompareKey` (`feature:play:api`) are the one
  replay and the one compare destination, ids only. `ReleaseFlag.FullReplay` gates recording (`PlaySessionStore`)
  and reading (`LoadSolveReplay`).
- **Hint seams** (ADR-0025) — `HintModeSource` (`core:game-ui`) hands each controller
  `HintMode.Legacy` or
  `Stepwise`: Queens, Tango and Mini Sudoku pass `Stepwise` at registration, Classic Sudoku keeps `Legacy`, and
  there is no hint release flag; a stepwise controller builds one `StepHints` over its game's `StepHints.Game`.
  `StepHints` runs the press, `HintLedger` decides its cost, and the `Spotlight` the adapter
  returns is drawn by `SpotlightOverlay` (`core:designsystem`). The recipe (A18): a new
  deduction is one step class in that game's catalog (`QueensStepper` ladder, `TangoStepper`
  rungs, `SudokuEliminations` rungs) plus its copy entry in `*StepCopy`; a new game is a
  `*SpotlightMapper`, `*StepCopy`, `*SpotlightGlyphs` and one `StepHints.Game`.
- **Hint cooldown** (F2, `core:game-ui`) — `LocalHintCooldown` (null = no wait),
  `HINT_COOLDOWN` (5 s), `rememberHintCooldown` → `HintCooldown` (`press`/`gate`/`ring`) and
  `Modifier.cooldownRing` in `core:designsystem`; `PuzzleBoxNavShell.withHintCooldown` provides
  the local for every entry.
- **Editor seam** (ADR-0019) — `GamePlugin.editor` (`GameEditor`, `core:game`) + a
  `GameEditorUi` binding (`core:game-ui`, collected into `GameEditorRegistry`); the Studio
  holds an `EditorDraft` and feeds `VerdictPipeline`, so adding an editor never touches
  `feature:create`. Queens, Zip, Tango, Sudoku (Mini) and Strands ship their editors, with no release flag
  gating the New puzzle sheet. The 9×9 Sudoku editor is debug-only.
- **`DraftRepository`** (`core:data`) — the draft store over Room `drafts`; drafts persist
  as the editor's versioned blob, autosaved by `DraftAutosave`.
- **`EditorNotation` / `NotationText`** (`core:game`, 00-interfaces §2.1/§2.8) — the text
  seam: a game opts in by implementing `EditorNotation<D>` on its `GameEditor`; callers go
  only through `NotationText.parse(text, registry)` → `NotationParse` (positions whole-text,
  1-based). Two callers, one verdict (ADR-0021 §5): `ImportDraftFromText` (`core:domain`,
  behind `ImportTextState`/`ImportSheetActions` in `feature:create:impl`) and `PuzzleCli`.
  Format doc: `docs/puzzle-notation.md`, pinned by `NotationDocTest`.
- **`SharedTextStore`** (`core:data`, 00-interfaces §5.2) — in-memory handoff of shared text
  from `MainActivity` to Create's ViewModel (see Entry points); `FakeSharedTextStore` in
  `core:testing`, one `SharedTextStoreContract`.
- **Repository interfaces** (`core:data`) — `DailyRepository.today()` is network-first
  (`DailyToday.Cached` carries the fetch error), `cached()`/`observeHistory()` never touch the
  network; `CompletionRepository` stamps ADR-0004 first-win semantics via `FirstWinPolicy`
  keyed by `PuzzleRefKeys`; `UserPreferencesRepository` — `themeMode` Flow + setter.
- **`IdentityService` / `LeaderboardRepository`** (`core:data`, Firebase impl) — identity is a
  `StateFlow<Identity?>`; `submit` returns `SubmitResult`, never throws. Backend = module swap.
- **`LeaderboardSubmitWatcher`** (app) — signed in, every record converts via
  `LeaderboardEntries.fromRecord` and submits once per (uid, puzzleRef).
- **Duels** (ADR-0026) — `MatchRepository` (the one remote port, `core:data`, never throws;
  `FirestoreMatchRepository` in `backend:firebase` over `FirestoreMatchStore` and
  `MatchDocCodec`), `MatchLocalRepository` (what this device knows of a duel, Room),
  `MatchResultOutbox` + `MatchResultPoster` (pending result posts, retried at every app start
  and when `NetworkStatus` reports the network back; `AndroidNetworkStatus` in `app/match/` is
  the one Android seam) + `MatchResultSync`. `core:domain` holds the use cases and the pure
  outcome rules (`ComputeMatchOutcome`, `ComputeMatchLegs`); the outcome is derived on the
  device from sealed results. `MatchRepositoryContract` and `MatchResultsContract` in
  `core:testing` pin the fake and the Firestore implementation to one behaviour. The Games
  screen's Duels tab is handed in by the shell as a `DuelsSlot` (`feature:play:impl`), so
  play has no compete edge; `feature:compete:impl` owns the Duels tab, the new-duel sheet, the
  duel screen, the reveal and the rematch sheet, and reaches play only through `MatchLegKey`.
- **LinkedIn account reads** (ADR-0028) — ports in `core:game` (`LinkedInAccountSource`,
  `LinkedInSessionCookies`), the Voyager query shapes and parsers in `core:network`, the link
  state in `core:datastore` (`LinkedInLinkDataSource`), the cache and call budget in
  `core:database`'s `LinkedInDatabase`, and the fetch policy, ghost writer, import mapping and
  `LinkedInLink` (the one owner of link and pause state) in `core:data`'s `linkedin/`; an
  import writes a completion tagged `CompletionSource.LinkedIn`. The `FakeLinkedIn*` doubles live
  in `core:testing`. Nothing is bound in Koin or drawn in the UI yet: Settings' LinkedIn row is
  reserved by ADR-0027's owner table, and the `LinkedInAccount` flag stays off.
- **`GoogleIdTokenProvider`** (`core:auth`) — the Credential Manager half of Google sign-in;
  the app binds it through `authModule`, and `feature:profile:impl` and `feature:compete:impl`
  (the New duel sheet's sign-in step) resolve it. `IdentityService` only sees the token.
- **`GameSettingsRepository`** (`core:data`, over `GameSetting` from `core:model`) — per-game
  boolean settings; each game declares its own keys (`QueensSettingKeys`) and the app adapts
  them behind each game-ui module's `QueensSettings` / `SudokuHighlightSettings`.
- **`DispatcherProvider`** — dispatcher injection keeps callers testable.
- **Handwritten fakes** in `core:testing` (test-scope only): `FakeDailyRepository`,
  `FakeCompletionRepository`, `FakeIdentityService`, `FakeLeaderboardRepository`,
  `FakeSavedPlayRepository`, `FakeUserPreferencesRepository`, `FakeGameCalendar`, `TestGames`,
  the match fakes, `FakeNetworkStatus`, `FakeGameSettingsRepository` and the `FakeLinkedIn*` set.
- **Pure layout policies** — `homeLayoutFor`, `playLayoutFor`, `historyLayoutFor`.
- **`Migrations.ALL`** (`core:database`) — every schema change bumps the Room version (now 12),
  commits the schema JSON, and registers a migration (1→2 puzzle_dailies, 2→3 completions,
  3→4 daily_puzzles rebuild, 4→5 drops the legacy daily_puzzles, 5→6 adds saved_plays, 6→7
  adds drafts, custom_saved_plays, published_puzzles, received_puzzles, 7→8 (MIGRATION_7_8)
  adds leg_saved_plays, matches_local, match_results and completions.matchCode, 8→9
  (MIGRATION_8_9) adds completions.source, 11→12 (MIGRATION_11_12, ADR-0030) adds the side table
  `completion_replays` and nullable `match_results.replay`); no destructive fallback. `LinkedInDatabase` is a
  separate Room file at version 1 with its own schema under
  `core/database/schemas/com.ivandsky.puzzlebox.database.linkedin.LinkedInDatabase/`, so backup
  can leave it out; it holds no cookie or token column.
- **Module graph guard** — `checkModuleGraph` (ADR-0008, see Layout) fails the build on a
  forbidden edge; `feature:create:impl` reaches games only through the registries.
- **Structure rules** — `:konsist` S0–S7 / P1–P9 (see Layout); allowlists only shrink.
- **Roborazzi** — plugin `puzzlebox.android.roborazzi` (app, designsystem, home, play, create,
  compete, zip-ui, tango-ui, queens-ui, strands-ui, sudoku-ui) unifies the switch: default compares (never fails on drift, never writes goldens),
  `-Proborazzi.verify=true` fails on drift, `-Proborazzi.record=true` records.
  Committed goldens sit in each module's `snapshots/` directory: `app/snapshots/`
  (`ShowkaseBrowserScreenshotTest` and the Studio authoring shots), `core/designsystem/snapshots/`
  (`SpotlightOverlayGoldenTest` and the cooldown ring), `feature/compete/impl/snapshots/`
  (`DuelsTabGoldenScreenshots`, `RevealGoldenScreenshots`), `feature/create/impl/snapshots/`,
  `feature/home/impl/snapshots/`, `feature/play/impl/snapshots/`, `games/queens-ui/snapshots/`
  (`QueensGoldenScreenshots`), `games/strands-ui/snapshots/` (`StrandsGoldenScreenshots`, the
  real 2026-11-06 NYT day through the production source), `games/sudoku-ui/snapshots/`
  (`SudokuGoldenScreenshots`), `games/zip-ui/snapshots/` and
  `games/tango-ui/src/test/resources/goldens/` (`TangoBoardGoldenTest`);
  zip-ui's `VariantMatrixScreenshotTest` writes review evidence to `.scratch`, not goldens.

## Working test commands

Counts are `@Test` annotation counts over each module's `src/test*/` trees at the pin
(5964 total excl. `:konsist`) — not executions. Robolectric where device APIs are touched; `:app` needs `ANDROID_HOME`
or `local.properties` (the SDK under the user's `Library` folder here); Gradle toolchain pinned to JVM 25
via `gradle/gradle-daemon-jvm.properties`. Android modules run `testDebugUnitTest`, pure-JVM
modules run `test`. The merge gate (ADR-0023) is

`./gradlew assembleDebug test`

which runs every module's unit tests, `:core:auth` and `:feature:compete:impl` included. A
narrower run: `./gradlew assembleDebug testDebugUnitTest :core:game:test :games:zip:test
:games:queens:test :games:tango:test :games:strands:test :games:sudoku:test :core:model:test
:core:network:test :tools:puzzle-cli:test -Proborazzi.verify=true --continue`.

Structure rules (ADR-0009): `./gradlew :konsist:test` — runs the 16 in-module rules (S0–S7 without S3, P1–P9; 130 tests with the
census and the engine tests), over the whole repo's sources (not in the count above; reruns
when any source, build script or allowlist changes).

- `:app:testDebugUnitTest` — 384 (DI graph, nav routes, saved-state restore, per-tab shell,
  the placement table and the shell: `nav/PlacementTest`, `nav/ShellChromeTest`,
  `nav/OpenOwnerTest`, `nav/LibraryRequestsTest`, `nav/TopLevelNavBarTest`,
  `nav/TabSetWidthChangeTest`; E2E robot rows incl. `e2e/ShellTabsE2ETest`,
  `e2e/ShellSummariesE2ETest`, `e2e/TabSetRestoreE2ETest`, `e2e/TabReselectE2ETest`,
  `e2e/ReachabilityInventoryE2ETest`, the duel flows `e2e/DuelFlowE2ETest` and
  `e2e/DuelLinkE2ETest`, and `e2e/LinkedInArchiveE2ETest`; submit watcher, share target,
  browser screenshots)
- `:backend:firebase:testDebugUnitTest` — 109
- `:core:auth:testDebugUnitTest` — 7
- `:core:common:testDebugUnitTest` — 11
- `:core:data:testDebugUnitTest` — 261
- `:core:database:testDebugUnitTest` — 33 (DAOs + migrations through 8→9; the LinkedIn database)
- `:core:datastore:testDebugUnitTest` — 12
- `:core:designsystem:testDebugUnitTest` — 139
- `:core:domain:testDebugUnitTest` — 352
- `:core:game:test` — 87
- `:core:game-ui:testDebugUnitTest` — 306
- `:core:navigation:testDebugUnitTest` — 29
- `:core:network:test` — 54
- `:core:model:test` — 10
- `:core:testing`, `:feature:*:api` — 0
- `:feature:home:impl:testDebugUnitTest` — 80
- `:feature:history:impl:testDebugUnitTest` — 53
- `:feature:play:impl:testDebugUnitTest` — 553
- `:feature:profile:impl:testDebugUnitTest` — 53
- `:feature:create:impl:testDebugUnitTest` — 395
- `:feature:compete:impl:testDebugUnitTest` — 184
- `:games:zip:test` — 292; `:games:zip-ui:testDebugUnitTest` — 186
- `:games:tango:test` — 375; `:games:tango-ui:testDebugUnitTest` — 235
- `:games:queens:test` — 454; `:games:queens-ui:testDebugUnitTest` — 266
- `:games:sudoku:test` — 265; `:games:sudoku-ui:testDebugUnitTest` — 287
- `:games:strands:test` — 222; `:games:strands-ui:testDebugUnitTest` — 356
- `:tools:puzzle-cli:test` — 25 (CLI contract, per-game CLI↔app parity, `NotationDocTest`; the
  fixtures live under each game's `src/test/resources/notation/`)
- `node --test backend/firebase/hosting-test/` — share-code normalizer; needs Node, not
  Gradle, not in the count above
- `./gradlew checkModuleGraph` — forbidden-edge guard (also run by `check`)

## Diagram

[docs/agents/project-map.html](project-map.html) — module-graph diagram regenerated from
`.archify/project-map/architecture-candidate.json` at `89c0672` (pre-0.9): the modules and
edges listed under Layout, with the forbidden-edge rules of ADR-0008. It predates the
per-tab navigation seams, the four-tab shell, `:core:auth`, `:feature:compete:*`, the LinkedIn
account reads and the Strands modules; this file is authoritative where they differ. The
Strands addition (app → features → `:games:strands-ui` → `:games:strands` →
`:core:game`, `NytHttpTransport`, by-date `DailyRepository`, trust gate, `CellState` specs) has
its own diagram in the gitignored `.scratch` directory (not on main).

## Drift rule

If `git rev-parse HEAD` differs from `pinned:`, run `git diff --stat <pin>..HEAD` and
refresh only the sections the touched paths hit (layout, entry points, seams, tests), then
bump the pin — never edit the map in conversation.
