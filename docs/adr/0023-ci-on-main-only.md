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
4. **A red main stops merges.** The next PR is either a `git revert` of the culprit squash
   commit or a one-line obvious fix forward. Other merges wait until main is green again.

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
