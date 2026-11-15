# PuzzleBox UX review (independent, stills and source only)

Reviewer stance: senior product designer, looking at PuzzleBox 0.32.0 alpha as it enters a polish phase. Three axes: ease of use, clarity of UX intentions, pleasure of use. Evidence is Roborazzi golden screenshots and Compose source; nothing was run on a device. Section 6 lists what that leaves out.

Paths are relative to the repo root. Golden filenames are given in full so they can be opened directly.

---

## 1. Verdict

PuzzleBox is already a well-built app with a clear personality: one green primary action, chunky tactile buttons, rounded display type, strong per-game colour, and a tablet layout that is a real two-pane design rather than a stretched phone. The daily loop is short (one tap from Today to a board), empty and error states are consistently humane, and the duel reveal is the most finished moment in the product. No blocker was found. What holds it back is not broken screens but three kinds of unevenness. First, the loop does not close on its own: there are no notifications of any kind, and the result screen does not lead with rank or offer a challenge, so "check rank, challenge a friend, come back tomorrow" relies on the player's memory. Second, the app names the same things differently in different places (Ranks / leaderboard / the board; rounds / puzzles / legs; queen / crown), and a few controls contradict the scoring rules they sit next to (Hint is the loudest button in play while hints cost rank). Third, the feel is inconsistent from game to game: board styling ranges from flat and hard-edged (Queens, Tango) to chunky and three-dimensional (Strands, Zip), haptics exist in three games and are missing in Tango, and the shared solve moment is silent. Polish should go to consistency and to closing the loop, not to new features.

### Scorecard

| Axis | Score | Justification |
|---|---|---|
| Ease of use | 7 / 10 | One tap to today's puzzle and excellent error handling, but nothing brings the player back, and rank, challenge and duels are each a few steps from where they are wanted. |
| Clear UX intentions | 6.5 / 10 | Screens are individually legible, but vocabulary drifts, Hint is over-promoted against the scoring rules, and several controls need explanatory text or are unlabelled icons. |
| Pleasure of use | 7.5 / 10 | Strong visual identity, good motion system and a standout duel reveal, pulled down by inconsistent board styling, uneven haptics and a muted ordinary-solve result. |

---

## 2. Findings by axis

Severity scale: Blocker (stops a core task), Major (weakens the core loop or misleads many users), Minor (local friction or confusion), Nit (cosmetic).

### 2.1 Ease of use

**E1. Major: the app never calls the player back.**
A search of all `*.kt` and `*.xml` for `NotificationManager`, `NotificationCompat`, `POST_NOTIFICATIONS` and `FirebaseMessaging` returns nothing. There is no daily reminder, no streak-at-risk alert, and no push when a duel is waiting on the player or when results are ready. Duels are asynchronous with a 24-hour deadline, and the only prompt to the opponent is "Nudge", which opens the system share sheet with a prepared text (`feature/compete/impl/src/main/kotlin/com/ivandsky/puzzlebox/compete/duel/DuelLegs.kt:207-208`, `DuelLegsModel.kt:152`). The streak card and its freezes (`feature/home/impl/snapshots/home-streak-freeze-phone360-light.png`) build a habit the app then does not protect. For a daily game this is the largest gap in the loop.
Recommendation: add three opt-in notifications, asked for in context rather than at first launch: a daily reminder at a time the player picks (offered after the first completed day), "streak ends in N hours" only when today is unplayed, and duel events (invite, your move, results ready). NEEDS_CONTEXT: this may be deliberately out of scope for the alpha.

**E2. Major: on the daily result, rank comes last (phone).**
The hero card shows the time, a personal-best line, Hints / Today / Average tiles, "Next: Tango", Share and Play again. Below it come the streak card, then a histogram with a paragraph of explanation, and only after that "Top 7% of players today" (`feature/play/impl/snapshots/results-hero/default-phone-light.png`; the tablet shows it without scrolling in `results-hero/all-done-tablet-light.png`). A rank chip `#N of M` exists but only when the solve is ranked (`feature/play/impl/src/main/kotlin/com/ivandsky/puzzlebox/play/ResultRankChip.kt`). The custom-puzzle result does the opposite and shows the leaderboard inline (`feature/play/impl/snapshots/friend/result-record-phone-light.png`). The product pitch is "score recorded, then check rank", so the comparison with other people should be the second thing seen, not the fifth.
Recommendation: put one social line inside the hero, directly under the time: "#12 of 340 today" or "Top 7% today", tappable to Ranks; when signed out, show the same slot as "Sign in to see where you rank". Move the histogram below it.

