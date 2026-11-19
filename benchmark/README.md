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
| `StartupBenchmark` | cold and warm, Partial and None compilation | no data state: the install's data, so class order in the run matters | `timeToInitialDisplayMs`, frame timing |
| `GameOpenBenchmark` | each of the five games: card, page, Play, board ready, back | `Opened(game)`: its puzzle stored, tutorial seen | frame timing |
| `TabSwitchBenchmark` | Today, Games, Ranks, Community and back | `OnToday` | frame timing |
| `ArchiveBenchmark` | calendar day taps and month arrows | `OnToday` | frame timing |
| `RanksBenchmark` | open, Who/When sheet, day arrows, board swipes | `OnToday` | frame timing |
| `ZipDragBenchmark` | drag from 1 toward 2 on the board, Clear, three times | `Opened(Zip)` | frame timing |
| `BaselineProfileGenerator` | cold start, tabs, every game board | no data state: the install's data, so class order in the run matters | writes a profile; `app/src/main/baseline-prof.txt` is its filtered copy |

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
- Ranks: there are no board dots, so a tab is ready on its When chip (text "When", or the picked window), and board swipes
  run in the right pane (60% to 90% of the width) instead of across the screen.
- Zip: only a digit inside the board pane is a waypoint; the stats panel beside it has digits too.

Journeys do not clear the benchmark package's data. Each `DataState` is set up idempotently from
whatever the last run left: `OnToday` launches to Today and nothing more; the app then starts syncing
today's puzzle for every game (its prefetch on Home and Games, when online), and nothing waits for
it. `Opened(game)` also opens that game with its tutorial skipped, so the game's puzzle is stored and
its tutorial is seen; a tutorial already seen is not shown again. In-run `pm clear` of the target
kills the instrumentation on Android 17, which is why nothing clears. Gradle's connected task
installs fresh and uninstalls afterwards; for on-device `am instrument` runs, uninstall the benchmark
package and reinstall first. A re-used install must have no solved daily: a solved puzzle's page
shows "See result" instead of Play, and the journeys stop there.

`GameOpenBenchmark` measures the stored open only. There is no unstored variant here: the Today launch
stores today's puzzles for all five games before the measured block (Home and Games prefetch), and a
first Play always opens the tutorial, so an unstored open cannot be reached from this module without an
app hook. The unstored path is covered by the JVM tests `ActivePlayBoardFirstTest`, `SlotOpeningTest`
and `RealGamesBoardFirstTest`, plus a visual check on the Pixel.

Startup and the baseline profile declare no data state: they measure the install's data as it is.

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

`app/src/main/baseline-prof.txt` is the committed profile and `:app` ships it: AGP compiles it into
the release APK as `assets/dexopt/baseline.prof`, and `androidx.profileinstaller` installs it on
first launch where Play has not. The `androidx.baselineprofile` Gradle plugin stays off: it adds
build types without the `.benchmark` suffix that carry the real release id.

`./gradlew :app:checkReleaseBaselineProfile` builds the release APK and fails when the committed
profile is missing or has no rule for `com.ivandsky.puzzlebox`, or when the APK carries no
`baseline.prof`. The APK half alone cannot catch a dropped profile: the libraries' own profiles
still fill it. `baseline-prof.txt` is generated data, exempt from the 300-line file limit.

Regenerate once per release, after the startup and game-open paths settle, on the benchmark package
only (never `com.ivandsky.puzzlebox` or `.debug`), with the device awake and unlocked:

```
ANDROID_SERIAL=<serial> ./gradlew :benchmark:connectedBenchmarkAndroidTest \
  -Ppuzzlebox.baselineProfileGeneration=true \
  -Pandroid.testInstrumentationRunnerArguments.class=com.ivandsky.puzzlebox.benchmark.BaselineProfileGenerator
```

`puzzlebox.baselineProfileGeneration` builds the benchmark install without R8: the recorded rules then
carry the sources' class and method names, which the release build's R8 maps to its own. Without it they
name R8's renamed and merged output, which matches no release build. Never pass it to a measuring run.
Gradle installs the benchmark build fresh and removes it afterwards. The generator writes
`BaselineProfileGenerator_generate-startup-prof.txt`
next to the benchmark results in `benchmark/build/outputs/connected_android_test_additional_output/`;
if Gradle did not copy it, pull it from
`/sdcard/Android/media/com.ivandsky.puzzlebox.macrobenchmark/additional_test_output/`.

Drop the platform's own classes (`android/`, `java/`, `javax/`, `dalvik/`, `libcore/`, `sun/`; the
app's dex holds no such rules), keep the rest (the app's `com.ivandsky.puzzlebox` rules and the
libraries it loads), and commit the result as `app/src/main/baseline-prof.txt`:

```
grep -v -E '^[HSP]*L(android|java|javax|dalvik|libcore|sun|org/json|org/w3c|org/xml)/' \
  <pulled>-startup-prof.txt > app/src/main/baseline-prof.txt
```

The rules are hit-sets, not a recording of this commit: a stale profile only costs first-launch speed.
