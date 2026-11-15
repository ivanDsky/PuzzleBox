# :benchmark

Macrobenchmark journeys for PuzzleBox. They give every polish fix a measured before and after on a
real device. The module drives the installed app through UiAutomator; it has no project
dependencies and ships nowhere.

## What it measures

The target is `:app`'s `benchmark` build type: release-like (R8, shrunk, not debuggable), profileable,
installed as `com.ivandsky.puzzlebox.benchmark` ("PuzzleBox Bench") beside the real apps. It is signed
out and never posts a score. It carries its own `google-services.json`
(`app/src/benchmark/`), a copy of the debug client with the package renamed, so sign-in is not
expected to work there. Real-board Ranks flows need an account and are not benchmarked.

**Never run a benchmark against `com.ivandsky.puzzlebox` or `com.ivandsky.puzzlebox.debug`.** The
release package holds the real account. `TARGET_PACKAGE` in `Target.kt` is the only package any
journey names.

| Class | Journeys | Metrics |
| --- | --- | --- |
| `StartupBenchmark` | cold and warm, Partial and None compilation | `timeToInitialDisplayMs`, frame timing |
| `GameOpenBenchmark` | each of the five games: card, page, Play, board ready, back | frame timing |
| `TabSwitchBenchmark` | Today, Games, Ranks, Create and back | frame timing |
| `ArchiveBenchmark` | calendar day taps and month arrows | frame timing |
| `RanksBenchmark` | open, Who/When sheet, day arrows, board swipes | frame timing |
| `ZipDragBenchmark` | drag from 1 toward 2 on the board, Clear, three times | frame timing |
| `BaselineProfileGenerator` | cold start, tabs, every game board | writes a profile; nothing is committed |

Frame timing reports `frameDurationCpuMs` and `frameOverrunMs` as P50, P90, P99. No journey finishes
a puzzle. Selectors are text and content descriptions; the app has no benchmark test tags.

## Run

The phone must be awake and unlocked (`Stay awake` on, charging) and on its home screen.

```
./gradlew :app:assembleBenchmark
./gradlew :benchmark:connectedBenchmarkAndroidTest
```

With more than one device attached, name one: `ANDROID_SERIAL=<serial> ./gradlew ...`. For the
Pixel 8 Pro that is its wireless-debugging address (`adb connect 100.92.178.105:<port>`; the port
changes when wireless debugging restarts). The Galaxy Tab S9 Ultra needs the same, with its own
serial; the Tab is a two-pane layout, which the tab selector handles by looking for the label in the
rail as well as the bar.

Journeys compile the app with `CompilationMode.None()` (the install-time state a user without a
profile runs); `-Pandroid.testInstrumentationRunnerArguments.compilation=partial` switches them to
Partial, which warms up and compiles the whole app first and takes minutes per test. Startup runs
both Partial and None, always.

One class or test: `-Pandroid.testInstrumentationRunnerArguments.class=com.ivandsky.puzzlebox.benchmark.StartupBenchmark`
(add `#coldPartial` for one test).

## Read the results

Each test writes `<Class>_<test>-benchmarkData.json` and a Perfetto trace under
`benchmark/build/outputs/connected_android_test_additional_output/benchmarkBenchmark/connected/<device>/`.
If Gradle did not copy them, pull them from
`/sdcard/Android/media/com.ivandsky.puzzlebox.benchmark/additional_test_output/`. In the JSON,
`metrics.timeToInitialDisplayMs` and `metrics.frameDurationCpuMs` / `frameOverrunMs` carry
`minimum`, `median`, `maximum` and the `runs` list; the P50/P90/P99 of the frame metrics are in the
Studio output and in the JSON's `percentiles`.

Compare like with like: same device, same battery state, same theme, rotation and font scale.
Restore anything you changed on the device afterwards.

## Baseline profile

`BaselineProfileGenerator` is wired and runs, but no profile is committed and `:app` has no
`profileinstaller`: shipping it is a later fix, so its delta is measured against the numbers this
module records today. Run it with
`-Pandroid.testInstrumentationRunnerArguments.class=com.ivandsky.puzzlebox.benchmark.BaselineProfileGenerator`
and pull the profile from the same additional-output directory.

## Known quirk

`./gradlew :app:tasks --all` fails with "Compose Preview ... Unit tests are disabled for this variant
('benchmark')". AGP creates a Compose Preview task for every build type and wants host tests on
each; the benchmark variant has none on purpose. `./gradlew :app:tasks` and every build, test and
benchmark task are unaffected.
