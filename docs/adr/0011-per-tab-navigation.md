# ADR-0011 — Per-tab navigation over one immutable state

Date: 2026-11-04 · Status: accepted · Source: navigation-ux run (design-it-twice nav A/B/C and
play A/B/C, stage-B review rulings) · Supersedes ADR-0010 (kept sessions)

*(Amended 2026-11-09 by ADR-0027: where content lives is decided by one owner per content type, a placement table and chrome types; the tab set becomes Today · Games · History · Create (shipped in 0.24.0; the NavV2 flag was removed). The reducer and its verbs are unchanged. Amended 2026-11-10 by ADR-0027's re-gate fixes: the placement table's chrome is `Bar` or `Immersive`, and the shell draws one avatar through a fourth entry decorator.)*

## Context
Up to 0.8 the app kept one `NavBackStack<NavKey>`: picking a tab reset the stack to that tab's
root, so leaving a half-played game for Home lost the board, and back from History on Home
could land in Play. A game's board lived in the route's `rememberSaveable`, so only one game
could be live and nothing resumed it after the activity was gone for good. 0.9 gives each tab
its own stack, lets any stack show any game's board, and keeps in-progress plays on disk
(ADR-0007 §4).

## Decision
1. **One immutable `NavigationState` (`core:navigation`).** A `@Serializable data class
   NavigationState(tab, stacks)`: the selected tab plus one stack per tab, each stack's first
   key its tab root, the first stack's root the start tab. Invariants are checked in `init`
   (so also on decode): I1 there are stacks and none is empty, I2 roots are distinct, I3 `tab`
   is a tab, I4 a root appears only at index 0 of its own stack. Views: `tabs`, `startTab`,
   `stackOf`, `currentStack`, `visibleTabs` (`[start]` or `[start, tab]`), `visibleKeys`,
   `allKeys`. `toString()` prints spec-row notation (`**Home**[Home] Play[Play, Game]`). Tests
   seed states with the constructor or `navigationState(vararg tabs)`; there is no builder DSL.
2. **The verb rules are pure transitions on the state.** Each verb returns a new state:
   - `selectTab(tab)` switches; reselecting the current tab pops it to its root;
   - `push(key)` pushes on the current stack; pushing a tab root throws (literal misuse);
   - `pushOnce(key)` is `push`, but a no-op when `key` is already on top (double tap);
   - `pop()` removes the top key; at a non-start root it selects the start tab and keeps both
     stacks; at the start root it returns null and the app exits;
   - `replaceTop(key)` swaps the top key (solve → Result, Play again, Next); at a root, or with
     `key` already on top, it returns the same state instead of throwing, so a stale callback
     cannot crash production (the E2E rows assert Result actually appears).

   `Navigator` is a thin `@Stable` holder: `state` is snapshot state with a private setter, and
   each verb is one line applying the reducer verb of the same name (`pop()` returns false at
   the start root). Feature entries receive a `Navigator`, never the stacks.
3. **The shell lives in `app/.../nav/`.** `TopLevelDestination`/`topLevelDestinations` is the tab
   registry (key, label, icon, test tag); `puzzleBoxNavigationState()` builds the fresh state
   from it, so Home is the start tab. `rememberTabEntries` is the only Navigation3 adapter: it
   decorates every tab's stack (saveable state, a per-entry `ViewModelStore`, the scene canvas,
   and a fourth, `shellChromeDecorator` in `ShellChrome.kt`, which supplies the
   one `ShellAvatar` and the shell-actions slot to Today's entry),
   namespaces content keys by tab, tags each entry with `OWNER_TAB` and `ENTRY_KEY` metadata,
   and returns `visibleKeys`' entries. `PuzzleBoxNavShell` hosts `NavDisplay` with `onBack` =
   `Navigator.pop`; `NavTransitions.kt` picks motion from the metadata (owner tab differs →
   fade-through, Result → its own entrance, reduced motion → opacity only). The shell hands
   `state.allKeys` to `PlaySessionStore.retain` whenever the stacks change and calls
   `PlaySessionStore.flush` at activity stop (ADR-0007 §4).
4. **Restore is versioned by one key.** `rememberNavigator` saves the encoded state under
   `NAVIGATION_STATE_KEY = "puzzlebox.navigation.v1"`. A restored state is
   `reconciledWith(fresh)`: stacks whose root is still a tab are kept, missing tabs are added at
   their root, stacks follow the fresh order, and a vanished selected tab falls back to the
   start tab. A bundle without the key (the 0.8 single `NavBackStack`), an undecodable one, or
   one that breaks an invariant starts fresh at Home.
5. **Serial-name policy.** Keys serialize polymorphically over `NavKey`
   (`navKeySavedStateConfiguration`, every feature's `<name>NavKeys()` registered in
   `app/nav/PuzzleBoxNavKeys.kt`). New keys keep the `com.ivandsky.puzzlebox.app.nav.*` serial
   prefix by convention (`…app.nav.Game` is new in 0.9), so every saved key reads alike.
   Compatibility across releases is versioned by `NAVIGATION_STATE_KEY`, not by pins: a change
   that old bundles must not decode into bumps the key, and a key whose shape changed simply
   fails to decode and falls back to a fresh state (a 0.8 `ResultKey(timeMs, hintsUsed)` does).
