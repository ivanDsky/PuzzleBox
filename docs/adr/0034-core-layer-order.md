# ADR-0034 — Layer order inside core: play kit, result UI, domain, data

Date: 2026-11-18 · Status: accepted · Source: architecture review 2026-11-18 (items 6, 7, 10) · Amends ADR-0008 §6, "Adding a game" and Consequences, and ADR-0028 §9 (each carries a dated note)

## Context
`core:game-ui` had two callers. The games (`games:*-ui`) compile against a play kit of 79 files / 4,582 LOC. Features and `:app` used 153 more files / 11,326 LOC of result, stats, daily-board and replay chrome that no game imports. The 18 files that imported `core:domain` were all on the result side, yet that one edge put all five game UIs downstream of domain, data, database and network. In 14 days, 111 of the 232 commits touching the module changed only result or stats files, and each of them retested all five game UIs.

ADR-0008 decision 4 put cross-feature logic in `core:domain`, where 112 of 200 files had one feature consumer. The guard had no layer order inside core: any core module could declare any other.

`core:domain` exposes `core:data` as `api`. Inverting that would spare dependents only when a commit stays inside `core:data`'s internal files. That happened in 26 of 143 such commits in 14 days (18%, about 2% of all commits). It would cost a move of 72 interfaces and edits to 103 domain files.

## Decision
1. **Single-consumer use cases live in their feature.** ADR-0008 decision 4 (as amended 2026-11-18) states the rule and [ADR-0009 P14](0009-in-module-structure-rules.md#p14) reports violations. This ADR only places it in the layer order.
2. **`core:game-ui` is the play kit.** It holds what `games:*-ui` main and test sources compile against, the kit's previews and registries, and `hidden/`. Its project edges are `core:game`, `core:model` (api), `core:designsystem` and `core:common` (`GameUiFlag` implements `ReleaseFlagKey`), plus test-only `core:testing` (`RecordVerdictTest`).
   **`core:result-ui`** (`com.ivandsky.puzzlebox.resultui`) holds result, stats, daily-board (`resultui/board`) and replay player/compare chrome (`resultui/replay`). It depends on `core:game-ui` (api) and may depend on `core:domain`.
   Placement test: a file a game UI needs goes in `core:game-ui`, and must then not need domain. Every other UI file shared by features goes in `core:result-ui`. `GameReplayBoard`, `ReplayPlaybackState`, `ReplayBoardOptions`, `ReplayHintBadge` and `ReplayStepDirection` stay in `gameui/replay`, because games implement or read them.
3. **The guard orders layers on declared edges, in every configuration.** Three arms of `forbiddenEdgeReason` (`ModuleGraphGuard.kt`), placed after every older arm so the older reasons still win:
   - `core:game-ui` and `games:*` declare no edge to `core:domain`, `core:data` or `core:result-ui`;
   - `core:database`, `core:datastore` and `core:network` are declared only by `core:data`, and by `:app` test configurations (`DiGraphTest`, `CloudSyncWorld`, `LinkedInArchiveE2ETest`);
   - `core:designsystem` declares no project edge.

   `games:* → core:testing` from a test configuration stays allowed; no game declares it today.
4. **`core:domain → core:data` stays (api).** Inversion does not pay at an 18% internal-only share. Revisit when either (a) more than 50% of `core:data` commits over a 14-day window touch only files with no public declaration, or (b) a second implementation of the `core:data` seams appears (for example a `backend:*` that replaces the repositories rather than feeding them). Measure (a) with the review's history script.

## Consequences
- Game UIs are no longer downstream of the data layer. Commits that retest all five game UIs fall from 56.1% to 34.4% (3 d) and from 49.8% to 26.3% (14 d). Mean retest load falls from 797 to 733 test files (3 d) and from 745 to 678 (14 d). `core:domain` dependents fall from 14 to 9, and `core:data` dependents from 17 to 14. [simulated on the review's commit window]
- Features and `:app` that show results declare both UI modules. `feature:profile:impl` declares only `core:result-ui`.
- A file a game starts needing moves from `core:result-ui` to `core:game-ui` and must drop its domain types first.
- Adding a game is unchanged. A game UI that reaches for result UI or domain fails `checkModuleGraph` with the ADR-0034 reason.
- The domain → data `api` leak stays a known deviation with a written trigger.
- `ModuleGraphGuardTest` lives in the included build `build-logic`; the root `test` task does not run it, so a guard change runs `./gradlew -p build-logic test` by hand.

## Rejected
- **Inverting `core:domain → core:data` now.** 72 interfaces moved and 103 domain files edited, to spare dependents in about 2% of commits (Context).
- **Splitting by package inside `core:game-ui`.** The guard reads module edges, so one module keeps every game UI downstream of domain however its packages are laid out.
