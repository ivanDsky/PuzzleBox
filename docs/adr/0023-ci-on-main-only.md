# ADR-0023 — CI runs on main only; PRs merge without a status check

Date: 2026-11-07 · Status: accepted · Supersedes: ADR-0022 decision 1 ("CI judges every merge") · Golden parts superseded by ADR-0033 (2026-11-17: no committed goldens, no verify) · `:app` e2e tier split out of `test` by ADR-0035 (2026-11-19)

## Context
ADR-0022 made `pr-check` (`./gradlew assembleDebug test`) main's required status check, with
`strict` on (branch up to date before merge). Every slice PR waited about 9 minutes for it. Main
merges every 5–10 minutes, and `strict` forced a rebase plus another full run on each one. Only
main wrote the Gradle cache, so PR runs restored a stale cache and rebuilt most modules.
Warm runs took about 3.5 minutes; most runs took 8–9.

Over 52 PR runs it failed twice, and both failures were the CI setup PRs themselves. It never
caught a code defect. The local gate already runs the same suite before a PR opens: the
`review-change` suite runs before every merge, and the slice ticket's DoD requires it. For the
user, the PR is a place to read the change, not a test gate.

## Decision
1. **No CI on PRs.** `.github/workflows/pr-check.yml` is deleted. Main's branch protection
   requires no status check, so `strict` is gone too. Force pushes and branch deletion stay
   blocked.
2. **Main is tested after every merge.** `.github/workflows/release.yml` (workflow "Main") runs
   on every push to main. It runs the old pr-check command first
   (`./gradlew assembleDebug test --continue`: the module-graph
   guard, every module's unit tests, `:app`'s release host tests and `:konsist`), then the
   signed release build and publish. A red test step stops the job before publishing, so a broken
   main never ships a release.
   *Amendment 2026-11-13:* the run is four jobs. Two test jobs run in parallel and together
   cover the same suite: `assembleDebug testCore`, and `testUi` (`:app` and `:feature:*`). A
   `release` job builds and verifies the signed APK beside them. `publish` needs all three, so a
   red test job still blocks the release. CI skips the golden screenshot classes outside `:app`
   (`-Pgoldens.skip`). Without pixel comparison on Linux they could only catch a render crash, and
   they took about 40% of the run. `:app` keeps its goldens (about 80 seconds): with them skipped,
   the Robolectric native graphics runtime aborted the `:app` test JVM on the Linux runners in 6 of
   8 runs.
   *Amendment 2026-11-14 (audit F5):* the default `test` run no longer runs golden classes in any
   module, and `-Pgoldens.skip` is gone (one switch: `-Proborazzi.verify` or `-Proborazzi.record`
   runs them, nothing else does). The old default captured about 1,000 goldens in a mode that
   could not fail. The local merge gate stays `./gradlew assembleDebug test` and gates no golden:
   goldens are macOS-recorded, and the owning ticket verifies the UI modules it changed with
   `-Proborazzi.verify=true` before it merges. `:app` no longer needs a golden to warm up the
   Robolectric native runtime: `NativeGraphicsWarmUpTest` (draw, pixel readback, `IntBuffer` copy,
   PNG encode) runs first in the package root and replaces it. Without it, the default `:app` run
   aborted at `LinkedInGhostIsolationE2ETest` on macOS too (2 of 2 runs); with it, the run is green.
   [INFERENCE] The fix is unproven on the Linux runners; if `:app` aborts there again, revert to
   running `:app`'s goldens in record mode on the `ui` shard. The Showkase browser golden test and its 14
   PNGs are deleted (56 touches in a week for a debug tool). A timing experiment on the 4-vCPU runner put the old single
   job at about 20 minutes worst case, with the CPU saturated (more test forks gained nothing).
   *Amendment 2026-11-18 (#1325):* that warm-up class did not hold for a run that skips it or
   runs it late (`--tests`, or any order where it is not first): the first `ImageDecoder` call in
   a JVM runs a native one-time init that aborts the JVM on a Coil `Dispatchers` worker, and a
   ghost avatar on the Ranks hub made it `LinkedInGhostIsolationE2ETest`'s first decode (8 of 20
   runs aborted under CPU load). `DrainTestApplication.onCreate` now decodes one PNG on the SDK
   main thread, so no test depends on order (0 of 20 aborted).
   *Amendment 2026-11-19 (ADR-0035):* the `ui` job runs `testUi :app:e2eTest`. `:app`'s e2e
   classes moved out of `testDebugUnitTest` into `:app:e2eTest`, so the job still runs every test
   it ran before.
