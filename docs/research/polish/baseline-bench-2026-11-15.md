# Baseline benchmark, Galaxy Tab S9 Ultra, 2026-11-15

## Run

Device: Samsung Galaxy Tab S9 Ultra (SM-X910), Android 16, build samsung/gts9uwifixx/gts9uwifi:16/BP4A.251205.006/X910XXS6EZI2:user/release-keys. Display 1848x2960, default mode 120 Hz (renderFrameRate 120.00001; supported 120/60/30), so the frame budget is 8.3 ms. Battery: 25% at the start of the successful run (41% at the end), AC powered, screen awake and unlocked, Stay awake on. A first attempt was rejected by Macrobenchmark with LOW-BATTERY (24%); it ran again once the tab reached 25%.

Target: `:app` benchmark build (`com.ivandsky.puzzlebox.benchmark`), origin/main ecad45b7d, command `ANDROID_SERIAL=<tab serial> ./gradlew :benchmark:connectedBenchmarkAndroidTest`. Only the tab was targeted; the release and debug packages were not touched. 5 iterations per journey, 10 per startup; journeys use CompilationMode None, startup runs Partial and None. Total run 54 min.

## Result: 6 of 17 tests passed, 11 failed

The failures are deterministic (same failure on the retry of every one of them), so the tab has numbers for startup and two tab-switch journeys only. Failures are tab-layout selector problems in the benchmark journeys, not measured slowness:

- GameOpenBenchmark strands, miniSudoku (retry): "Not on screen within 10000ms: DESC Back"; queens, zip, tango: `StaleObjectException` in `leaveGame` called from `skipTutorial` (Games.kt:32, 52). BaselineProfileGenerator.generate: same `StaleObjectException`.
- ArchiveBenchmark.dayTapsAndMonthArrows: "Not on screen within 10000ms: DESC Back" (Games screen, two-pane).
- RanksBenchmark.open, scopePeriodAndBoards and TabSwitchBenchmark.allTabs: "Not on screen within 10000ms: DESC prefix `Board `"; the tab's Ranks screen shows a calendar (Back/Forward, M T W...) but no `Board ...` node.
- ZipDragBenchmark.dragAndClear: `StaleObjectException`.

Fix is in the journeys (tablet two-pane selectors), then rerun those classes; the passing six are a valid baseline.

## Tab numbers (ms)

Startup `timeToInitialDisplayMs` is median (min-max). Frame columns are P50 / P90 / P99.

| Test | TTID | frameDurationCpuMs | frameOverrunMs |
| --- | --- | --- | --- |
| StartupBenchmark.coldPartial | 207.4 (194.6-248.9) | 6.0 / 28.3 / 60.9 | 0.6 / 42.8 / 72.7 |
| StartupBenchmark.coldNone | 226.8 (212.0-259.0) | 4.7 / 43.2 / 116.2 | 0.5 / 70.1 / 145.5 |
| StartupBenchmark.warmNone | 77.7 (68.0-109.7) | 5.3 / 12.9 / 35.5 | -1.3 / 15.8 / 48.2 |
| StartupBenchmark.warmPartial | 63.7 (53.8-102.0) | 5.1 / 10.4 / 28.1 | -1.8 / 8.8 / 32.8 |
| TabSwitchBenchmark.todayAndGames | - | 4.7 / 9.3 / 65.1 | 1.7 / 11.2 / 71.1 |
| TabSwitchBenchmark.createOpen | - | 4.1 / 11.5 / 42.6 | 4.5 / 12.9 / 49.0 |
| GameOpen x5, Archive, Ranks x2, TabSwitch.allTabs, ZipDrag | FAILED, no data | | |

## Pixel 8 Pro numbers (PR #977), same tests