**E3. Major: there is no way to challenge a friend from the result.**
The daily result route and view model contain no duel or challenge action (`feature/play/impl/src/main/kotlin/com/ivandsky/puzzlebox/play/ResultRoute.kt`, `ResultViewModel.kt`). "Challenge" appears only on the Duels pill inside Games (`feature/compete/impl/src/main/kotlin/com/ivandsky/puzzlebox/compete/tab/DuelCards.kt:72`) and in the player sheet in Ranks (`feature/history/impl/src/main/kotlin/com/ivandsky/puzzlebox/history/ranks/PlayerSheet.kt:90-99`). The moment of highest motivation to challenge someone is right after a good time, and the app offers only Share.
Recommendation: add a secondary "Challenge a friend" action to the result hero that opens the existing new-duel sheet with the "Daily duel" preset selected.

**E4. Major: Duels are hard to find.**
Duels have no tab. They are the third pill inside Games, after Dailies and Friends (`feature/play/impl/src/main/kotlin/com/ivandsky/puzzlebox/play/LibraryList.kt:130-143`). The bottom-bar badge announces only "Game in progress" (`app/src/main/kotlin/com/ivandsky/puzzlebox/app/nav/TopLevelNavBar.kt:114`), and Home mentions duels only when something is already waiting. A player who has never dueled gets no cue that the feature exists.
Recommendation: keep the four tabs, but give duels a permanent, small entry on Today (one row: "Duel a friend" when idle, "2 duels need you" when active) and let the Games tab badge also reflect "your move".

**E5. Minor: the Friends hint in Ranks is a dead end.**
Ranks opens on the Friends scope. A new player sees a board containing only themselves and the line "Duel someone or connect LinkedIn to fill Friends", which is plain text with no action (`feature/history/impl/src/main/kotlin/com/ivandsky/puzzlebox/history/ranks/RanksFriendsHint.kt:17`).
Recommendation: replace the sentence with two buttons, "Challenge a friend" and "Connect LinkedIn", and consider landing on Global until Friends has at least one other person.

**E6. Minor: Ranks controls are hidden behind three different mechanisms.**
The game is switched from a title dropdown ("Zip ⌄") or by swiping, signalled by page dots and a one-time pill ("Swipe for Zip ›", `feature/history/impl/src/main/kotlin/com/ivandsky/puzzlebox/history/ranks/SwipeNudge.kt:44`). Scope and period live in a sheet behind a tune icon, labelled "Who" and "When" (`feature/history/impl/src/main/kotlin/com/ivandsky/puzzlebox/history/ranks/TuneSheet.kt:42-66`). Day / Week / Month, the most common leaderboard question, is two taps away and the current period is not shown unless it has been changed (`feature/history/impl/snapshots/ranks/friends-day-phone-light.png`).
Recommendation: show the current scope and period as two always-visible chips under the title ("Friends · Today"); tapping either opens the existing sheet.

**E7. Minor: icon-only actions on the result.**
Share and Play again are bare icons. Play again uses a circular-arrow icon (`PuzzleIcons.Retry`, content description "Play again", `feature/history/impl/src/main/kotlin/com/ivandsky/puzzlebox/history/ranks/YourSolveRow.kt:122`) that reads as "refresh". The same icon means "Watch again" in the duel summary (`feature/compete/impl/snapshots/reveal/summary-win-phone-light.png`).
Recommendation: give both a text label on the result, and use a play-style icon for "Watch again".

