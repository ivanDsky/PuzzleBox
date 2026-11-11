# ADR-0022 — Trunk delivery: unfinished work merges dark behind release flags

Date: 2026-11-07 · Status: accepted · Source: trunk switch (PROFILE `changes: trunk`)

## Context
Runs used to land as one branch or stack per feature. The branch drifted from main for days
and the PR grew past what a reviewer can read. Every push to main also publishes a release
when the version is new (`release.yml`). Under trunk-based delivery each ticket merges into
main within hours as a small PR (≤ ~400 lines), and main must stay releasable after every
merge. That only works if merging and releasing are separate steps: a half-built feature has
to merge without being reachable in a release build.

## Decision
1. **CI judges every merge.** *(Superseded by ADR-0023: PRs no longer run CI; main runs this
   suite after each merge, before releasing.)* `.github/workflows/pr-check.yml` (job `pr-check`) runs on every PR
   into main and on main itself: `./gradlew assembleDebug test`, which covers the module-graph
   guard, every module's unit tests, `:app`'s release host tests and `:konsist`. Main's branch
   protection requires `pr-check`. Screenshot goldens are recorded on macOS, so CI runs them in
   record mode without comparing pixels (`-Pgoldens.pixelCompare=false` turns off the Tango
   harness's always-on diff); verify them locally with `-Proborazzi.verify=true`.
2. **Release flags are an enum in `core:common`.** `ReleaseFlag` (`com.ivandsky.puzzlebox.common.flags`)
   has one entry per feature in flight, each with a `label` and a `releaseDefault`. Keys are
   typed: code gates on `ReleaseFlag.X` and never looks a flag up by string. Every layer that
   gates (feature, game, domain) can see `core:common`, and ADR-0008's forbidden edges stay
   unchanged.
3. **`ReleaseFlags` is the one reader, and `:app` binds it per build type**, the same pattern as
   `buildTypeGameModules` (ADR-0015). It exposes `enabled: StateFlow<Set<ReleaseFlag>>`; gates
   collect it, or call `isEnabled(flag)` for one-shot checks.
   - Release (`src/release` `buildTypeReleaseFlagsModule`) binds `ShippedReleaseFlags`, which
     holds every `releaseDefault`. Release builds have no override path.
   - Debug (`src/debug`) binds `DebugReleaseFlags`: a local override when one is set, otherwise
     the release default. Overrides live in SharedPreferences (`release_flags`) and change live
     from Catalog → **Release flags** (Profile → Design system catalog, debug builds only).
     Collecting screens re-render without a restart.
4. **Lifecycle, one PR per step.**
   - Ticket 01 of a feature adds the entry with `releaseDefault = false`.
   - Each slice gates its new entry points on the flag. Slices never bump the version and never
     change a `releaseDefault`.
   - User-check runs on a debug build from main with the flag forced on.
   - The **flip PR** sets `releaseDefault = true`, bumps the version and is the release.
   - A cleanup ticket right after the flip deletes the entry and the gated-off branch. A flag
     still in the enum after its feature shipped is debt.
5. **Gate the entry, not the internals.** Flag the place a user could reach the unfinished
   behavior (a navigation entry, a tile, a button, a DI registration). Code behind that point
   merges unflagged. Refactors, tests, additive unused APIs and plumbing need no flag.
6. **No remote flags yet.** Firebase Remote Config is not wired. It becomes an ops or rollout
   flag for a *finished* feature (kill switch, staged rollout) only when one needs it, and a
   release flag is never enabled remotely before its flip PR.

## Consequences
- Release builds still contain gated code, but it is unreachable: R8 keeps it because the
  value is read from the enum at runtime. This is the same trade as the unregistered 9x9
  classes in ADR-0015. The guarantee is unreachability, not absence.
- Changes that cannot be flagged ship live and must stay backward compatible: Room
  migrations, persisted formats, manifest and permissions, dependency or SDK bumps. They merge
  only with the user's approval.
- An empty `ReleaseFlag` means no feature is in flight; the Catalog tab says so.
- Tests over `PuzzleBoxApplication.appModules` resolve the build type's `ReleaseFlags`:
  `testRelease/ReleaseFlagsBindingTest` pins release to `ShippedReleaseFlags`, and
  `testDebug/DebugReleaseFlagsBindingTest` pins debug to the one `DebugReleaseFlags` the
  Catalog tab edits. A test for a gated screen takes `ReleaseFlags` as a parameter and
  covers both flag states.

## Flip history
- FriendSharing flipped to `releaseDefault = true` in 0.17.0 on 2026-11-07. The App Links
  filter for `puzzlebox-f3356.web.app/p/` moved from the debug manifest to the main manifest
  in the same PR.
- The FriendSharing flag was removed, with every branch on it, on 2026-11-07.
- ResultsHero flipped to `releaseDefault = true` in 0.18.0 on 2026-11-08. The e2e graph turned it
  on by default, so the shell tests waited on the hero page (`result.hero.screen`).
- The ResultsHero flag was removed, with the old daily result page and every branch on it, on
  2026-11-08. The e2e graph is back to no flags on.
- HintParity flipped to `releaseDefault = true` in 0.20.0 on 2026-11-09. Queens, Tango and Mini
  Sudoku give one deduction per press; Classic Sudoku, Zip and Strands are unchanged.
- The HintParity flag was removed on 2026-11-10, with the app's `HintModeSource` binding. The
  Queens and Tango legacy hint paths were removed in 37a/37b and 38a/38b. Classic Sudoku keeps
  the legacy hint (A24).
- NavV2 flipped to `releaseDefault = true` in 0.24.0 on 2026-11-10.
