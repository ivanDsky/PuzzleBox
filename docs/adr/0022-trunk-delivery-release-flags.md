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
   harness's always-on diff); verify them locally with `-Proborazzi.verify=true`. *(ADR-0023,
   2026-11-14: the default run no longer runs goldens at all. ADR-0033, 2026-11-17: goldens are
   no longer committed or verified; screenshots are review renders.)*
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
     from Settings → **Release flags** (debug builds only).
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
- The NavV2 flag was removed, with every branch on it, on 2026-11-11.
- Competitions flipped to `releaseDefault = true` in 0.26.0 on 2026-11-11. The Games tab gains
  Dailies / Friends / Duels, the Play tab badge counts duels that need you, and `/m/` links open
  a duel. Hosting, Firestore rules and the App Links filter were live before the flip.
- LinkedInAccount flipped to `releaseDefault = true` in 0.27.0 on 2026-11-11. Profile's Accounts list
  gains a LinkedIn row behind an opt-in risk warning, and the game page shows the player's imported
  result and connections as ghost rows on one combined board. Room 9 and the backup rules were live
  before the flip.
- StreakFreeze flipped to `releaseDefault = true` in 0.28.0 on 2026-11-13. Streaks hold freezes derived from
  solved-day history (never stored); a missed day spends one instead of resetting the streak. Existing
  players' current streaks and bests may rise on first launch. No persisted format, no migration;
  rollback = flip back.
