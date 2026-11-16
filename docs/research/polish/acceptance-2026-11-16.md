# Device acceptance, polish Phase 1, 2026-11-16

Scope note: device testing was cut short by the user's ruling of 2026-11-16. This is the Pixel benchmark suite, a Pixel startup A/B, and trace analysis; the manual checks and the whole tablet run are not done (see "What was not done, and why"). The criteria table is the section "Success criteria".

## Run

Build under test: origin/main `eb395fdbf` (all fix tickets 01 to 15 merged, splash hold and Baseline Profile included), benchmark install `com.ivandsky.puzzlebox.benchmark` only. The release and debug packages were not touched.

Pixel 8 Pro, Android 17, 120 Hz (render frame rate 120.00, so the frame budget is 8.3 ms), dark theme, font scale 1.0, auto-rotate off, wireless charging, screen awake and unlocked. Command: `ANDROID_SERIAL=<phone address> ./gradlew :benchmark:connectedBenchmarkAndroidTest`, the whole suite, journeys on `CompilationMode.None` (the module default, which the README states; the baseline in `baseline-bench-2026-11-15.md` was taken the same way) and Startup on Partial and None. 16 of 16 tests passed. Gradle runs the classes alphabetically, so the order was Archive, BaselineProfileGenerator, GameOpen, Ranks, Startup, TabSwitch, ZipDrag; Startup therefore ran on an install whose games were already stored. To check that this did not move the startup numbers, Startup was run again alone on a fresh install, together with the pre-fix commit `4981aaf39` (the app before the first polish fix merged) on the same phone the same evening.

Raw JSON and Perfetto traces are kept outside the repo; only numbers are quoted here.

## Pixel numbers, before and after