| Test | TTID | frameDurationCpuMs | frameOverrunMs |
| --- | --- | --- | --- |
| StartupBenchmark.coldPartial | 181.3 (171-200.4) | 2.5 / 27 / 74.2 | -12.4 / 39.2 / 67.3 |
| StartupBenchmark.coldNone | 192.7 (182.4-211.9) | 3.6 / 27.3 / 134.2 | -10.5 / 54.6 / 135.7 |
| StartupBenchmark.warmNone | 74.8 (67-90.4) | 2.8 / 7 / 42.1 | -12 / 0.4 / 37 |
| StartupBenchmark.warmPartial | 75 (59-86.5) | 2.5 / 5.9 / 26.5 | -12.1 / -3.9 / 22.6 |
| TabSwitchBenchmark.todayAndGames | - | 3.7 / 6.1 / 32.5 | -11 / -4 / 30.4 |
| TabSwitchBenchmark.createOpen | - | 3.2 / 5.4 / 19.5 | -11.7 / -3.6 / 13.1 |
| TabSwitchBenchmark.allTabs | - | 3.2 / 6.4 / 38.8 | -11.4 / 1.6 / 32.1 |
| GameOpen strands / miniSudoku / queens / zip / tango | - | P50 2.9-3.8, P90 5.3-6.5, P99 35.2-39.9 (tango worst) | P99 30-35.4 |
| ArchiveBenchmark.dayTapsAndMonthArrows | - | 3.9 / 7.2 / 29.6 | -10.7 / -3.2 / 30.2 |
| RanksBenchmark.open / scopePeriodAndBoards | - | 3.4 / 6.2 / 35.2 and 3.9 / 6.8 / 19.2 | P99 31.8 and 25.5 |
| ZipDragBenchmark.dragAndClear | - | 4.9 / 8.2 / 15.3 | -9.3 / -0.9 / 8.5 |

The Pixel row is from a 120 Hz display too; its Android is 17 versus 16 on the tab.

## Verdict against smoothness-glitch-report section 5.4

Thresholds used: frame P50 pass at or under 8 ms, investigate over 8.3; P99 pass at or under 16.6, investigate over 33; P90 is read against the P95 row (pass at or under 11, investigate over 16.6) because the harness gives no P95. Cold-start row is compared with TTID (the harness does not report `am start -W` TotalTime); all are far under 800 ms.

| Test | Tab | Pixel |
| --- | --- | --- |
| coldPartial | investigate (P90 28.3, P99 60.9) | investigate (P99 74.2) |
| coldNone | investigate (P90 43.2, P99 116.2, one or more frames over 100 ms) | investigate (P99 134.2, over 100 ms) |
| warmNone | investigate (P99 35.5; P90 12.9 is between thresholds) | investigate (P99 42.1) |
| warmPartial | P99 28.1 between pass and investigate; P90 10.4 pass | P99 26.5 between pass and investigate; P90 5.9 pass |
| todayAndGames | investigate (P99 65.1) | P99 32.5 between thresholds |
| createOpen | investigate (P99 42.6, P90 11.5 between) | P99 19.5 between thresholds |
| TTID (all startups) | pass (under 800 ms) | pass |
| P50 (all measured) | pass (4.1-6.0) | pass (2.5-4.9) |

No test meets the P99 pass line on either device. Frames over 100 ms occur in cold start on both (coldNone P99 116.2 tab, 134.2 Pixel, so at least one over 100 ms is likely; per-frame counts were not extracted). The tab is slower than the Pixel in every shared row except Pixel's P99 outliers in cold start; tab P50 is about 1.5x and P90 about 1.5-2x the Pixel on warm and tab-switch paths. Game-open, Archive, Ranks and Zip drag have no tab verdict.

## Three worst journeys per device (by CPU frame P99, measured tests only)

Tab: StartupBenchmark.coldNone (116.2), TabSwitchBenchmark.todayAndGames (65.1), StartupBenchmark.coldPartial (60.9). Next: createOpen 42.6.

Pixel: StartupBenchmark.coldNone (134.2), StartupBenchmark.coldPartial (74.2), StartupBenchmark.warmNone (42.1). Across all 16 Pixel tests the next are GameOpen.tango 39.9 and TabSwitch.allTabs 38.8.

Raw JSON and Perfetto traces: `benchmark/build/outputs/connected_android_test_additional_output/` in the bench-tab worktree, copy of the first complete run in `/tmp/run1/`.
