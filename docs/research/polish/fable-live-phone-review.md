# PuzzleBox live phone review (Pixel 8 Pro, benchmark install)

Reviewer stance: senior product designer using the app as a new player. Build: `main` at `ecad45b7d`, versionName 0.32.0, package `com.ivandsky.puzzlebox.benchmark` ("PuzzleBox Bench"), fresh install at 14:43 on 2026-11-15, signed out. Device: Pixel 8 Pro, Android 17, 120 Hz, 1008×2244 px at 360 dpi (about 448×997 dp), dark theme by default. Session ran 14:43 to 15:37 device time.

How it was done: every screen was driven over adb (taps, drags, text), captured with `screencap` and short `screenrecord` clips, and read back with `uiautomator dump`. Evidence is in `/tmp/fable-live/`; `name.png` is full size, `name.s.png` is half size, `name.m.png` is a montage of several shots or clip frames. Pass 1 notes were written to `/tmp/fable-live/notes.md` before the stills review was opened.

Limits to keep in mind. Input was injected, so nothing was physically felt: thumb reach is judged from coordinates, and haptics are judged from the system vibration log (`dumpsys vibrator_manager`), not from the hand. `screenrecord` adds load, so frame numbers are slightly pessimistic. Everything signed-in (posting scores, real leaderboards, duel play and reveal, publishing, LinkedIn) was not exercised.

---

## 1. Verdict

PuzzleBox is fast, stable and pleasant on a phone in portrait at the default font size. Cold start to a populated Today screen takes about a third of a second, one tap reaches today's puzzle, every game teaches itself with a short interactive tutorial, and "Next: Tango" on the result chains all five dailies without ever returning to a menu. I solved the whole set in under half an hour (14:43 to 15:10, tutorials and probing included) without meeting a crash, a blank frame or a lost input.

No blocker was found. Six things hold it back, in this order. The core loop stops at the result: a signed-out player sees no rank and no way to challenge anyone, and finishing all five puzzles ends on a grey countdown. Hint is the loudest button, behaves differently in every game, and is counted against the player even when it placed nothing. Text does not survive larger system fonts: clipping starts at 1.3× and the Games list is unreadable at 2.0×. Phone landscape switches to the tablet layout and breaks. Today has several things that look tappable and are not, and its "Recent" section contradicts what the player just did. And the feel is uneven between games: Strands has no solve moment at all, and Zip and Tango produced no haptics in the vibration log while Queens and Sudoku did.

### Scorecard

| Axis | Score | One line |
|---|---|---|
| Ease of use | 7 / 10 | One tap to play and a self-chaining daily set, but rank, challenge and duels are missing from where the player is, and Today has dead surfaces. |
| Clear UX intentions | 6 / 10 | Each screen reads well alone; Hint, the Ranks "Friends" label, "See all", the Settings visibility radios and Studio's unexplained overlays all say one thing and do another. |
| Pleasure of use | 7 / 10 | Strong identity and a good solve beat in three games; flat finish for the day, no beat in Strands, uneven haptics, muted Queens colours in dark. |
| Smoothness | 8 / 10 | 2.27% janky frames over 29,305, p50 6 ms, p99 22 ms, no flicker; a few 30–60 ms hitches around the solve hand-off and layout shifts in Studio and the Sudoku tutorial. |

---

## 2. First-time-user journey

**Launch (14:43).** No onboarding and no sign-in wall. The splash hands over to Today in about 0.35 s with no empty frame (`02-coldstart2.sheet.png`; `am start` reported `TotalTime` 165 and 144 ms). Today says "0 of 5 done", shows five game chips marked "New", an "UP NEXT Zip · Not started" line and one lime button, "Play Zip" (`01-first-launch.png`). The next step is unmistakable. Friction: nothing on this screen mentions duels or friends; the splash icon is a red-brown glyph on grey while the app's brand colour is lime.

**First puzzle, Zip.** A three-step interactive tutorial starts by itself, with step dots and Skip (`05-zip-tut.m.png`, `08-tut.m.png`). Bumping a wall shows a short message under the board (`04-zip-wall-after.png`). Friction: in step 3, taking the wrong shortcut from 1 to 2 produces no feedback at all (`07-zip-tut3-short.png`); the tutorial ends on a generic "You're ready! / Let's play" card that adds a tap. In the real puzzle the timer is already at 0:02–0:04 on the first frame. A wrong move shows "Go to 2 first" at the bottom-left, far from the board (`12-zip-wrongnum.png`). Undo still looks enabled when nothing is left to undo (`10-zip.m.png`). Hint, the only coloured button, fills in one cell and starts a 20-second cooldown (`11-zip-hint.m.png`); nothing says what it costs.

