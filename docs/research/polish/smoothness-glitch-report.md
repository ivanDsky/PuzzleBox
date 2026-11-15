# PuzzleBox 0.32.0 — Smoothness and glitchiness audit (static)

Scope: static code and golden reading only. No device, no Gradle, no profiler. Every "symptom" below is inferred from code, git history, and a handful of Roborazzi goldens (`feature/home/impl/snapshots/home-streak-freeze-phone360-light.png`, `feature/history/impl/snapshots/ranks/overall-phone-light.png`, `feature/play/impl/snapshots/tabs/tabs-dailies-phone-light.png`). Goldens may be stale. Anything marked "likely" or "speculative" must be confirmed on the Tab S9 Ultra with the protocol in section 5 before it is treated as real.

Confidence key used per finding:
- **confirmed-by-code**: the mechanism is plainly in the source; only its size on-device is unknown.
- **likely**: mechanism present, user-visible effect depends on timing or data size.
- **speculative**: pattern known to cause trouble, no evidence here that it does.

---

## 1. Summary

**Overall smoothness/glitch risk: MEDIUM.**

The motion layer is unusually careful. Animated values are mostly read in the draw or layout phase through lambdas, lists are keyed, work goes to injected dispatchers, and git history shows several rounds of deliberate anti-flicker work on the Ranks hub, day cards and game settle. The risks that remain are concentrated in three places:

1. Cold start has no baseline profile and a long chain of main-thread work before the first frame.
2. State holders re-seed themselves with "empty" values after the 5 s `WhileSubscribed` grace, so screens can blink to a skeleton or zero state on return.
3. A few shared building blocks (`MorphCell`, the 100-row animated board, the Zip cell grid) do more per-frame or per-move work than their neighbours, and they sit on the hottest screens.

**Top 5**

| # | Finding | Severity |
|---|---|---|
| 1 | S-01 No baseline profile, no profileinstaller, no benchmark module: every cold start and first scroll runs JIT/interpreted code | High |
| 2 | S-06 / S-07 Home and Library re-seed "empty" after the 5 s grace, so Today card, rank and best scores blink out and back (confirmed in the code, the Home code comment admits the rank blip) | Med |
| 3 | S-09 `MorphCell` reads animated alpha in composition and changes blur radius every frame; it is used by 17 call sites across Ranks, the Archive day card and LinkedIn chip | Med |
| 4 | S-02/S-03/S-04/S-05 Cold-start chain: heavy `Application.onCreate`, blank composition until DataStore theme arrives, window background that ignores the in-app theme, chrome padding that starts at 0 and jumps | Med |
| 5 | S-18 Ranks board draws up to 100 rows eagerly in a non-lazy scroll, each with three `Animatable`s, a `LaunchedEffect` and clipping | Med |

---

## 2. Findings

### 2.1 Table