**E8. Minor: recent solves on Home cannot be tapped on phone.**
On tablet a Recent row selects that day; on phone the rows are inert and only "See all" leads anywhere (`feature/home/impl/src/main/kotlin/com/ivandsky/puzzlebox/home/HomeRecentSection.kt:33`). Rows that look like list items and do nothing feel broken.
Recommendation: on phone, a tap opens that day's result or replay.

**E9. Minor: the test solve ends by sending the author backwards.**
After a successful test solve the only action is "Back to editor" (`feature/create/impl/src/main/kotlin/com/ivandsky/puzzlebox/create/editor/TestSolveScreen.kt:45-64`, `feature/create/impl/snapshots/test-solve/solved-phone-light.png`), although the next real step is the publish details.
Recommendation: make the primary action "Continue to publish" and keep "Back to editor" as the secondary one.

**E10. Minor: sign-in prompts are well judged but worded many ways.**
Sign-in is always asked in context and never blocks play, with good reassurance copy. The titles, however, vary: "Sign in to see the ranks" (`feature/history/impl/.../ranks/RanksSignInPrompt.kt:27`), "Sign in to see today's times" (`feature/play/impl/.../play/PlayTimesCard.kt:51`), "Sign in to see how friends did" (`feature/play/impl/.../play/friend/BoardPreview.kt:76`), "Sign in to duel friends" (`feature/compete/impl/.../create/NewDuelSheet.kt:269`), "Sign in to accept" (`.../duel/DuelInviteCard.kt:189`), "Sign in to publish" (`feature/create/impl/.../editor/SignInPublishSheet.kt:93`), "Sign in to see this duel" (`feature/play/impl/.../play/MatchLegResultRoute.kt:106`).
Recommendation: keep the contextual pattern; settle on one sentence shape and one shared reassurance line.

### 2.2 Clear UX intentions

**C1. Major: the same concept has several names.**
- The leaderboard is "Ranks" in the tab bar, "Leaderboard" on the game page row (`feature/play/impl/src/main/kotlin/com/ivandsky/puzzlebox/play/GamePageRankRow.kt:41-44`), "the board" in loading and sign-in copy (`feature/history/impl/.../ranks/RanksHeroCard.kt:41`) and "leaderboards" in Settings (`feature/profile/impl/.../VisibilitySection.kt:72-79`). "The board" is also what every game calls its grid.
- A duel is made of "rounds" in the customise panel (`feature/compete/impl/.../create/DuelCustomizePanel.kt:40`), "Puzzle 1 of 4" during play, and "leg" in accessibility labels ("Compare leg N", `feature/compete/impl/.../reveal/RevealReplayCompare.kt:16`).
- Queens rules say "queen" and "color" in Play (`feature/play/impl/src/main/kotlin/com/ivandsky/puzzlebox/play/GameRules.kt:24-26`) and "crown" and "colour" in Create (`feature/create/impl/src/main/kotlin/com/ivandsky/puzzlebox/create/GameRuleLines.kt:15`).
- "this phone" and "this device" appear in the same card (`feature/profile/impl/.../accounts/GoogleRow.kt:50-51`); on a tablet "phone" is simply wrong.
- "Friends" means puzzles received from someone (Games → Friends), a leaderboard scope (Ranks → Friends), and LinkedIn connections.
Each is small. Together they make the app feel assembled from parts and force the player to re-learn terms.
Recommendation: write a one-page term list (Ranks, puzzle, queen, color, device, "Shared with you" for received puzzles) and apply it in one pass, including content descriptions.

**C2. Major: Hint is the most prominent control during play, and hints cost rank.**
In the shared action bar, Hint is the only button filled with the game's accent; Undo, Redo and Clear are neutral (`core/game-ui/src/main/kotlin/com/ivandsky/puzzlebox/gameui/PlayActionBar.kt:23-24,54-56`; `games/sudoku-ui/snapshots/mid-6x6-twopane-tablet-light.png`). Ranking is "fastest first, then fewer hints". The only signals of cost are the label changing to "Hint · 1" and a cooldown ring. The visual hierarchy tells the player that Hint is the recommended move while the rules punish it.
Recommendation: make Hint neutral like its neighbours and let it take the accent only after a period of no progress, or keep the colour and state the cost on first use ("Hints count against your rank").