**The solve (Zip, 3:03).** A sheen crosses the board, the outline lights up, the toolbar dims, and about 0.8–0.9 s later the screen slides to the result, settling at about 1.8–2.2 s (`16-solve.m.png`, clip `16-zip-solve.mp4`). This is a good beat. The result shows "First solve", the time, "1 Hint", "1 of 5 Today", a lime "Next: Tango", and two unlabelled icon buttons (`16-zip-solved-c.png`, 14:50). Below that: a streak card, a histogram with a single bar and two paragraphs explaining it, and only then, below the fold, "Sign in to see where you rank today." (`17-zip-result-scroll.png`, 14:51). There is no rank and no "challenge a friend".

**Tango, Queens, Strands, Mini Sudoku.** "Next: <game>" carries the player through the set, each with its own tutorial. Hint changes meaning on the way. In Tango, Queens and Sudoku it places nothing: it shows a tooltip naming a technique ("Pair", "Line lock", "Hidden single (box)"), the tooltip covers part of the board, and any tap dismisses it (`27-tango.m.png`, `39-q-hint-big.png`, `58-su-hint.png`, `59-su-hint-tap.png`). The result still reports "1 Hint". In Strands a hint must be earned by finding three other words first (`47-s.m.png`, `48-s.m.png`). The Sudoku tutorial is the roughest: tapping a digit before selecting a cell does nothing, the layout shifts between steps, step 3 has four lines of instruction, and the keypad reflows there to icon-only Notes/Erase/Clear (`53-su-s2.png`, `54-su-s3.png`).

**Finishing the day (15:10).** The fifth result replaces the lime button with "All done for today / New puzzles in …" (`60-su-result.png`). No celebration, no summary of the day, no primary action. Back on Today: "5 of 5 done", "That's the whole set", a grey countdown pill "New puzzles in 11:49:08", and a "Recent" section that still reads "No games yet — Finished puzzles and scores will appear here." (`61-today-done.png`, `62-today-scrolled.png`). Tapping "See all" next to Recent opens the Ranks tab (`63-seeall.png`).

**Looking for rank.** Ranks, signed out, shows a well-worded "Sign in to see the ranks" card that makes clear solves stay on the phone and playing does not need an account, plus a "Swipe for Zip ›" pill. A green "Friends" word beside the title looks like a button and does nothing (`67.m.png`).

**Looking for a friend to challenge.** Nothing on Today, the result or the tab bar mentions duels. They are the third chip inside Games, where a "Challenge a friend / Sign in to duel friends" card opens a sheet with "Continue with Google" (`75-duel-tap.png`).

**Coming back tomorrow.** The app does not ask to remind me, and Settings has nothing about reminders (`103.m.png`). The package requests no notification permission.

**Making a puzzle.** Create opens with a short intro and a list of games. Picking Zip creates a saved draft named "Cozy Birch" at once and opens an empty board with no hint on how to begin (`80-studio-zip.png`). Dragging draws the path and places 1 and 2 by itself (`81-studio-path1.png`). When the path is complete a chip turns amber, "2+ solutions", and cyan squares and a green line appear on the board with no legend (`82-studio-full.png`, 15:17); tapping the chip explains them (`83-studio-chip.png`). Adding numbers until the chip turns green "Unique" unlocks "Test solve" (`87-numbers3.png`, 15:18). The test solve has no toolbar or timer; after it the editor shows publish details and a lime "Sign in & publish", which I did not tap (`90.m.png`).

---

## 3. Findings

Severity: Blocker stops a core task; Major weakens the core loop or misleads many users; Minor is local friction; Nit is cosmetic. Times in brackets are the device clock on the screenshot or the timestamp inside a clip.

### Major

**L1. The loop stops at the result: no rank, no challenge, no reason to return.**
What happened: on every daily result the only forward action is "Next: <game>". Signed out, the comparison with other players is a single line below the fold, after the streak card and the histogram. There is no challenge action anywhere on the result. After the fifth puzzle the primary button is replaced by a countdown.
Evidence: `16-zip-solved-c.png` (14:50) and `17-zip-result-scroll.png` (14:51); `60-su-result.png` and `61-today-done.png` (15:10); `110.m.png` (15:25, light).
Why it matters: the product's loop is solve → see rank → challenge a friend → come back. The first step is excellent and the other three are absent at the moment the player is most willing.
Recommendation: put one social line in the hero under the time ("#12 of 340 today", or when signed out "Sign in to see where you rank"), add a secondary "Challenge a friend" beside "Next", and when all five are done make that the lime primary action.