| ID | Sev | Category | Where | Symptom | Confidence |
|---|---|---|---|---|---|
| S-01 | High | startup | `app/build.gradle.kts:54-60` | Slow cold start, janky first scroll and first game open | confirmed-by-code (absence); likely (impact) |
| S-02 | Med | startup | `app/src/main/kotlin/com/ivandsky/puzzlebox/app/PuzzleBoxApplication.kt:48-67` | Long time to first frame | likely |
| S-03 | Med | startup/glitch | `app/src/main/kotlin/com/ivandsky/puzzlebox/app/MainActivity.kt:63-70` | Blank window before first content | confirmed-by-code |
| S-04 | Med | glitch | `app/src/main/res/values/themes.xml`, `values/colors.xml`, `values-night/colors.xml` | Wrong-colour flash at launch when in-app theme differs from system | likely |
| S-05 | Med | glitch | `app/src/main/kotlin/com/ivandsky/puzzlebox/app/PuzzleBoxApp.kt:168-169,185,202,205` | Content shifts when bottom bar / rail first measures | likely |
| S-06 | Med | glitch | `feature/home/impl/src/main/kotlin/com/ivandsky/puzzlebox/home/HomeViewModel.kt:155,161,186,220-229` | Today card, rank and best scores flip to skeleton/blank after returning from a game | confirmed-by-code |
| S-07 | Med | glitch | `feature/play/impl/src/main/kotlin/com/ivandsky/puzzlebox/play/LibraryViewModel.kt:159,175-176,192-196` | Library rows lose stats/progress/best for a moment on return | confirmed-by-code |
| S-08 | Med | glitch | `HomeViewModel.kt:221-232`, `HomeStreakSection.kt:75`, `HomeRecentSection.kt:65` | Cold Home shows "0 days in a row · Best 0" and "No games yet" before real data | confirmed-by-code |
| S-09 | Med | jank | `core/designsystem/src/main/kotlin/com/ivandsky/puzzlebox/designsystem/MorphCell.kt:62-71` | Frame drops during day/board text morphs | likely |
| S-10 | Med | jank | `games/zip-ui/src/main/kotlin/com/ivandsky/puzzlebox/games/zipui/ZipPlayUi.kt:174-208`, `ZipBoardCells.kt:59-179` | Drag trace on a big Zip board may stutter | likely |
| S-11 | Low | jank | `games/queens-ui/.../QueensBoard.kt:75`, `QueensMotionState.kt:39`, `QueensPlayController.kt` (violation getter) | Queens verdict recomputed several times per tap | likely |
| S-12 | Med | glitch | `feature/play/impl/.../ReplayRoute.kt:29`, `ReplayCompareRoute.kt:66` | Replay screens are empty (no back bar) while loading | confirmed-by-code |
| S-13 | Low | jank | `core/game-ui/.../PlayChrome.kt` (`CLOCK_TICK_MS = 200`), `TimerBar.kt:30-61` | Timer bar recomposes 5×/s to show 1×/s digits | confirmed-by-code |
| S-14 | Low | consistency | `core/game-ui/.../TutorialHost.kt:73`, `replay/ReplayStableState.kt:20`, `replay/ReplayTransport.kt:93`, every `*Editor*.kt` board | Non-lifecycle `collectAsState()` keeps collecting while stopped | confirmed-by-code |
| S-15 | Low | consistency | `QueensBoard.kt:104,121,129`; no haptics in `games/zip-ui`, `games/tango-ui` | Some games buzz on input, others do not | confirmed-by-code |
| S-16 | Low | jank | `core/designsystem/.../ReducedMotion.kt:24-45` | One ContentObserver (a binder call) per call site; 113 sites | likely |
| S-17 | Low | glitch | `feature/history/impl/.../ranks/RanksHeldBoard.kt:372,405-408`, `RanksViewModel.kt:88-99,156,170-174` | Ranks falls back to Loading after 1.5 s; first board waits on a DataStore read | confirmed-by-code |
| S-18 | Med | jank | `core/game-ui/.../board/AnimatedBoardList.kt:55-102`, `BoardRows.kt`, `RanksScreen.kt:104` | Global board (100 rows) may drop frames on first load/re-rank | likely |
| S-19 | Low | glitch | `app/src/main/kotlin/com/ivandsky/puzzlebox/app/nav/PuzzleBoxNavShell.kt:102-127` | Transition-spec lambdas mutate state | speculative |
| S-20 | Low | jank | `build-logic/convention/src/main/kotlin/com/ivandsky/puzzlebox/AndroidCompose.kt` | No Compose stability config or compiler reports | speculative |
| S-21 | Low | glitch | `feature/play/impl/.../GameViewModel.kt:55-66`, `GameRoute.kt:173` | One Loading beat before a game with lessons opens | likely |
| S-22 | Low | jank | `feature/home/impl/.../HomeRoute.kt:36-39` | Network sync and update check on every resume; update pill grows in after first frame | confirmed-by-code |
| S-23 | Low | consistency | golden `feature/history/impl/snapshots/ranks/overall-phone-light.png` | World shape shows through a translucent row | speculative |

### 2.2 Details

#### S-01 No baseline profile (High, startup)

- **Evidence.** Release build is minified and shrunk (`app/build.gradle.kts:54-60`, `proguard-android-optimize.txt` plus `app/proguard-rules.pro`). There is no baseline-profile plugin, no `androidx.profileinstaller` dependency, no macrobenchmark module. A search of the Gradle files, version catalog and manifest finds none of them.
- **Symptom.** On a fresh install or after an app update the first launch, first scroll in Ranks or Library, and first board open run interpreted/JIT code. Compose is especially sensitive to this, so the first animation after install is the one that stutters.
- **Mechanism.** Without a profile, ART compiles nothing ahead of time for the app's hot paths. Compose runtime and Material3 get some coverage from library-shipped profiles only if `profileinstaller` is present.
- **Fix sketch.** Add `androidx.profileinstaller` and a `:baselineprofile` module (Baseline Profile Gradle plugin) that scripts: cold start to Home, open each game, Ranks tab and tab switches, Create. Ship the generated `baseline-prof.txt` in `app/src/main`. Add a macrobenchmark `StartupBenchmark` for TTID/TTFD with `CompilationMode.None` vs `Partial`.
- **Confidence.** confirmed-by-code (absence); impact likely, measure with section 5.

#### S-02 Heavy `Application.onCreate` (Med, startup)

