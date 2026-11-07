# PuzzleBox

One Android app for daily logic puzzles — play official NYT/LinkedIn dailies, build and share custom games, and compete via leaderboards and 1v1.

> **Status: alpha (0.x)** — Zip, Queens, Tango and Mini Sudoku are playable; Strands and social features are on the roadmap.

## Install

Download `PuzzleBox-<version>.apk` from the [latest release](https://github.com/ivanDsky/PuzzleBox/releases/latest) on your Android phone and open it; allow installs from your browser if Android asks. Release builds show an **Update available** pill on Home when a newer release is out.

## Games

- **Zip** — LinkedIn daily: draw one path through every cell, visiting the numbers in order
- **Tango** — LinkedIn daily: fill suns and moons with balanced rows and columns
- **Queens** — LinkedIn daily: one crown per row, column and color region, none touching
- **Mini Sudoku** — LinkedIn daily: 6x6 Sudoku with 2x3 boxes, notes, undo and staged hints
- **Sudoku** — 9x9 on the same engine, debug builds only (one bundled puzzle; no content source yet)
- **Strands** — NYT daily *(planned)*

<p>
  <img src="docs/screenshots/home.png" width="720" alt="PuzzleBox home screen">
</p>

## Why

Official puzzle apps scatter dailies across NYT and LinkedIn with separate logins — and keep no browsable history of your games and scores. PuzzleBox puts them in one place: open the app → today's puzzle or a shared custom game → score recorded → check your rank or challenge a friend → come back tomorrow.

## Features

- **Daily games** — every game's puzzle of the day, one tap from Home
- **Today at a glance** — Home lists every game with today's status: not started, in progress, or solved
- **Learn by playing** — each game's first play opens a few tiny practice boards, one rule at a time; How to play reopens them with the clock paused
- **Play history** — finished puzzles and scores stay browsable (unlike the official apps)
- **Pick up where you left off** — leave a game mid-play (Back, another tab, or the app closing) and the picker offers Continue with the clock where you stopped
- **Feedback you can read** — a refused move or a broken rule says why in a reserved caption under the board, so the board never jumps
- **Honest best** — only a day's first solve can set "New best" or tie it; replays are practice and say so
- **Custom games** — build and share puzzles via shareable archives *(planned)*
- **Leaderboards & 1v1** — Google sign-in, compete with friends *(planned)*
- **Adaptive design** — phone and tablet layouts, light and dark themes

## Architecture

Multi-module Gradle project, Kotlin + Compose (Material 3), shaped after
[nowinandroid](https://github.com/android/nowinandroid): convention plugins in `build-logic`
(`puzzlebox.*`), `core:*` layers, one `games:<id>` + `games:<id>-ui` pair per game, and
`feature:<name>:{api,impl}` pairs.

```mermaid
graph TD
    app[":app<br/>composition root: Koin, Nav3 shell, GAME_ORDER"]
    backend[":backend:firebase"]

    subgraph features["features: home, play, history, profile"]
        impl[":feature:*:impl<br/>screens, ViewModels, nav entries"]
        api[":feature:*:api<br/>NavKeys"]
    end

    subgraph games["games: zip, tango, queens"]
        gameui[":games:*-ui<br/>GamePlayController + GamePlayUi"]
        game[":games:*<br/>GamePlugin + DailySource"]
    end

    subgraph core["core"]
        navigation[":core:navigation"]
        domain[":core:domain<br/>use cases"]
        data[":core:data<br/>DailyRepository, records"]
        database[":core:database"]
        network[":core:network"]
        datastore[":core:datastore"]
        coregameui[":core:game-ui<br/>GamePlayRegistry"]
        coregame[":core:game<br/>GameRegistry, contracts"]
        designsystem[":core:designsystem"]
        common[":core:common"]
        model[":core:model"]
    end

    app --> impl & api & gameui & game & backend
    backend --> data
    impl --> api
    impl --> navigation & domain & data & coregameui & designsystem
    api --> navigation
    gameui --> game
    gameui --> coregameui & designsystem
    game --> coregame
    domain --> data
    data --> database & network & datastore & coregame & common
    network --> coregame
    datastore --> common
    coregameui --> coregame & designsystem & common
    coregame --> model
```

`impl` modules reach another feature only through its `api`; no feature depends on a game;
games plug in through Koin and the `GameRegistry` / `GamePlayRegistry` seams. The forbidden
edges (over every configuration, tests included) are enforced by `./gradlew checkModuleGraph`,
which `check`, `assembleDebug` and `testDebugUnitTest` run — see
[ADR-0008](docs/adr/0008-module-graph.md). Architecture decisions are recorded as ADRs in
[`docs/adr`](docs/adr).

### Adding a game

1. `settings.gradle.kts`: `include(":games:<id>")` and `include(":games:<id>-ui")`.
2. `games/<id>` (`puzzlebox.jvm.library`): the game's `GamePlugin` and `DailySource`, bound in
   its Koin module — the plugin under `qualifier(<Id>Definition.id.value)`, the source with
   `bind DailySource::class`.
3. `games/<id>-ui` (`puzzlebox.android.library.compose`): one `GamePlayController` + one
   `GamePlayUi`, bound in `<id>UiModule` as `single<GamePlayUi>(qualifier = qualifier(<Id>Definition.id.value))`.
4. `app/build.gradle.kts`: `implementation(projects.games.<id>)` and
   `implementation(projects.games.<id>Ui)`.
5. `PuzzleBoxApplication.appModules`: add `<id>Module` and `<id>UiModule`.
6. `app/.../di/AppModule.kt`: add the id to `GAME_ORDER`.

No `core:*` or `feature:*` module changes.

## Tech stack

Kotlin · Jetpack Compose (Material 3) · Coroutines & Flow · Room · Retrofit · Coil · Koin · Paging 3 · Roborazzi (screenshot testing)

## Build

```bash
./gradlew :app:assembleDebug
```

Requires an Android SDK (`ANDROID_HOME` or `local.properties`). Install and run on a connected device/emulator:

```bash
./gradlew :app:installDebug
```

Tests:

```bash
./gradlew testDebugUnitTest :core:game:test :games:zip:test :games:queens:test :games:tango:test :core:model:test :core:network:test -Proborazzi.verify=true
                                       # JVM unit tests against fakes; screenshot goldens in verify mode
./gradlew check                        # tests, lint and the module-graph guard (checkModuleGraph)
```

Without `-Proborazzi.verify=true` the screenshot tests re-record their goldens.

## Contributing

Trunk-based: each change is a small PR into `main` that merges within hours. A PR can merge
only when the required `pr-check` workflow ([`.github/workflows/pr-check.yml`](.github/workflows/pr-check.yml))
is green. It runs the same command you can run locally:

```bash
./gradlew assembleDebug test           # module-graph guard, every module's unit tests, :konsist
```

CI does not compare screenshot pixels (the goldens are recorded on macOS), so run the
`-Proborazzi.verify=true` command above before you push UI changes. `main` must stay
releasable: every push that carries a new `versionName` publishes a GitHub release.