**L2. Hint is the loudest control, works four different ways, and is counted even when it does nothing.**
What happened: Hint is the only filled, coloured toolbar button in every game. In Zip it places a cell and starts a 20 s cooldown. In Tango, Queens and Sudoku it shows a named technique and places nothing; the tooltip covers part of the board and any tap dismisses it. In Strands it must be earned by finding three other words. In all cases the result shows "1 Hint", and nothing in play says what a hint costs; the stills review reads the scoring as penalising hints. Technique names are jargon: "Pair", "Line lock", "Hidden single (box)".
Evidence: `09-zip-game.png`, `36-queens-game.png`, `56-sudoku-game.png` (prominence); `11-zip-hint.m.png`; `27-tango.m.png`, `28.m.png`; `39-q-hint-big.png`, `40.m.png`; `58-su-hint.png`, `59-su-hint-tap.png`; `47-s.m.png`, `48-s.m.png`; `65-howranks.png`.
Why it matters: the hierarchy recommends the one action the scoring punishes, and a new player cannot predict what the button will do in the next game.
Recommendation: make Hint neutral like Undo; on first use in each game show one line ("Hints count against your rank"); in the teaching hints add an explicit "Place it" action so the hint that was paid for can be used; replace technique names with plain sentences.

**L3. Larger system fonts clip and overlap text; the Games list is unreadable at 2.0×.**
What happened: at font scale 1.3 descenders are cut on the Games cards and the archive hero, the calendar's "#447 – #461" line is cut, and Today shows "Mini Sud…". At 2.0 every Games card has overlapping lines of text, the archive hero overlaps its text, the calendar month header and weekday letters are cut, the game top bar wraps and cuts the puzzle number ("#928", "#460"), the Today chip times are clipped and names shorten to "Quee…", "Stran…", "Mini …", the result histogram labels are cut, and the Sudoku Notes/Erase/Clear labels are cut at the bottom.
Evidence: `124.m.png`, `124c.m.png` (15:30, 1.3×); `122-font-gameslist.png` (15:29), `121.m.png`, `119-font-today.png`, `118.m.png`, `123-font-sudoku.png` (15:28–15:29, 2.0×).
Why it matters: 1.3× is a common setting, not an edge case, and the Games tab is one of four top-level screens. Cards use fixed heights that text cannot push.
Recommendation: let card and hero heights grow with content (min height, not fixed), allow the game title to shorten to the glyph plus number, and add 1.3× and 2.0× Roborazzi variants for Games, the archive page, Today and the result.

**L4. Phone landscape switches to the tablet layout and breaks.**
What happened: rotating the phone gives a navigation rail plus two or three panes, chosen by width alone on a window about 448 dp tall. In a game the toolbar sits above the board and the board shrinks to about 485 px, so a 9×9 Queens cell is about 24 dp. The rail's active pill is cut off, the Create label is cut, and the Profile item is 10 px tall, so Profile and Settings cannot be reached. The archive pane truncates "Back to today" to "Back to", wraps stat labels mid-word ("Solv/ed", "Hint-fre/e") and crushes the calendar. The side pane of a past puzzle is titled "Queens · today" for puzzle #928.
Evidence: `114-land-queens.png`, `115-land-archive.png`, `116-land-today.png`, `117-land-result.png` (15:27–15:28).
Why it matters: anyone with auto-rotate on will hit this by accident, and a 24 dp target is half the minimum.
Recommendation: either lock phones to portrait, or choose the expanded layout only when both width and height are at least medium; in landscape on a phone put the toolbar beside the board.

**L5. Today has dead surfaces, and "Recent" contradicts the player.**
What happened: after five solves "Recent" still said "No games yet — Finished puzzles and scores will appear here." Later it showed "November 14" with a Zip chip and a dash, which is an archive puzzle I had only started. So Recent lists other days, never today, and includes unfinished play. Its "See all" link opens Ranks. The Recent card, the day rings, the freeze chip and the streak card do not respond to taps; `uiautomator` reports only "See all" as clickable below the game chips.
Evidence: `61-today-done.png`, `62-today-scrolled.png`; `106-today-scrolled.png` and `107-recent-tap.png` (15:24); clip `63-seeall.mp4`; `108-freeze-tap.png`.
Why it matters: this is the home screen at the moment a new player has just finished their first day. It tells them they have played nothing and offers no next step.
Recommendation: rename the section "Earlier days" or include today; make rows open that day's result; make the day ring open that day; make the freeze chip explain freezes; send "See all" to a history list, or relabel it "See ranks".

