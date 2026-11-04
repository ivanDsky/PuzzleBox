# add-a-game fixture

What a new game looks like when it follows ADR-0008 "Adding a game" exactly, with sample game id `sample`.
Every structure rule must report zero violations here. Files are parsed by Konsist, never compiled.
Change this fixture only through a ledgered Ruling.

| File | ADR-0008 step |
|---|---|
| `settings.gradle.kts` | includes only the modules the recipe needs |
| `core/game/build.gradle.kts`, `core/game/src/main/kotlin/com/ivandsky/puzzlebox/game/*.kt` | contracts the recipe references (`GameDefinition`, `GamePlugin`, `DailySource`, `GamePlayUi`, plus `GameId` and the `GamePlayController` marker); no edit needed per game |
| `core/designsystem/**` | `PuzzleBoxPreviews` multipreview used by step 3 previews |
| `games/sample/build.gradle.kts` | step 2: pure-Kotlin game module |
| `games/sample/.../SampleDefinition.kt` | step 2: game identity |
| `games/sample/.../SamplePlugin.kt` | step 2: `samplePlugin` and `sampleModule` (Koin, root package of `games:*`) |
| `games/sample/.../feed/FixtureSampleDailySource.kt` | step 2: the game's `DailySource` |
| `games/sample-ui/build.gradle.kts` | step 3: Android compose module |
| `games/sample-ui/.../SampleUiModule.kt` | step 3: `sampleUiModule` registering `GamePlayUi` |
| `games/sample-ui/.../SamplePlayUi.kt`, `SamplePlayController.kt` | step 3: play registration and controller |
| `games/sample-ui/.../SampleBoard.kt`, `SampleBoardPreviews.kt` | step 3: board composable and its preview |

Step 1 (create the modules and include them) is `settings.gradle.kts` plus the four build files.
Steps 4-6 (`app` dependencies, `appModules`, `GAME_ORDER`) live in `:app`, which is not part of this fixture.
