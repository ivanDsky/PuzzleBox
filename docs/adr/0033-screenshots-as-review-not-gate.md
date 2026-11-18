# ADR-0033 — Screenshot renders are review evidence: no committed goldens, no verify

Date: 2026-11-17 · Status: accepted · Source: user ruling 2026-11-17 on the golden investigation (agent session logs, 2026-11-01 to 2026-11-17) · Supersedes the golden parts of ADR-0022 §1 and ADR-0023 §2 and its 2026-11-14 amendment.

## Context
Roborazzi tests captured about 1,610 committed golden PNGs (119 MB) in 13 modules. `-Proborazzi.verify=true` compared them pixel for pixel against fresh renders. A UI ticket had to verify every module in its diff before merging and, when a golden failed, prove on a clean main worktree that the failure was not its own.

The investigation of the session logs found:
- 2,577 agent verify or record runs, 646 of them on 2026-11-17 alone. 281 of 1,598 commits touched goldens, 17 of them only to re-record.
- 61 golden failures whose output was kept were classified by hand: 34 were goldens already stale on main, 8 were environment or harness faults, 6 were intended changes and 7 were unclear. None was a real bug.
- 124 clean-main baseline worktrees across 20 sessions, each built only to show that a golden failure was stale.
- 14 of 128 merge conflicts in the logs were on PNGs.
- Agents opened rendered PNGs 1,180 times, and about 102 of those views led to a fix of a real UI defect (6 of 8 spot checks were genuine).

So the pixel compare cost hours a day and caught nothing, while looking at the renders did catch defects. The project has no emulator, and the user checks builds on the tablet.

## Decision
1. **Renders are the agents' eyes, not a gate.** Test classes whose name contains `Golden` or `Screenshot` run only with `-Proborazzi.record=true` (bare `-Proborazzi.record` counts). They write PNGs, mostly into the module's `snapshots/` directory, and the author and reviewer open the PNGs of the screens they changed. A render fails only when the composition throws.
2. **Nothing is committed.** `**/snapshots/` is gitignored and every committed golden is deleted. Tango's renders move from `src/test/resources/goldens/` to `games/tango-ui/snapshots/` like every other module.
3. **No verify, no pixel compare.** `-Proborazzi.verify`, the `roborazziGoldens { harnessModeProperty }` hook, Tango's `GoldenCompare` and `-Pgoldens.pixelCompare` are removed. With nothing to compare against, there is no "prove it fails on clean main" step.
4. **The merge gate is unchanged.** `./gradlew assembleDebug test` excludes the render classes, as before. In render mode the test task is never up to date and never comes from the build cache, because the PNGs are not declared outputs and a render is asked for to see the current code.
5. **Renders stay on the Mac.** They are not sent to remote build hosts: the PNGs are needed on the machine of the agent that looks at them, and Linux font rendering differs.

## Consequences
- A visual regression that nobody looks at is no longer caught by a failing test. The defence is a UI ticket rendering and viewing the screens it changed, plus the user's check on the tablet.
- The repository loses 119 MB of PNGs from the working tree (history keeps them). PNG merge conflicts and re-record commits stop.

## Rejected
- **A curated golden set** (a few dozen hero screens kept and verified): golden compares cannot run on Linux CI or the remote build host, because the goldens are macOS renders, so a curated set would keep the per-ticket verify and clean-main proofs for a smaller but still zero catch rate.
- **A new render flag** (`-Pscreenshots`): agents and `remote-build` already key on `roborazzi.record`, so keeping that name is the smaller change.