6. **Play writes coalesce by trailing throttle.** A slot's first session change opens a 300 ms
   window and the window's end writes the latest blob (`SlotWriter`). It is a trailing
   throttle, not a debounce: continuous moves cannot starve the save, and a change is on disk
   within 500 ms. Every save, delete and opening read runs through one store-wide `WriteLane`.
7. **Supersedes ADR-0010 (kept sessions, 0.9–0.10).** The per-slot `saved_plays` rows replace
   the per-game kept-session DataStore. A slot write carries the session's displayed elapsed
   ms (`saved_plays.elapsedMs`, schema v6) beside its blob. `ObserveTodaySet` reads today's
   status from saved rows plus records: Solved beats a save, so a replay never downgrades it.
   Home's "Play today", the hub's status lines, the Play tab dot (any game in progress) and
   Result's Next (`nextUnsolved` over playable games) all read that one set. The 0.10
   `kept_sessions` DataStore file is not migrated: it stays on disk unread, and those
   half-played boards start fresh. A game's first-play tutorial is the `GameKey` entry's
   (`GameViewModel`'s `TutorialGate`), not the store's: while it shows the board's
   composition is gone, so the chrome's clock effect has folded the clock, and nothing
   pauses the store's play.

### Why immutable state, not the recipe's mutable `NavBackStack`s
The Navigation3 multiple-back-stacks recipe keeps one mutable `NavBackStack` per tab plus a
mutable selected-tab field and applies the rules by mutating them. Here the rules (reselect pops,
back at a non-start root returns to the start tab, roots can't be pushed, `replaceTop` at a root
is a no-op) are the product, so they live in one pure reducer: unit-tested without Compose by
`data class` equality, a property test over the transition table ("a successful pop removes
exactly the last visible key", which keeps predictive back honest), invariants that hold on
every value including a decoded one, and one snapshot write per verb so readers never see a
half-applied move across two stacks. Saving is one `encodeToSavedState` of one value under one
versioned key. The cost is a list copy per verb, negligible at these depths. Navigation3 types
stay in `rememberTabEntries`; `core:navigation` exposes only `NavKey`.

## Rejected designs
- **One `navigate(NavIntent)` command (nav A).** With no queue or undo it only makes every call
  site longer; deleting it loses nothing.
- **TabGraph with BackRule/SelectRule chains and TabSpec/TabOwnership through Koin (nav B).**
  There is one rule set; each seam had one adapter, and `topLevelDestinations` already makes a
  new tab one destination plus one stack. B's tab-set normalisation was also dropped in favour
  of `reconciledWith`.
- **A `NavigationStateBuilder` DSL (nav C).** The constructor with its `init` invariants is the
  test seeding API.
- **`replaceTop` throwing at a root (nav C).** A stale callback must not crash.
- **Subscription-count attachment of plays (play A).** Implicit and fragile; the shell calls
  `retain` explicitly.
- **A per-slot debounce (play A/C).** Continuous input can starve it; see §6.
- **A `ProcessLifecycleOwner` flush port (play B).** One adapter, and its ON_STOP arrives about
  700 ms late; the shell's `LifecycleEventEffect(ON_STOP)` flushes instead.
- **Pinning a null-day game's day in `SavedStateHandle` (play C).** The store pins it on first
  open; after process death a null-day key re-resolves to today, and yesterday's save resumes
  through History.
- **An in-memory LRU of plays.** Saved plays live in Room (`saved_plays`), pruned to the newest
  eight slots.
- **`PlaySessionStore` exported as a play:impl effect composable.** It would need a fake S4
  preview; `app` is the composition root and calls `retain`/`flush` itself.

## Adding a tab
1. In the feature's `api` module: a `@Serializable` root key with
   `@SerialName("com.ivandsky.puzzlebox.app.nav.<Name>")`, registered in its `<name>NavKeys()`.
2. In the feature's `impl` module: the `<name>Entry(navigator, …)` builder on
   `EntryProviderScope<NavKey>` that draws the root and any keys the tab pushes.
3. In `app`: one `TopLevelDestination(key, label, icon, "nav.tab.<name>")` in
   `topLevelDestinations` at its bar position (the first entry stays the start tab), the
   `<name>Entry` call in `puzzleBoxEntryProvider`, the `<name>NavKeys()` call in
   `puzzleBoxNavKeys`, and the feature's Koin module in `PuzzleBoxApplication.appModules`.

`NavigationState`, `Navigator`, the shell and the bar stay untouched. A saved state from before
the tab existed restores with the new tab added at its root (`reconciledWith`).

## Consequences
- Each tab keeps its screens' state and ViewModels while another tab is shown; a popped entry
  releases its own (`rememberViewModelStoreNavEntryDecorator`), so ViewModels are per entry.
- Navigation rules change in `NavigationState` only, with a spec-notation test row each.
- A key payload change breaks old bundles into a fresh Home unless `NAVIGATION_STATE_KEY` is
  bumped deliberately; no pin keeps them alive.
- `app` adds `lifecycle-viewmodel-navigation3`; `feature:play:impl` drops its `home:api` edge and
  gains `core:domain`. No new core → non-core or impl → impl edge (ADR-0008).
