# ADR-0027 — Navigation placement: one owner per content type

Date: 2026-11-09 · Status: accepted · Source: `.scratch/nav-ia/spec.md` (A1–A19) · Amended by ADR-0028 (§2 owner table gains the row "LinkedIn connection | Profile (Accounts list) | —") · Amended 2026-11-10 by the re-gate fixes F1–F8 (§2, §3, §4, §11, §12) · Amended 2026-11-13 by the leaderboard hub (`.scratch/leaderboard-hub/spec.md` A1, A33: §2 owner table, Ranks owns daily leaderboards, the history timeline and lifetime stats) · Amended 2026-11-16 by the nav rethink (`.scratch/nav-rethink/spec.md` N7: §4, §12, Profile and Settings sit in a layer above the tabs) · Amended 2026-11-16 by the nav rethink (`.scratch/nav-rethink/spec.md` N9, N10, A2: §2, §3, §4, §6, §9, §10, §12, Custom in Games takes the place of the Create root and the Studio is immersive; behind the CustomInGames flag, not flipped yet, so §12's base table still applies; flip and removal dates are added here by their tickets) · Amended 2026-11-19 by the Insights epic (`.scratch/insights/spec.md` A11: §2 owner table, placement of the Insights keys)

## Context
The app grew screen by screen. Stats show on five screens, boards on four and the week strip on two, so no screen is
*the* place for them. Profile mixes identity, lifetime stats, a leaderboard, the only setting and a debug link. History
has no home: Today and Profile each push it on their own stack, so back lands somewhere different by entry. Nothing tells
a new feature where it belongs (Duels landed as a Games sub-tab by local choice).

ADR-0011 fixed how navigation moves (one immutable `NavigationState`, one stack per tab). It says nothing about *where
content lives*. This ADR adds that. The reducer, its verbs and its invariants stay as ADR-0011 has them. The tab set
was fixed here at four (shipped in 0.24.0; the NavV2 flag was removed); Profile stops being a tab.
*(Amended 2026-11-13: History's slot became Ranks, §2; the hub's release flag that gated it is deleted.)*
*(Amended 2026-11-16, nav rethink N9, N10; 2026-11-17: the CustomInGames flag shipped on and is deleted, and the
CommunityPuzzles flag followed it in 0.34.3.)* The tab set is Today · Games · Community · Ranks and making puzzles
lives in Community › Yours; no other tab set remains, so §10 and §12 describe the folded form.

## Decision
1. **One owner.** Each content type has one owner screen. Every other screen shows at most one summary row for it, and
   that row opens the owner.
2. **Owner table.** The content contract:

   | Content type | Owner | Allowed summaries elsewhere |
   |---|---|---|
   | Today's set (status per game, play-today) | Today | — |
   | Streak + week strip | Today | — |
   | App update available | Today (the existing pill) | — |
   | Lifetime stats (solved, best streak, no-hint, days) | Ranks | — |
   | History timeline (every past play) | Ranks | Games › game page: "Your history" row; Today: the recent days, and on the tablet the picked day's detail |
   | Per-game stats (solved, hint-free, avg, best) | Games › game page | The block opens the game's stats page (Insights) |
   | Past-day archive (play a past day) | Games › game page | History detail: "Play this day" action |
| Random puzzle (a door, not a content owner: community puzzles stay with the Community tab, the archive with the Games game page) | Games › game page (the Random chip opens the sheet) | — |
   | Daily leaderboards | Ranks | Games › game page: one summary row; Today: "Today's rank: #12 on Zip"; Result: "You: #12 of 340" |
   | Duels | Games › Duels section | Today: "2 duels need you" |
   | Drafts, published puzzles, friends' puzzles (received, enter code) | Community › Yours, Community › Friends | — |
   | Puzzle Studio (make or edit one puzzle) | The Studio, a full-screen mode | — |
   | Identity and sign in/out | Profile (opened by the avatar) | A signed-out prompt *in place* on identity-gated owners |
   | Settings: Appearance, About (version), Developer (debug: Catalog) | Settings (opened by Profile's top-right gear) | — |
   | LinkedIn connection (link state, Resume, Disconnect) | Profile (Accounts list) | — |
   | Recaps (a month's or a year's story, with its share image) | Insights › recap story | Today: a recap card in a period's first days; Profile: the entry and past periods |
   | Stats page (trends, records, rank history, vs friends; one game or all games) | Insights › stats page | Games › game page: the stats block opens it; Profile: the all-games entry |

   A section inside a tab (the update pill) is a valid home. History (what I played) and Archive (play a past day) are
   two content types with two owners, cross-linked by one row each.

   *(Amended 2026-11-13, leaderboard hub A1/A33.)* Ranks is the leaderboard hub. It takes History's tab slot and owns
   the three rows above that name it; the game page, Result and Today keep one summary row each. The tab keeps its
   root key (`HistoryRootKey`) and its `Tab` value (`Tab.History`): only the label, the icons and the test tag
   (`nav.tab.ranks`) change, so the placement table, saved navigation and "go to owner" read the same under either
   tab set. The hub shipped behind a release flag; the flag, the History screen and the game-page board are
   deleted, so the tab always reads Ranks and the route table registers only the hub's entry for the root key. A pushed
   `HistoryKey` opens the hub at that game.

   *(Amended 2026-11-19, Insights epic `.scratch/insights/spec.md` A11.)* Insights owns recaps and the stats page, in
   `feature:insights`. Its three keys are `Home.Shell`: the hub (`InsightsKey`) and the stats page (`InsightsStatsKey`)
   are `Bar`; a recap story (`InsightsRecapKey`) is `Immersive`, like a game's board. None is a tab root, so each is a
   plain push on the current tab (`openOwner`). The routes are registered
   ungated; each entry point that opens one (Today's recap card, Profile's entries, the game page's stats block) arrives
   with its own ticket behind its own release flag (`MonthlyRecap`, `YearRecap`, `StatsPage`), so no screen reaches them
   before then. Ranks stays leaderboards only.

   *(Amended 2026-11-16, nav rethink N9; 2026-11-17: the puzzles moved to Community.)* Create is not a tab. Games has
   two sections, Dailies · Duels. Community owns every puzzle the player made or received, in three sections: New
   today (community puzzles); Friends (received, with Enter code); Yours (New puzzle and Enter code; Continue
   (drafts); Published by you). A section is one new file plus one registration line (nav rethink A1). The Studio and
   the published page are pushed on Community. `feature:create` stays a module (drafts, the editor, publishing, the
   published page); only its tab root goes away.
3. **Chrome types.** Every `NavKey` has one of two:
   - **Bar** shows the tab bar (rail on expanded width). Tab roots, pushed lists and details, Profile, Settings, the
     published page and Catalog are `Bar`.
   - **Immersive** hides the bar or rail: boards, results, a duel leg's board and result, the duel reveal, and the
     Studio.

   *(Amended 2026-11-16, nav rethink N10.)* The Studio (`EditorKey`) is Immersive: no bar or rail, like the game
   board. Back returns to where the player came from. It has four entries, and no other screen opens it:
   - Community › Yours › New puzzle;
   - a draft row in Community › Yours;
   - "Make one" on each game page, `onMakeOne: ((GameId) -> Unit)?`;
   - "Make one like this" on Result, `onMakeOneLikeThis: ((GameId, VariantId?) -> Unit)?`, seeded with that game and
     size.

   Autosave and the tablet side pane stay as they are.

   *(Amended 2026-11-18, nested-sheets 01.)* New duel (`NewDuelKey`) is Immersive and pushed on Games, behind
   `ReleaseFlag.SheetScreens`, on the phone only. Its bar has Back and the screen keeps no tab bar. With the flag off,
   or in the tablet's pane, New duel stays a sheet.

   The avatar is the shell's rule, not a chrome type. There is one avatar on a 48dp target (36dp on the rail, 28dp in
   Today's header), and Profile is its
   only target. On the tablet it sits at the rail's bottom on every non-immersive screen, Profile and Settings
   included. On the phone it sits in Today's header only. Other phone screens show none, and the phone bar is
   unchanged. The shell has no gear; the gear is Profile's own control (§4). The rail is 96dp wide with the tabs
   centred, a 56dp avatar slot, 28dp icons, `labelLarge` labels and 12dp gaps.
4. **Movement rules.**
   - A summary tap switches to the owner's tab and resets it to its root. *(Amended 2026-11-16, #1120.)* A
     link-driven switch (Today's rank, day, streak and duels rows, the game page's rank and "Your history" rows,
     Result's rank chip, anything that opens Custom) leaves the origin tab's stack untouched, and one Back from the
     landed screen returns to the screen the link was tapped on, Result included. The rank links land on Ranks over its
     root, showing that board, day or calendar, and never push onto the current tab. A rail tap is not a link: Back from
     a tab root there still returns to Today. The reducer records the origin as `returnTo` on `NavigationState`
     (`switchTo`); any tab select clears it.
   - Every summary targets an owner's tab root. Routing to an owner that is pushed on a tab (not a root) lands with its
     first caller.
   - *(Amended 2026-11-16, nav rethink N7; shipped in 0.33.0; the nav-doors flag was removed.)* Profile and Settings sit
     in a layer above the tabs, not on the current tab's stack. The avatar opens the layer at Profile, over whatever
     the current tab shows, and no tab's stack ever holds Profile, so it cannot reappear when the user comes back to
     a tab. Settings opens from the gear at Profile's top right and sits on top of Profile inside the layer, so Back
     from Settings returns to Profile and Back from Profile returns to the screen under the layer. Any tab tap closes
     the layer; a tap on the tab under it only closes it. On the tablet the avatar stays visible over the layer, and
     a second tap closes it. A door out of the layer (a summary, a link-in, a game) closes it first.
     Settings is avatar then gear: two taps on the tablet, three on the phone from a tab other than Today.
   - Every summary row is one designsystem `SummaryLinkRow` card (56dp minimum height, extra-large corners, a raised
     surface, a chevron): Today's rank and duels rows and the game page's "Your history" row.
     The duels row opens Games at its top, on the Duels section.
   - *(Amended 2026-11-16, nav rethink N9; 2026-11-17.)* Anything that opens Yours or Friends switches to Community
     at its root, on that section.
   - Profile's content starts at the same padding as Settings' (`Spacing.lg`) under the same top bar.
   - Link-ins land as before, except the puzzle ones: friend link → Community › Friends, shared text → Community › Yours,
     update link → browser.
   - "Go to owner" composes existing reducer verbs (`selectTab` again at a non-root resets, then `push`), as shared text
     does. *(Amended 2026-11-16.)* ADR-0011's `NavigationState` gains the layer (its §1, §2); the tab verbs close an
     open layer on the way.
5. **Badge rule.** Only an owner's tab carries a dot, and only for state that needs the user.
6. **Tab-label rule.** A tab label names the content it owns. There are at most five tabs. *(Amended 2026-11-16, nav
   rethink N9.)* The tabs are Today, Games, Community and Ranks, and the rail has four items. Making a puzzle is an act
   that starts where the urge comes (a game page, Result, Yours), so it has no tab label. The Games pills follow the
   same rule: Dailies and Duels each name what the section owns.
7. **Tag convention.** The owner renders `<type>.*` test tags. A non-owner renders at most one `summary.<type>` row, for
   example `summary.leaderboard`, `summary.duels`, `summary.history`.
8. **The one exemption.** The tablet in-game side panel (`PlayStatsPanel`) stays. It is an immersive in-play companion,
   so it does not break one-owner.
9. **Placement describes the target.** The placement table's homes describe the three-tab information architecture
   (Today · Games · Ranks). `NavigationState.tabs` is the runtime truth for which tabs exist. Before 0.24.0 the tab set
   differed (Profile was a tab, History was not); that is historical. Code that needs the live tab set reads
   `NavigationState`, never the table.
10. **One flag-on convention.** A route or composable never reads a flag. *(Amended 2026-11-16, nav rethink A2.)*
    ViewModels and DI modules may read `ReleaseFlags` (ADR-0022 §3; precedents `ResultViewModel`,
    `ProfileViewModel`); a route or composable gets the result instead. Its entry takes:
    - (a) a nullable callback or a nullable slot object for every new link or block, where `null` is the old form
      (`DuelsSlot` is the precedent);
    - (b) a `Boolean` named for what it hides, defaulting to the old form, only where no callback carries the change; or
    - (c) a source bound in the app graph, where the change is the app's structure rather than one screen's content
      (the tab list was one before 0.34.3).

    No mode enums. One parameter never switches two things. The app passes the values. Cleanup deletes the null or
    default branch.

    The Custom entries, now plain parameters, so code and this text agree:
    - `CustomSlot(published, yours, yoursSummary)`, required by the Community entry; Games takes none;
    - game page `onMakeOne: ((GameId) -> Unit)?`; Result `onMakeOneLikeThis: ((GameId, VariantId?) -> Unit)?`; `null`
      hides the button;
    - the tab list is `topLevelDestinations` (Today, Games, Community, Ranks); no source picks it any more.
11. **Where does new content go?**

    | You add | It goes |
    |---|---|
    | A new screen | A `NavKey`, a row in the placement table, an entry |
    | A new screen's shell chrome | Nothing. The shell draws the avatar itself, and only the designsystem top bars (`LargeTopBar`, `CompactTopBar`) draw `LocalShellActions`, which the app provides to Today's entry on the phone alone. A screen never reads the slot |
    | A new tab | ADR-0011 "Adding a tab": a `Tab` value, a `TopLevelDestination` that carries it, and a `TabRoot` row |
    | A per-game play preference | The game's ⋮ menu (`GameSettingsRepository`) |
    | An app-wide preference | Settings |
    | A summary of another owner's content | One `summary.<type>` row that opens the owner |

12. **The per-key table lives only in code.** `app/src/main/kotlin/com/ivandsky/puzzlebox/app/nav/Placement.kt` holds one
    row per `NavKey`: its home (tab root of T, pushed on T, the shell, or debug-only) and its chrome (`Bar` or
    `Immersive`). A shell key (Profile, Settings) belongs to no tab: it opens in the layer above the tabs (§4).
    Only the bar-hiding predicate (`isImmersive`) derives from the table; where the avatar sits is the shell's
    rule (§3), not a table column. This ADR never copies the table. A Konsist rule (P9, ADR-0009 conventions)
    fails when a production `NavKey` has no row, and its failure text links this ADR.

    *(Amended 2026-11-16, nav rethink N9, A2; 2026-11-17, cleanup 29 and release 0.34.3.)* **Delta placement, folded.**
    While a flag is in flight, its placement is a small delta over the one base table: one override row per key the flag
    moves, never a second full table. The CustomInGames and CommunityPuzzles deltas shipped and were folded into the
    base table with their flags' removal: the Studio (`EditorKey`) is pushed on Community with `Immersive` chrome, the
    published page (`PublishedPuzzleKey`) is pushed on Community with `Bar` chrome, and the Create root is no tab (its
    row, home `Retired`, stays only so an older saved state decodes). No delta remains.

## Rejected
- **A per-key table in this ADR.** It would drift from the code on the first new key. The table is data in
  `Placement.kt`, and P9 guards it.
- **A fifth tab for Profile.** Profile is identity and sign-in, not a place content lives. The avatar opens it, and
  the bar keeps three labels that each name owned content.
- **Dropping the `Tab` enum for the tab's root key.** The enum names a tab independent of the key its stack starts
  with. Keying a tab by its root key would tie the tab's identity to a key class, and a key that moves would move the
  tab.
- **Keeping a Create tab.** Making a puzzle is priority 5 in the player's list, yet it held one of four tabs, and puzzles
  the player made sit apart from puzzles friends sent. Custom in Games holds both, and the Studio opens from where the
  urge to make comes, after playing a game.

## Consequences
- Every new key needs a placement row in the PR that adds it, or P9 fails.
- A new feature has one table to read for where its content goes, and one convention for how it takes the flag.
- Profile, Settings and History (root) get keys and entries before the tab set changes; the shell showed them only when
  the NavV2 flag was on (shipped in 0.24.0; the flag was removed).
- Navigation state restores as ADR-0011 §4 says: `NAVIGATION_STATE_KEY` stays `v1`, no shipped serial name is
  retired, old bundles reconcile (the Profile stack is dropped, the History stack is added fresh).
- *(Amended 2026-11-16, nav rethink N7.)* Profile and Settings live in the layer above the tabs, so Back from Profile
  always returns to the screen under the layer and a tab never comes back showing Profile. A bundle saved before the
  layer existed restores with it closed.
- *(Amended 2026-11-16, nav rethink N9, N10.)* The rail drops to three items and Games gets heavier. The Studio's
  placement moves from pushed on Create (Bar) to pushed on Games (Immersive). Saved navigation that still holds the
  Create stack reconciles the way ADR-0011 §4 describes for a dropped tab stack.
- *(Amended 2026-11-17, nav rethink cleanup 29.)* The CustomInGames flag shipped in 0.34.2 and is deleted with the Create
  tab and the Games Friends pill. `CreateKey` is the one key kept for legacy: it stays registered under its serial name
  `com.ivandsky.puzzlebox.app.nav.Create`, so an old four-tab bundle still decodes (a bundle whose decode fails resets
  every stack), and its `Placement.kt` row has the home `Retired`, which no tab owns. Restore drops its stack.
- *(Done for the hub flag: the flag-off branches and tab set are deleted. `DuelsSlot`-style nullable slots for other features stay.)*
