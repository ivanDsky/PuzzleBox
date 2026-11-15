# Polish phase: findings and approach

Written 2026-11-15 against 0.32.0. This file merges three independent reports into one plan:

| Report | What it is | Method |
|---|---|---|
| [what-makes-an-app-good.md](what-makes-an-app-good.md) | Research: what a good, smooth, delightful daily-puzzle app does, with sources, a 55-item audit checklist and a phased approach | Web research, primary sources cited |
| [fable-ux-review.md](fable-ux-review.md) | Independent design review on ease of use, clear UX intentions and pleasure of use | Claude Fable, cold, ~70 goldens + Compose source, not shown the other reports |
| [smoothness-glitch-report.md](smoothness-glitch-report.md) | Smoothness (jank, startup) and glitchiness (flicker, jumps, state flashes) audit, 23 findings, plus a device measurement protocol | Static code analysis; nothing measured on a device yet |

## Where the app stands

| Axis | Verdict |
|---|---|
| Ease of use | 7 / 10. One tap from Today to a board and humane errors. But nothing brings the player back, and rank, challenge and duels are a few steps from where the player wants them. |
| Clear UX intentions | 6.5 / 10. Each screen is readable alone. Vocabulary drifts between screens, Hint is the loudest button even though it costs rank, and some icons have no labels. |
| Pleasure of use | 7.5 / 10. Strong identity, a good motion system and an excellent duel reveal. Board styling and haptics are uneven, and an ordinary solve gets a muted result. |
| Smoothness / glitches | Medium risk. The motion layer is careful. The risks are cold start (no baseline profile, main-thread startup work), screens that re-seed "empty" after the 5 s `WhileSubscribed` grace and blink on return, and a few per-frame-heavy building blocks (`MorphCell`, the 100-row Ranks board, the Zip grid). |

The research and the review point to the same conclusion. The app needs no new systems: the tokens, reduced motion, `RevealHaptics` and humane states already exist. The polish work is to make it **consistent**, **glitch-free** and **closed-loop**.

## Approach (recommended order)

Juice added on top of jank makes the jank stand out, so glitches come before delight. Each phase is a run of small trunk PRs.

### Phase 0: Measure (prerequisite, small)
- Add a Macrobenchmark + Baseline Profile module and a `benchmark` build type (release-like, `profileable`). Smoothness S-01.
- Record a baseline on the Tab S9 Ultra with the protocol in the smoothness report § 5 (cold start, open each game, solve, Ranks scroll, Create studio). Keep the numbers in this folder.

### Phase 1: Glitches and startup (what users see as "buggy")
1. Stop the blink on return: Home/Library re-seed after the `WhileSubscribed` grace and the cold "0 days · Best 0 / No games yet" flash (S-06, S-07, S-08, S-17).
2. Cold-start chain: `runBlocking` and eager `.start()` calls in `PuzzleBoxApplication`, the blank first composition waiting on DataStore, the launch window colour versus the in-app theme, and chrome padding that jumps from 0 (S-02..S-05).
3. Replay screens blank while loading; one Loading beat before game lessons (S-12, S-21).
4. Ship the Baseline Profile, then re-measure.

### Phase 2: Jank hot paths (only where measurement confirms)
- `MorphCell` per-frame blur (S-09), the eager 100-row animated Ranks board (S-18), Zip recomposing every cell per drag step (S-10), the 5×/s timer recomposition (S-13), and one `ContentObserver` per `rememberReducedMotion()` call (S-16).

### Phase 3: Consistency ("one app, not parts")
- Vocabulary pass: Ranks / Leaderboard / board; rounds / puzzles / leg; queen / crown; colour / color; phone / device (Fable C1, top move 1).
- Label the Share and Play again icons, fix the "Show errors" triangle, separate "Watch again" from "Play again", fix the three truncations and the cryptic archive stat (C3, C5, E7).
- De-emphasise Hint or state its cost (C2).
- Haptic grammar across all five games: Tango has none, Zip only drag ticks, the solve moment is silent. Add one Settings toggle (P2, S-15).
- Route raw motion literals through `MotionTokens`. Re-verify goldens and refresh stale ones; several are known stale (Home title, `docs/screenshots/home.png`).

### Phase 4: Close the loop and add delight
- Rank line and "Challenge a friend" in the result hero (E2, E3).
- Ranks Friends empty state becomes two actions; show the scope/period chips (E5, E6).
- A warmer ordinary-solve line and a real "All done for today" moment (P3).
- One board kit; restyle Queens and Tango to match Zip and Strands (P1). This is the biggest perceived-quality gain and the biggest effort.
- Notifications: daily reminder, streak at risk, duel events (E1). The app has none today; there is no `NotificationCompat` or `POST_NOTIFICATIONS` anywhere. This is the largest retention lever and a feature of its own (permission design, scheduling, FCM for duels).

## Open questions found by the reports

- Fable flagged fixtures that mask real content (duel, reveal, publish and test-solve glyph placeholders; "You — Posted" on a signed-out friend card; the Create empty state listing 3 games, not 5). These are worth a quick check on device before ticketing.
- Custom puzzles skip the tutorial and "How to play". Is that intended?
- Retention proxies (share rate, 7/30-day play) need analytics the app does not have.