**L6. The feel is uneven between games: Strands has no solve beat, and Zip and Tango are silent in the hand.**
What happened: in Strands the "Spangram!" chip appears at 1.78 s in the clip and the slide to the result begins at about 1.85 s, so there is no hold on the finished board. Zip, Tango and Queens hold for 0.8–0.9 s; Sudoku runs a green ripple wave. For haptics, the system vibration log recorded nothing for a Zip drag of three cells, two undos and a hint (15:33), and nothing for four Tango taps (15:36). Sudoku logged one `TEXTURE_TICK` per digit placed (15:35:50.959 and 15:35:56.266); selecting a cell, Erase and Undo logged nothing. Queens is inconsistent: one sequence of taps that ended in an adjacent-queen conflict logged `TEXTURE_TICK` at 15:34:47.620 and `HEAVY_CLICK` at 15:34:48.590, while three later taps on another cell plus Undo (15:35:01–15:35:08) logged nothing. Earlier in the session the log shows 5 `CLICK` and 14 `TEXTURE_TICK` at 15:05, when Strands was being played. Whether the solve itself vibrates was not verified.
Evidence: `50-sg.m.png`, `50-sf.m.png`, clip `50-s-solve.mp4`; `16-solve.m.png`, `30-solve.m.png`, `60-sf.m.png`; vibration log excerpts in `/tmp/fable-live/vib0.txt` plus the live readings above.
Why it matters: the solve is the reward for every play, and two of five games give no physical confirmation of a move. The caveat is that input was injected, so a real finger was not tested [INFERENCE that it behaves the same].
Recommendation: route Strands through the same hold-then-hand-off as the other games; give all five one haptic vocabulary (place, remove, reject, solve) and one Settings switch.

### Minor

**M1. Studio moves under the finger and explains itself late.**
The board grows and moves when switching Path → Numbers because the "Reverse / Clear path" row disappears (`82-studio-full.png` vs `84-numbers.png`, 15:17); it shifts again by about 48 px when the draft becomes unique (`87-numbers3.png`, 15:18). "Next: Numbers" stays on screen while already in Numbers. The uniqueness overlay (green line, cyan squares) has no legend, and its green line runs through green digits (`85-numbers-added.png`). The sheet's advice, "Change one of the marked cells so only one fits.", is an awkward fit for Zip, where I fixed it by adding numbers. The empty board gives no instruction (`80-studio-zip.png`). Recommendation: reserve a fixed-height tool row so the board never moves; one line of coach text on an empty board ("Drag to draw the path"); a one-line legend the first time the overlay appears; per-game advice text.

**M2. Test solve is not "like a player".**
The screen promises "Solve it like a player…" but has no toolbar and no timer, leaves a large empty band, and keeps the bottom navigation visible (`88-testsolve.png`). Completion has no solve animation; "Solved in 0:20" simply appears with "Back to editor" (`89-test-done.png`, `89-done.m.png`). Recommendation: reuse the real play chrome.

**M3. Sudoku tutorial reflows, and digit-first input is ignored.**
The layout shifts between steps; in step 3 the keypad reflows to icon-only Notes/Erase/Clear; the instruction is four lines; tapping a digit with no cell selected gives no response; the conflict text mentions the row only; the pale-orange digit on a cyan selected cell is low contrast (`52-su.m.png`, `53-su-s2.png`, `54-su-s3.png`). Recommendation: fixed layout across steps, two-line instructions, and either support digit-first or pulse the board when a digit is tapped with nothing selected.

**M4. Ranks: a label that looks like a button, a default scope that is empty, and a statistic under the wrong heading.**
The green "Friends" text beside the title does nothing on tap (clip `67-friends-tap.mp4` shows no frame change; `67.m.png`). The scope defaults to Friends for a signed-out player with no friends. In the month view on the Zip page, "Best 1:28" is the Mini Sudoku time (`68.m.png`). Recommendation: make the scope label open the tune sheet; default to Global until Friends has someone else; scope the month statistics to the page's game.

**M5. Settings: visibility radios are dead when signed out, and common settings are missing.**
None of the four Visibility options is selected, tapping one does nothing, and nothing says why (`104-visibility-tap.png`, 15:24). There is no haptics switch, no reminder, no way to hide the timer, no rules, no feedback link and no privacy or terms link (`103.m.png`). Recommendation: show the section disabled with "Sign in to choose"; add haptics and reminder switches when those features land.

**M6. Queens region colours are hard to tell apart in dark theme.**
Nine regions sampled at RGB (50,95,52) (74,76,128) (76,59,1) (73,46,100) (1,72,65) (102,37,32) (118,68,31) (2,93,109) (21,61,109); blues and purples sit close together, and white crowns hide among up to about 72 white auto-crosses (`41-q.m.png`, `133-q928.png`). Light theme is clearly easier (`113.m.png`). Recommendation: raise lightness and saturation of the dark palette, dim the auto-crosses, and give the crown a filled backing.

