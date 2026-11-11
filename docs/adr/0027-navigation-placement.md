# ADR-0027 — Navigation placement: one owner per content type

Date: 2026-11-09 · Status: accepted · Source: `.scratch/nav-ia/spec.md` (A1–A19) · Amended by ADR-0028 (§2 owner table gains the row "LinkedIn connection | Settings | —") · Amended 2026-11-10 by the re-gate fixes F1–F8 (§2, §3, §4, §11, §12)

## Context
The app grew screen by screen. Stats show on five screens, boards on four and the week strip on two, so no screen is
*the* place for them. Profile mixes identity, lifetime stats, a leaderboard, the only setting and a debug link. History
has no home: Today and Profile each push it on their own stack, so back lands somewhere different by entry. Nothing tells
a new feature where it belongs (Duels landed as a Games sub-tab by local choice).

ADR-0011 fixed how navigation moves (one immutable `NavigationState`, one stack per tab). It says nothing about *where
content lives*. This ADR adds that. The reducer, its verbs and its invariants stay as ADR-0011 has them. The tab set
becomes Today · Games · History · Create behind `ReleaseFlag.NavV2` (ADR-0022); Profile stops being a tab.

## Decision
1. **One owner.** Each content type has one owner screen. Every other screen shows at most one summary row for it, and
   that row opens the owner.
2. **Owner table.** The content contract:

   | Content type | Owner | Allowed summaries elsewhere |
   |---|---|---|
   | Today's set (status per game, play-today) | Today | — |
   | Streak + week strip | Today | — |
   | App update available | Today (the existing pill) | — |
   | Lifetime stats (solved, best streak, no-hint, days) | History (header) | — |
   | History timeline (every past play) | History | Games › game page: "Your history" row; Today: the recent days with "See all" to History's root, and on the tablet the picked day's detail |
   | Per-game stats (solved, hint-free, avg, best) | Games › game page | — |
   | Past-day archive (play a past day) | Games › game page | History detail: "Play this day" action |
   | Daily leaderboards (Today/Yesterday; the top five, with the player's own row pinned beneath when it ranks lower) | Games › game page | Today: "Today's rank: #12 on Zip"; Result: "You: #12 of 340" and "Full board" |
   | Duels | Games › Duels section | Today: "2 duels need you" |
   | Friends' puzzles (received, enter code) | Games › Friends section | — |
   | Drafts and published | Create | — |
   | Identity and sign in/out | Profile (opened by the avatar) | A signed-out prompt *in place* on identity-gated owners |
   | Settings: Appearance, About (version), Developer (debug: Catalog) | Settings (opened by Profile's top-right gear) | — |
   | LinkedIn connection (link state, Resume, Disconnect) | Settings (opened by Profile's top-right gear) | — |

   A section inside a tab (the update pill) is a valid home. History (what I played) and Archive (play a past day) are
   two content types with two owners, cross-linked by one row each.
3. **Chrome types.** Every `NavKey` has one of two:
   - **Bar** shows the tab bar (rail on expanded width). Tab roots, pushed lists and details, Profile, Settings, the
     Studio and editor, and Catalog are `Bar`.
   - **Immersive** hides the bar or rail: boards, results, a duel leg's board and result, the duel reveal.

   The avatar is the shell's rule, not a chrome type. There is one avatar on a 48dp target (36dp on the rail, 28dp in
   Today's header), and Profile is its
   only target. On the tablet it sits at the rail's bottom on every non-immersive screen, Profile and Settings
   included. On the phone it sits in Today's header only. Other phone screens show none, and the phone bar is
   unchanged. The shell has no gear; the gear is Profile's own control (§4). The NavV2 rail is 96dp wide with the tabs
   centred, a 56dp avatar slot, 28dp icons, `labelLarge` labels and 12dp gaps.
4. **Movement rules.**
   - A summary tap switches to the owner's tab and resets it to its root. Back from that root returns to Today.
   - Every summary targets an owner's tab root. Routing to an owner that is pushed on a tab (not a root) lands with its
     first caller.
   - The avatar opens Profile, pushed on the current tab's stack, so back returns to where the user was. Settings opens
     from the gear at Profile's top right and is pushed on top of Profile, so Back from Settings returns to Profile.
     Settings is avatar then gear: two taps on the tablet, three on the phone from a tab other than Today.
   - Every summary row is one designsystem `SummaryLinkRow` card (56dp minimum height, extra-large corners, a raised
     surface, a chevron): Today's rank and duels rows, the game page's "Your history" row and Result's "Full board"
     row. The duels row opens Games at its top, on the Duels section.
   - Profile's content starts at the same padding as Settings' (`Spacing.lg`) under the same top bar.
   - Link-ins land as before: friend link → Games, shared text → Create, update link → browser.
   - "Go to owner" composes existing reducer verbs (`selectTab` again at a non-root resets, then `push`), as shared text
     does. ADR-0011's `NavigationState` is unchanged.
5. **Badge rule.** Only an owner's tab carries a dot, and only for state that needs the user.
6. **Tab-label rule.** A tab label names the content it owns. There are at most five tabs.
7. **Tag convention.** The owner renders `<type>.*` test tags. A non-owner renders at most one `summary.<type>` row, for
   example `summary.leaderboard`, `summary.duels`, `summary.history`.
8. **The one exemption.** The tablet in-game side panel (`PlayStatsPanel`) stays. It is an immersive in-play companion,
   so it does not break one-owner.
9. **Placement describes the target.** The placement table's homes describe the NavV2 information architecture.
   `NavigationState.tabs` is the runtime truth for which tabs exist. With NavV2 off the tab set differs (Profile is a
   tab, History is not), and the table still describes the target. Code that needs the live tab set reads
   `NavigationState`, never the table.
10. **One flag-on convention.** A feature never reads the flag. Its entry takes:
    - (a) a nullable callback or a nullable slot object for every new link or block, where `null` is the old form
      (`DuelsSlot` is the precedent); and
    - (b) a `Boolean` named for what it hides, defaulting to the old form, only where no callback carries the change.

    No mode enums. One parameter never switches two things. The app passes the values. Cleanup deletes the null or
    default branch.
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
    `Immersive`). Only the bar-hiding predicate (`isImmersive`) derives from it; where the avatar sits is the shell's
    rule (§3), not a table column. This ADR never copies the table. A Konsist rule (P9, ADR-0009 conventions)
    fails when a production `NavKey` has no row, and its failure text links this ADR.

## Rejected
- **A per-key table in this ADR.** It would drift from the code on the first new key. The table is data in
  `Placement.kt`, and P9 guards it.
- **A fifth tab for Profile.** Profile is identity and sign-in, not a place content lives. The avatar opens it, and
  the bar keeps four labels that each name owned content.
- **Dropping the `Tab` enum for the tab's root key.** The enum names a tab independent of the key its stack starts
  with. Keying a tab by its root key would tie the tab's identity to a key class, and a key that moves would move the
  tab.

## Consequences
- Every new key needs a placement row in the PR that adds it, or P9 fails.
- A new feature has one table to read for where its content goes, and one convention for how it takes the flag.
- Profile, Settings and History (root) get keys and entries before the tab set changes; the shell shows them only when
  `NavV2` is on.
- Navigation state restores as ADR-0011 §4 says: `NAVIGATION_STATE_KEY` stays `v1`, no shipped serial name is
  retired, old bundles reconcile (the Profile stack is dropped, the History stack is added fresh).
- When the flag is deleted, cleanup removes the null or default branches and the flag-off tab set.