3. **The merge gate is local.** Before an agent merges a slice PR, the full suite
   (`./gradlew assembleDebug test`) must be green on the PR branch rebased onto fresh
   `origin/main`, and the `review-change` fast tier must be clean. A rebase that pulls in new
   main commits runs the suite again before the merge.
   *Amendment 2026-11-08:* the green run must be on the PR rebased onto fresh `origin/main`.
   If main moves while the suite runs, the gate runs again only when the new main commits change
   a file the PR also changes. Otherwise the green run counts and the PR merges. PR #90 passed
   nine runs and never merged, because main moved every 5 minutes. `merge-local.sh` does this.
   *Amendment 2026-11-17:* the gate is debug only:
   `./gradlew assembleDebug test -x :app:testReleaseUnitTest -x :app:testBenchmarkUnitTest`.
   `:app`'s release host tests (`ReleaseGameModulesTest`, `ReleaseUpdateChecksTest`,
   `ReleaseFlagsBindingTest`, `ReleaseShareLinksTest`) compile every module a second time in
   release. `testBenchmarkUnitTest` is disabled, but a disabled task's dependencies still run, so
   it compiled the release-like benchmark variant as well. Without both, the gate drops from
   2,380 to 1,777 tasks and from 110 to 78 Kotlin compile tasks. Up to three agent builds share
   one 8-core machine through the `build-slot` queue, so every changed module paid for the
   second compile. Main's `testUi` job still runs the release host tests after every merge, so
   a release-only break turns main red and rule 4 applies. A ticket that touches release-only
   wiring (`app/src/testRelease`, `app/src/release`, `buildTypeGameModules`, release flags or
   build type config) runs `./gradlew :app:testReleaseUnitTest` before its merge.
   *Amendment 2026-11-18 (One merge lane per repository):* the per-session lane re-ran a suite
   because main moved in 85.2% of batches. Over 3 days it logged 119 requeues; 57% of suites
   tested a single ticket, and the median suite took 4.0 minutes. Pushes to main ran at a median
   of 7/h, p90 14/h, peak 36/h.
   - **Rerun rule.** A suite reruns only when main moved a batch module, the shared build setup
     or the `structure:` rules. A moved dependency or dependent gets a compile check. Anything
     else merges as is.
   - **One lane.** There is one lane per repository, shared by every session and worktree. Its
     state lives in `<git-common-dir>/agent-builds/lane/`.
   - **Batches.** A batch holds at most 5 tickets. Both suites start together across the two
     remote build slots, B stacked on A's top. B's green run stands if main equals A's top after
     A merges; otherwise the rerun rule applies. If A goes red, B is discarded and requeued.
   - **Final outcomes.** Each ends with a next step:
     - `refused` (lasting merge refusal): fix, then re-enqueue.
     - `closed` (user closed the PR): back to the user.
     - `stuck` (requeued 6 times for its own reasons): re-enqueue, count reset.

     A requeue caused by a ticket stacked below never counts toward the 6.
   - **Base race.** "Base branch was modified" right after the batch's own squash, with
     `origin/main` unchanged in tree, is retried up to 3 times. A real main move gets the rerun,
     compile or push verdict.
   - **Commands.** `merge_lane.py enqueue --who <slug> --nn <NN>` (plus
     `--worktree --title --body-file`; see ship-to-trunk `merge-loop.md`), then
     `merge_lane.py wait --who <slug> --nn <NN>`. The old `--queue` is removed.
   - **Consequences.**
     - Capacity is about 30 suites/h, and the projected rerun trigger falls to about 35%. The
       35% comes from replaying the 3-day log; it is not a measurement.
     - B's suite is wasted when A is red.
     - Merges can land out of order across batches. That is accepted.
     - Scratch worktrees live under `.git/agent-builds/lane/wt`; IDEs and backups should
       exclude that path.
     - The lane needs `local.properties` or `ANDROID_HOME`.
   *Amendment 2026-11-19 (ADR-0035):* `test` no longer runs `:app`'s e2e classes. The lane adds
   `./gradlew :app:e2eTest` to a batch's suite when the batch touches `:app`, `:core:navigation`
   or a `:feature:*:api` module (PROFILE `e2e:`). Every other batch merges on the gate alone, and
   main's `ui` job runs the tier after every merge.