Before is the Pixel table of `baseline-bench-2026-11-15.md` (PR #977). Frame metrics are `frameDurationCpuMs` P50 / P90 / P99 in ms; the harness also reports P95, quoted where it matters.

| Test | TTID before | TTID after | Frames before | Frames after |
| --- | --- | --- | --- | --- |
| Startup coldPartial | 181.3 | 298.5 (281.3 to 333.9) | 2.5 / 27 / 74.2 | 2.4 / 13.3 / 84.1 |
| Startup coldNone | 192.7 | 419.3 (408.6 to 478.5) | 3.6 / 27.3 / 134.2 | 2.5 / 17.8 / 89.8 |
| Startup warmNone | 74.8 | 109.3 (95.8 to 119.4) | 2.8 / 7 / 42.1 | 2.6 / 6.3 / 41.8 |
| Startup warmPartial | 75 | 93.2 (86.7 to 101.8) | 2.5 / 5.9 / 26.5 | 2.6 / 5.8 / 26.2 |
| TabSwitch todayAndGames | | | 3.7 / 6.1 / 32.5 | 3.6 / 6.1 / 33.2 |
| TabSwitch createOpen | | | 3.2 / 5.4 / 19.5 | 3.4 / 6.1 / 20.6 |
| TabSwitch allTabs | | | 3.2 / 6.4 / 38.8 | 3.2 / 6.3 / 37.0 |
| GameOpen, five games | | | P50 2.9 to 3.8, P90 5.3 to 6.5, P99 35.2 to 39.9 | P50 3.4 to 3.8, P90 5.5 to 6.3, P99 36.3 to 40.1 |
| Archive dayTapsAndMonthArrows | | | 3.9 / 7.2 / 29.6 | 3.5 / 7.0 / 33.9 |
| Ranks open | | | 3.4 / 6.2 / 35.2 | 3.3 / 6.2 / 32.4 |
| Ranks scopePeriodAndBoards | | | 3.9 / 6.8 / 19.2 | 4.1 / 7.0 / 21.6 |
| ZipDrag dragAndClear | | | 4.9 / 8.2 / 15.3 | 3.8 / 7.4 / 17.1 |

Reading against the smoothness report's section 5.4 pass lines (P50 at most 8 ms, P95 at most 11 ms, P99 at most 16.6 ms, no frame over 100 ms in a transition):

- P50 passes everywhere (2.4 to 4.1 ms). P95 (11 ms line) passes in the five GameOpen journeys (9.4 to 10.9), both Ranks journeys and createOpen (10.2); it misses in the four Startup rows (11.3 to 22.2), todayAndGames (11.4), Archive (14.4), allTabs (14.9) and ZipDrag (13.2, which includes the Clear taps). P99 (16.6 ms line) passes in no row; the lowest are createOpen 20.6, Ranks scope 21.6 and ZipDrag 17.1, and every other row is 26 to 90 ms, as before.
- Frames over 100 ms: before, coldNone had a P99 of 134.2 ms, so at least one. After, coldNone still has 2 CPU frames over 100 ms in 10 iterations (P99 89.8); coldPartial P99 went from 74.2 to 84.1. No other journey has a frame over 100 ms.
- Named P99 misses against 16.6 ms, one per journey: coldPartial 84.1, coldNone 89.8, warmNone 41.8, warmPartial 26.2, todayAndGames 33.2, createOpen 20.6, allTabs 37.0, GameOpen strands 40.1, miniSudoku 37.9, queens 38.9, zip 36.3, tango 37.5, Archive 33.9, Ranks open 32.4, Ranks scopePeriodAndBoards 21.6, ZipDrag 17.1. The ZipDrag miss splits: drag frames alone pass (P99 12.2, see the Zip section); the miss comes from the Clear taps.
- The journeys changed little. The polish fixes target things the journeys barely exercise (a stored board, the clock, redraws while idle), so a flat result here is expected. The stored-board, clock and cold-start effects are in the next sections.

## Startup: the TTID increase

`timeToInitialDisplayMs` after the polish merges is higher than the pre-fix baseline on every startup row: coldPartial 298.5 against 181.3 (+65 %), coldNone 419.3 against 192.7 (+118 %), warmNone 109.3 against 74.8 (+46 %), warmPartial 93.2 against 75 (+24 %). The spec's pass line is "no worse than baseline +10 % with the splash hold included". **Named miss on all four TTID rows**, with a stated cause: the spec's TTID criterion measured the blank first frame of the old build, and since the splash hold (#997) TTID ends on the Home frame instead. Whether the criterion should be amended is the user's decision and goes into the milestone digest; this document does not decide it.

The like-for-like row is time to Home content: the end of the frame that first draws the Home content, taken from the frame timelines of the Startup traces (end of the last frame longer than 30 ms in the first 450 ms after the launch slice starts, median of 10 runs, same phone, same evening, both builds fresh-installed).

| Cold start | TTID before | TTID after | Time to Home content before | after | Against the +10 % line |
| --- | --- | --- | --- | --- | --- |
| coldPartial | 174 | 297 | 267 | 270 | TTID miss; Home content pass (+1 %) |
| coldNone | 189 | 419 | 377 | 391 | TTID miss; Home content pass (+4 %) |

The Baseline Profile criterion ("median beats the `CompilationMode.None` baseline") is reported both ways. On TTID it is a miss: coldPartial after (298.5) is above the pre-fix coldNone (192.7), though it is below the post-fix coldNone (419.3); the two are not comparable for the reason above. On time to Home content it passes: coldPartial 270 against the pre-fix coldNone 377 (and against the post-fix coldNone 391).

The measurement was repeated to rule out device state and class order. On the same phone, on a fresh install per run, the pre-fix commit gave coldPartial 174.4 (166.6 to 194.3), coldNone 188.5 (177.2 to 202.2) and warmNone 73.7 (66.3 to 85.4); the current build gave coldPartial 296.8 (287.9 to 333.9) and coldNone 419.2 (406.1 to 439.9). The increase is real and repeatable and does not depend on what data the install held.

Does the splash hold explain it? The frame timeline of the cold starts says it does, in the sense that the metric moved and the user-visible time did not:

- Before the fix, the app's first drawn frame is the blank window (the theme is still being read), at about 150 ms after the launch slice starts. Macrobenchmark's TTID ends at that frame. The Home content (a zero-state Home) arrives about 120 ms later, in the next large frame, finishing at about 267 ms (coldPartial, median of 10, end of the last frame longer than 30 ms) and 377 ms (coldNone).
- After the fix, the first frame the app presents is the Home frame itself: the window's first traversal draws nothing while the splash is up, then one 82 ms frame composes Home with the real state, finishing at about 270 ms (coldPartial) and 391 ms (coldNone). TTID now ends at that frame.
- So the Home content is on screen at the same time as before, within 3 ms for coldPartial and 14 ms for coldNone, and the extra TTID is the blank frame that no longer exists plus the first Home frame now counting as the initial display. The hold itself adds no measurable wait: the state it waits for is ready before the composition that needs it.

Summary: the polish build shows the real Home no later than the old build showed its zero-state Home (+3 ms and +14 ms, inside run spreads of 253 to 314 ms and 360 to 417 ms), and removes the blank frame. The no-blank, no-zero-state claim itself rests on the frame timelines here and on ticket 08's merge-time recording; it was not re-recorded.

Warm starts show the same shift: the first heavy frame is at about 47 ms in both builds, but the launch slice now ends later (about 82 to 105 ms against 49 ms). [INFERENCE] same cause: the activity's first presented frame is the composed Home, not a blank window.

## Zip drag (criterion 6)

The journey's 17.1 ms P99 includes the Clear taps. Taken from the Perfetto traces of the five iterations, selecting only the frames inside the drag gestures (13 drags, 1,541 frames): P50 4.6, P90 6.8, P99 12.2 ms, **no frame over 16.6 ms**, longest 34.4 ms in one frame that sits right after a Clear tap, not in a drag. Each Clear tap produces one 30 to 48 ms frame (Compose `insertMovableContent` plus a 10 to 13 ms measure and layout in one frame); that is a hitch outside the criterion, noted for the consistency phase.

## Game screen redraw cadence (criterion 3, cadence part)

In the ZipDrag traces, idle seconds on the open Zip board produce one frame per second in 25 of 28 quiet seconds (two seconds had two frames, one had none). The clock redraw was 5 per second before the fix (200 ms tick), so the idle cadence fell from 5 to 1 per second.

## What was not done, and why

Device testing stopped at the user's ruling of 2026-11-16, after the Pixel benchmark suite and the startup A/B. The phone's wireless debugging dropped during the A/B and the user chose to wrap up instead of resuming. Not done on the Pixel: the 5-cold-start recording review, stored and unstored game-open recordings (including the "Getting today's puzzle..." placeholder), a 20-switch Ranks scrub review, a rotation test, phone landscape, and a recording of each broken-promise behaviour. Not done on the tablet at all: no post-fix run and no manual checks. The tablet column below is the pre-fix baseline only, from the same journeys at `447013eb2` plus journeys `20fa6d75`.

The two paths ticket 08 left for this ticket are therefore untested on a device: the 1 s splash cap (only `SplashReleasePolicyTest` over an injected clock covers it) and the launcher-icon splash look (shell launches show the solid-colour variant, so it needs a real launcher tap). Ticket 08 recorded three Pixel cold starts at merge with no blank or zero-state frame; that is the only recording evidence.

Of the five promised behaviours plus the landscape gate (criterion 9), none was recorded in this ticket. The tablet's two-pane Recent deselect was never exercised on a device after the fix.

## Success criteria

Status key: **Pass** is measured on the Pixel in this ticket. **JVM** means the behaviour is covered by the named test, green in its ticket's gate at merge, and was not re-run here. **Miss** is a named miss. **Not tested** is not exercised on a device in this ticket (the covering JVM test is named when there is one). Tablet post-fix is not tested in every row, by ruling; only the pre-fix baseline is available there.

| # | Criterion | Pixel | Tablet |
| --- | --- | --- | --- |
| 1a | Each splash release path has a JVM test (Home ready, non-Home launch, read failure, cap) | JVM: `SplashReleasePolicyTest`, `FirstScreenReadyShellTest`, `HomeViewModelTest` | not tested |
| 1b | No blank frame and no zero-state Home in a recording of 5 cold starts per device | Not tested (3 cold starts recorded at merge in #997 showed none; not re-recorded here). The frame timelines of the runs I inspected show no blank frame: the first frame after the splash is the composed Home | not tested |
| 1c | TTID median no worse than baseline +10 % | **Miss.** coldPartial 298.5 against 181.3 (+65 %), coldNone 419.3 against 192.7 (+118 %), warmNone 109.3 against 74.8, warmPartial 93.2 against 75. Cause: TTID now ends on the Home frame, not the blank frame | not tested; no pre-fix startup baseline on the tablet |
| 1d | Like-for-like: time to Home content within +10 % (same traces) | **Pass.** coldPartial 267 to 270 ms (+1 %), coldNone 377 to 391 ms (+4 %) | not tested |
| 1e | After the Baseline Profile, the median beats the `CompilationMode.None` baseline, on TTID | **Miss.** coldPartial 298.5 against the pre-fix coldNone 192.7; below the post-fix coldNone 419.3 | not tested |
| 1f | Same, on time to Home content | **Pass.** coldPartial 270 against the pre-fix coldNone 377 | not tested |
| 1g | The splash 1 s cap on a device | Not tested; JVM: `SplashReleasePolicyTest` | not tested |
| 1h | Launcher-icon splash look | Not tested (needs a launcher tap; shell launches show the solid-colour variant) | not tested |
| 2a | Stored daily: every game shows its board within 300 ms of the tap (warm) | Not tested; the five GameOpen journeys ran green. JVM: `ActivePlayBoardFirstTest`, `SlotOpeningTest`, `RealGamesBoardFirstTest` | not tested; pre-fix GameOpen frame P99 39.5 to 46.0 ms |
| 2b | Zip cold from about 3.3 s to under 600 ms | Not tested | not tested |
| 2c | No centred spinner and no blank frame, stored and unstored (the board-shaped placeholder) | Not tested. JVM: `GameTutorialPendingReadTest`, `SlotOpeningTest` | not tested |
| 3a | Clock label shows the floor of elapsed seconds; final frame equals the result | JVM: `TimerBarRecompositionTest` | not tested |
| 3b | Idle redraw cadence from 5/s to 1/s | **Pass.** 1 frame per second in 25 of 28 quiet seconds on an open Zip board (trace) | not tested |
| 3c | Janky frames on game screens from 11 to 16 % to under 3 % | **Miss as measured.** Frames over their deadline across a whole GameOpen journey: 6.7 to 8.0 %. This includes the open transition and is not the pre-fix metric; no idle-screen reading was taken | not tested |
| 3d | Section 5.4 pass lines over all journeys (P50 8, P95 11, P99 16.6 ms, no frame over 100 ms) | P50 pass everywhere (2.4 to 4.1 ms). P95 partial (misses in four Startup rows, todayAndGames, Archive, allTabs, ZipDrag). **P99 miss** in every journey (named above). **coldNone miss:** 2 frames over 100 ms in 10 runs | not tested; pre-fix baseline cpu P99 16.1 to 46.0 ms |
| 4a | Ranks hero shows only the target board's face after each interruption (data mid-scrub, cancelled touch, move, missing slice) | JVM: `RanksHeroInterruptionTest`, `ScrubCellTest` | not tested |
| 4b | In 20 fast Global to Month switches, no frame shows two values in one line | Not tested (the Ranks journeys ran green: scopePeriodAndBoards P99 21.6 ms; signed-out has no hero values) | not tested |
| 5a | Sign-out then sign-in with different data shows nothing from the first account | JVM: `SeededOnceTest`, `HomeViewModelTest`, `LibraryViewModelTest` | not tested |
| 5b | Leaving Home or Games for over 5 s shows no empty or zero frame | Not tested on a device | not tested |
| 6a | No frame over 16.6 ms during a Zip drag | **Pass.** 13 drags, 1,541 frames: P50 4.6, P90 6.8, P99 12.2 ms, none over 16.6. Also JVM: `ZipDragRecomposeTest` (cell redraw, cancelled pointer, rotation mid-drag) | not tested; pre-fix journey cpu P99 16.1 ms |
| 6b | ZipDrag journey P99 | **Miss, explained.** 17.1 ms, from the Clear taps (one 30 to 48 ms frame each), not from dragging | not tested |
| 7 | Board-first data: a refresh after the first action writes nothing; an equal puzzle writes nothing; two concurrent opens make one request | JVM: `OfflineFirstDailyRefreshTest`, `ActivePlayBoardFirstTest`, `SlotOpeningTest`, `RealGamesBoardSwapTest`. Not a device criterion | JVM only |
| 8a | One layout policy feeds Home, play and Ranks | JVM: `PanePolicyTest`, `PlayLayoutPolicyTest`, `HomeLayoutPolicyTest`, `HistoryLayoutPolicyTest` | JVM only |
| 8b | Back stack and selection survive crossing the phone and two-pane boundary | JVM: `TabSetWidthChangeTest`. Instrumented `RotationDeviceTest` exists but was not run | not tested |
| 9a | Today chip moves the calendar and the day card | Not tested on a device. JVM: `LibraryRouteTest` | not tested |
| 9b | No-replay control is disabled and says "No replay saved" | Not tested on a device (needs a solve). JVM: `YourSolveRowTest` | not tested |
| 9c | Recent has no "See all"; empty state reads "Earlier days will show here" | Not tested on a device. JVM: `HomeRouteTest`, `HomeScreenRecentTest` | not tested |
| 9d | Two-pane: tapping the selected Recent row deselects it | Not applicable on phone. JVM: `HomeScreenRecentTest` | not tested after the fix |
| 9e | A Ranks player row opens the player sheet, also straight after a scope change | Not tested (signed out has no rows). JVM: `RanksScreenRowTapTest` | not tested |
| 9f | Phone landscape keeps bottom navigation and one pane | Not tested on a device. JVM: `PanePolicyTest` (phone landscape stays single pane) | not applicable |
| 10 | Gate: `assembleDebug test`, `:konsist:test`, `checkModuleGraph` green; review-change passed | Each fix PR recorded a green gate at merge. The gate over `eb395fdbf` and review-change are not part of this ticket | not applicable |

Tally of the 31 rows, Pixel column: 4 pass measured (1d, 1f, 3b, 6a); 5 misses (1c, 1e, 3c, 3d, 6b); 7 covered by JVM tests only (1a, 3a, 4a, 5a, 7, 8a, 8b); 14 not tested on a device (1b, 1g, 1h, 2a, 2b, 2c, 4b, 5b, 9a, 9b, 9c, 9d which is not applicable on a phone, 9e, 9f); 1 outside this ticket (10). The tablet has no post-fix result in any row.

## Tablet pre-fix baseline (for the next run)

From `tab-baseline-prefix.md`, 12 journeys, 5 iterations each, Tab S9 Ultra, Android 16, app `447013eb2` plus journeys `20fa6d75`; frame CPU P50 / P90 / P99 ms: GameOpen strands 4.5 / 9.5 / 44.9, miniSudoku 4.4 / 6.9 / 42.8, queens 4.5 / 6.6 / 42.0, zip 3.4 / 6.2 / 39.5, tango 4.6 / 7.2 / 46.0; Archive 5.2 / 9.5 / 28.0; Ranks open 3.5 / 8.5 / 34.0, scopePeriodAndBoards 4.6 / 7.3 / 18.7; TabSwitch allTabs 3.6 / 6.0 / 39.2, todayAndGames 3.7 / 6.1 / 38.5, createOpen 3.5 / 5.5 / 21.7; ZipDrag 5.1 / 7.7 / 16.1. Startup was not run on the tablet. A post-fix tablet run, the two-pane checks (Recent deselect, tablet portrait single-pane, tablet landscape two-pane) and the same startup A/B are the open items for whoever runs the tablet next.