**C3. Minor: text truncates in places that matter.**
"Mini Sud…" in the Today badges at 360 dp (`feature/home/impl/snapshots/home-update-phone360-light.png`; `feature/home/impl/src/main/kotlin/com/ivandsky/puzzlebox/home/TodayBadge.kt:95-96`). The Studio subtitle collapses to "Qu… · ✓ Saved" when the verdict pill is wide (`feature/create/impl/snapshots/studio/incomplete-phone-light.png`, `app/snapshots/studio/unique-toast-phone411-light.png`; `feature/create/impl/src/main/kotlin/com/ivandsky/puzzlebox/create/editor/StudioTopBar.kt:133-146`). On the tablet game page an archive stat is cut off, and its source text is cryptic in full: "$solves · best yes, streak never" (`feature/play/impl/src/main/kotlin/com/ivandsky/puzzlebox/play/GameStatsCard.kt:90`; `feature/play/impl/snapshots/tabs/tabs-dailies-tablet-light.png`).
Recommendation: use "Sudoku" in badges; drop the game name from the Studio subtitle when space is short and keep the save state; rewrite the archive stat as a sentence.

**C4. Minor: the result histogram needs a paragraph to be understood.**
The caption reads "Each bar counts how many of your solves landed in that time range, across every day of the week. Taller means more solves. Tap the highlighted word to switch." (`core/game-ui/src/main/kotlin/com/ivandsky/puzzlebox/gameui/ResultHeroCopy.kt:115-118`; `feature/play/impl/snapshots/results-hero/default-phone-light.png`). A control that must explain how to operate itself is not self-evident, and the inline "any day ⇄" word-chip is an unfamiliar pattern.
Recommendation: replace the word-chip with a two-option segmented control and cut the caption to one short line that says what the highlighted bar is.

**C5. Minor: the replay "Show errors" toggle is a warning triangle in a green circle.**
The top-right button on replay and duel-compare screens is a caution icon on success green (`feature/compete/impl/snapshots/reveal-replay/compare-both-short-phone-light.png`, `feature/play/impl/snapshots/replay-player/short-playing-phone-light.png`). Source shows it is the "Show errors" switch (`feature/play/impl/src/main/kotlin/com/ivandsky/puzzlebox/play/ReplayShowErrors.kt:11`). A warning sign normally means something is wrong now, and green means all is well; together they say nothing clear, and the on/off state is carried only by the fill.
Recommendation: move it next to "Skip idle" as a labelled check ("Show errors"), matching the control already there.

**C6. Minor: the Ranks background glyph competes with the data.**
A very large ghost of the game glyph sits bottom-right behind the list at 22% alpha (`feature/history/impl/src/main/kotlin/com/ivandsky/puzzlebox/history/ranks/RanksWorld.kt:34,71`) and overlaps the last rows (`feature/history/impl/snapshots/ranks/friends-day-phone-light.png`, `ranks/overall-phone-dark.png`, `ranks/two-pane-tablet-light.png`).
Recommendation: confine it to the header area or lower it to about 8–10% alpha.

**C7. Minor: the connected LinkedIn row does not say "LinkedIn".**
When connected, the row shows a name, an initial avatar and lines such as "142 connections seen · 5 days this week"; the word "LinkedIn" is used as a title only when disconnected (`feature/profile/impl/src/main/kotlin/com/ivandsky/puzzlebox/profile/accounts/LinkedInRow.kt:71`; `feature/profile/impl/snapshots/sync/worst-case-phone-light.png`). The worst case stacks five status lines and shows the same red error twice.
Recommendation: keep "LinkedIn" as a fixed label, limit the row to two status lines, and show one error with one action.

**C8. Minor: the Duels empty and signed-out states explain too little.**
There is one sentence under the call to action and the rest of the screen is blank (`feature/compete/impl/snapshots/tab/tab-empty-phone-light.png`, `tab/tab-signed-out-phone-dark.png`). The sealed-times idea, which is the interesting part, gets one line.
Recommendation: three short steps with small illustrations: pick puzzles, each plays in their own time, watch the reveal together.