**M7. Four sign-in labels, and a silent failure.**
"Sign in" (result), "Sign in with Google" (Ranks, Profile), "Continue with Google" (Duels sheet), "Sign in & publish" (Studio). Early in the session I tapped the result's "Sign in" twice: the button rippled and nothing else happened (`19-signin-frames.m.png`); logcat showed `PuzzleBoxAuth: idToken unavailable: NO_CREDENTIAL`. No account chooser appeared and nothing was sent. This is expected for the benchmark package, but the app showed no message. Recommendation: one label, and a visible error when sign-in cannot start.

**M8. Zip feedback placement and Undo state.**
"Go to 2 first" appears bottom-left above the toolbar, while the tutorial shows messages centred under the board (`12-zip-wrongnum.png` vs `04-zip-wall-after.png`). Undo looks enabled with nothing to undo (`10-zip.m.png`). Clear wipes the board at once with no confirmation; it can be undone (`13-14.m.png`). Recommendation: one message position directly under the board; disable Undo at the start state.

**M9. Past puzzles are not marked while playing, and their label changes.**
The play header reads only "Zip · #607" (`72-zip607.png`). The archive chip says "Past puzzle" before play and "From the archive" after (`71-arch-sel.png` vs `73.m.png`). Recommendation: one label, shown as a small chip in the play header.

**M10. Tutorials are silent on wrong actions.**
Zip step 3 shortcut, an invalid Strands selection and tapping a Tango given all produce nothing (`07-zip-tut3-short.png`, `43-s.m.png`, `21-tango-open.png`). Tango's ghost cells look disabled. Tango advances the instant a step is satisfied, so a quick second tap lands on the next board. Recommendation: reuse the in-game rejection messages inside tutorials and add a 300 ms pause before advancing.

**M11. "How to play" replays the whole tutorial.**
There is no short rules card for a player who only wants to check one rule mid-solve. Recommendation: open a rules sheet with "Replay tutorial" as a secondary action.

**M12. Share text is bare.**
The payload is "PuzzleBox Zip #608 · 3:03 · 1 hint": no link and no grid. The share sheet was opened once, dismissed, and its screenshot deleted because it showed personal contacts; nothing was sent. Recommendation: add the app link and, where it fits, an emoji summary.

**M13. Replay opens at the end, and Back loses the result's scroll position.**
Replay starts paused on the finished board, so the first tap must be a scrub or restart; the "Show errors" control is a triangle with no label; returning to the result loses its scroll position (`18-replay.m.png`). Recommendation: open at the start and play.

**M14. Enter code sheet details.**
The sheet is good, but typed characters stay lowercase, autocorrect is on, and closing takes two Back presses (`76-entercode.png`, `77.m.png`). Recommendation: force capitals and turn off suggestions.

**M15. Light theme: white text on the cyan and orange game cards is low contrast.**
`111.m.png` (15:26): the cyan and orange game cards carry white text at low contrast. Recommendation: use the dark ink colour on those two, as the lime button already does.

**M16. Games tab wording does not follow the chip.**
"Every puzzle, back to #1" stays under the title on Friends and Duels; "Enter code" appears in the header and again in the Friends empty state (`74.m.png`).

### Nit

- Splash icon is red-brown on grey; brand is lime (`01-coldstart.sheet0.5.png`).
- "TODAY" appears twice in the Today card (`61-today-done.png`).
- Two accents on one screen: blue active-tab pill, lime primary button.
- "crown" and "colour" in Create ("One crown in every row, column and colour region", `93-newpuzzle.png`) against "queen" and "color" in the play error (`35-q.m.png`).
- The Zip solve sheen is a square frame over a rounded board (`05-done-frames.m.png`).
- Studio "by" placeholder truncates: "Filled from Google on s…" (`91.m.png`).
- A brand-new player already holds two streak freezes (❄ 2 on Today after the first day, `61-today-done.png`).

---

## 4. Phone-specific issues

**One-hand reach.** Good where it counts. The play toolbar sits at y≈2095 of 2244 px and the primary result and Today buttons are in the lower half. Out of reach for a right thumb: Back (72,185), How to play (936,185), the overflow menu beside it, Profile (918,269) and "Enter code" in the Games header. All are low-frequency except Back, which the system gesture covers. Boards are vertically centred with large empty bands above and below (`09-zip-game.png`); Sudoku instead starts its board high (first row at y≈389 against y≈682 for Tango) and its keys nearly touch the grid (`56-sudoku-game.png`).

**Keyboard.** Handled well. The Enter code sheet autofocuses and rides above the keyboard (`77.m.png`). In Studio's publish details the content pans so the focused field stays visible (`91.m.png`, 15:20). In the Strands editor the board and the word field both stay visible and letters fill the tiles as typed (`97-strands-typed2.png`, 15:22). One oddity, low confidence: a single `adb input text bird` produced "BR" twice while four separate key events within 387 ms produced "BIRD"; this is probably the test tool, but it is worth one manual fast-typing check.

