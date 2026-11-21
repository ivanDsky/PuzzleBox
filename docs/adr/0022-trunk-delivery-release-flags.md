# ADR-0022 — Trunk delivery: unfinished work merges dark behind release flags

Date: 2026-11-07 · Status: accepted · Amended 2026-11-18 (flags belong to their owner; migrating, F1–F7) · Source: trunk switch (PROFILE `changes: trunk`)

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
     still in the enum after its feature shipped is debt. An entry without a named
     `releaseDefault = false` that no production file outside its own file, `debug*` source sets,
     or `:core:testing` reads fails konsist P13 (ADR-0009), so a rebase cannot bring a deleted entry
     back unseen.
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

## Amendment 2026-11-18: flags belong to their owner (report item 4)
Status: migrating, F1–F7. Decision 2 above is replaced and decision 3 extended as each slice lands.

*Context.* All release flags were entries of one enum in `core:common`. 13.8% of a day's commits
edited it, and each edit invalidated 19 modules (69% of main sources, 1,252 test files) though
only 9 read flags. A rebase re-added a removed flag (`LinkedInPush`), which then had no reader.
The reader was optional: `ReleaseFlags?` with a `null` default in 8 files and `getOrNull` in 15
Koin sites, so a missing binding read as "every flag off" with no error. `DomainModule` read the
UI flag `ResultHeroPolish` to choose a use-case parameter.

*Decision.*
1. A release flag is an interface in `core:common` (`com.ivandsky.puzzlebox.common.flags`):
   `key`, `label`, `releaseDefault`. Each module declares the flags it reads as an enum
   `<root>.flags.<Owner>Flag` whose `key` is the entry `name`; that key is the SharedPreferences
   key in `release_flags`, so a debug override survives a flag's move between modules. Keys stay
   typed: code gates on `PlayFlag.X`, never on a string. Until the central enum is deleted (F7)
   the interface is named `ReleaseFlagKey`; F7 renames it `ReleaseFlag`.
2. An owner contributes its flags with `flagSet("<owner>", <Owner>Flag.entries)` in a Koin module
   in its `di` package (`com.ivandsky.puzzlebox.common.di.flagSet`). `:app` aggregates them into
   `DeclaredFlags` (`getAll<FlagSet>()`, ordered by owner, each set in its declaration order). A
   duplicate key throws on first resolve; a repeated owner is caught by konsist P15 (Koin keeps
   the last set). `ShippedReleaseFlags`, `DebugReleaseFlags` and the Settings → Release flags
   panel read `DeclaredFlags`.
3. A flag read by two modules is declared in the lowest module both depend on. `core:domain`
   declares and reads no flag: a use case takes a Boolean, and the Koin module of the feature
   that owns the flag supplies it.
4. `ReleaseFlags` is a required dependency everywhere: no `ReleaseFlags?`, no
   `getOrNull<ReleaseFlags>()`. Tests bind `FakeReleaseFlags`. New reads take it required from F1
   on; the older nullable reads go owner by owner (F4–F7).
5. konsist P15 (ADR-0009) fails on a flag enum not registered with `flagSet` in `main` sources of
   its own module or `:app` (`:app` may register a module that has no Koin), on a repeated owner,
   on a new entry in the central enum and on a frozen name the central enum no longer has. P13
   still fails on a shipped entry with no reader, in any flag enum. P16 fails on any name in the
   flags package in `:core:domain` production sources. A later slice adds a rule for a nullable or
   `getOrNull` flag read.