**C9. Minor: the friend puzzle card repeats itself.**
The game is named three times: glyph, "Queens · from a friend", and a "Queens" chip (`feature/play/impl/snapshots/friend/card-top3-signed-out-phone-dark.png`). The signed-out variant shows "You — Posted" above "Sign in to see how friends did", which reads oddly (possibly a fixture).
Recommendation: drop the chip; show the author's name in the eyebrow instead.

**C10. Minor: the Sudoku keypad count is unlabelled.**
Each digit key shows a small number beneath it, which source confirms is how many of that digit remain to be placed (`games/sudoku-ui/src/main/kotlin/com/ivandsky/puzzlebox/games/sudokuui/SudokuDigitKey.kt:77-99`; `games/sudoku-ui/snapshots/mid-6x6-twopane-tablet-light.png`). It is useful, and regular Sudoku players will read it, but a "3" under a "1" is ambiguous for newcomers.
Recommendation: mention it once in the Sudoku tutorial; no change to the key.

**C11. Nit: developer language reaches users.**
"Paste notation from the PuzzleBox tool" (`feature/create/impl/.../NewPuzzleSheet.kt:186`); "Debug build only — same engine, 3×3 boxes" as a rule line (`feature/create/impl/src/main/kotlin/com/ivandsky/puzzlebox/create/GameRuleLines.kt:19`); "Unpublish…" with an ellipsis (`feature/create/impl/snapshots/published/page-phone-light.png`); typed "✓" characters ("Unique ✓", "✓ Saved") alongside real check icons. The import error "Line 3, column 2: unexpected character 'x'" (`feature/create/impl/snapshots/import/malformed-phone-light.png`) is precise and fine for the audience that imports text.

**C12. Nit: Studio is dense on phone.**
One screen carries the verdict pill, a toast, the board, "Unique ✓ — try solving it", a tool chip, a coach line, the tool strip, "Not tested" and the call to action (`app/snapshots/studio/unique-toast-phone411-light.png`). On tablet, "Not tested" sits above and "Not unique" below a disabled "Test solve" (`app/snapshots/studio/none-clash-tablet-light.png`), so two status messages bracket one button.
Recommendation: one status line at a time, chosen by priority: not unique, then not tested, then ready.

### 2.3 Pleasure of use

**P1. Major: the five boards do not look like they belong to one app.**
Queens is flat, with hard black borders, square corners and black glyphs (`games/queens-ui/snapshots/queens-compact-light.png`). Zip has a rounded board, a raised path with a lip, and waypoint numerals in a different, plainer typeface (`games/zip-ui/snapshots/replay-middle-compact-light.png`). Strands uses chunky raised tiles (`games/strands-ui/snapshots/solved-compact-dark.png`). Tango is thin-line and flat (`games/tango-ui/src/test/resources/goldens/tango-board-light-a-frc1_5.png`). Sudoku mixes a raised selected cell with a flat grid (`games/sudoku-ui/snapshots/mid-6x6-twopane-tablet-light.png`). The chrome around them (raised buttons, bold game cards) promises a tactile toy, and two of the five boards do not keep that promise. The board is where the player spends nearly all their time.
Recommendation: define one board kit (outer corner radius, grid line weight and colour, how a placed piece sits, one numeral typeface) and bring Queens and Tango to it first. Use the display typeface for Zip numerals.