**Small width.** At 448 dp nothing truncated at default font: "Mini Sudoku" fits on Today and the Studio subtitle fits beside the "2+ solutions" chip. A 9×9 Queens board gives cells of 104 px, about 46 dp (`113.m.png`), just under the 48 dp guideline; scaled to a 360 dp phone that would be about 37 dp [INFERENCE, not measured].

**Large fonts.** See L3. Summary: usable but scruffy at 1.3×, broken on Games and the archive at 2.0×; the board, keypad digits and toolbar hold up.

**Landscape.** See L4.

**Light and dark.** Both are complete. Dark is the better-tuned of the two except for Queens (M6); light has the white-on-cyan problem (M15).

---

## 5. Smoothness and glitches

Numbers from `dumpsys gfxinfo com.ivandsky.puzzlebox.benchmark` at the end of the session (15:31), covering all play, recording and theme, rotation and font changes:

- `Total frames rendered: 29305`, `Janky frames: 665 (2.27%)`
- 50th percentile 6 ms, 90th 13 ms, 95th 15 ms, 99th 22 ms
- `Number Missed Vsync: 22`, `Number Slow UI thread: 415`, GPU 99th percentile 8 ms
- Mid-session reading at 14:58: 8,303 frames, 205 janky (2.47%), worst frames 73 ms and 81 ms

At 120 Hz the budget is 8.3 ms, so roughly a third of frames miss a 120 Hz slot while almost all make 60 Hz. To the eye this is smooth with an occasional soft step.

Frame gaps inside animations, from `screenrecord` timestamps (frames / gaps over 12.5 ms / gaps over 25 ms / longest gap):

| Clip | Result |
|---|---|
| `69-tab-games.mp4` | 100 / 4 / 0 / 19.9 ms |
| `79-tab-create.mp4` | 99 / 2 / 0 / 15.8 ms |
| `101-tab-today.mp4` | 100 / 2 / 0 / 16.0 ms |
| `73-back.mp4` | 44 / 3 / 1 / 32.4 ms |
| `63-seeall.mp4` | 104 / 9 / 2 / 41.0 ms |
| `64-ranks-swipe.mp4` | 74 / 4 / 1 / 40.8 ms |
| `16-zip-solve.mp4` | 356 / 15 / 4 / 58.1 ms |
| `50-s-solve.mp4` | 271 / 10 / 3 / 41.3 ms |
| `60-su-solve.mp4` | 211 / 8 / 0 / 18.1 ms |
| `105-chip.mp4` (Today chip → result) | 159 / 6 / 1 / 25.3 ms |
| `88-testsolve.mp4` | 74 / 5 / 1 / 43.4 ms |

What that means in use: tab switches are a clean 100–150 ms cross-fade (`101-tab.m.png`). The solve hand-off from board to result is where the longest hitches sit (one 58 ms gap in Zip, 41 ms in Strands). Opening Ranks and the first page swipe each drop a few frames. Sudoku's ripple is the cleanest solve.

Glitches seen: none of the classic kind (no flicker, no blank frame, no stuck state, no crash across about 50 minutes). 45 rapid Tango taps all registered (`29.m.png`). What does read as a glitch is layout movement: Studio's board resizing between modes (M1) and the Sudoku tutorial reflow (M3). The snackbar after deleting a draft lifts the "New puzzle" button (`100-delete-confirm.png`), which is correct behaviour.

---

## 6. Pass 2: the stills review, checked on the phone

Source: `docs/research/polish/fable-ux-review.md`. "Not testable" means it needs a signed-in account or content this install could not reach.

