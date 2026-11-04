# PuzzleBox

One Android app for daily logic puzzles — play official NYT/LinkedIn dailies, build and share custom games, and compete via leaderboards and 1v1.

> **Status: alpha (0.x)** — Zip ships first; more games (Queens, Tango, MiniSudoku, Strands) and social features are on the roadmap.

<p>
  <img src="docs/screenshots/home.png" width="720" alt="PuzzleBox home screen">
</p>

## Why

Official puzzle apps scatter dailies across NYT and LinkedIn with separate logins — and keep no browsable history of your games and scores. PuzzleBox puts them in one place: open the app → today's puzzle or a shared custom game → score recorded → check your rank or challenge a friend → come back tomorrow.

## Features

- **Daily games** — today's puzzle front and center, with a preview grid and streak tracking
- **Play history** — finished puzzles and scores stay browsable (unlike the official apps)
- **Custom games** — build and share puzzles via shareable archives *(planned)*
- **Leaderboards & 1v1** — Google sign-in, compete with friends *(planned)*
- **Adaptive design** — phone and tablet layouts, light and dark themes

## Architecture

Multi-module Gradle project, Kotlin + Compose (Material 3):

```
app                  # navigation shell, MainActivity
├── feature/*/impl   # feature screens + nav entries: home, play, history, profile
├── feature/*/api    # each feature's NavKeys (the only cross-feature edge)
├── core/navigation  # Navigator over the single back stack, NavKey saved-state config
├── games/zip        # Zip game (plugin module)
├── core/game        # game engine contracts
├── core/game-ui     # shared game UI components
├── core/data        # repositories (offline-first over the sources below)
├── core/database    # Room database, DAOs, migrations, schemas
├── core/network     # LinkedIn feed transport (OkHttp/Retrofit)
├── core/datastore   # Preferences DataStore data source
├── core/designsystem# theme + components, screenshot-tested
└── core/common      # shared utilities, dispatchers
```

Games plug in through module contracts (see `docs/adr/0001-game-plugin-contracts.md`); puzzle archives act as `PuzzleSource`s (ADR-0002). Architecture decisions are recorded as ADRs in [`docs/adr`](docs/adr).

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
./gradlew :app:testDebugUnitTest        # JVM unit tests against fakes
./gradlew :core:designsystem:verifyRoborazziDebug  # design-system screenshot tests
```