**P2. Major: touch feedback is uneven and the solve itself is silent.**
Queens, Sudoku and Strands have considered haptics (`games/queens-ui/.../QueensBoard.kt:66-129`, `games/sudoku-ui/.../SudokuHaptics.kt`, `games/strands-ui/.../board/StrandsHaptics.kt`). Zip gets only generic drag ticks from the shared board (`core/designsystem/.../PuzzleBoard.kt:116-124`). Tango has none: a search for `performHapticFeedback` and `HapticFeedbackType` in `games/tango-ui/src/main` and `games/zip-ui/src/main` returns nothing. The shared solve sequence (sweep, glyph stamp, hold, hand-off) fires no haptic either; the same search in `core/game-ui/src/main/kotlin/com/ivandsky/puzzlebox/gameui/PlaySolveFrame.kt` is empty. The duel reveal, by contrast, has a graded haptic script (`feature/compete/impl/src/main/kotlin/com/ivandsky/puzzlebox/compete/reveal/RevealHaptics.kt`). There is no sound anywhere and no haptics setting.
Recommendation: one shared haptic vocabulary (place, remove, reject, complete a unit, solve), applied to all five games; a distinct solve pattern timed to the stamp in `PlaySolveFrame`; a single toggle in Settings. Sound can wait.

**P3. Minor: an ordinary solve feels flat, and finishing the day feels flatter.**
A new best gets a particle burst and a pill (`core/designsystem/.../NewBestBurst.kt`). Any other solve gets a large time and "Your best is 1:30 · 10s off" (`core/game-ui/src/main/kotlin/com/ivandsky/puzzlebox/gameui/ResultHeroCopy.kt:65`; `feature/play/impl/snapshots/results-hero/default-phone-light.png`), which frames a success as a shortfall. Completing every daily produces the text "All done for today" and a countdown (`core/game-ui/src/main/kotlin/com/ivandsky/puzzlebox/gameui/ResultPrimaryAction.kt:61-74`; `results-hero/all-done-tablet-light.png`). The peak of the day receives the least ceremony.
Recommendation: lead with whichever fact is positive (streak extended, faster than your average, faster than yesterday) and show the gap to best second. Give "All done" its own moment: the five game glyphs stamping in, total time for the day, the streak ticking up, and one share action for the whole day.

**P4. Minor: screenshots do not show the real look in several places.**
Duel and reveal goldens show generic rounded-square glyphs and "Best of 4"; the Strands result shows a crown (`feature/play/impl/snapshots/results-hero/strands-phone-dark.png`); the Duels pill shows "Duel 1 / 2 / 3" placeholders (`feature/play/impl/snapshots/tabs/tabs-duels-phone-dark.png`); publish, published and test-solve goldens show a blank grey board and "Fake · share code" (`feature/create/impl/snapshots/published/page-phone-light.png`). These are probably test fixtures, but they mean visual regressions on exactly those surfaces cannot be caught, and this review could not judge them. NEEDS_CONTEXT: confirm real glyphs and board previews render at runtime.
Recommendation: feed real glyphs and a real board into those previews.

**P5. Nit: empty vertical space.**
About 110 dp of nothing sits between the back arrow and the hero card on the phone result, and the reveal race screen is half empty (`feature/compete/impl/snapshots/reveal/race-phone-dark.png`). The compare replay likewise leaves large gaps above and below the progress dots (`feature/compete/impl/snapshots/reveal-replay/compare-both-short-phone-light.png`).
Recommendation: centre content vertically or let the main element grow.

---

## 3. Notes by surface

**Home (Today).** The strongest screen. "2 of 5 done", five badges with progress rings and times, an "Up next" line and one button that names the game ("Continue Tango") make the next step unmistakable (`feature/home/impl/snapshots/home-update-phone360-light.png`, `home-update-tablet1280-dark.png`). Issues: "Mini Sud…" truncation (C3), inert Recent rows on phone (E8), no standing entry to duels (E4). Goldens title this screen "PuzzleBox" while current code passes the tab label "Today"; confirm which is intended.

**Play, shared chrome.** First play is gated by a tutorial whose time is not counted, and rules can be reopened from the top bar (`feature/play/impl/src/main/kotlin/com/ivandsky/puzzlebox/play/GameRoute.kt:140-190`). Custom puzzles have neither the gate nor the "How to play" button (`GameRoute.kt:95-115`), so a friend who receives a Tango link as their first contact with the app gets no rules. Hint prominence is covered in C2.

**Zip.** Rejection messages are specific and kind ("Go to 3 first", "A wall blocks that way", `games/zip-ui/.../ZipFeedback.kt`). Numeral typeface differs from the rest of the app; haptics are drag ticks only.