- FullReplay flipped to `releaseDefault = true` in 0.29.0 on 2026-11-14.
- The FullReplay flag was removed, with every branch on it, on 2026-11-14.
- LeaderboardHub flipped to `releaseDefault = true` in 0.32.0 on 2026-11-14 (#969).
- The LeaderboardHub flag was removed, with the History screen, the game-page board and every branch on it, on 2026-11-14 (#974).
- The LinkedInAccount flag was removed, with every branch on it, on 2026-11-14 (#970).
- The nav-doors flag flipped to `releaseDefault = true` in 0.33.0 on 2026-11-16 (nav-rethink M1, PRs #1002–#1009; user check ok on 0.32.0-nav-rethink.1).
- The nav-doors flag was removed, with every branch on it, on 2026-11-16 (nav-rethink M1 cleanup).
- FlatSurfaces, FinishForMe, BoardErgonomics, BoardTokens and HintFirstMove flipped to `releaseDefault = true` together in 0.34.0 on 2026-11-16 (nav-rethink M2 PRs #1014–#1019, #1024; M6 PRs #1042, #1047–#1049, #1053, #1066, #1067, #1070; M7 and M9 PRs #1038, #1039, #1045, #1057, #1060, #1062, #1064, #1069, #1072–#1079; M10 PRs #1035, #1036, #1081–#1084, #1086, #1091, #1095, #1105, #1107; user check ok on 0.33.0-nav-rethink.17 (build 1152); M11 PRs #1105, #1107 and #1115 merged after it). FlatStrands, CustomInGames, ResultMoments and CommunityPuzzles stay off.
- CustomInGames, CommunityPuzzles, UnderlineTabs and ResultMoments flipped to `releaseDefault = true` together in 0.34.2 on 2026-11-17 (nav-rethink M10 and M12 slice PRs, #1165 and ticket 133 for the solve moment; user check ok on 0.34.1-nav-rethink.19 (build 1161); replaces the closed held flips #1041, #1051 and #1063). FlatStrands, PlayFeel and LinkedInPush stay off.
- The ResultMoments flag was removed, with every branch on it, on 2026-11-17 (nav-rethink ticket 39).
- The UnderlineTabs flag was removed, with the Games pill row, the app's Boolean wiring and every branch on it, on 2026-11-17 (nav-rethink M13 cleanup; the underline strip with swipe is the only tab header on Games and Community; user check ok on build 1163).
- TabletCrossfade flipped on in 0.34.3 on 2026-11-17 (#1118, #1119, PR #1142; user check ok on 0.34.0-check-bench.2). The flip and the removal ship as one release PR, at the user's request.
- The CustomInGames flag was removed, with the Create tab, the Games Friends pill, the placement delta that moved the Studio and published page, and every branch on it, on 2026-11-17 (nav-rethink M13 cleanup, ticket 29; the legacy `CreateKey` stays registered only so an old four-tab saved state decodes). Custom's home then moved from Games › Custom to Community › Yours with the CommunityPuzzles removal below.
- The TabletCrossfade and CommunityPuzzles flags were removed in 0.34.3 on 2026-11-17, with every branch on them (the tab set Today, Games, Community, Ranks is the only one). CustomInGames, UnderlineTabs and ResultMoments were already removed on trunk, in the lines above.
- The BoardTokens and HintFirstMove flags were removed on 2026-11-17, with every branch on them (nav-rethink ticket 49 and 49b): the readable board tokens and geometry are the only board look (`PuzzleBoxTheme` lost its `readableBoard` Boolean), the first-move rule is the only hint rule in all five games, and `HintMode.FirstMove`, `Stepwise.firstMove` and `ReplayFirstMove` are gone.
- FlatStrands flipped on, and the FlatSurfaces and FlatStrands flags were removed, on 2026-11-17 (nav-rethink ticket 19c, user-approved), shipping as one release in 0.35.x. Strands goes flat (outlined squares, merged found words, gold spangram) and the lipped look is deleted from designsystem, game-ui, the games and the features; the flat look was already on everywhere else.
- PlayFeel flipped to `releaseDefault = true` in 0.37.0 on 2026-11-18 (#1166; user-approved with the seven other flips of that release): every game ticks, buzzes and confirms the solve; the first applied hint shows its cost line once; a friend or custom puzzle opens How to play first when the tutorial is unseen.
- DuelLineup flipped to `releaseDefault = true` in 0.37.0 on 2026-11-18 (#1388; user-approved with the seven other flips of that release): Customize builds a duel puzzle by puzzle.
- The DuelLineup flag was removed on 2026-11-18, with every branch on it and the count-and-games Customize panel (the rounds stepper and game chips); Customize is the ordered lineup everywhere (#1389).
- WhatsNew flipped to `releaseDefault = true` in 0.37.0 on 2026-11-18 (#1404; user-approved with the seven other flips of that release): Settings, About opens the What's new page.
- RulesSheet flipped to `releaseDefault = true` in 0.37.0 on 2026-11-18 (#1414; user-approved with the seven other flips of that release): How to play opens the rules sheet; `RulesSheetFlagTest` was deleted.
- PlaceIt flipped to `releaseDefault = true` in 0.37.0 on 2026-11-18 (#1429; user-approved with the seven other flips of that release): a Tango, Queens or Mini Sudoku hint offers Place it.
- The PlaceIt flag was removed on 2026-11-18 (#1431), with its `placeIt` parameters, DI reads and flag-off branches: a Tango, Queens or Mini Sudoku hint always carries Place it.
- ShareResult flipped to `releaseDefault = true` in 0.37.0 on 2026-11-18 (#1433; user-approved with the seven other flips of that release): the daily result's Share text carries a solve strip and a link; the hosting page (#1430) was live before the flip.
- The ShareResult flag was removed, with `ResultShare`'s `enabled` parameter, the view model's flag read and the flag-off text, on 2026-11-18 (#1434); the daily result's Share text keeps the solve strip and link as shipped in 0.37.0.
- Reminders flipped to `releaseDefault = true` in 0.37.0 on 2026-11-18 (#1447; user-approved with the seven other flips of that release): Settings gets the Reminders section; `RemindersFlagTest` was deleted.
- The Reminders flag was removed, with the runner's and the Settings view model's `enabled` parameters and every branch on it, on 2026-11-18 (#1448); the daily reminder stays as shipped in 0.37.0.
- The WhatsNew flag was removed, with the Settings view model's `whatsNew` parameter, the DI read and the flag-off About row, on 2026-11-18 (#1405); Settings, About keeps the What's new row as shipped in 0.37.0.
- RandomPuzzle flipped to `releaseDefault = true` in 0.37.0 on 2026-11-18 (#1456; user-approved with the seven other flips of that release): a Random action opens one puzzle from past days or New today.
- The RulesSheet flag was removed, with its DI read and the flag-off route test, on 2026-11-18 (#1415); How to play opens the rules sheet for a game with rules copy and the tutorial for one without, as shipped in 0.37.0.
