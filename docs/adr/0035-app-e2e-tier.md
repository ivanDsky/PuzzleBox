# ADR-0035 — `:app` e2e tests are a tier, not part of every merge

Date: 2026-11-19 · Status: accepted · Source: architecture review 2026-11-18 (item 5) · Amends ADR-0023 §2 and §3 (each carries a dated note)

## Context
`:app` hosts 167 test files, and 82 of them (14,647 LOC) are e2e tests in
`com.ivandsky.puzzlebox.app.e2e`. Almost every module is a dependency of `:app`, so its tests
reran on 97% of commits. The e2e classes are also the most load-sensitive tests in the build. They
wait on real-time Compose timeouts, so the root build file starts `:app`'s tests only after every
other module's tests have finished. Three red main runs in the 9 days before ADR-0023's 2026-11-18
amendment were e2e timeouts: `FullReplayE2ETest` twice, `DuelUserCheckE2ETest` once.

The review asked for timing data before adopting this. The merge lane's event log has 43 suites
with per-module test time. In them, `:app`'s tests took a median of 163 s (p90 176 s). No other
module took longer, and `:app` was 26% of the summed module test time. The per-class JUnit XML
puts the e2e package at 77% of `:app`'s test time. Two recent lane suites had 70 e2e classes out
of `:app`'s 148 test classes.

## Decision
1. **e2e runs in its own task.** `:app:e2eTest` runs the classes under
   `com.ivandsky.puzzlebox.app.e2e`. It also runs `NativeGraphicsWarmUpTest`, which warms up the
   Robolectric native runtime for that JVM. It reuses `testDebugUnitTest`'s classes, classpath and
   test JVM settings. `testDebugUnitTest` skips the package, and so do `test` and the merge gate
   (`./gradlew assembleDebug test -x :app:testReleaseUnitTest -x :app:testBenchmarkUnitTest`).
   `e2eTest` never runs beside `:app`'s other tests.
2. **The lane runs the tier when a batch touches the shell.** PROFILE has
   `- e2e: ./gradlew :app:e2eTest   # when: app, core:navigation, feature:*:api`. When a batch's
   commits touch `:app`, `:core:navigation` or a feature's `api` module (its NavKeys, the
   contract every entry is built on), ship-to-trunk's merge lane appends the task to the batch's
   suite. It runs in the same check and build slot as the suite, so a red e2e is a red suite. Any
   other batch merges on the gate alone. A shared build setup change on its own does not trigger
   the tier.
3. **Main CI always runs the tier.** The Main workflow's `ui` job runs `testUi :app:e2eTest` after
   every merge. A red e2e turns main red, and ADR-0023 §4 applies.
4. **Outside the lane**, a change to `:app`, `:core:navigation` or a feature's `api` module runs
   `./gradlew :app:e2eTest` by hand before its PR.

## Consequences
- A batch outside those modules no longer pays for the e2e classes. That is about 125 s of test
  time per suite (77% of the 163 s median) [INFERENCE: from the medians, not a measured A/B].
  It also takes the most load-sensitive tests out of the shared build slot.
- A break that only an e2e test catches, made in a feature's `impl` or a `core` module, is found
  after the merge, by main's run (CI median 11 minutes), instead of before it. ADR-0023 §4 covers
  it: a red main stops merges, and the fix is a revert or a one-line fix forward.
- A batch that touches those modules runs the same tests in the same slot as before.
- The trigger works on whole modules. A feature's entry builder lives in `impl` (`HomeEntry`,
  `CreateEntries`), so a change there does not trigger the tier. A change to its NavKeys in `api`
  does.
- Each lane `suite` event has an `e2e` field: the matched modules, whether the tier ran, and its
  wall time. The trigger can be judged again from that log.
- Main's `ui` job covers the same tests as before: `testUi` plus `e2eTest` is what `testUi` used
  to run.

## Rejected
- **Run the tier on main only.** Batches that change the shell itself would merge without the
  tests they are most likely to break.
- **Move the e2e tests into their own module.** The new module would need `:app`'s test
  dependencies and its `sharedTest` sources. A separate task gives the same scheduling and
  touches no test file.
