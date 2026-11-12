# ADR-0023 — CI runs on main only; PRs merge without a status check

Date: 2026-11-07 · Status: accepted · Supersedes: ADR-0022 decision 1 ("CI judges every merge")

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
   (`./gradlew assembleDebug test --continue -Pgoldens.pixelCompare=false`: the module-graph
   guard, every module's unit tests, `:app`'s release host tests and `:konsist`), then the
   signed release build and publish. A red test step stops the job before publishing, so a broken
   main never ships a release.
3. **The merge gate is local.** Before an agent merges a slice PR, the full suite
   (`./gradlew assembleDebug test`) must be green on the PR branch rebased onto fresh
   `origin/main`, and the `review-change` fast tier must be clean. A rebase that pulls in new
   main commits runs the suite again before the merge.
   *Amendment 2026-11-08:* the green run must be on the PR rebased onto fresh `origin/main`.
   If main moves while the suite runs, the gate runs again only when the new main commits change
   a file the PR also changes. Otherwise the green run counts and the PR merges. PR #90 passed
   nine runs and never merged, because main moved every 5 minutes. `merge-local.sh` does this.
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
- Screenshot goldens are still not pixel-verified on CI (ADR-0022): verify them locally with
  `-Proborazzi.verify=true`.
- A semantic conflict through a file the PR does not touch can now reach main. Example: #109's
  forced-cells test broke after #89 changed `TangoRules`. Main's release run is the backstop,
  and §4 (red main stops merges) applies.