**Queens.** Hatched conflict cells read well. The board is the flattest and hardest-edged of the five and is the first candidate for restyling (P1).

**Tango.** Clean but thin, and the only game with no haptics at all (P2).

**Mini Sudoku.** Good conflict wording ("Two 2s in row 2"), notes mode and remaining counts. On tablet the conflict message sits alone at the top of an otherwise empty side pane (`games/sudoku-ui/snapshots/mid-6x6-twopane-tablet-light.png`); that pane could hold the timer, rules or hint explanation.

**Strands.** The most tactile board, with the best in-game haptic script. Keep it as the reference for the others.

**Results.** Good content in the wrong order: rank last, no challenge (E2, E3), icon-only actions (E7), a histogram that needs instructions (C4), restrained celebration (P3).

**Ranks.** The board itself is clear, and "#1 on PuzzleBox" chips and the player sheet are good touches. Controls are hidden (E6), the Friends landing is a dead end for new users (E5), the watermark fights the rows (C6).

**Duels / Compete.** The populated list is excellent: Invites, Your move, Results ready, Waiting and Finished, each colour-coded with time-left pills (`feature/compete/impl/snapshots/tab/tab-populated-phone-light.png`). The reveal is the best moment in the app (`reveal/intro-phone-light.png`, `reveal/summary-win-phone-light.png`). Weak points are finding it at all (E4), the thin empty state (C8), and the absence of any push when it is the player's turn (E1).

**Create / Studio / Publish.** The live uniqueness verdict, witness solutions, clash regions and auto-save are sophisticated and explained well (`app/snapshots/studio/multiple-witness-phone411-dark.png`). The publish ticket with its large code is memorable (`feature/create/impl/snapshots/publish/ticket-phone-light.png`). Issues: density on phone (C12), subtitle truncation (C3), test solve ending on "Back to editor" (E9), developer wording (C11). The empty state lists only Queens, Zip and Tango (`feature/create/impl/snapshots/create/empty-phone-dark.png`) although Strands and Sudoku editors exist; possibly a fixture.

**Profile / Settings.** Reached only through the avatar on Home, which is acceptable for a low-frequency screen. The LinkedIn risk warning is unusually honest and should stay (`feature/profile/impl/.../linkedin/LinkedInRiskWarning.kt:23-40`). Issues: unlabelled LinkedIn row and stacked errors (C7), "phone" versus "device" (C1), no haptics toggle (P2).

---

## 4. Top 10 polish moves, ranked by user impact ÷ effort

1. **One vocabulary pass** (C1). Copy only, touches many screens, removes the "assembled from parts" feeling. Small effort, broad effect.
2. **Rank line and "Challenge a friend" in the result hero** (E2, E3). Reuses the existing rank chip and new-duel sheet; closes the core loop at its most motivated moment.
3. **Solve haptic in `PlaySolveFrame`, haptics for Tango and Zip, one Settings toggle** (P2). A few dozen lines for the solve; the reward lands on every single play.
4. **Make the Ranks Friends hint two buttons and show scope/period chips** (E5, E6). Turns a dead end into the entry point for both social features.
5. **De-emphasise Hint or state its cost** (C2). One colour change in a shared component; aligns the interface with the scoring rules.
6. **Label Share and Play again; fix the "Show errors" triangle; separate "Watch again" from "Play again"** (E7, C5). Small icon and label changes.
7. **Fix the three truncations and the cryptic archive stat** (C3). Visible on the first screen at common phone widths.
8. **Warmer ordinary-solve line and a real "All done for today" moment** (P3). Copy change is trivial; the all-done moment is moderate work and is the daily peak.
9. **Notifications: daily reminder, streak at risk, duel events** (E1). The largest effect on retention of anything here; ranked ninth only because it is the largest piece of work and needs permission design.
10. **One board kit; restyle Queens and Tango** (P1). Biggest gain in perceived quality during play, and the most design and engineering effort.