| ID | Stills finding | Live result | Evidence |
|---|---|---|---|
| E1 | App never calls the player back | Confirmed. No permission prompt in the session, no reminder setting, and the installed package requests no notification permission. | `103.m.png`; `dumpsys package` shows no `POST_NOTIFICATIONS` |
| E2 | Rank comes last on the result | Confirmed, and worse signed out: the only rank line is a sign-in prompt below the fold. | `17-zip-result-scroll.png`, `110.m.png` |
| E3 | No challenge from the result | Confirmed. | `16-zip-solved-c.png`, `60-su-result.png` |
| E4 | Duels are hard to find | Confirmed. Third chip in Games; absent from Today, result and tab bar. | `75-duel-tap.png`, `111.m.png` |
| E5 | Friends hint in Ranks is a dead end | Not testable signed out. Related live finding: default scope is Friends and the "Friends" label is inert. | `67.m.png` |
| E6 | Ranks controls hidden three ways | Confirmed, with a change: the scope is shown as green text, but it cannot be tapped. | `63-seeall.png`, `64-ranks.m.png` |
| E7 | Icon-only Share and Play again | Confirmed. | `16-zip-solved-c.png` |
| E8 | Recent rows inert on phone | Confirmed, and wider: day rings, freeze chip and streak card are inert too. | `107-recent-tap.png`, `108-freeze-tap.png` |
| E9 | Test solve ends on "Back to editor" | Changed by live use. The button is the only action, but the editor it returns to has already switched to publish details with "Sign in & publish" in lime. The label undersells what happens. | `89-test-done.png`, `90.m.png` |
| E10 | Sign-in worded many ways | Confirmed; button labels vary as well as titles. | M7 |
| C1 | Same concept, several names | Confirmed for Ranks / "the ranks" / "leaderboards", crown-colour vs queen-color, "this phone" vs "Saved on this device", and three meanings of Friends. Duel rounds/legs not testable. | `63-seeall.png`, `102-profile.png`, `103.m.png`, `93-newpuzzle.png`, `35-q.m.png`, `70-zip-archive.png` |
| C2 | Hint over-promoted against scoring | Confirmed, and worse live: behaviour differs per game and teaching hints are counted without being applied. | L2 |
| C3 | Truncations ("Mini Sud…", Studio subtitle) | Changed. Neither occurs at 448 dp and default font. "Mini Sud…" appears at 1.3× and in landscape. Tablet archive stat not testable. | `106-today-scrolled.png`, `124.m.png`, `116-land-today.png`, `82-studio-full.png` |
| C4 | Histogram needs a paragraph | Confirmed; on a first solve it is one bar with two paragraphs. The "any day" word-chip was not seen (first day only). | `17-zip-result-scroll.png` |
| C5 | "Show errors" triangle | Confirmed. | `18-replay.m.png` |
| C6 | Ranks glyph competes with data | Partly confirmed. Signed out there are no rows to fight, but the giant glyph is cropped by the right edge. | `63-seeall.png`, `64-ranks.m.png` |
| C7 | Connected LinkedIn row unlabelled | Not testable. Disconnected row is clear. | `102-profile.png` |
| C8 | Duels empty state explains little | Confirmed: one card, one sentence, blank below. | `111.m.png` |
| C9 | Friend puzzle card repeats itself | Not testable (no valid code). | — |
| C10 | Sudoku key count unlabelled | Confirmed; the tutorial does not mention it. Content descriptions are good ("1, 5 left"). | `56-sudoku-game.png` |
| C11 | Developer language | Confirmed for "Paste notation from the PuzzleBox tool" and typed "✓ Saved". | `93-newpuzzle.png`, `80-studio-zip.png` |
| C12 | Studio dense on phone | Confirmed, including "Not tested" and "Not unique" bracketing the buttons. Live adds layout shifts. | `80-studio-zip.png`, M1 |
| P1 | Five boards look like different apps | Confirmed. Zip rounded with a raised path, Strands chunky tiles, Queens and Sudoku square and flat, Tango thin-line. | `130-zip607.png`, `133-q928.png`, `56-sudoku-game.png`, `45-strands-game.png`, `21-tango-open.png` |
| P2 | Haptics uneven, solve silent | Mostly confirmed by the vibration log: Sudoku ticks per digit, Queens vibrates on some taps, Tango logged nothing. Changed for Zip: the stills review expected drag ticks, and none were logged. Solve haptic not verified. | L6 |
| P3 | Ordinary solve and all-done are flat | Confirmed for all-done. The ordinary (non-first, non-best) result was not seen on day one. | `60-su-result.png`, `61-today-done.png` |
| P4 | Goldens show placeholder glyphs | Refuted at runtime: real glyphs render on Today, Games, result, Ranks and Create, and Studio shows the real board. Duel and publish screens not testable. | `106-today-scrolled.png`, `111.m.png`, `92-create-drafts.png` |
| P5 | Empty vertical space on the result | Changed. The gap between Back and the hero is about 53 dp here, not 110. The empty bands are in play, above and below the board. | `110.m.png`, `09-zip-game.png` |
| §3 | Tutorial time not counted | Consistent: the timer shows 0:02–0:04 on the first real frame after a tutorial. | `09-zip-game.png` |
| §5 | Tablet layouts are a strength | Changed for phones: the same layouts trigger in phone landscape and break. | L4 |
| §5 | Dark theme well tuned | Confirmed except Queens region colours. | M6 |
| §5 | Undo snackbars for destructive actions | Confirmed. | `100-delete-confirm.png`, clip `100-delete.mp4` |
| NC3 | Home title "PuzzleBox" or "Today"? | Live answer: "Today". | `01-first-launch.png` |
| NC4 | Create empty state lists three games? | Live answer: all five. | `79-create.png`, `93-newpuzzle.png` |