- **Evidence.** `PuzzleBoxApplication.kt:48-67`: `startKoin` with about 25 modules; `runBlocking { koin.get<PendingCacheClear>().run() }` on line 54; `prepareCloudSync(koin)` line 56; then five `koin.get<…>().start()` calls (LeaderboardSubmitWatcher, CustomBoardSubmitter, MatchResultPoster, DailyReplayPublisher, CloudSyncRunner), lines 59-67. This is the only `runBlocking` in non-test code.
- **Mechanism.** All of it runs on the main thread before the first activity. `PendingCacheClear` reads SharedPreferences (disk on first access). Each `get<>()` resolves a dependency graph, which instantiates Room databases' builders, DataStore, Firebase/Firestore clients and repositories. Room DBs themselves are lazy (`core/database/.../di/DatabaseModule.kt`) and DataStore runs on an IO scope (`core/datastore/.../DatastoreModule.kt`), which is good; the graph construction is still on Main. FirebaseInitProvider also auto-initialises before `onCreate`.
- **Symptom.** Longer black/blank gap before first draw, mostly on cold start of a cold device.
- **Fix sketch.** Keep `startKoin` and the cache-clear mark check, but move the five `.start()` calls and `prepareCloudSync` off the critical path: launch them from an application-scope coroutine on `Dispatchers.Default` after the first frame (for example from `MainActivity`'s first `withFrameNanos`), or use `androidx.startup` with lazy initialisers. Measure with Perfetto slices `bindApplication` and `activityStart` first; if the chain is under ~100 ms this drops to Low.
- **Confidence.** likely.

#### S-03 Composition is held blank until DataStore answers (Med, startup/glitch)

- **Evidence.** `MainActivity.kt:63-70`: `val themeMode by preferences.themeMode.collectAsState(initial = null)` then `themeMode?.let { … }`. Nothing is composed until the theme preference arrives. No splash-screen API (`installSplashScreen`) is used anywhere.
- **Symptom.** The window shows only its background between process start and first composition. The intent (comment at line 64-65) is to avoid a light flash for a stored dark theme; the cost is that first content waits on a disk read.
- **Mechanism.** `collectAsState` (not lifecycle aware, S-14) starts with null; DataStore's first read happens on its IO scope; the first content frame cannot be earlier than that read plus one composition of the whole app.
- **Fix sketch.** Read the theme synchronously once before `setContent` (a tiny SharedPreferences mirror written whenever the DataStore value changes, or `runBlocking` on a cached read with a strict timeout), or adopt the AndroidX SplashScreen API and `setKeepOnScreenCondition { themeMode == null }` so the held period is a styled splash instead of a bare window.
- **Confidence.** confirmed-by-code.

#### S-04 Launch window background ignores the in-app theme (Med, glitch)

- **Evidence.** Theme `Theme.PuzzleBox` (`app/src/main/res/values/themes.xml`) has parent `android:Theme.Material.Light.NoActionBar` and `windowBackground=@color/puzzlebox_launch_background`: `#F2F4F7` in `values/colors.xml`, `#101418` in `values-night/colors.xml`. Night resources follow the system dark mode, not the in-app theme override.
- **Symptom.** A player whose in-app theme differs from the system theme sees the wrong background colour during the S-03 hold, then a hard colour switch when composition starts. With the system in light mode and the app forced dark, that is a light flash on every cold start.
- **Fix sketch.** Same as S-03: a splash with a neutral colour that works in both themes, or choose the activity's night mode from the stored preference with `AppCompatDelegate.setDefaultNightMode` before `super.onCreate`.
- **Confidence.** likely (depends on how many players force a theme).

#### S-05 Chrome padding starts at 0 and jumps (Med, glitch)

- **Evidence.** `PuzzleBoxApp.kt:168-169`: `barHeight` and `railWidth` start at `0.dp`; they are filled by `onSizeChanged` at lines 185 and 202; `chromePadding(expanded, barHeight, railWidth)` at 205 feeds `LocalChromePadding` (line 207).
- **Symptom.** In the first frame the content has no bottom/start padding; one frame later it is pushed by the bar height or rail width. On cold start and on rotation or fold changes the content visibly shifts once.
- **Mechanism.** Sizes arrive after the first measure. Because they are composition state, the write recomposes `PuzzleBoxApp` and the nav shell, and a `compositionLocalOf` change invalidates every reader (all scenes).
- **Fix sketch.** Use a layout-phase approach: measure the bar in a `SubcomposeLayout` or a custom `Layout` that places the bar and passes its height to the content in the same pass, or provide `LocalChromePadding` through a `State` read only in the layout/`Modifier.layout` of consumers. A cheap stopgap is a stable default (the known Material bar height) instead of 0.
- **Confidence.** likely. Check with a 120 fps screenrecord of cold start.

#### S-06 Home re-seeds "empty" after the 5 s grace (Med, glitch)

- **Evidence.** `HomeViewModel.kt:155` (`.onStart { emit(emptyMap()) }` for bests), `:161` (`todaySet().onStart { emit(null) }`), `:186` (`.onStart { emit(null) }` for rank), `:220` (`SharingStarted.WhileSubscribed(5_000)`), `:221-232` (seed state). The comment at `HomeRoute.kt:34-35` admits the rank blip.
- **Mechanism.** After the last subscriber leaves, `stateIn` keeps its last value for 5 s. If the player is away longer (any game, the result page, another tab), the upstream restarts and each source first emits its seed. The first combined emission therefore has `today = null`, empty bests and null rank, which overwrites the good last value held by the `StateFlow`.
- **Symptom.** Returning to Home after more than 5 s: the Today card's status line goes to `SkeletonLine` placeholders (`HomeTodaySummary.kt:104,127-128`), the "Today's rank" row and Best scores disappear, then everything lands again. The window is one Room read long, probably a frame or a few, but it is visible as a flicker on the most visited screen.
- **Fix sketch.** Do not seed with `onStart`. Let the combined flow wait for real values (`stateIn` initial value already covers the cold case), or keep the last non-null value with `scan`. Alternatively `SharingStarted.WhileSubscribed(5_000, replayExpirationMillis = Long.MAX_VALUE)` and drop the seeds for everything except the very first subscription.
- **Confidence.** confirmed-by-code. Verify by returning from a game after about 10 s with a 120 fps recording.

#### S-07 Library repeats the same pattern (Med, glitch)

- **Evidence.** `LibraryViewModel.kt:159` (`numbers` seeded empty), `:175-176` (`allStats.onStart { emit(emptyMap()) }`, `allProgress.onStart { emit(emptyMap()) }`), `:192-196` (`WhileSubscribed(STOP_TIMEOUT_MS)` with a `seed` initial state). `linkedInView` uses `WhileSubscribed(0)` at `:207`, so it restarts on every re-entry by design.
- **Symptom.** After a 5 s absence the rows' best time, puzzle number and archive progress fall back to the seed and then return. `stats`/`progress` drive the progress bar and "531 of 598 solved" text seen in golden `feature/play/impl/snapshots/tabs/tabs-dailies-phone-light.png`.
- **Fix sketch.** As S-06.
- **Confidence.** confirmed-by-code.

#### S-08 Cold Home shows wrong data first (Med, glitch)

- **Evidence.** Initial state at `HomeViewModel.kt:221-232` uses `stats = NO_STATS` (line 275) and `history = emptyList()`. `HomeStreakSection.kt:75` formats "`N days in a row · Best M`" straight from `stats`; `HomeRecentSection.kt:65-66` shows `EmptyState(title = "No games yet")` for an empty history.
- **Symptom.** Right after install-launch or process death the streak card reads "0 days in a row · Best 0" and Recent says "No games yet", then both swap to real content. The recent section changes height, pushing content below it. `staggerIn` fades (`StaggerIn.kt`) soften but do not hide it.
- **Fix sketch.** Add a `loading`/`loaded` flag to `HomeUiState` (Create already does: `CreateUiState.loading`, `CreateUiState.kt:20`) and render skeletons of the final height until stats and history have arrived.
- **Confidence.** confirmed-by-code.

#### S-09 `MorphCell` reads animated alpha in composition (Med, jank)

- **Evidence.** `MorphCell.kt:62-71`: `val alpha = layer.alpha.value` is read in composition, then used in `.blur(MotionTokens.DayMorphBlur * (1f - alpha), BlurredEdgeTreatment.Unbounded)` and in `.semantics { this[MorphAlpha] = alpha }`. The same file's `graphicsLayer { this.alpha = layer.alpha.value }` at line 69 is correctly deferred. 17 call sites: `RanksBoardMorph.kt:77`, `RanksBoardSlot.kt:65`, `RanksHeroCard.kt:105`, `RollingLine.kt:63,90`, `YourSolveRow.kt:74,134`, `ArchiveDayCard.kt:118,129,151,176,178,243,257`, `LinkedInSyncChip.kt:95`, `DailyBoard.kt:88`.
- **Mechanism.** Every animation frame (180 ms out, 280 ms in, 30 ms delay) recomposes `MorphCell` and re-invokes the `content` lambda for every layer, and builds a new blur `RenderEffect` because the radius changes continuously. The sibling `Frosted.kt:10-31` documents the better design: 8 pre-built blur steps, opacity read in the graphics layer. `ScrubCell` already uses it.
- **Symptom.** A day switch on the Archive card or a board change on Ranks triggers several simultaneous morphs; each costs a recomposition plus a RenderEffect allocation per frame. This is the first place to look if the day card stutters.
- **Fix sketch.** Reimplement `MorphCell` on top of `Modifier.frosted { layer.alpha.value }` (the same lambda read, 9 cached effects) and move the semantics read into a lambda.
- **Confidence.** likely (mechanism confirmed; cost needs a trace).

#### S-10 Zip drag recomposes every cell (Med, jank)

- **Evidence.** `ZipPlayUi.kt:174-208`: the model is remembered on `controller.session`, so it is new after every committed step. It feeds `DragTraceBoard(path = model.path.map { GridCell(…) }, edgeOverlays = zipWallOverlays(model), …)` and `ZipCellDecor(model, row, col, …)` for every cell (lines 202, 205, 207-209). `ZipBoardCells.kt:59-179` gives every cell an `animateFloatAsState`, two `Animatable`s, two `LaunchedEffect`s, and a `BoxWithConstraints` (a subcomposition) for each labelled cell.
- **Mechanism.** Since the model instance changes, none of the n×n cells can skip. A 7×7 or 8×8 board does 49-64 cell recompositions plus list allocations per drag step. The finger tip itself is correctly deferred: `finger` is read inside `ZipPipes`' `Canvas` (line 206 and `ZipBoardCells.kt:200-206`), so only committed steps pay.
- **Symptom.** On slow devices a fast drag across a big board can drop frames at each cell crossing. On the Tab S9 Ultra this may be invisible; check in the Zip flow.
- **Fix sketch.** Pass per-cell immutable render data (`model.cells[idx]`, `hint`, `rejected`) so only changed cells recompose, replace `BoxWithConstraints` with the board's known pitch, and hoist the `path.map`/overlays into `remember(model.path)`.
- **Confidence.** likely.

#### S-11 Queens recomputes the verdict repeatedly (Low, jank)

- `QueensBoard.kt:75` calls `QueensBoardModel.of(state.session, state.guide)` in composition without `remember`. The same model is rebuilt in the motion effect (`QueensMotionState.kt:39`) and in the controller's `violation` getter. Each runs the rules verdict (`QueensBoardModel.kt:51-53`). Cheap per call on 8×8, but three calls per tap on the input path. Fix: compute once per session change and share. Confidence: likely.

#### S-12 Replay routes are blank while loading (Med, glitch)

- **Evidence.** `ReplayRoute.kt:29` (`ReplayState.Loading -> Unit`) and `ReplayCompareRoute.kt:66`. The state is loaded in `ReplayViewModel`'s `init` after a DataStore read and `loadReplay` on the default dispatcher.
- **Symptom.** The screen slides in as an empty box, no back bar, no skeleton; the page pops in when the timeline arrives. Back is only possible via gesture.
- **Fix sketch.** Draw the back bar and a skeleton of the board and transport in `Loading`, as `GameLoading` does for games (`GameRoute.kt:228-233`).
- **Confidence.** confirmed-by-code.

#### S-13 Timer ticks 5× per second (Low, jank)

- `PlayChrome.kt` ticks every `CLOCK_TICK_MS = 200`; each controller writes a `mutableStateOf` (`nowMs`, e.g. `ZipPlayController.kt:67,213`). `GameRoute.kt:159` reads it inside the top bar's `actions` lambda, which is its own restart scope, so only the bar recomposes (good). The text changes once per second. Fix: tick at 1 s aligned to the window start, or derive the second in `derivedStateOf`. Confidence: confirmed-by-code, impact negligible alone.

#### S-14 Non-lifecycle collection (Low, consistency)

- 56 uses of `collectAsState()` against 107 `collectAsStateWithLifecycle()`. Remaining non-lifecycle sites: `MainActivity.kt:66`, `TutorialHost.kt:73`, `replay/ReplayStableState.kt:20`, `replay/ReplayTransport.kt:93`, and all editor boards/tools (`StrandsEditorBoard.kt:97-122`, `TangoEditorBoard.kt:58-62`, `ZipEditorBoard.kt:67-135`, `QueensEditorBoard.kt:88-91`, `SudokuEditorBoard.kt:65-70`). They keep recomposing while STOPPED. Fix: switch to the lifecycle variant everywhere. Confidence: confirmed-by-code.

#### S-15 Haptics are inconsistent (Low, consistency)

- Haptic feedback exists in Queens (`QueensBoard.kt:104,121,129`), Strands (`StrandsHaptics.kt`), Sudoku (`SudokuHaptics.kt`), `core/designsystem/.../PuzzleBoard.kt:75-124`, Reveal and verdict pills. Zip (`ZipPlayUi.kt:198-220`, `DragTrace.kt:106-153`) and Tango (`TangoPlayController.kt`, `TangoBoardRenderer.kt`) have none, though Zip's drag is the closest analogue to Queens' haptic drag. A player moving between games feels the difference. Fix: a shared haptic policy in `core/game-ui`. Confidence: confirmed-by-code.

#### S-16 One `ContentObserver` per `rememberReducedMotion()` (Low, jank)

- `ReducedMotion.kt:24-45` registers a `ContentObserver` per call (a binder call) and reads `Settings.Global` twice. 113 call sites, including every `MorphCell` instance (`MorphCell.kt:52`), `DailyBoard.kt:56,123`, `DailyBoardList.kt:70`, `DockedSelfRow.kt:86,129`. Row-level code does not call it (checked `DailyBoardRow`, `RankCell`, `RollingScore`). A screen with a day card and a board registers a few dozen observers on first composition. Fix: provide it once through a `CompositionLocal` from the root (`PuzzleBoxTheme`). Confidence: likely; confirm by counting binder transactions in a Perfetto trace.

#### S-17 Ranks hold cap and first-board wait (Low, glitch)

- `RanksHeldBoard.kt:372` (`BOARD_HOLD_CAP_MS = 1_500`), `:405-408`: after 1.5 s without an answer the old board is dropped and "Loading" shows. This is deliberate and good for fast answers, but on a slow network a day switch ends in a visible flash to Loading. `RanksViewModel.kt:88-99,156`: a fresh hub does not emit boards until the stored default scope is read, and `uiState` starts from `overallSlice(…Loading…)` (`:170-174`), so the first frame is a Loading Overall board even if the stored default is Friends. Fix: preload the scope in `RanksLanding` at app start. Confidence: confirmed-by-code.

#### S-18 100 animated rows in a non-lazy scroll (Med, jank)

- **Evidence.** `BoardPage.PAGE_SIZE = 100` (`core/data/.../leaderboard/BoardPage.kt:10`). `RanksScreen.kt:104` hosts the board in `verticalScroll`, so all rows are composed. `AnimatedBoardList.kt:55-102` composes every item with `key(item.key)`, three `Animatable`s per item (`BoardRows.kt:34-36`), a `LaunchedEffect(present)` (line 96), `clipToBounds` per row, and a custom `Layout` that measures every child every pass (lines 65-67). `BoardRows.update` (`:105-128`) rebuilds maps and `HashSet`s on each recomposition. `placeAt` (`:71`) launches coroutines from the placement block.
- **Mechanism.** A first load or a re-rank staggers up to `StaggerCap = 8` rows (`BoardMotion.kt:20,47`), but all 100 are composed and measured at once, and each pass creates 300 `Animatable`s.
- **Symptom.** Global board, week/month/overall: a hitch on first load or on scope change (Friends to Global), mostly in a debug build.
- **Fix sketch.** Only animate rows within the viewport plus a buffer, or use `LazyColumn` with `animateItem` for rows beyond the first ~20; create the `Animatable`s lazily on first move.
- **Confidence.** likely.

#### S-19 State mutation inside transition specs (Low, glitch)

- `PuzzleBoxNavShell.kt:102-127`: `play(chosen)` assigns `motion.value` and `lift.*` from inside `NavDisplay`'s transition-spec lambdas. Specs may be evaluated more than once; the writes are idempotent here, but it is a side effect in a spec. Predictive-back scrub is linear 280 ms. Confidence: speculative.

#### S-20 No Compose stability config or reports (Low, jank)

- `AndroidCompose.kt` only applies the plugin and BOM. Kotlin 2.2.10 enables strong skipping, so unstable parameters are not a blocker, but `List`-typed UI models (`RanksUiState.boards`, `HomeUiState`) are compared by identity. Enable compiler metrics once (`-P plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=…`) and check restartable-but-not-skippable composables on Ranks and Home. Confidence: speculative.

#### S-21 One Loading beat before a game opens (Low, glitch)

- `GameViewModel.kt:55-66`: for games with lessons the gate starts `Checking` and waits for `prefs.tutorialSeen(...).first()`; `GameRoute.kt:173` draws `PlayBoardFallback(Loading)` meanwhile. A DataStore read usually returns within a frame or two, then the board loads from `ActivePlay.load` (`ActivePlay.kt:116-135`, Room/cache on a coroutine), so a game open can show two sequential loading states. Fix: read the tutorial flag in the same step as the slot open. Confidence: likely.

#### S-22 Work on every Home resume (Low, jank)

- `HomeRoute.kt:36` writes `setSummariesShown(true)` from a `SideEffect` on every composition; `:37-39` runs `syncToday()` and `checkForUpdate()` on each resume. The update pill uses `expandVertically` (`HomeListPane.kt`), so nothing jumps; the cost is network on every tab return. Confidence: confirmed-by-code.

#### S-23 World shape shows through a row (Low, consistency)

- Golden `feature/history/impl/snapshots/ranks/overall-phone-light.png`: the large grey ring from `RanksWorld` crosses behind the last row ("Sam") and its note area, visible through the rows' deliberately translucent fill (`WORLD_SURFACE_ALPHA`, `RanksBoardSlot.kt:27,55`). Possibly intended. Rows 1-4 look opaque because of their tint; the plain white fifth row looks smoky. Confidence: speculative (the golden may be stale).

---

## 3. Recurring hotspots from git history

1349 commits. A search of messages for flicker, jank, glitch, flash, jump, stutter, smooth and related words (ignoring "flag" noise) points at the same five areas again and again. Fix there often has not been final.

| Area | Commits | What they say |
|---|---|---|
| Ranks hub | `0f481e59c` (F22 F23 one-height rows, LinkedIn pill follows list), `04a46a842` (F15-F17 swipe/refresh), `a6cc2fa95` (F3 F9 F2 user-check round 1), `3a8cd28f3` (leaderboard rows animate when they move, appear, leave), `f37a5c7a9` (selected game outline stays whole while row fades in) | Four user-check rounds. Any change to the board, held board or morph deserves a Ranks scroll and swipe pass. |
| Day card / LinkedIn morph | `47fde70ca` (text morphs in place, badge held across day switch), `ec41e0de0` (day chip state from first frame after a day switch) | The `MorphCell` family (S-09). |
| Replay animation | `4ebb3ee4e` (seek never animates, narrow frame-clock read), `64d33c146` (skip idle waits), `3d1558dfd` (saved board animates like live) | Per-frame cost matters here; S-12 sits on this path. |
| Game-board settle and debounce | Tango `b60cd59b3`, `f5b370e6f`, `2dadf144c` (runningSince as Compose state); Queens `9e6556f68`, `35acd2010`, `78b8a2929` (hold last verdict while Checking); `98fd912e4` (elapsed clamped ≥ 0 on first frame); `0c9aa2e3b` (Strands trace recomposes only changed letters) | Settle is `MotionTokens.SettleMillis = 500` ms (`Motion.kt:196`); verdicts and clock first-frame values were bug sources. |
| Navigation | `15ff45a0a` (one game-page enter beat), `4534c6645` (faster back, 180 ms), `61e1d954b` (phone full-screen slides, tablet card lift), `cf7746afd` (polish baseline: cones, scrim clock, tablet panel, touch rests settle), `c0e32bc0e` (GameButton enable fade, hoisted springs) | Transition tuning is still moving; S-05 and S-19 live in the same shell. |
| Studio keyboard | `c22527bd6` (IME never hides board/field), `8a0e4acaf` (phone Studio Strands board height) | `editor/StudioImeInsets.kt:91`. No finding from reading, but worth an IME open/close recording. |

---

## 4. What is already done well

- **Phase discipline in motion code.** Animated values are mostly read in layout or draw lambdas: `NavSceneCanvas.kt` scrim in `drawBehind` (line 65-69); Ranks `BoardScrub`/`MorphCell`/`ScrubCell` take `progress: () -> Float` and read it in `layout`/`graphicsLayer` (`RanksBoardSlot.kt:349-358`, `Frosted.kt:18-31`); `AnimatedBoardList` slides rows with a layer offset so "the rows' content is not recomposed" (`AnimatedBoardList.kt:35-37`, `BoardRows.kt:17-18`); Zip finger tip read only in the Canvas (`ZipPlayUi.kt:195-196`).
- **Deliberate anti-flicker design.** Held boards while the next loads (`RanksHeldBoard.kt`), loading states that keep final shape (`PlayStatsPanel.kt:49,132`), the Home update pill using `expandVertically + fadeIn` (`HomeListPane.kt:61-70`), CTA and countdown sharing a height (`HomeTodaySection.kt:131`), `CreateUiState.loading` (`CreateUiState.kt:20`).
- **Scoped timers.** `RolloverCountdown` ticks every second from `repeatOnLifecycle(STARTED)` into pill-local state; play clock reads sit in their own restart scope; `PlayClockEffects` folds on pause.
- **Threading.** One `DispatcherProvider` used consistently: solvers, verdicts, imports, hint search, forced layers and replay open on `dispatchers.default` (`DraftAnalysis.kt:59`, `VerdictPipeline.kt:52`, `LoadSolveReplay.kt:36`, `SudokuForcedLayer.kt:48`, `TangoForcedLayer.kt:36`); repository I/O on `dispatchers.io` (`OfflineFirstDailyRepository.kt:42,78,108`); Room has no `allowMainThreadQueries`; DataStore on an IO scope; Strands dictionary loads lazily off-main.
- **Lists.** Every `LazyColumn`/pager found has `key=` (`DuelsTab.kt:120`, `CreateLists.kt:88,110,165`, `FromFriendsRow.kt:86`, `HistoryCalendar.kt:259`, `ReplayFlipPeek.kt:129`).
- **Reduced motion and tokens.** All motion is token-driven (`Motion.kt`), has a reduced-motion fallback, and the nav shell supports predictive back.
- **Release build hygiene.** `isMinifyEnabled` and `isShrinkResources` are on for release (`app/build.gradle.kts:54-60`).
- **`staggerIn` entrances** use `rememberSaveable appeared`, so they do not replay after rotation or process-safe restores (`StaggerIn.kt:175-194`).

---

## 5. Device measurement protocol (Samsung Tab S9 Ultra, `com.ivandsky.puzzlebox.debug`)

Purpose: confirm or reject the "likely" findings, and get a baseline for the polish phase. Keep every run the same: same device, same account, same data set, airplane mode on for offline runs and off for network runs, battery above 50 %, screen at 120 Hz.

### 5.1 Build to measure

A debug build is much slower than release (no R8, debuggable, Compose runtime checks, no AOT), so debug numbers only rank hotspots, they are not pass/fail. For pass/fail, use a release-like build:

1. Create a `benchmark` build type: `initWith(release)`, `signingConfig = debug`, `matchingFallbacks += "release"`, `isDebuggable = false`, minified. Use `./gradlew :app:assembleBenchmark` (or `assembleRelease` with local signing props).
2. Ideally add the baseline profile first (S-01), then compare `CompilationMode.None` vs `Partial` (`adb shell cmd package compile -m speed-profile -f <pkg>`; reset with `--reset`).
3. Test the debug package id `com.ivandsky.puzzlebox.debug` for the first pass; the benchmark/release id is `com.ivandsky.puzzlebox` (adjust the commands below).

### 5.2 Counters

```
PKG=com.ivandsky.puzzlebox.debug

# Cold start
adb shell am force-stop $PKG
adb shell am start -W -S -n $PKG/com.ivandsky.puzzlebox.app.MainActivity   # TotalTime, WaitTime
# Run 10 times, report median and P90.

# Frame stats around one flow
adb shell dumpsys gfxinfo $PKG reset
#   ... perform the flow by hand or with `adb shell input` / UIAutomator ...
adb shell dumpsys gfxinfo $PKG framestats > /sdcard/gfx.txt
adb shell dumpsys gfxinfo $PKG                                  # Janky frames %, 50/90/95/99th percentile

# Perfetto (Compose + frame timeline + binder + scheduling)
adb shell perfetto -o /data/misc/perfetto-traces/pb.pftrace -t 20s \
  sched freq idle am wm gfx view binder_driver hal dalvik input res memory \
  -a $PKG
```

For custom config, use a text proto with data sources `linux.ftrace` (events `sched/sched_switch`, `sched/sched_wakeup`, `power/cpu_frequency`, `binder/*`; atrace categories `gfx view input am wm binder_driver dalvik res`; `atrace_apps: "com.ivandsky.puzzlebox.debug"`), `android.surfaceflinger.frametimeline`, `linux.process_stats` (1 s), and `android.log`. Open the trace in ui.perfetto.dev and check the "Expected/Actual Timeline" rows of the app, the `Choreographer#doFrame` slices, and the `Recomposer:recompose` / `Compose:...` slices.

For glitch review, record with `adb shell screenrecord --bit-rate 40000000 --size 1600x2560 /sdcard/flow.mp4` (cap at 180 s; or `scrcpy --record` at the native 120 Hz) and step through frame by frame (`ffmpeg -i flow.mp4 -vf "select=..."`). Look for: layout shifts (S-05), skeleton/zero-state flashes (S-06, S-07, S-08), blank screens (S-03, S-12), and colour flashes (S-04). Turn on `Settings > Developer options > Show layout bounds` only for one diagnostic pass, never for timings.

### 5.3 Flows to script

Reset gfxinfo before each flow, run each at least 5 times.

| # | Flow | Findings it confirms |
|---|---|---|
| 1 | Cold start to Home visible (kill, `am start -W`, record). Repeat with in-app theme forced to the opposite of the system | S-01…S-05, S-08 |
| 2 | Home → Games tab → back to Home after 3 s and after 10 s | S-06, S-07 |
| 3 | Open each game from Home: Zip, Queens, Tango, MiniSudoku, Strands. Note time to first interactive board, Loading frames | S-21, S-01 |
| 4 | Play a full board in each game (use a seeded or pre-solved puzzle). Zip: fast drag across the longest path on the biggest board. Queens: 20 rapid taps. Tango: 20 rapid taps incl. a rule violation and the 500 ms settle | S-10, S-11, S-13, S-15 |
| 5 | Solve → result page → Ranks → replay open (own solve) and compare | S-12 |
| 6 | Ranks: scroll the board, swipe between boards (all 5 games + Overall), switch Friends/Global, Day/Week/Month/Overall, pick another calendar day, pull to refresh, reselect tab | S-09, S-17, S-18 |
| 7 | Games tab → Archive: switch days 10×, change month | S-09 |
| 8 | Create: open Studio, type in a field with the IME open/close, publish a draft, Drafts/Published switch | Studio IME, `Crossfade` tab |
| 9 | Duels tab → open a duel → reveal | Compete screens (not statically audited in depth) |
| 10 | Rotation and fold/split (Tab S9 Ultra has no fold, so use rotation and split-screen) on Home, Ranks and a game | S-05 |
| 11 | Settings: switch theme light/dark/system | Theme switch behaviour (not audited) |

### 5.4 Pass thresholds

Per flow, release-like build, 120 Hz (frame budget 8.3 ms), measured through `gfxinfo` and Perfetto frame timeline:

| Metric | Pass | Investigate |
|---|---|---|
| Janky frames (gfxinfo) | < 3 % | > 5 % |
| P50 frame time | ≤ 8 ms | > 8.3 ms |
| P95 frame time | ≤ 11 ms | > 16.6 ms |
| P99 frame time | ≤ 16.6 ms | > 33 ms |
| Frames over 100 ms during any transition or morph | 0 | ≥ 1 |
| Cold start (`am start -W` TotalTime, release + baseline profile) | < 800 ms | > 1200 ms |
| Cold start (debug) | informational; flag > 2 s | |
| Time from tap to board interactive (any game) | < 300 ms warm | > 600 ms |
| Input latency while tracing a Zip path / tapping Queens | no dropped frame per cell step | any frame > 16.6 ms during a drag |
| Visual glitches in 120 fps recording (flash, shift, blank > 1 frame) | 0 | any |

If the tablet is held at 60 Hz by the system, multiply the frame budgets by two (16.6 ms) and state this in the run notes. Record the refresh rate (`adb shell dumpsys display | grep -i refresh`) with every run.

### 5.5 Order of work

1. Run flows 1, 2, 6 and 7 on debug to rank the hotspots, then rebuild as benchmark to see whether the same ones survive.
2. Capture a Perfetto trace of flow 1 and look at the main-thread slices of `bindApplication`, `Application.onCreate` (S-02), the gap before the first `Choreographer#doFrame` (S-03) and JIT activity (S-01).
3. Re-run after each fix; keep the numbers in `docs/research/polish/`.

---

## 6. Not covered and open questions

- Compete (Duels), Create/Studio, Profile and the Strands/Sudoku/Tango boards were read only at the level needed for the findings above; no per-frame review was done. A Strands trace-step recomposition fix exists (`0c9aa2e3b`) and Studio IME has a recent fix (`c22527bd6`), so I did not look for new issues there.
- `ObserveGameHistory` / `ObserveGameStats` were not checked for main-thread mapping. `HomeViewModel` recomputes `recentDays` and `homeWeek` (a `groupBy` over all history) on `viewModelScope` (Main) at every emission, which grows with history size. Worth a Perfetto look after a long play history is loaded.
- Goldens opened: 3 of about 991. None showed loading vs loaded variants for Home or Ranks (those states are not in the snapshot set), so layout stability between loading and loaded could only be judged from code.
- NEEDS_CONTEXT: (a) whether release signing props exist or a `benchmark` build type may be added; (b) which of the five games and which flows matter most, to prioritise section 5.3.
