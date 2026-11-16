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

| Class | Journeys | Data state | Metrics |
| --- | --- | --- | --- |
| `StartupBenchmark` | cold and warm, Partial and None compilation | not cleared: the install's data | `timeToInitialDisplayMs`, frame timing |
| `GameOpenBenchmark` | each of the five games: card, page, Play, board ready, back | `Opened(game)`: its puzzle stored, tutorial seen | frame timing |
| `TabSwitchBenchmark` | Today, Games, Ranks, Create and back | `Cleared` | frame timing |
| `ArchiveBenchmark` | calendar day taps and month arrows | `Cleared` | frame timing |
| `RanksBenchmark` | open, Who/When sheet, day arrows, board swipes | `Cleared` | frame timing |
| `ZipDragBenchmark` | drag from 1 toward 2 on the board, Clear, three times | `Opened(Zip)` | frame timing |
| `BaselineProfileGenerator` | cold start, tabs, every game board | not cleared: the install's data | writes a profile; nothing is committed |

Frame timing reports `frameDurationCpuMs` and `frameOverrunMs` as P50, P90, P99. No journey finishes
a puzzle. Selectors are text and content descriptions; the app has no benchmark test tags.

## Run

The device must be awake and unlocked (`Stay awake` on, charging) and on its home screen.

```
./gradlew :app:assembleBenchmark
./gradlew :benchmark:connectedBenchmarkAndroidTest
```

With more than one device attached, name one: `ANDROID_SERIAL=<serial> ./gradlew ...`. For the
Pixel 8 Pro that is its wireless-debugging address (`adb connect <phone address>:<port>`; the port
changes when wireless debugging restarts). The Galaxy Tab S9 Ultra needs the same, with its own
serial. The Tab is a two-pane layout (840 dp wide, wider than tall, at least 480 dp tall: the app's own rule, which
`isTwoPane()` in `Navigation.kt` repeats), and the journeys run on both layouts unchanged:

- Tabs: the label is looked for in the rail as well as the bar.
- Games: the selected game's page sits beside the list, so there is no Back on it; `leaveGame` and the
  archive journey take it only on one pane. A game card is open once its name shows twice (card and
  page title), because the page beside the list belongs to the game selected before it.
- Ranks: there are no board dots, so a tab is ready on its "Tune ranks" button, and board swipes
  run in the right pane (60% to 90% of the width) instead of across the screen.
- Zip: only a digit inside the board pane is a waypoint; the stats panel beside it has digits too.

Each journey clears the benchmark package's app data with `pm clear` before every run (`clearAppData()`
in `Journey.kt`, run through the instrumentation's shell), then sets up the data state its `DataState`
declares. `Cleared` launches to Today and nothing more; the app then syncs today's puzzle for every
game (its prefetch on Home and Games, when online). `Opened(game)` also opens that game once with its
tutorial skipped, so the game's puzzle is stored and its tutorial is seen. Uninstalling is not needed
to reset.

`GameOpenBenchmark` measures the stored open only. There is no unstored variant here: the Today launch
stores today's puzzles for all five games before the measured block (Home and Games prefetch), and a
first Play always opens the tutorial, so an unstored open cannot be reached from this module without an
app hook. The unstored path is covered by the JVM tests (ticket 04) and a visual check on the Pixel
(ticket 16).
Startup and the baseline profile do not clear: they measure the install's data as it is.

A journey never holds a `UiObject2` across a wait or a transition; it finds and acts in one `poll`,
which finds the node again when a recomposition makes it stale.

Journeys compile the app with `CompilationMode.None()` (the install-time state a user without a
profile runs); `-Pandroid.testInstrumentationRunnerArguments.compilation=partial` switches them to
Partial, which warms up and compiles the whole app first and takes minutes per test. Startup runs
both Partial and None, always.

One class or test: `-Pandroid.testInstrumentationRunnerArguments.class=com.ivandsky.puzzlebox.benchmark.StartupBenchmark`
(add `#coldPartial` for one test).

Smoke check (correctness only): add `-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.dryRunMode.enable=true`.
Each journey runs one iteration, with no compilation and no result files (it still traces), which proves the selectors and
navigation on a layout without waiting for a full run.

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