New on the phone and absent from the stills review: L3 (font scaling), L4 (phone landscape), L5's Recent contradiction and "See all" target, the per-game hint behaviour in L2, Strands' missing solve beat, Studio and tutorial layout shifts, the dead Settings radios, and the smoothness figures.

---

## 7. Top 10 polish moves, ranked by impact ÷ effort

1. **Fix Today's Recent section and dead surfaces** (L5). Copy plus three click handlers on the home screen every player sees daily.
2. **Rank line and "Challenge a friend" in the result hero; make it the primary action when all five are done** (L1). Closes the loop with components that already exist.
3. **Make Hint neutral and state its cost once; add "Place it" to teaching hints** (L2). One shared toolbar change plus one action in three hint tooltips.
4. **Lock phones to portrait, or gate the expanded layout on height** (L4). A one-line manifest or window-class change removes a whole class of breakage.
5. **Let Games cards and the archive hero grow with text** (L3). Replace fixed heights with minimum heights; add 1.3× and 2.0× goldens.
6. **Give Strands the shared solve hold, and add place/solve haptics to Zip and Tango** (L6). Small, and it lands on every play.
7. **Stop Studio and the Sudoku tutorial from moving** (M1, M3). Reserve fixed rows; add one coach line on the empty board.
8. **Make the Ranks scope label tappable, default to Global when Friends is empty, fix the cross-game "Best"** (M4).
9. **One sign-in label, a visible sign-in error, and a disabled state with a reason for Visibility** (M5, M7). Copy and state only.
10. **Retune the dark Queens palette and dim auto-crosses** (M6). A palette change in one game.

Next in line: a real "all done" moment with the day's total (L1, stills P3), a rules sheet behind "How to play" (M11), real play chrome in Test solve (M2), a past-puzzle chip in the play header (M9), a richer share text (M12).

---

## 8. What is great (keep)

- **Speed to play.** 0.35 s to Today, one tap to a board, and "Next: <game>" chaining the set.
- **Tutorials that are played, not read**, in all five games, with step dots and Skip.
- **The solve beat in Zip, Tango and Queens, and Sudoku's ripple**: sheen, outline, a short hold, then the result.
- **The archive page.** Calendar with puzzle numbers, jump chips, a legend, and exact copy about what counts: "Counts toward your best, never your streak", "Needs a connection the first time". Leaving mid-puzzle saves silently and offers "Keep going" (`70-zip-archive.png`, `73.m.png`).
- **Today's day rings and per-chip times**, and a solved chip that opens its result.
- **Sign-in that never blocks play**, with reassurance at the point where it is asked (the Ranks card says solves stay on the phone either way).
- **Error copy in the game's own terms**: "Go to 2 first", "Too short", "Not in word list", and one-sentence conflict lines with hatching in Tango and Queens.
- **Studio's live uniqueness check** with Solution A/B on the board, 1 and 2 placed automatically as the path is drawn, immediate auto-save, and "Delete draft" with an Undo snackbar instead of a dialog.
- **The Enter code sheet**: autofocus, rides the keyboard, disabled until six characters, inline "No puzzle with that code".
- **Keyboard handling** everywhere it was tried.
- **Sudoku details**: remaining counts on keys and thorough content descriptions ("Row 1, column 1, empty").
- **Stability and input fidelity**: no crash, no dropped tap in 45 rapid taps, no blank frames.

---

## 9. Housekeeping

- Device settings restored and verified at 15:37: `font_scale=1.0`, `Night mode: yes`, `accelerometer_rotation=0`, `user_rotation=0`. The benchmark app is on Today (`140-final-today.png`).
- Left in the benchmark install only: one unpublished local Zip draft "Cozy Birch" (author field "Tester"), today's five solves, and four opened archive puzzles from 14 November (Zip #607, Queens #928 and Mini Sudoku #460 in progress; one Tango with four taps). Nothing was published, signed in or sent.
- The packages `com.ivandsky.puzzlebox` and `com.ivandsky.puzzlebox.debug` were never launched or touched.
- The build ran once through the machine's `build-slot` wrapper, which I did not request; it appeared only to queue the Gradle run [INFERENCE].
- No tests were run and no source was changed; this file is the only repo change.

## 10. NEEDS_CONTEXT

1. Is phone landscape meant to be supported? If not, locking to portrait resolves L4.
2. Is "Recent" on Today meant to exclude today and to include unfinished archive plays (L5)?
3. Should a hint that only teaches, and is dismissed unused, count against rank (L2)?
4. Does the production build show an error when Google sign-in cannot start? The benchmark build fails silently, which may be specific to its unregistered package (M7).
5. Are two streak freezes for a brand-new player an intended starting gift?
6. Are the Visibility radios meant to be inert when signed out (M5)?
7. Do Zip and Tango vibrate under a real finger? The log says no for injected input (L6).