Next in line: a standing duel entry on Today (E4), "Continue to publish" after test solve (E9), tappable Recent rows on phone (E8), a quieter Ranks watermark (C6), a fuller Duels empty state (C8), real fixtures in goldens (P4).

---

## 5. What is already great (keep)

- **The Today card.** Progress, per-game state and one named next action on one card, with a rollover countdown when everything is done.
- **The streak card** with week rings in game colours and freeze snowflakes (`feature/home/impl/snapshots/home-streak-freeze-phone360-light.png`).
- **Games cards**: bold brand colour, background motifs, archive progress such as "531 of 598 solved" (`feature/play/impl/snapshots/tabs/tabs-dailies-phone-light.png`).
- **The duel list and the reveal.** Sectioning by what the player must do, sealed times, the VS intro, the per-puzzle race with "Tap to speed up" and Skip, the summary with Rematch, and share text with coloured squares.
- **Humane errors and empty states.** "You're offline — A new code needs a connection to open. Puzzles you've opened before still play." (`feature/play/impl/src/main/kotlin/com/ivandsky/puzzlebox/play/friend/FriendOpenError.kt:92-129`); undo snackbars for destructive actions; "Board changed — test solve cleared. Undo".
- **Sign-in that never blocks play**, asked where it unlocks something, with reassurance ("Playing never needs it.", "Your draft and test solve stay right here.").
- **The Enter code sheet**: "Case and dashes don't matter.", "Codes never use the letter U." (`feature/play/impl/src/main/kotlin/com/ivandsky/puzzlebox/play/friend/EnterCodeSheet.kt:145-189`).
- **Studio's live verdict** (unique, multiple, none), witness solutions, clash regions, difficulty and auto-save, and the publish ticket.
- **In-play feedback copy** that says what went wrong in the game's own terms.
- **The motion system**: tokens with reduced-motion fallbacks, a quicker back than forward, container transform on tablet (`core/designsystem/.../Motion.kt`), and the solve sweep and stamp.
- **Tablet layouts** that use the second pane for real content (`feature/play/impl/snapshots/tabs/tabs-dailies-tablet-light.png`, `feature/history/impl/snapshots/ranks/two-pane-tablet-light.png`).
- **A well-tuned dark theme** on every sampled screen.
- **Tutorial gating** on first play, with the time not counted.
- **The honest LinkedIn risk warning.**
- **The overall character**: rounded display type, raised pill buttons, one green primary.

---

## 6. Limits of this review

- **Stills only.** Nothing was run. Motion, timing, haptics, scroll behaviour, keyboard handling, loading speed and gesture feel were judged from source and from single frames, not experienced. Claims about haptics and notifications rest on source searches, not on a device.
- **Sampled, not exhaustive.** About 70 of roughly 991 goldens were opened, mostly phone light, with some dark and tablet variants.
- **Goldens may be stale.** `docs/screenshots/home.png` shows an older three-tab design. `feature/play/impl/snapshots/friend/library-undo-phone-light.png` shows an older Games layout than the current pill tabs. Home goldens title the screen "PuzzleBox" while code passes "Today".
- **Fixtures mask real content** on duel, reveal, publish and test-solve screens (P4), so those surfaces were judged on layout and copy only.
- **Not seen at all:** a phone golden of the game page, the new-duel sheet, Settings and the Ranks tune sheet. Those were reviewed from source text only.
- **No real users, no analytics.** Severity reflects design judgement about the stated core loop, not observed behaviour.
- **Accessibility was not audited** beyond noting content descriptions met along the way; contrast, touch-target size, font scaling and TalkBack order need their own pass.

### NEEDS_CONTEXT

1. Are notifications deliberately out of scope for the alpha (E1)?
2. Do real game glyphs and board previews render at runtime where goldens show placeholders (P4)?
3. Is the Home title meant to be "PuzzleBox" or "Today"?
4. Is the Create empty state meant to list all five games (golden shows three)?
5. Is the absence of a tutorial and "How to play" on custom puzzles intentional, given that a shared link can be someone's first contact with a game?
6. Is "You — Posted" on the signed-out friend card real behaviour or a fixture?
