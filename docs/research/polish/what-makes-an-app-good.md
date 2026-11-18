# What makes an app good: pillars, puzzle-app craft, anti-patterns, and a polish audit

Scope: evidence for the PuzzleBox POLISH phase (v0.32.0 alpha; Zip, Queens, Tango, MiniSudoku, Strands, custom puzzles, Friends/Global ranks, 1v1 Duels, phone + tablet). This is a research document, not an app review. Repo and golden references are used only to make the audit actionable. Goldens are Roborazzi PNGs and may be slightly stale.

Source discipline: **[P]** = primary source read directly (platform docs, NN/g, Apple HIG, AOSP). **[S]** = secondary or journalistic source read directly. **[I]** = my inference, not stated by a source. Where the official page was unreadable (Material 3 pages are JS-rendered), I say so.

---

## 1. Pillars of a good app

Android's own quality framing and NN/g's heuristics agree on the shape: an app feels good when it responds instantly, behaves predictably, stays out of the way, and never loses your work. I group the evidence into five pillars. Each has testable criteria.

### 1.1 Ease of use

People should be able to act without explanation. Jonathan Knight, head of NYT Games, says a game must be approachable enough to "hand your phone to someone without explanation" ([Nieman Lab, 2023](https://www.niemanlab.org/2023/09/the-new-york-times-finds-a-match-with-the-word-game-connections)) **[S]**. NN/g's heuristics give the checkable form: *recognition rather than recall* (offer help in context, not a long tutorial), *flexibility and efficiency* (accelerators for experts), *error prevention* (eliminate error-prone conditions or confirm before commit) and *aesthetic and minimalist design* ("every extra unit of information … diminishes their relative visibility") ([NN/g 10 heuristics](https://www.nngroup.com/articles/ten-usability-heuristics/)) **[P]**.

Testable criteria:

- A first-time player reaches an interactive board without reading a paragraph; rules are learnable in context ([heuristic 6 and 10](https://www.nngroup.com/articles/ten-usability-heuristics/)) **[P]**.
- Primary tap targets are at least 48dp x 48dp ([Android accessibility](https://developer.android.com/guide/topics/ui/accessibility/apps), [Compose defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)) **[P]**. Apple's equivalent is 44pt; relevant only for cross-platform comparison.
- Standard back button and back gesture work everywhere, with no custom on-screen back prompts ([core app quality, Back_Button_Nav / Back_Gesture_Nav](https://developer.android.com/docs/quality-guidelines/core-app-quality)) **[P]**.
- Text under 18sp (or under 14sp bold) has contrast of at least 4.5:1; larger text at least 3:1 ([Android accessibility](https://developer.android.com/guide/topics/ui/accessibility/apps)) **[P]**.
- Phone and tablet, portrait and landscape expose the same features and actions ("Display_State_Parity") and the app fills the window with no letterboxing ([core app quality](https://developer.android.com/docs/quality-guidelines/core-app-quality)) **[P]**.

### 1.2 Clarity of intent

The user always knows where they are, what just happened and what to do next. NN/g heuristic 1, *visibility of system status*: feedback "as quickly as possible (ideally, immediately)" and "no action with consequences to users should be taken without informing them" ([heuristics](https://www.nngroup.com/articles/ten-usability-heuristics/)) **[P]**. Heuristic 4, *consistency*: both internal (same thing looks and acts the same everywhere) and external (platform conventions) ([same](https://www.nngroup.com/articles/ten-usability-heuristics/)) **[P]**. Heuristic 9: error messages in plain language that say what went wrong and offer a fix ([same](https://www.nngroup.com/articles/ten-usability-heuristics/)) **[P]**.

Apple's HIG adds a rule I find useful for games: haptics and animation must mean one thing each, and a failure haptic must never double as success ([HIG: Playing haptics](https://developer.apple.com/design/human-interface-guidelines/playing-haptics)) **[P]**.

Testable criteria:

- Every tap produces visible state change or feedback within 0.1s ([NN/g response times](https://www.nngroup.com/articles/response-times-3-important-limits/)) **[P]**.
- One verb per concept across the app (e.g. "Solved", "Finished", "Completed" must not mix) **[I]**, derived from heuristic 4.
- Each error state names the cause and has one clear action (retry, back, update) ([heuristic 9](https://www.nngroup.com/articles/ten-usability-heuristics/)) **[P]**.
- Where an app has a state that survives leaving it (game progress), it restores on return, and back navigation does not silently destroy work ("State_Preservation", [core app quality](https://developer.android.com/docs/quality-guidelines/core-app-quality)) **[P]**.

### 1.3 Pleasure and delight

Delight is not decoration. Apple: motion must be purposeful, never gratuitous; feedback animation should be brief and precise; avoid motion on frequent interactions; don't make people wait for an animation; make motion optional and never the only channel ([HIG: Motion](https://developer.apple.com/design/human-interface-guidelines/motion)) **[P]**. NN/g: "the duration of most animations should be in the range of 100–500 ms"; simple feedback (checkbox, toggle) about 100 ms; substantial screen changes (modal into view) 200–300 ms; "It is far more common for animations to be too long than too short" ([NN/g animation duration](https://www.nngroup.com/articles/animation-duration/)) **[P]**.

Material 3 supplies a token vocabulary. Duration tokens: short1–4 = 50/100/150/200 ms, medium1–4 = 250/300/350/400, long1–4 = 450/500/550/600, extra-long1–4 = 700/800/900/1000 ms. Emphasized decelerate (cubic 0.05, 0.7, 0.1, 1.0) is for entering, about 400 ms; emphasized accelerate (0.3, 0, 0.8, 0.15) is for exiting, about 200 ms ([M3 easing and duration tokens](https://m3.material.io/styles/motion/easing-and-duration/tokens-specs); the official page is JS-rendered, so I read the numbers from the mirror [md3-reference-hub](https://github.com/Glavo/md3-reference-hub/blob/main/docs/styles/motion/easing-and-duration/tokens-specs.md)) **[S]**. The mirror notes this easing/duration system is no longer maintained in favour of the "expressive" spring system; Compose supports springs, so a spring-based consistent system is also valid **[S]**.

Haptics: the best haptic is one users "miss when it's turned off"; keep them short, match intensity to the visual, make them optional ([HIG: Playing haptics](https://developer.apple.com/design/human-interface-guidelines/playing-haptics)) **[P]**. AOSP guidance: press stronger than release; lighter haptics for shallower interactions; repeated texture effects must be subtle; negative sentiment gets a stronger sensation ([AOSP haptics UX](https://source.android.com/docs/core/interaction/haptics/haptics-ux-design)) **[P]**. Compose exposes `HapticFeedbackType` including `Confirm` ([reference](https://developer.android.com/reference/kotlin/androidx/compose/ui/hapticfeedback/HapticFeedbackType.html)) **[P]**.

"Juice" is the game-design term for this layer. The canonical demonstration is Jonasson and Purho's "Juice It or Lose It": a bare Breakout clone gets tweening, particles, sound and screen effects layered on, each toggled on and off to show what it contributes ([talk page](https://www.gamedesign.gg/watch/juice-it-or-lose-it), [YouTube](https://www.youtube.com/watch?v=Fy0aCDmgnxg)) **[S]** (I read the description, not the video). Swink's *Game Feel* defines feel as real-time response, simulated space and polish, with "real-time response" meaning a correction cycle under 100 ms; Liz England's review stresses the model fits action games and has limited fit elsewhere ([review](https://lizengland.com/blog/review-game-feel-by-steve-swink/)) **[S]**. The transferable part for a puzzle game is the <100 ms input-to-feedback bar and the idea that feedback is something you measure, not something you sprinkle **[I]**.

Testable criteria:

- Feedback animations 100–200 ms; screen-level transitions 200–400 ms; nothing user-triggered over 500 ms ([NN/g](https://www.nngroup.com/articles/animation-duration/), [M3 tokens](https://m3.material.io/styles/motion/easing-and-duration/tokens-specs)) **[P/S]**.
- Entering motion decelerates, exiting accelerates and is shorter ([M3 tokens](https://m3.material.io/styles/motion/easing-and-duration/tokens-specs)) **[S]**.
- All motion has a reduced-motion fallback and haptics are optional ([HIG: Motion](https://developer.apple.com/design/human-interface-guidelines/motion), [HIG: Haptics](https://developer.apple.com/design/human-interface-guidelines/playing-haptics)) **[P]**.

### 1.4 Smoothness and reliability

The numbers are the best-defined part of this document.

Frame budget ([Android: render performance](https://developer.android.com/topic/performance/issues/render)) **[P]**: under 16 ms for 60 fps, 11 ms for 90 fps, 8 ms for 120 fps. A frame that overruns even by 1 ms is dropped entirely. Android vitals defines *slow* frames as over 16 ms and *frozen* frames as over 700 ms ("No frames should ever take longer than 700ms"); an ANR is no input response for over 5 s. Initial-frame exceptions at startup or screen transition are common, so the docs track them separately. The recommended triage order is to eliminate the 700 ms frozen frames first.

Startup and responsiveness ([Measuring performance](https://developer.android.com/topic/performance/measuring-performance)) **[P]**: targets of cold start under 500 ms, warm under 200 ms, hot under 150 ms; keep P95/P99 close to the median; target 90 Hz; transitions smooth, "no delays or visual flicker". Play vitals flags excessive startup at cold ≥ 5 s, warm ≥ 2 s, hot ≥ 1.5 s ([launch time](https://developer.android.com/google/play/vitals/launch-time)) **[P]**. Core app quality says the app should load quickly or show feedback if loading takes over two seconds ("App_Startup_Time") ([core quality](https://developer.android.com/docs/quality-guidelines/core-app-quality)) **[P]**.

NN/g's three response-time limits: 0.1 s feels instantaneous (direct manipulation); 1.0 s keeps flow of thought uninterrupted though the delay is noticed; 10 s is the attention limit and beyond it needs a percent-done indicator and a way to interrupt ([NN/g response times](https://www.nngroup.com/articles/response-times-3-important-limits/)) **[P]**. Animations should be timed from a real-time clock, not tied to execution speed (same article) **[P]**.

Stability: Play's bad-behaviour thresholds are 1.09% user-perceived crash rate and 0.47% user-perceived ANR rate overall, and 8% for either on any single phone model ([Android vitals](https://developer.android.com/google/play/vitals)) **[P]**.

Testable criteria: the frame, startup and crash numbers above; plus no frozen frames on core flows; no first-frame flicker on screen transitions.

### 1.5 Trust

Trust is mostly the absence of surprises: data is never lost, scores are fair, notifications and permissions are earned, and nothing nags. NN/g: stacked or stalking overlays fight each other, modals are the most hated overlay type, worse on small screens, and teams should not "ask for location or notification permissions unless it's truly necessary"; a quick audit is to walk top tasks and count modals encountered ([NN/g overlay overload](https://www.nngroup.com/articles/overlay-overload/)) **[P]**. Core app quality requires that game progress is preserved and restored across leaving the foreground and that back navigation does not cause accidental data loss ([State_Preservation](https://developer.android.com/docs/quality-guidelines/core-app-quality)) **[P]**.

For competitive apps, trust also means fair play and honest social context. Wardle (Wordle) was wary of global leaderboards because they shift competition from yourself and friends toward strangers, and removed the link from the share text because it "feels spammy" ([Slate](https://slate.com/culture/2022/01/wordle-game-creator-wardle-twitter-scores-strategy-stats.html)) **[S]**.

---

## 2. What great puzzle apps do

### 2.1 A daily ritual with a bounded session

Wordle: one puzzle a day, the same for everyone, no push notifications, no sign-up, no endless play, no monetization ([Slate](https://slate.com/culture/2022/01/wordle-game-creator-wardle-twitter-scores-strategy-stats.html)) **[S]**. LinkedIn Games: 84% of players return the next day and 80% a week later; one puzzle a day prevents binge play ([Content Marketing Institute](https://contentmarketinginstitute.com/strategy-planning/linkedin-gaming-strategy), [Wired](https://www.wired.com/story/games-are-still-the-best-part-of-linkedin/)) **[S]**. NYT's retention metrics are 7-day and 30-day daily play; Knight calls retention king ([Nieman Lab](https://www.niemanlab.org/2023/09/the-new-york-times-finds-a-match-with-the-word-game-connections)) **[S]**. Over half of NYT Games weekly users play more than one puzzle a day, over a quarter play four or more ([Creative Review](https://www.creativereview.co.uk/new-york-times-games-wordle-crossplay-design-jonathan-knight/), partially read) **[S]**; this supports a "today" hub that moves you smoothly to the next game rather than ending the session. A snippet-only claim that Knight frames the goal as "Time Well Spent" (come back tomorrow, not stay longer) is from an interview I could not open; treat it as unverified.

Human-made puzzles are a recurring pillar: NYT has an editor behind every puzzle, LinkedIn's are human-made ([Nieman Lab](https://www.niemanlab.org/2023/09/the-new-york-times-finds-a-match-with-the-word-game-connections), [Wired](https://www.wired.com/story/games-are-still-the-best-part-of-linkedin/)) **[S]**. Wardle filtered the word list from about 13,000 to about 2,500 so players would not feel cheated ([Slate](https://slate.com/culture/2022/01/wordle-game-creator-wardle-twitter-scores-strategy-stats.html)) **[S]**. The lesson for a polish phase: *fairness is a quality dimension*; an ambiguous or unsolvable puzzle feels like a bug.

**Relevance:** the Home "Today / 1 of 3 done / Play today" card (`docs/screenshots/home.png`, tablet two-pane) is this pattern. The ritual's polish bar is that it must be right on every cold open.

### 2.2 First-run: teach inside the board

Knight: "easy to learn, hard to master"; he and his team discuss how feedback is given and how reactive buttons are so the game feels tactile ([Nieman Lab](https://www.niemanlab.org/2023/09/the-new-york-times-finds-a-match-with-the-word-game-connections)) **[S]**. Wordle's keyboard coloring reflects game state, which is an ease-in aid with no tutorial ([Slate](https://slate.com/culture/2022/01/wordle-game-creator-wardle-twitter-scores-strategy-stats.html)) **[S]**. Monument Valley set out for a short, consumable game that anyone in its audience could *finish*, and introduced each new element slowly so players learn one thing before elements mix ([Polygon](https://www.polygon.com/2014/3/18/5522874/monument-valley-everyone-finish)) **[S]**. NN/g's "help in context, not a long tutorial" makes the same case ([heuristics](https://www.nngroup.com/articles/ten-usability-heuristics/)) **[P]**.

### 2.3 The solve loop: feedback, hints, undo

Good Sudoku (Zach Gage) is the reference for hint design: an AI hint system shows the next logical move and teaches the technique behind it (reportedly more code than the rest of the game), robust notes, removal of busywork (tap a cell to get the only possible number), difficulty levels that explain the skills needed ([The Verge](https://www.theverge.com/21334116/good-sudoku-iphone-ipad-app-game-zach-gage)) **[S]**. NYT Strands gates its hint behind effort: the hint unlocks after three non-theme words ([NYT announcement](https://www.nytimes.com/2024/03/04/crosswords/strands-word-search-game.html); paywalled, only the rules snippet was visible) **[S, partial]**. Principle: hints should teach and be earned or priced, not dump answers **[I]**.

Press feedback in puzzle games is a game-feel matter: <100 ms response, short haptics for discrete events, a stronger one for failure than success only if failure matters ([Swink via England](https://lizengland.com/blog/review-game-feel-by-steve-swink/), [AOSP haptics](https://source.android.com/docs/core/interaction/haptics/haptics-ux-design)) **[S/P]**. Error handling follows NN/g heuristics 3 and 5: undo, and prevent slips with constraints rather than punishing them ([heuristics](https://www.nngroup.com/articles/ten-usability-heuristics/)) **[P]**.

**Relevance:** the repo already uses `HapticFeedbackType` in the shared `core/designsystem/src/main/kotlin/com/ivandsky/puzzlebox/designsystem/PuzzleBoard.kt`, the Queens and Strands boards, and `feature/compete/impl/src/main/kotlin/com/ivandsky/puzzlebox/compete/reveal/RevealHaptics.kt`, which deliberately buzzes nothing for a loss or draw (see its KDoc). That matches the AOSP "don't overuse" guidance; the audit should confirm other boards (Zip, Tango, MiniSudoku) are equally disciplined.

### 2.4 The completion moment and results

Completion is the emotional peak, and it should be brief, specific and repeatable, because players see it daily; HIG warns against motion on frequent interactions ([HIG: Motion](https://developer.apple.com/design/human-interface-guidelines/motion)) **[P]**. LinkedIn's results screen shows your score, average time and which connections played, rather than a true ranking, plus a "better than X% of ..." line ([Wired](https://www.wired.com/story/games-are-still-the-best-part-of-linkedin/)) **[S]**.

**Relevance:** `feature/play/impl/snapshots/results-hero/first-solve-phone-light.png` shows time (1:40), "First solve — this is your time to beat", hints used, "1 of 3 Today", a "Next: Tango" primary action plus share and replay icon buttons. That is a strong shape: it anticipates a multi-puzzle session (see 2.1). The same golden shows a "1 day streak" panel and a timing histogram below the fold.

### 2.5 Sharing

Wardle's emoji grid came from a player; it is spoiler-free; he added it in-game; most sharing is in group chats and with family; people share "for themselves" ([Slate](https://slate.com/culture/2022/01/wordle-game-creator-wardle-twitter-scores-strategy-stats.html)) **[S]**. Share rate was a launch signal at NYT ([Nieman Lab](https://www.niemanlab.org/2023/09/the-new-york-times-finds-a-match-with-the-word-game-connections)); LinkedIn players paste emoji scores in comments ([Wired](https://www.wired.com/story/games-are-still-the-best-part-of-linkedin/)) **[S]**. Share output should be spoiler-free, compact, with one functional link and no promotional links **[S/I]**.

### 2.6 Streaks without guilt

A streak is loss aversion; it is valuable when it feels like encouragement and harmful when it feels like threat. Smashing Magazine's framing: an ethical streak "gives users space to breathe" and imperfection is allowed without fear of losing a long effort; compulsion is when you act because you are "terrified of seeing your progress reset to zero"; a test is whether the product makes money by selling solutions to anxiety it created ([Smashing Magazine, 2026](https://www.smashingmagazine.com/2026/02/designing-streak-system-ux-psychology/)) **[S]**. A 2026 ACM IDC paper found families described streak repair mechanics (freezes, recovery prompts) as *reinforcing obligation* when paired with currency or premium prompts ([ACM](https://dl.acm.org/doi/full/10.1145/3773077.3806127), snippet only) **[S, partial]**. LinkedIn added streak freezes ([CMI](https://contentmarketinginstitute.com/strategy-planning/linkedin-gaming-strategy)) while Wired notes midnight-reset streak anxiety ([Wired](https://www.wired.com/story/games-are-still-the-best-part-of-linkedin/)) **[S]**.

**Relevance:** PuzzleBox ships `feature/play/impl/snapshots/results-hero/freeze-used-phone-light.png`. The audit question is the copy and tone: it should read as relief ("freeze covered yesterday"), never as a debt, and never involve a purchase.

### 2.7 Friends first, rankings second

LinkedIn's boards are scoped by connections, company, school and job title, and notifications are personalised ("beat your average") ([CMI](https://contentmarketinginstitute.com/strategy-planning/linkedin-gaming-strategy)) **[S]**. Wardle's caution about global leaderboards (see 1.5) pairs with this: the Friends board should be the emotionally primary one **[S/I]**.

### 2.8 Restraint

A LinkedIn Games user: the games "don't interrupt it or pollute the play experience with a lot of other things, with pop-ups"; "easy to go into and get out five minutes later" ([Wired](https://www.wired.com/story/games-are-still-the-best-part-of-linkedin/)) **[S]**. Wordle: no notifications, no sign-up, no monetization ([Slate](https://slate.com/culture/2022/01/wordle-game-creator-wardle-twitter-scores-strategy-stats.html)) **[S]**. NN/g's heuristic 8 and overlay-overload article back this ([heuristics](https://www.nngroup.com/articles/ten-usability-heuristics/), [overlays](https://www.nngroup.com/articles/overlay-overload/)) **[P]**. The Verge on Good Sudoku: clean design, learned from hundreds of thousands of generated puzzles curated down, not shipped raw ([The Verge](https://www.theverge.com/21334116/good-sudoku-iphone-ipad-app-game-zach-gage)) **[S]**.

---

## 3. Anti-patterns that make an app feel cheap or glitchy

Each item lists the source or reasoning, then how it shows up in a Compose app.

1. **Layout jumps.** Content that shifts when data arrives (header grows, list insert pushes a button down). Android's guidance wants "no delays or visual flicker" in transitions ([Measuring performance](https://developer.android.com/topic/performance/measuring-performance)) **[P]**. Compose: reserve space for async content, use `animateContentSize` or `Modifier.animateItem` deliberately, and give list items stable keys ([Compose best practices](https://developer.android.com/develop/ui/compose/performance/bestpractices)) **[P]**.
2. **Loading flicker and flashing skeletons.** NN/g: for loads under 1 s, skeletons and spinners are unnecessary and "using a skeleton screen in such situations can be annoying because the quick flashing page can cause users to feel like they can't keep up" ([Skeleton screens](https://www.nngroup.com/articles/skeleton-screens/)) **[P]**. Fix by showing nothing for the first ~1 s, or by delaying the indicator and then holding it for a minimum time **[I]**.
3. **Spinners on cached data.** Local data (Room) should render in the first frame; a spinner for it signals the architecture, not the work. NN/g: spinners suit 2–10 s on a single module; full-screen loads under 10 s want a content-shaped skeleton; frame-only skeletons "are essentially equivalent to a spinner" and should not be used ([Skeleton screens](https://www.nngroup.com/articles/skeleton-screens/)) **[P]**. Stale-while-revalidate is the usual remedy **[I]**.
4. **Animated shimmer overuse.** NN/g: animated skeleton shimmer can be distracting, annoying or an accessibility problem ([same](https://www.nngroup.com/articles/skeleton-screens/)) **[P]**.
5. **Skeletons or blockers hiding slowness.** "Skeleton screens … do not replace performance-optimization efforts" ([same](https://www.nngroup.com/articles/skeleton-screens/)) **[P]**.
6. **Inconsistent motion.** Different durations or easings for the same kind of transition. Heuristic 4 (internal consistency) ([NN/g](https://www.nngroup.com/articles/ten-usability-heuristics/)) **[P]** and tokenised motion ([M3 tokens](https://m3.material.io/styles/motion/easing-and-duration/tokens-specs)) **[S]**. Remedy: one `MotionTokens` object, one reduced-motion gate. PuzzleBox has `MotionTokens`, `rememberReducedMotion()` in `core/designsystem/src/main/kotlin/com/ivandsky/puzzlebox/designsystem/ReducedMotion.kt`, and nav motion in `app/src/main/kotlin/com/ivandsky/puzzlebox/app/nav/ChromeMotion.kt`. The audit should find call sites that bypass them (raw `tween(…)` literals) **[I]**.
7. **Dead taps.** A tap with no visible, audible or haptic result. Violates "feedback as quickly as possible" (heuristic 1) and the <0.1 s limit ([NN/g](https://www.nngroup.com/articles/ten-usability-heuristics/), [response times](https://www.nngroup.com/articles/response-times-3-important-limits/)) **[P]**. Includes disabled controls with no explanation, targets under 48dp, and taps lost to gesture conflicts.
8. **No press feedback.** Missing ripple or pressed state, or a press that fires only on release with no earlier cue. AOSP haptics: press feel should be stronger than release ([AOSP](https://source.android.com/docs/core/interaction/haptics/haptics-ux-design)) **[P]**.
9. **Janky lists.** Lazy lists without `key` and `contentType`, `index`/`hashCode` as keys, nested same-direction scrollables, heavy work in composition, unstable parameters. All from the Android best-practices and measuring pages ([bestpractices](https://developer.android.com/develop/ui/compose/performance/bestpractices), [measuring performance](https://developer.android.com/topic/performance/measuring-performance)) **[P]**. Also: using `Column` for a long list, and `Row`/`Column` is only fine for roughly 1–10 items (best practices) **[P]**.
10. **Modal overload.** Stacked sheets, dialogs and prompts: "the most disruptive and annoying version of overlays is the modal overlay", worse on small screens, and stacked overlays "often are buggy and work against each other" ([NN/g](https://www.nngroup.com/articles/overlay-overload/)) **[P]**. Specific to PuzzleBox: a results sheet over a rematch sheet over a notification-permission prompt would be this **[I]**.
11. **Permission and notification asks at the wrong time.** NN/g: do not ask for notification or location unless truly necessary and desirable ([overlays](https://www.nngroup.com/articles/overlay-overload/)) **[P]**.
12. **Frozen frames, ANRs.** Frames over 700 ms are the first thing to fix; ANR is over 5 s with no input response ([render](https://developer.android.com/topic/performance/issues/render)) **[P]**.
13. **Debug-build polish judgements.** Never judge smoothness in a debug build; Android says never measure debug ([Measuring performance](https://developer.android.com/topic/performance/measuring-performance)) **[P]**. A debug Compose build can look janky when release is fine; the reverse is rarer **[I]**.
14. **Streak and notification guilt.** See 2.6.
15. **State loss.** Rotation, split-screen, process death, back, app-switcher: game progress must survive all of them ([core app quality State_Preservation, Orientation_Transitions](https://developer.android.com/docs/quality-guidelines/core-app-quality)) **[P]**.
16. **Weak error states.** A dead end with only "Something went wrong". Heuristic 9 wants plain language plus a fix ([NN/g](https://www.nngroup.com/articles/ten-usability-heuristics/)) **[P]**. PuzzleBox benchmark: `feature/play/impl/snapshots/friend/error-offline-phone-light.png` reads "You're offline — A new code needs a connection to open. Puzzles you've opened before still play." with a "Try again" action, which is the target pattern: cause, reassurance, one action. Compare the sibling goldens `error-failed-check`, `error-needs-update`, `error-no-longer-available`, `error-not-found`, `error-unknown-game` for the same standard.

---

## 4. Polish audit checklist

Run each item screen by screen on **phone (320dp and 411dp wide) and tablet**, **light and dark**, in a **release or profileable build** on a mid-range device. Screens to cover: Home, Play (game list, each of the five games' boards, hints, results), History, Profile, Create (editors), Compete (Ranks, Duels intro/race/reveal/compare), error and empty states, first run. The existing goldens give a quick visual baseline, e.g. `feature/play/impl/snapshots/results-hero/*`, `feature/play/impl/snapshots/friend/*`, `feature/compete/impl/snapshots/*` (compare-*, intro, race, rematch-sheet, summary-*), `games/*-ui/snapshots/`, `app/snapshots/`, and `docs/screenshots/home.png`. They may be stale; re-verify with Roborazzi before relying on them.

### A. Ease of use

- [ ] A new user gets from install to a playing board in at most two taps with no modal in between. ([overlay overload](https://www.nngroup.com/articles/overlay-overload/))
- [ ] Each game explains its rules in context (first-run coach or inline hint), not a text wall. ([heuristics 6, 10](https://www.nngroup.com/articles/ten-usability-heuristics/))
- [ ] A friend could be handed the phone and start playing with no verbal instructions. ([Nieman Lab](https://www.niemanlab.org/2023/09/the-new-york-times-finds-a-match-with-the-word-game-connections))
- [ ] The home "Play today" action lands on the correct next unfinished game. ([2.1](https://www.creativereview.co.uk/new-york-times-games-wordle-crossplay-design-jonathan-knight/))
- [ ] Every tappable element measures at least 48dp x 48dp (board cells exempt only where the board is the target), including icon buttons in results (share, replay). ([Android a11y](https://developer.android.com/guide/topics/ui/accessibility/apps))
- [ ] Back button and back gesture work on every screen and never trap the user; no custom on-screen back prompt. ([core quality](https://developer.android.com/docs/quality-guidelines/core-app-quality))
- [ ] Tablet two-pane and phone single-pane expose the same actions; no feature exists on one only. ([Display_State_Parity](https://developer.android.com/docs/quality-guidelines/core-app-quality))
- [ ] Tablet layouts fill the window, with no awkward empty half pane (see `docs/screenshots/home.png`, where the right pane floats a content card in blank space). ([Fullscreen_Display](https://developer.android.com/docs/quality-guidelines/core-app-quality))
- [ ] Text meets 4.5:1 contrast (below 18sp) or 3:1 (large) in both themes, including muted captions and the green primary button label. ([Android a11y](https://developer.android.com/guide/topics/ui/accessibility/apps))
- [ ] Common slips are prevented by constraints; destructive actions (give up, abandon duel) confirm or offer undo. ([heuristic 3, 5](https://www.nngroup.com/articles/ten-usability-heuristics/))
- [ ] Expert accelerators exist where they help (e.g. drag-to-fill, long-press, quick undo). ([heuristic 7](https://www.nngroup.com/articles/ten-usability-heuristics/))

### B. Clarity of intent

- [ ] Every screen has one visually dominant primary action. ([heuristic 8](https://www.nngroup.com/articles/ten-usability-heuristics/))
- [ ] One verb per concept in copy across all screens and modules (solved/finished/completed, rank/place, friend/connection). ([heuristic 4](https://www.nngroup.com/articles/ten-usability-heuristics/))
- [ ] Every error state states the cause in plain language and offers one action; compare all `error-*` goldens to the `error-offline` standard. ([heuristic 9](https://www.nngroup.com/articles/ten-usability-heuristics/))
- [ ] Every empty state (no friends, no history, no rank yet) says why it is empty and what to do next. ([heuristic 1](https://www.nngroup.com/articles/ten-usability-heuristics/))
- [ ] Disabled controls show why they are disabled (or hide). ([heuristic 1](https://www.nngroup.com/articles/ten-usability-heuristics/))
- [ ] Ranks tab labels (Friends/Global, Day/Week/Month/Overall) always show which scope you are viewing, and what "score" means. ([heuristic 1, 2](https://www.nngroup.com/articles/ten-usability-heuristics/))
- [ ] Leaving a game and returning (back, app switch, rotation, process death) restores board state and timer exactly. ([State_Preservation](https://developer.android.com/docs/quality-guidelines/core-app-quality))
- [ ] Rotation or fold change mid-puzzle causes no rendering problem and no lost progress. ([Orientation_Transitions](https://developer.android.com/docs/quality-guidelines/core-app-quality))
- [ ] Haptic and sound meanings are consistent: one event type, one effect, across all five games and Duels. ([HIG haptics](https://developer.apple.com/design/human-interface-guidelines/playing-haptics))
- [ ] Share text is spoiler-free, compact, with one functional link and no promotional links. ([Slate](https://slate.com/culture/2022/01/wordle-game-creator-wardle-twitter-scores-strategy-stats.html))

### C. Pleasure and delight

- [ ] Input-to-feedback in every game cell tap is under 100 ms (visible state change). ([Swink via England](https://lizengland.com/blog/review-game-feel-by-steve-swink/), [NN/g](https://www.nngroup.com/articles/response-times-3-important-limits/))
- [ ] Simple feedback animations last about 100 ms; screen transitions 200–400 ms; nothing user-triggered exceeds 500 ms. ([NN/g](https://www.nngroup.com/articles/animation-duration/), [M3 tokens](https://m3.material.io/styles/motion/easing-and-duration/tokens-specs))
- [ ] Enter motion decelerates; exit motion accelerates and is shorter than enter. ([M3 tokens](https://m3.material.io/styles/motion/easing-and-duration/tokens-specs))
- [ ] Every animation reads from `MotionTokens` (no ad-hoc durations) and has a reduced-motion fallback via the animator-scale setting. ([Apple HIG motion](https://developer.apple.com/design/human-interface-guidelines/motion))
- [ ] Nothing makes the player wait for an animation they cannot skip (reveal sequences in Duels have a skip). ([HIG motion](https://developer.apple.com/design/human-interface-guidelines/motion))
- [ ] Haptics are short, matched to the visual, and appear only on discrete meaningful events; there is a setting to turn them off. ([HIG haptics](https://developer.apple.com/design/human-interface-guidelines/playing-haptics), [AOSP](https://source.android.com/docs/core/interaction/haptics/haptics-ux-design))
- [ ] Negative or invalid-move haptics are stronger than positive ones, and rare. ([AOSP](https://source.android.com/docs/core/interaction/haptics/haptics-ux-design))
- [ ] The completion moment is brief on the 100th solve; it does not re-stage a long celebration daily. ([HIG motion](https://developer.apple.com/design/human-interface-guidelines/motion))
- [ ] Results show the personal comparison first (your time, your best, today's progress, "Next: ..."); rank is secondary. ([first-solve golden](feature/play/impl/snapshots/results-hero/first-solve-phone-light.png), [Wired](https://www.wired.com/story/games-are-still-the-best-part-of-linkedin/))
- [ ] Streak and freeze copy reads as relief and encouragement, never debt; no purchase path. ([Smashing](https://www.smashingmagazine.com/2026/02/designing-streak-system-ux-psychology/))
- [ ] Dark theme is designed, not inverted: check every board's cell, highlight and hint colors in `*-dark.png` goldens. ([contrast](https://developer.android.com/guide/topics/ui/accessibility/apps))

### D. Smoothness and reliability

- [ ] Cold start under 500 ms to first meaningful content (target), never over 5 s (Play bad behaviour). ([Measuring performance](https://developer.android.com/topic/performance/measuring-performance), [launch time](https://developer.android.com/google/play/vitals/launch-time))
- [ ] No frame over 700 ms on any core flow (open puzzle, solve, results, ranks, duel reveal). ([render](https://developer.android.com/topic/performance/issues/render))
- [ ] Slow-frame rate (over 16 ms; 11 ms at 90 Hz) is low and stable across Home scroll, Ranks scroll, tab switches, board interaction and navigation transitions. ([render](https://developer.android.com/topic/performance/issues/render))
- [ ] P95 and P99 frame time stay close to the median in each benchmarked journey. ([Measuring performance](https://developer.android.com/topic/performance/measuring-performance))
- [ ] Cached or local data renders on the first frame with no spinner. ([NN/g skeleton](https://www.nngroup.com/articles/skeleton-screens/))
- [ ] Loads under 1 s show no skeleton or spinner; 2–10 s single-module loads use a spinner; full-screen loads use a content-shaped skeleton; over 10 s uses a progress bar plus cancel. ([NN/g skeleton](https://www.nngroup.com/articles/skeleton-screens/), [response times](https://www.nngroup.com/articles/response-times-3-important-limits/))
- [ ] Screen transitions show no layout jump or first-frame flicker (placeholders reserve final size). ([Measuring performance](https://developer.android.com/topic/performance/measuring-performance))
- [ ] Lazy lists use stable keys and `contentType`; no `index` or `hashCode` keys; no nested same-direction scrollables. ([Compose best practices](https://developer.android.com/develop/ui/compose/performance/bestpractices))
- [ ] Frequently changing state (timers, drag position, animation) is read in lambdas or `derivedStateOf`, not in the composable body of a large tree. ([Compose best practices](https://developer.android.com/develop/ui/compose/performance/bestpractices))
- [ ] Composable stability reports show no unexpected non-skippable hot-path composables (only investigate if measurement shows an issue). ([Diagnose stability](https://developer.android.com/develop/ui/compose/performance/stability/diagnose))
- [ ] Offline, flaky-network and server-error states are all handled without a crash or an infinite spinner. ([core quality: interruptions](https://developer.android.com/docs/quality-guidelines/core-app-quality))
- [ ] Release build ships a Baseline Profile covering startup and the main journeys. ([Baseline Profiles](https://developer.android.com/topic/performance/baselineprofiles/overview))
- [ ] Crash and ANR rates are under 1.09% and 0.47% respectively (or whatever Play Console shows), and no device model exceeds 8%. ([Android vitals](https://developer.android.com/google/play/vitals))

### E. Trust

- [ ] First-run and daily flows meet the modal budget: at most one blocking dialog per top task (count them per NN/g's method). ([overlay overload](https://www.nngroup.com/articles/overlay-overload/))
- [ ] No stacked sheet over sheet over dialog; no dismissed prompt that returns in a smaller form. ([overlay overload](https://www.nngroup.com/articles/overlay-overload/))
- [ ] Notification permission is requested only at a moment the user asked for something that needs it, with notifications personalised and capped. ([overlay overload](https://www.nngroup.com/articles/overlay-overload/), [CMI](https://contentmarketinginstitute.com/strategy-planning/linkedin-gaming-strategy))
- [ ] Game progress is never silently lost (back, kill, crash, update). ([State_Preservation](https://developer.android.com/docs/quality-guidelines/core-app-quality))
- [ ] Leaderboard and duel results are consistent between screens (same score, same rank in Ranks, results hero, profile, history). ([heuristic 4](https://www.nngroup.com/articles/ten-usability-heuristics/))
- [ ] Friends scope is the default or the most prominent leaderboard; Global is available but secondary. ([Slate](https://slate.com/culture/2022/01/wordle-game-creator-wardle-twitter-scores-strategy-stats.html), [CMI](https://contentmarketinginstitute.com/strategy-planning/linkedin-gaming-strategy))
- [ ] Midnight reset and timezone edge cases (today's puzzle, streak day, freeze) are correct and explained. ([Wired](https://www.wired.com/story/games-are-still-the-best-part-of-linkedin/))
- [ ] Custom puzzles show a clear verdict on solvability before sharing (unsolvable or ambiguous puzzles feel like bugs). ([Slate on fairness](https://slate.com/culture/2022/01/wordle-game-creator-wardle-twitter-scores-strategy-stats.html), [Verge](https://www.theverge.com/21334116/good-sudoku-iphone-ipad-app-game-zach-gage))
- [ ] Hints teach rather than reveal and have a visible cost or gate, consistently across games. ([Verge](https://www.theverge.com/21334116/good-sudoku-iphone-ipad-app-game-zach-gage), [NYT Strands](https://www.nytimes.com/2024/03/04/crosswords/strands-word-search-game.html))
- [ ] Notifications are rare, specific and helpful, and never guilt-laden. ([Smashing](https://www.smashingmagazine.com/2026/02/designing-streak-system-ux-psychology/))

That is 55 items. Several are cheap to check from the goldens plus code reading; those marked with timing, frame or startup numbers need on-device measurement (section 5).

---

## 5. Suggested polish-phase approach

There is no benchmark module or JankStats wiring in the repo today: a search of `settings.gradle.kts`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml` and `gradle/libs.versions.toml` for `benchmark`, `baseline` and `profileable` returned nothing **[repo fact]**. Phase 0 is therefore mostly setup.

### Phase 0: Measurement setup (prerequisite)

- **Macrobenchmark** module for repeatable lab runs of startup and key journeys: `StartupTimingMetric` (time to initial display) and `FrameTimingMetric` (`frameDurationCpuMs`, and `frameOverrunMs` on API 31+, where positive means a dropped frame) with percentile output (P50/P90/P95/P99) ([Write a Macrobenchmark](https://developer.android.com/macrobenchmark), [metrics](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics)) **[P]**. Docs advise that frame-render metrics matter more than counting janky frames ([Measuring performance](https://developer.android.com/topic/performance/measuring-performance)) **[P]**.
- Build the app under test as a release-like build; mark it `profileable` with `android:shell="true"` for profiling; never measure a debug build ([Measuring performance](https://developer.android.com/topic/performance/measuring-performance)) **[P]**.
- **JankStats** (`androidx.metrics:metrics-performance`) for in-app frame reporting with UI state context (which screen, which interaction), built on `FrameMetrics` on API 24+ ([JankStats](https://developer.android.com/topic/performance/jankstats)) **[P]**. Use it in internal builds to tag jank to screens.
- **`dumpsys gfxinfo <package>`** to check what render metrics the OS has logged for the app ([Vitals render](https://developer.android.com/google/play/vitals/render), [Measuring performance](https://developer.android.com/topic/performance/measuring-performance)) **[P]**. The `framestats` option is the per-frame variant; I did not find it in the pages I read, so treat that detail as **[I]**.
- **Perfetto** traces with the FrameTimeline for jank attribution ([render](https://developer.android.com/topic/performance/issues/render)) **[P]**. Use sparingly custom trace sections: about 5 µs overhead each, so not around every method ([Measuring performance](https://developer.android.com/topic/performance/measuring-performance)) **[P]**.
- **Compose compiler reports** (`composeCompiler { reportsDestination … }`, release build only) for skippability and stability of composables and classes; use only if a measured problem points there, because chasing full skippability is premature optimisation ([Diagnose stability](https://developer.android.com/develop/ui/compose/performance/stability/diagnose)) **[P]**. Layout Inspector recomposition counts are a cheaper first look; do not rely on counts alone (same page) **[P]**.
- **Baseline Profile** generation for startup and the main navigation journeys: the docs state about 30% faster code execution from first launch ([Baseline Profiles overview](https://developer.android.com/topic/performance/baselineprofiles/overview), [create](https://developer.android.com/topic/performance/baselineprofiles/create-baselineprofile)) **[P]**. Do this *after* the glitch pass so you do not profile bad code paths **[I]**.
- **Field metrics**: Play Console Android vitals plus `FrameMetricsAggregator` / Firebase Performance Monitoring ([render](https://developer.android.com/topic/performance/issues/render)) **[P]**.

### Phase 1: Audit

Walk the app screen by screen against section 4 on a mid-range device, light and dark, phone and tablet (and a 90 Hz device if possible). Record findings with a severity: **S1** data loss, crash, frozen frame, dead tap; **S2** visible jank, layout jump, inconsistent state, a failing contrast/target check; **S3** inconsistency and tone. Use the NN/g modal-count method on the top five tasks (open daily, solve, share, check ranks, start duel) ([overlay overload](https://www.nngroup.com/articles/overlay-overload/)) **[P]**. Take a Macrobenchmark baseline for each journey **before** changing anything so Phase 2 has a measured delta.

### Phase 2: Fix glitches and performance

Order of work:

1. S1 items, starting with frames over 700 ms and any lost-state bugs ([render](https://developer.android.com/topic/performance/issues/render), [State_Preservation](https://developer.android.com/docs/quality-guidelines/core-app-quality)).
2. Dead taps, missing press feedback, and sub-48dp targets.
3. Layout jumps and loading flicker (anti-patterns 1–3).
4. List and composition hot paths found by Perfetto and Macrobenchmark, using the best-practices checklist ([Compose best practices](https://developer.android.com/develop/ui/compose/performance/bestpractices)).
5. Ship the Baseline Profile; re-measure.

**Exit criteria:** no frozen frame on core journeys; slow-frame rate on benchmarked journeys down versus the Phase 1 baseline; cold start toward the under-500 ms target; P95/P99 close to median ([Measuring performance](https://developer.android.com/topic/performance/measuring-performance)) **[P]**.

### Phase 3: Consistency pass

Make the app feel like one app. Audit all raw durations, easings, spacing, copy verbs, empty/error templates and haptic usage; route everything through shared tokens (`MotionTokens`, design-system components). Unify error and empty states to the offline-golden standard. Collapse duplicate or stacked modals. Re-run Roborazzi verification and refresh goldens in the same change so the goldens remain a trustworthy baseline ([heuristic 4](https://www.nngroup.com/articles/ten-usability-heuristics/), [M3 tokens](https://m3.material.io/styles/motion/easing-and-duration/tokens-specs)) **[P/S]**.

**Exit criteria:** zero raw motion literals outside the token file; one pattern per state class; a modal count of at most one per top task.

### Phase 4: Delight pass

Only after phases 2–3, because juice on top of jank amplifies jank **[I]**. Candidate work, each justified by evidence in this document:

- Tighten press-to-feedback on boards under 100 ms ([Swink via England](https://lizengland.com/blog/review-game-feel-by-steve-swink/)).
- A short, skippable, once-per-day-feeling completion moment; keep the first-solve and personal-best variants distinct from routine solves ([HIG motion](https://developer.apple.com/design/human-interface-guidelines/motion)).
- Haptic grammar across all games (one effect per event kind, strongest for rare big wins, silent on losses) as `RevealHaptics` already models ([AOSP](https://source.android.com/docs/core/interaction/haptics/haptics-ux-design)).
- Hint quality: explain the technique, not just the cell ([Verge](https://www.theverge.com/21334116/good-sudoku-iphone-ipad-app-game-zach-gage)).
- Friends-first social surface and a spoiler-free share card ([Slate](https://slate.com/culture/2022/01/wordle-game-creator-wardle-twitter-scores-strategy-stats.html)).
- Streak and freeze tone review ([Smashing](https://www.smashingmagazine.com/2026/02/designing-streak-system-ux-psychology/)).

Evaluate delight by A/B-in-a-sentence: toggle each effect on and off, as the juice talk does, and keep only what the player would miss ([HIG haptics](https://developer.apple.com/design/human-interface-guidelines/playing-haptics), [Juice It or Lose It](https://www.gamedesign.gg/watch/juice-it-or-lose-it)) **[P/S]**.

**Exit criteria:** the NYT-style retention proxies that matter to this app: share rate, 7-day and 30-day daily play ([Nieman Lab](https://www.niemanlab.org/2023/09/the-new-york-times-finds-a-match-with-the-word-game-connections)) **[S]**. These need analytics the repo may not yet have **[I]**; without them, rely on the checklist and qualitative playtests.

---

## Source reliability notes and gaps

- Material 3 motion numbers came via a GitHub mirror because the official m3.material.io pages are JS-rendered.
- The Juice talk and Swink's book are cited through a talk page and a review, not the originals.
- The NYT Strands page was paywalled (rules snippet only); the ACM streak paper and the Enrich interview with Jonathan Knight (404) were snippet-level or unavailable.
- Creative Review was read only in part.
- No source I read quantifies how much polish raises retention; the evidence here is about thresholds (perception, platform vitals) and about what leading puzzle apps choose to do. Claims about "feel" beyond those thresholds are design judgement.