6. Migration, until the central enum is deleted: `ReleaseFlag` is frozen at its five entries
   (ResultHeroPolish, HintPolish, RanksFreshness, ZipDotPulse, SheetScreens) and `:app`
   registers it as the `common` set. A new flag goes to its owner's enum; a flip of an entry
   still in `ReleaseFlag` edits it in place. A removal or a move deletes the entry and, in the
   same PR, its name from P15's frozen list, which only shrinks. A move slice moves only the
   entries left at its rebase.

   *Amendment 2026-11-19 (#1670, #1677, #1689):* the move slices took four entries to their owners: RanksFreshness to
   `HistoryFlag` (#1670), HintPolish to `GameUiFlag` (#1677), ResultHeroPolish and ZipDotPulse to
   `PlayFlag` (#1689). `ReleaseFlag` and P15's `FROZEN` now hold `SheetScreens` alone.

*Consequences.* A flag's declaration, readers and removal sit in one module; adding, flipping
or removing one retests its owner and `:app`, not 19 modules. A flag enum that is never
registered reads as off even with `releaseDefault = true`; P15 is what prevents it. Release
still holds the gated code and the guarantee is still unreachability (Consequences, first
bullet). The Release flags panel lists flags grouped by owner. "An empty `ReleaseFlag`"
(Consequences) becomes "no flag enum has an entry"; the panel's empty state says so.

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
- The RandomPuzzle flag was removed, with the `RandomPuzzleEntry` holder and the null-`onRandom` gate in the Games route, on 2026-11-18 (#1457); a game page with an archive always has Random, as shipped in 0.37.0.
- The PlayFeel flag was removed on 2026-11-18 (#1167), with `LocalPlayFeel`, the `PlayFeel` DI holder and every flag-off branch: the shared move haptics, the one solve confirm per solve and lesson, the hint cost line and How to play on a friend's or custom puzzle's first open are the only behaviour.
- SheetScreens flipped to `releaseDefault = true` in 0.38.0 on 2026-11-18 (#1563; user-approved with FilterSheets): New duel is a screen on the phone and fills the Duels pane on the tablet, with Add puzzle as a page of it. The central `ReleaseFlag` keeps the entry until its cleanup ticket removes it with its name in P15's `FROZEN`.
- FilterSheets flipped to `releaseDefault = true` in 0.38.0 on 2026-11-18 (#1663; user-approved with SheetScreens; the flag lives in `FilterFlag` since #1646): the Add puzzle picker, Community, Random puzzle and Ranks Who and When use dropdown filter chips that open sheets of options.
- HintPolish flipped to `releaseDefault = true` in 0.38.0 on 2026-11-18 (#1172, #1174; user-approved with the other three flips of that release; the flag lives in `GameUiFlag`): the Hint button reads as a peer of Undo and hint captions give a plain sentence before any technique name.
- RanksFreshness flipped to `releaseDefault = true` in 0.38.0 on 2026-11-18 (#1357; user-approved with the other three flips of that release; the flag lives in `HistoryFlag`): Ranks shows when a day's LinkedIn results were last read, and a pull on the board also asks LinkedIn for that day. RanksTuneBoardOnly stays off.
- The FilterSheets flag was removed, with every branch on it, on 2026-11-19 (#1665); the Add puzzle picker, Community, Random puzzle and Ranks Who and When keep the dropdown filter chips as shipped in 0.38.0, and `FilterFlag` went with its only entry.
- RanksTuneBoardOnly flipped to `releaseDefault = true` in 0.38.1 on 2026-11-19 (#1547, checked by the user on the tablet; the flag lives in `HistoryFlag`): the Ranks filter sheet holds only Who and When, so Ranks has no Tune icon and Opens on and Who can see you live in Settings only.
- The ZipDotPulse flag was removed, with every branch on it, on 2026-11-19 (#1749); on the live board and the replay the next number to reach keeps its trace-coloured circle with the rippling halo, and the thumbnail of an unplayed puzzle stays unmarked.
- The RanksFreshness flag was removed, with every branch on it, on 2026-11-19 (#1749); Ranks keeps its "LinkedIn updated" line under a game's Friends day board and the pull that also asks LinkedIn for that day, both built whenever LinkedIn is in the graph.
- The RanksTuneBoardOnly flag was removed, with every branch on it, on 2026-11-19 (#1749); the Ranks filter sheets hold only Who and When, Ranks has no Tune icon or Tune sheet, and Opens on and Who can see you live in Settings only.
- The HintPolish flag was removed, with every branch on it, on 2026-11-19 (#1749); the Hint button stays a peer of Undo with only its cooldown ring in the game's accent, and Queens, Tango and Sudoku hint captions stay plain sentences before any technique name, `LocalHintPolish` and `GameUiFlag.HintPolish` went with it.
- The ResultHeroPolish flag was removed, with every branch on it, on 2026-11-19 (#1749); the Results hero keeps previous and best as two labelled columns, the signed-out sign-in line under the time, the day's total on the full-set moment and the slow-solve "N+" bar, and a community puzzle's result keeps the daily hero and its two-pane layout; the old custom result card went with the flag.
- The SheetScreens flag was removed, with every branch on it, on 2026-11-19 (#1749); New duel is a screen on the phone and fills the Duels pane on the tablet, with Add puzzle as a page of it, and the central `ReleaseFlag` enum and P15's `FROZEN` set went with its last entry.
- HomeWidget flipped to `releaseDefault = true` in 0.39.0 on 2026-11-21 (#1896, checked by the user on the tablet as #1895; user-approved with the other seven flips of that release; the flag lives in `PlayFlag`): the widget receiver is enabled from the flag, so the launcher offers the home-screen widget with today's rings, the streak and Up next.
- AppShortcuts flipped to `releaseDefault = true` in 0.39.0 on 2026-11-21 (#1843; user-approved with the other seven flips of that release; the flag lives in `PlayFlag`): the app publishes launcher shortcuts for Play Up next and one per game.
- ModeTiles flipped to `releaseDefault = true` in 0.39.0 on 2026-11-21 (#1854; user-approved with ChoiceRows and the other six flips of that release; the flag lives in `GameUiFlag`): the Zip editor's step switch, the Queens editor's Queen | Paint switch and Solution A | B are mode tiles.
- ChoiceRows flipped to `releaseDefault = true` in 0.39.0 on 2026-11-21 (#1854; user-approved with ModeTiles and the other six flips of that release; the flag lives in `TopBarFlag`, whose MainTabsLargeBar stays off): Appearance and the Random puzzle source are radio rows.
- FriendsPastGlobalPage flipped to `releaseDefault = true` in 0.39.0 on 2026-11-21 (#1767, #1779; user-approved with the other seven flips of that release; the flag lives in `HistoryFlag`): Ranks Friends boards read friends who placed past the global top 100, so they count on Day, Week, Month and Overall.
- GamesFilter flipped to `releaseDefault = true` in 0.39.0 on 2026-11-21 (#1707; user-approved with RanksLinkedInDefault and the other six flips of that release; the flag lives in `HistoryFlag`): Ranks has a third filter, Games, with All games or LinkedIn games.
- RanksLinkedInDefault flipped to `releaseDefault = true` in 0.39.0 on 2026-11-21 (#1770; user-approved with GamesFilter and the other six flips of that release; the flag lives in `HistoryFlag`): Settings' Ranks block has the LinkedIn games only row, the Games filter Ranks opens on.
- FirstSolveNoGap flipped to `releaseDefault = true` in 0.39.0 on 2026-11-21 (#1762; user-approved with the other seven flips of that release; the flag lives in `PlayFlag`): a first solve that isn't the player's best leads with its rank instead of the gap to the best.
- The FirstSolveNoGap flag was removed, with every branch on it, on 2026-11-21 (#1976); a first solve that isn't the player's best keeps leading with its rank instead of the gap to the best.
- The RanksLinkedInDefault flag was removed, with every branch on it, on 2026-11-21 (#1976); Settings' Ranks block keeps the LinkedIn games only row and the stored preference (`ranksLinkedInDefault`) stays, and `RanksLinkedInRow` went with the flag.
- The GamesFilter flag was removed, with every branch on it, on 2026-11-21 (#1976); Ranks keeps the Games filter chip (All games or LinkedIn games), the How ranks work line, and landing on the stored LinkedIn games default.
- The FriendsPastGlobalPage flag was removed, with every branch on it, on 2026-11-21 (#1976); the Ranks hub's Friends boards, Home, Result and the game page keep counting a friend who placed past the global page, and `LoadFriendsPastPage.Binding` went with the flag.
- The AppShortcuts flag was removed, with every branch on it, on 2026-11-21 (#1976); the app start always publishes the launcher menu (Play Up next and one per game), and `ShortcutPublisher.clear` went with the flag's off branch.
- The HomeWidget flag was removed, with every branch on it, on 2026-11-21 (#1976); the app start always enables the widget receiver, redraws it and arms its rollover wake-up (the manifest still ships the receiver disabled), and `WidgetComponentSwitch` lost its disable path.
- The ChoiceRows flag was removed, with every branch on it, on 2026-11-21 (#1976); Profile's Appearance and Play's Random source always show as radio rows, and the segmented controls they replaced are gone from both.
- The ModeTiles flag was removed, with every branch on it, on 2026-11-21 (#1976); the Zip editor steps, the Queens editor Queen | Paint and the verdict card Solution A | B always show as tiles, and the segmented control is gone (konsist rule S14 keeps it out).
- MonthlyRecap flipped to `releaseDefault = true` in 0.40.0 on 2026-11-21 (#1617; user-approved with YearRecap and StatsPage after the hands-on tablet check of the story recap redesign, #1824; the flag lives in `InsightsFlag`): Today in the first days of a month and Profile open the month's story recap.
- YearRecap flipped to `releaseDefault = true` in 0.40.0 on 2026-11-21 (#1618; user-approved with MonthlyRecap and StatsPage; the flag lives in `InsightsFlag`): Profile from Dec 20, Today in early January and past years open the year's story recap.
- StatsPage flipped to `releaseDefault = true` in 0.40.0 on 2026-11-21 (#1619; user-approved with MonthlyRecap and YearRecap; the flag lives in `InsightsFlag`): the game page's stats block and Profile open the stats page.
- The MakeOwnLink flag was turned on and removed, with every branch on it, on 2026-11-21 (#1983; user-approved with the other non-epic flags): How to play ends with "Make your own <Game>" beside "Open the lesson", and the full-width Make one button and `LocalMakeOwnLink` went with the flag.
- The PastYouPhoto flag was turned on and removed, with every branch on it, on 2026-11-21 (#1983; user-approved with the other non-epic flags): Past you wears the player's own photo in an opponent-coloured ring with the past mark (New duel's Play against row, the Duels tab rows, the duel page header, the reveal and the replay compare); no photo or signed out keeps the initial; `CompeteFlag` went with its last entry.
- The CutWrongPart flag was turned on and removed, with every branch on it, on 2026-11-21 (#1983; user-approved with the other non-epic flags): sliding back onto the wrong part of the Zip line cuts it there, and the correct part still steps back one.