4. **A red main stops merges.** The next PR is either a `git revert` of the culprit squash
   commit or a one-line obvious fix forward. Other merges wait until main is green again.
   *Amendment 2026-11-11:* nobody waits for main's run.
   - **Workers.** A worker merges and moves on to its next ticket. It never runs
     `gh run watch` or `gh run view` on main.
   - **Watcher.** Each orchestrator runs one background watcher
     (`ship-to-trunk/scripts/main_health.py watch`). The watcher records each finished Main run
     in the shared ledger `.git/agent-builds/main-health.jsonl`, which is never committed.
   - **Before merging.** Right before `gh pr merge`, an agent runs `main_health.py status`.
     - Exit 3 means main is red: the PR stays open and is recorded as held.
     - Every merge except the claimed fix waits.
   - **Fixing red main.**
     - The orchestrator whose merge in the red range touches the failing module claims the
       incident (`main_health.py claim`).
     - It reverts that merge, or fixes it forward in one line.
     - A claim older than 30 minutes with no merged fix can be taken over.
   - **Back to green.** Held PRs merge under §3's rerun rule.
   - **Why.** Main runs take 17–32 minutes, and 2 of the last 100 failed. Agents had been
     polling main CI after every merge (more than 100 watch calls in one day) and sat idle while
     waiting for an almost-certain green.

   *Amendment 2026-11-18 (flaky tests and test-time downloads):* red has to mean a defect again.
   Of the 20 red main runs in the 9 days before this amendment, 13 were flaky tests, 4 were
   Robolectric artifact downloads, 2 were GitHub and 1 was a defect. One flaky test caused 8 red
   runs over 8 days. The red rate rose from 2% (11-11) to 5.4% (since 11-17).

   | Cause | Runs | Detail |
   |---|---|---|
   | `HintSearchTest` race (`core:game-ui`) | 8 | First red 11-10, fixed 11-18 by #1495. Eight days, eight red runs, including the 130-minute incident |
   | Robolectric artifact download failed at test time | 4 | Took down 110, 303, 148 and 133 tests. No pre-fetch or cache step existed in the workflow or Gradle config |
   | `FullReplayE2ETest` Compose timeout (`:app`) | 2 | |
   | `TangoStepwiseHintTest` | 1 | One more run failed together with `HintSearchTest`; it is counted in that row |
   | `CreatePublishedTabTest`, `DuelUserCheckE2ETest` | 1 each | The first went green on the next run with no fix |
   | GitHub infrastructure (no job started) | 2 | |
   | **Real defect** (konsist `P4UseCaseShapeTest` after #772) | 1 | Fixed forward by #774 in 14 min |

   - **Flaky tests are quarantined in the same incident.** A test that fails on main and passes
     on rerun with no code change gets
     `@Ignore("quarantine #<issue> until <YYYY-MM-DD>")`, which takes it out of the gate and CI.
     The issue owns the fix, and the date is at most 48 hours away. Konsist rule S11 (ADR-0009)
     fails any other `@Ignore` in test sources. It checks the form of the reason, not the date,
     so an expired quarantine never turns main red by itself. `main_health.py status` and `watch`
     print each expired quarantine written in the positional form `@Ignore("…")`, with unchanged
     exit codes; the S11 format makes that form the norm.
   - **Every red run's failing tests go into the main-health ledger.** For each red Main run
     (keyed on run id and attempt) the watcher records the failing test names from the failed
     jobs' log (`Class.method`, else the failed Gradle tasks) and a cause hint: `test`,
     `compile`, `download`, `infra` or `unknown`. The red line in `status` and `watch` names the
     cause and the first failing tests. A `download` or `infra` red names no commit and needs
     no culprit: the failed jobs are rerun (`gh run rerun <id> --failed`), and a green attempt
     turns main green again.
   - **CI downloads nothing at test time.** Robolectric loads its Android runtime jars
     (`android-all-instrumented`, one per SDK) from `~/.m2/repository` and fetched a missing one
     from Maven Central in the middle of the run. A failed fetch logged
     `AssertionError("Failed to fetch maven artifact …")` from `MavenArtifactFetcher`, wrapping an
     `IOException`, and failed every test class of that JVM. The Main test jobs now restore the
     jars from an `actions/cache` entry keyed by the list `ROBOLECTRIC_ANDROID_ALL` in
     `release.yml` (SDK 32 and 34 from `@Config(sdk = …)`, 37 from targetSdk; Robolectric 4.17).
     On a miss, a prefetch step downloads them with retries and checks their SHA-512 before any
     test runs, then saves the entry, so a red test step still keeps it. A failed download
     fails that one step. The setup-gradle cache is unchanged. If a test still downloads a jar
     the list lacks (after a Robolectric bump or a test on a new SDK), the "Check Robolectric
     runtime list" step turns the job red and names the jar to add.

## Consequences
- PRs merge minutes after review instead of after about 9 minutes of CI and rebase churn.
  Runner minutes drop by roughly half: one job per merge instead of a PR run, a main pr-check
  run and a release run.
- A bad merge is caught after it lands, not before it lands. The window lasts one main run of
  about 10 minutes, and no release goes out in it. The workflow's concurrency group keeps one
  pending run: when merges land faster than a run finishes, the runs in between are skipped,
  so a red run can point at several commits. Bisect them locally.
- Nothing on GitHub enforces the local gate. It depends on the agent and the user following
  PROFILE's consent ledger. If merges start breaking main, bring back a required check (a
  warm-cache PR check takes about 3.5 minutes; see `.scratch/ci-speed/`).
- Screenshot goldens gate nothing on CI or in the merge gate. Before the 2026-11-13 amendment they
  all ran without pixel comparison (ADR-0022). Since ADR-0033 they are not committed at all.
- A semantic conflict through a file the PR does not touch can now reach main. Example: #109's
  forced-cells test broke after #89 changed `TangoRules`. Main's release run is the backstop,
  and §4 (red main stops merges) applies.
- Red main means a defect again (amendment 2026-11-18). A quarantined test is a known gap until
  its date. The Robolectric jars (about 545 MB) share the repository's Actions cache quota
  with the Gradle cache, and the first Main run after the amendment proves the cache step.
