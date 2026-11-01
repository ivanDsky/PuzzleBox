# PuzzleBox — project map

pinned: 7b8eeea383c6533c691e9370a32b6fece28fab2f

## Purpose

One Android app for daily logic puzzles — play official NYT/LinkedIn dailies, build and share
custom games, compete via leaderboards and 1v1 (Zip ships first). Phase 0 (current): the app
shell, the design-system vocabulary, and the offline-first daily-feed infrastructure; Play and
Profile are placeholders. Product frame and versioning policy live in PROFILE.md (semver source
of truth; alpha 0.x.y until all five games ship tested).

## Layout

Gradle root `PuzzleBox`, five modules; dependency direction `:app` → `:feature:home` →
`:core:data` → `:core:common`, with `:core:designsystem` consumed by `:app` and `:feature:home`.

- `app/` (`:app`) — `PuzzleBoxApplication` (starts Koin), `MainActivity` (Compose host,
  theme resolution, edge-to-edge), `PuzzleBoxApp` (Navigation3 shell: route table, tab
  bar/rail, fade-through transitions, opaque scene canvas), NavKey route objects
  (Home/Play/Profile/Catalog), placeholder Play/Profile screens, DI-graph test.
- `core/common/` — `DispatcherProvider` seam (+ default impl), `AppResult`/`AppError` and
  `resultOf` convention (typed errors, never thrown surprises), Koin `commonModule`.
- `core/data/` — Room stack (`PuzzleBoxDatabase` with `DailyPuzzleDao` and
  `DailyPuzzleEntity` keyed on gameId+day, JSON `CellsConverter`, schemas exported under
  `core/data/schemas`, `Migrations.ALL` registry), network stack (`PuzzleFeedApi` Retrofit
  feed, `AuthHeaderInterceptor` + `AuthHeaderSource` fun interface, OkHttp with
  Authorization/Cookie log redaction, placeholder LinkedIn base URL), repositories
  (`DailyPuzzleRepository` → `OfflineFirstDailyPuzzleRepository`,
  `UserPreferencesRepository` → `DataStoreUserPreferencesRepository`), Koin `dataModule`,
  and handwritten fakes under `data.testing` (main source set, usable by features).
- `core/designsystem/` — `PuzzleBoxTheme` composition root, token vocabulary
  (`ColorTokens` light/dark schemes, `Type` Baloo 2, `Shape`, `Spacing`, `MotionTokens` +
  named `MotionPattern`s), shared `PuzzleBoard`, components (buttons, chips, dialogs,
  top bars, states), `ShowkaseTokens` `@ShowkaseColor`/`@ShowkaseTypography` metadata
  registrations; font license in `licenses/`. The component browser itself is debug-only
  in `app/src/debug` (Showkase browser screen; goldens in `app/snapshots/`).
- `feature/home/` — `HomeRoute` (DI-bound entry: observes cached history, never the
  network), `HomeScreen` (single/two-pane arrangement, theme segmented control, history
  teaser), `HomeLayout` + `homeLayoutFor` pure pane policy.
- `PROFILE.md` — product frame, ranked care-axes, deploy targets, testing style.

## Entry points

- `PuzzleBoxApplication.onCreate` — `startKoin` with `appModules` = `commonModule` +
  `dataModule` (the whole object graph).
- `MainActivity` — sole activity; holds composition until the persisted `ThemeMode`
  arrives (no light flash), resolves dark theme against the system setting, keeps system
  bar icon appearance in step with the resolved in-app theme, then hosts `PuzzleBoxApp`.
- `PuzzleBoxApp` route table (Navigation3 `NavDisplay`): Home → `HomeRoute`; Play →
  placeholder; Profile → placeholder with a detail route to Catalog — the debug-only
  Showkase browser (reflective `CatalogDebugBinding` registration, FLAG_DEBUGGABLE-gated;
  non-debuggable builds get `CatalogUnavailableScreen`); tab selection resets to the tab
  root. Bottom bar on compact widths, navigation rail on expanded-width landscape (same
  `homeLayoutFor` decision drives both nav shape and Home's panes).
- No deep links, widgets, or services.

## Seams

- **Koin DI graph** — `commonModule` (`DispatcherProvider`) + `dataModule` (Json,
  `AuthHeaderSource`→`NONE`, OkHttp, Retrofit→`PuzzleFeedApi`, Room + `DailyPuzzleDao`,
  `DailyPuzzleRepository`→offline-first impl, DataStore +
  `UserPreferencesRepository`→DataStore impl). Graph completeness is asserted by
  `DiGraphTest` under Robolectric.
- **Repository interfaces** (live in core, features inject them):
  `DailyPuzzleRepository` — `refresh()` is network-first and returns
  `AppResult`; `observeHistory()`/`cached()` never touch the network, so callers
  degrade to cache by observing. `UserPreferencesRepository` — `themeMode` Flow + setter.
- **`DispatcherProvider`** — dispatcher injection keeps callers testable.
- **`AuthHeaderSource`** — fun interface where LinkedIn session headers plug in (Phase 1);
  currently `NONE`, so requests go out unsigned.
- **Handwritten fakes** in `core/data` main source set: `FakeDailyPuzzleRepository`
  (seeded cache, queued refresh outcome, recorded calls), `FakeUserPreferencesRepository`
  (in-memory mode, recorded writes) — the tdd-fakes style PROFILE.md names.
- **`homeLayoutFor`** — pure width+orientation → pane arrangement; tested without a device.
- **`Migrations.ALL`** — every schema change bumps the Room version, commits an exported
  schema JSON, and registers a migration here; destructive fallbacks disallowed.
- **Roborazzi screenshot harness** — designsystem unit tests record goldens by default;
  run with `-Proborazzi.verify` to fail on pixel drift instead.

## Working test commands

All run green at the pinned sha (JUnit via `testDebugUnitTest`; Robolectric sdk 34 where
device APIs are touched; App module requires `ANDROID_HOME` or `local.properties` — SDK
lives at `~/Library/Android/sdk` here). Totals: 78 tests, 0 failures, 0 errors, 0 skipped.

- `./gradlew :app:testDebugUnitTest` — 2 tests (DI graph: static verify + full resolve)
- `./gradlew :core:common:testDebugUnitTest` — 8 tests (AppResult/resultOf)
- `./gradlew :core:data:testDebugUnitTest` — 26 tests (offline-first repo, DAO, DataStore
  prefs, auth interceptor, fakes)
- `./gradlew :core:designsystem:testDebugUnitTest` — 32 tests (token checks, component and
  puzzle-board interaction, screenshot goldens in record mode)
- `./gradlew :feature:home:testDebugUnitTest` — 10 tests (layout policy, home screen)

## Diagram

[docs/agents/project-map.html](project-map.html) — archify architecture candidate
(finalized; validate/deliver/check/browser-check all pass; candidate at
`.archify/project-map/architecture-candidate.json`). Note: archify's repository-evidence
gate requires a git remote identity and this repo has none (no origin), so per-node source
citations were omitted and the pinned revision is recorded in the diagram's Contracts card;
the topology, labels, and cards are evidence-derived from the modules above.

## Drift rule

If `git rev-parse HEAD` differs from `pinned:`, run `git diff --stat <pin>..HEAD` and
refresh only the sections the touched paths hit (layout, entry points, seams, tests), then
bump the pin — never edit the map in conversation.
