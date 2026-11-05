# ADR-0008 — The module graph and its guard

Date: 2026-11-04 · Status: accepted · Source: P1–P9 nowinandroid-style refactor

## Context
P1–P8 moved PuzzleBox from a few wide modules to a nowinandroid-shaped graph: `build-logic`
convention plugins (`puzzlebox.*`), split `core:*` layers, one `games:<id>` + `games:<id>-ui`
pair per game, and `feature:<name>:{api,impl}` pairs. The graph only stays that shape if the
seams the games and features meet through have one owner each and the forbidden edges fail
the build instead of a review.

## Decision
1. **`GameRegistry` (`core:game`) is the only `GameId` → `GamePlugin` lookup.** Every game
   module binds its `GamePlugin` under its id qualifier; `app/di/AppModule.kt` builds the one
   registry from `getAll<GamePlugin<*, *, *>>()`, sorted by the app-side `GAME_ORDER`
   (`zip`, `tango`, `queens`; an unlisted game sorts after them by id). Consumers that must
   not know game ids — `core:data` decoding, `core:domain` use cases, Home, History, the play
   shell — inject the registry; nobody else aggregates plugins or scans for an id.
2. **Dailies flow `DailySource` → `DailyRepository`.** Each game module owns its feed adapter
   (wire extraction, parsing, trust gate) as a `DailySource` (`core:game`) bound
   `bind DailySource::class`; `core:data`'s `OfflineFirstDailyRepository` collects every
   source by `gameId` and is the `DailyRepository` (fetch-today with cache degrade,
   cache-only `cached`/`observeHistory`).
3. **`GamePlayRegistry` (`core:game-ui`) has a single owner.** Each `games:<id>-ui` module
   binds exactly one `GamePlayUi` under its id qualifier; `app` builds
   `GamePlayRegistry(getAll())` once (a duplicate id throws). `feature:play:impl` drives any
   game through `GamePlayController` (ADR-0007).
4. **Cross-feature business logic lives in `core:domain`** as use cases (`domainModule`):
   `ComputeStreak`, `GetFeaturedGame`, `GetReplaySession`, `ObserveGameHistory`,
   `ObserveTodayLeaderboard`. Features call use cases or `core:data` repositories, never
   each other's ViewModels.
5. **Navigation is api/impl.** `feature:<name>:api` holds only the feature's `@Serializable`
   NavKeys and a `<name>NavKeys()` polymorphic registration (`core:navigation`'s
   `navKeySavedStateConfiguration`); `feature:<name>:impl` holds screens, ViewModels, Koin
   module and the `<name>Entry` builder `app` installs in `puzzleBoxEntryProvider`. A feature that
   navigates elsewhere depends on the target's `api` only. *(Amended 2026-11-04, ADR-0010.)*
   Every key's `@SerialName` uses the `com.ivandsky.puzzlebox.app.nav.*` prefix by convention,
   new keys included; compatibility of saved navigation is versioned by `NAVIGATION_STATE_KEY`
   (a bundle under another key, or one that fails to decode, starts fresh), not by pins.
   `app/nav/PuzzleBoxNavKeys.kt` registers every feature's keys.
6. **Forbidden edges, enforced by `checkModuleGraph`.** The convention plugins
   `puzzlebox.android.application`, `puzzlebox.android.library` and `puzzlebox.jvm.library`
   register a `checkModuleGraph` task in every module
   (`build-logic/.../ModuleGraphGuard.kt`). It reads the project dependencies of ALL
   configurations — `implementation`, `api`, `testImplementation`,
   `androidTestImplementation`, `ksp`, … — and fails listing each violation with its
   configuration. `check`, Android `preBuild` and JVM `classes` depend on it, so
   `./gradlew check`, `assembleDebug` and `testDebugUnitTest` all fail on a violation.
   Forbidden:
   - anything → `:app` (the composition root, which may depend on everything);
   - `:core:testing` from a non-test configuration;
   - `core:*` → any non-`core` module (so `core:testing` depends on `core:*` only);
   - `feature:*` → `games:*`;
   - `feature:*:api` → anything but `:core:navigation` / `:core:model`;
   - `feature:a:impl` → `feature:b:impl`;
   - `games:*` → `feature:*`;
   - `games:x` → `games:y`, except `games:x-ui` → `games:x`;
   - `:konsist` → any module, and any module → `:konsist`, in every configuration (test
     configurations included).

   `:konsist` is a verification-only family (ADR-0009, [in-module structure rules](0009-in-module-structure-rules.md)):
   it reads sources as files and has zero edges, so it never joins the app's graph. Two arms of
   `forbiddenEdgeReason` guard it, placed before the `:core:testing` test-configuration exemption.
   `:backend:*` is otherwise unconstrained (it implements `core:data` seams).

## Adding a game
1. `settings.gradle.kts`: `include(":games:<id>")` and `include(":games:<id>-ui")`.
2. `games/<id>` (`puzzlebox.jvm.library`, `api(projects.core.game)`): definition, rules,
   session codec, score model, its `DailySource`, the `GamePlugin`, and a Koin module
   binding the plugin under `qualifier(<Id>Definition.id.value)` plus
   `single { … } bind DailySource::class`.
3. `games/<id>-ui` (`puzzlebox.android.library.compose`, on `games:<id>`, `core:game`,
   `core:game-ui`, `core:designsystem`): one `GamePlayController`, one `GamePlayUi`, and a
   `<id>UiModule` with `single<GamePlayUi>(qualifier = qualifier(<Id>Definition.id.value))`.
4. `app/build.gradle.kts`: `implementation(projects.games.<id>)` and
   `implementation(projects.games.<id>Ui)`.
5. `app/.../PuzzleBoxApplication.kt`: add both Koin modules to `appModules`.
6. `app/.../di/AppModule.kt`: add the id to `GAME_ORDER` at its product position.

No `core:*` or `feature:*` module changes; `checkModuleGraph` rejects any shortcut edge.

## Consequences
- A new edge that breaks the layering fails locally on the first build, with the rule that
  forbids it, before review.
- The guard reads declared project dependencies, not the resolved graph: transitive `api`
  exposure is allowed by design (e.g. `core:domain` re-exporting `core:data`).
- New module families need a rule in `forbiddenEdgeReason` and an update here.
- What is inside a module (file size, one type per file, package and naming rules) is guarded by
  the `:konsist` suite, not by this task: see ADR-0009.
