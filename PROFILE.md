# PROFILE.md — written by init-profile, read by /start
# 1.0.0 = public release of ALL games (Zip, Queens, Tango, MiniSudoku, Strands) in a
# good tested state. Until then the app is ALPHA: 0.x.y forever; minor = phase/feature
# milestone, patch = a user-requested release of accumulated fixes.

## Version
- app-id: com.ivandsky.puzzlebox
- app-name: PuzzleBox
- version: 0.22.1  # semver source of truth; main's release line; every merge to main publishes GitHub release v<version> (.github/workflows/release.yml, skipped when the tag exists). Bugfixes merge without a bump and ship in the next release the user asks for (user rule 2026-11-08); a release bumps this line and the Gradle versionName fallback in one commit. Installs use deliver build_identity.py: <semver>-<slug>.<n>, APK PuzzleBox-<versionName>-<code>-<variant>.apk. Gradle versionCode fallback 100 (2026-11-05: headroom above every worktree's hand-set codes, max 23). History: 0.22.1 = a crafted Strands share code can no longer crash a friend's app (#467); a Strands end drag yields to an edit from another finger (#465); 0.22.0 = Tango editor released: author Tango puzzles 4×4–10×10 with signs, live uniqueness verdict, hints, test solve, Publish with share codes and leaderboards; a duel reads the opponent's results at close and match listeners recover after an error (#453); 0.21.1 = Zip editor: a wall tap works along the whole side, near corners the nearer wall wins (#448); the compete reveal count-up shares the app motion spec (#447); 0.21.0 = Zip editor released: author 5×5–8×8 Zip puzzles with walls and numbers, live uniqueness verdict and hints, test solve, Publish with share codes and leaderboards; the NavV2 rail is centred and enlarged (#439); 0.20.0 = one-deduction-per-press hints live in Queens, Tango and Mini Sudoku (flip PR, ADR-0022): Hint never places, shows one human step in a Spotlight, marks a wrong entry first, repeats are free, five-second cooldown ring on every hint button; Classic Sudoku, Zip and Strands keep their hints; 0.19.5 = Studio on small phones: compact Strands word field and a Publish-only action area in phone Edit board, Strands cells ≥30dp at 360dp (#408); Strands dimmed links meet the painted lip (#410); faster Queens look-ahead hints (#409); duel reveal taps out its beats (#417) and follows late results (#412); past the deadline a duel no longer asks for your move (#419); 0.19.4 = Studio: Edit board stays open when the passed test comes back (#402); phone Studio keeps every Strands row in reach and Edit board gives the board its height back (#354); Strands editor: thinner lines on small cells (#367), no end ring (#382), board centred on phones (#384), "isn't" in the overflow line (#353); Strands trace never draws a link to a cell outside the path or through a rejected tile (#389, #390); Queens every drag clears the Spotlight (#398) and a stored solution counts only when it is the board's one (#400); five-second hint cooldown with a fill ring (#394); Sudoku and Tango drop a pending hint when the board swaps (#391, #399); duel results retry posting when the network returns (#405); 0.19.3 = Queens Studio: Undo/Redo in publish details (#304), the keyboard keeps the board and the focused field visible (#309), a long check shows an indeterminate progress cue on the verdict card (#326); 0.19.2 = Tango fast repeat taps on one cell never dropped (play and test solve); 0.19.1 = Zip/Strands trace rework: hit-test, path-as-wall, constrained live tip, lift home; 0.19.0 = per-game daily leaderboards live (flip PR, ADR-0022/0024): Profile switcher + per-game board, Result board, tablet panel ranked by each game's rule, honest posting status; 0.18.0 = hero Results page live after a daily solve (flip PR, ADR-0022): large counting-up time, new-best celebration, streak and week, chart of your times for the game, rank among players today when signed in; 0.17.1 = Tango Hint button finishes real dailies (line-completion rung); 0.17.0 = friend sharing live (flip PR, ADR-0022): publish a custom puzzle from the editor, share code + link, Enter code, Library From friends, friend card + per-puzzle leaderboard, Create Published tab, https://puzzlebox-f3356.web.app/p/<code> links open the app (App Links filter now in the main manifest); 0.16.0 = polish: bigger Sudoku numpad, smoother Zip/Strands tracing, Splash colours with lips, chunky icon game buttons, Queens Candy palette + small crosses + satisfaction motion, softer nav, full-width tablet play with stats side panel; 0.15.0 = release pipeline (signed APK per merge to main, release key ~/.android/puzzlebox-release.jks) + Home "Update available" pill in release builds; debug installs as com.ivandsky.puzzlebox.debug "PuzzleBox Dev"; 0.14.0 = Home summary card with status badges + tablet streak & stats pane; Play is a games library (list + game page: day card, month calendar back to #1, stats, how to play with the lesson); History is the All timeline only; every game's own brand colour and glyph; Strands rolls over at LA midnight and ranks bests hints first; debug 9x9 Sudoku only from debug settings; LinkedIn archives from #1 merged in; board thumbnails retired; 0.13.6 = a segmented ring around each day replaces the day dots, Play hub goes to a 2-column grid on phones at 5+ games; 0.13.5 = versionCode 22 so it installs over other builds; 0.13.4 = Play hub without buttons (tap a card showing your live board thumbnail), History calendar folds to one week; 0.13.3 = versionCode 20 to sit above the parallel strands/sudoku builds (vc18/19) so it installs over them; 0.13.2 = Tango glyph is the board's rayed sun only; 0.13.1 = History month calendar with tappable days + month paging, cleaner History pills and buttons, Tango sun-and-moon glyph; 0.13.0 = surfaces: Home tiles + one next-puzzle button + streak week, hub brand cards, immersive play with one chrome and solve beat, History game tabs + stats + weeks, Result card with Share, lesson cleared beat, Profile record + Appearance; 0.12.0 = identity layer: game brands + accent slots, 9-role type, pill buttons, icon set + game glyphs, nav icon states, motion tokens + solve sweep/stamp, week strip, debug design settings; 0.11.1 = solved-day streak, Home recent grouped by day, puzzle-day clock, m:ss everywhere, Tango Practice, plain copy; main: 0.13.0 = Mini Sudoku (LinkedIn daily) + debug 9x9 Sudoku on one engine; main: 0.12.0 = NYT Strands (fifth game: drag board, hints, rating) + past puzzles by date for archive games, per-game rank order, per-game saved-play cap; 0.11.0 = per-tab back stacks, games hub, saved plays per (game, day) that resume after process death (replaces 0.9.0's kept sessions); 0.10.1 = Queens lesson 2 taps, dashed ghost crowns, Tango lesson 4 copy; 0.10.0 = first-play tutorials + How to play, Home Today card, plain copy; 0.9.0 = kept sessions + Continue, feedback caption slot, contrast floors, honest best; 0.8.0 = Play tab opens the game picker; core|game chrome split; command-based undo/redo; 0.7.0 = merge of Queens and Tango runs

## Product (frame)
# purpose: one app for daily logic puzzles — play official NYT/LinkedIn dailies, build and share custom games, compete via leaderboards and 1v1; Zip ships first
# users: daily-puzzle players who want one place to play, create, and compete with friends
# core loop: open app → today's puzzle or a shared custom game → score recorded → check rank / challenge a friend → return tomorrow
# pain points: no history — official apps keep no browsable record of past games and scores; dailies scattered across NYT and LinkedIn apps with separate logins
# replay-awareness: app pulls your LinkedIn result for today so it shows you already played; any puzzle replayable anytime

## Care-axes (ranked: what quality means here)
1. UI/UX (premium)          # tied #1 with architecture; detailed design direction set at spec/design stage
1. architecture/SOLID       # tied #1 with UI/UX; equal review priority
2. reactivity
# non-goal: accessibility (TalkBack semantics, CVD cues, spoken labels) — user ruling 2026-11-04; not audited, ticketed or fixed unless asked

## Effort
- level: thorough            # restored 2026-11-01 after brief downgrade: run default only; PER-TASK EFFORT IS THE RULE (effort-mapping.md): fast = contained fixes, standard = ordinary feature tickets, thorough = architecture/module/cross-cutting only; agent flags each ticket's level in the run plan
- debug: off

## Platform & stack (deltas only)
# Preferred stack + default platform live in ~/.agents/AGENTS.md.
# platform: (none — matches user default, Android; mobile + tablet support)
# stack: (none — matches preferred stack)

## Deploy targets (in order; empty list = build-only)
- local: ./gradlew :app:assembleDebug
- release: .github/workflows/release.yml  # workflow "Main": push to main → full build + unit tests, then (green only) signed release APK on GitHub releases; local release builds sign with the release key via ~/.gradle/gradle.properties
# releases run in one queue; a 3rd quick merge replaces the 2nd's pending run (that version is skipped; next run releases HEAD — re-dispatch from main to publish now)
# debug installs are com.ivandsky.puzzlebox.debug ("PuzzleBox Dev") beside the release app com.ivandsky.puzzlebox
- tailscale: ivandsky.github
  - device: ivans-tab-s9-ultra @ 100.93.158.113  # verified 2026-11-01 (only Android node on tailnet)
  - logs: wireless adb over tailscale, already paired: `adb connect 100.93.158.113:<port from Wireless debugging>`  # verified 2026-11-05
# - firebase: <project id>  # activate once the Firebase project is created; deliver moves off tailscale then

## Review site publish
off  # user ruling 2026-11-05 (replaces 2026-10-31 `always`): publish the review site ONLY when the user explicitly asks. Explicit user no — also overrides the thorough-effort default. REVIEW.md is still written every review; deliver gives its file path only.

## Delivery
- changes: trunk   # user ruling 2026-11-07 (replaces 2026-11-05 `pr`): ship-to-trunk — one small PR per ticket off fresh origin/main, unfinished behavior merges dark behind a release flag, the flip PR (flag on + version bump) is the release
- pr-check: none   # user ruling 2026-11-07 (ADR-0023, replaces the required `pr-check`): PRs run no CI and main requires no status check. The merge gate is local: `./gradlew assembleDebug test` green on the PR branch rebased onto fresh origin/main. Main's workflow re-runs it after every merge; red main → stop merging, revert the culprit or fix it forward in one line. Skip `gh pr checks` waits (there are none)

## Consent ledger
- pre-approved: merge dark slices on green   # user ruling 2026-11-07: agent merges dark/non-behavioral slice PRs on a green LOCAL suite (`./gradlew assembleDebug test` on the rebased branch, ADR-0023) + clean fast review; flip PRs, live changes (migrations, persisted formats, manifest/permissions, dependency/SDK bumps) still wait for the user

## Testing style
- runner: ./gradlew :app:testDebugUnitTest   # JVM unit tests against fakes
- approach: tdd-fakes
- seams: repository/use-case interfaces (+ fakes), dispatcher provider
- structure: ./gradlew :konsist:test   # in-module structure rules (ADR-0009)

## Integrations (facts for spec/research)
- NYT Strands: public API exists — no scraping needed
- LinkedIn games (Zip, Queens, Tango, MiniSudoku): no public API — unofficial endpoints + session; research Unknown at spec time
- Firebase: Auth Google-sign-in-only (no email/password); leaderboard / 1v1 backend decided at spec

## Device access (Bug C session, 2026-11-02)
- Tab (ivans-tab-s9-ultra, 100.93.158.113) supports wireless adb over Tailscale: user opens Developer options → Wireless debugging → "Pair device with pairing code" (code+port rotate EVERY open — pair within seconds of receiving), then `adb connect 100.93.158.113:<debug-port-from-main-screen>`. Paired 2026-11-02, persists.
- Supervised-process PATH lacks sdk tools: use full path `/Users/ivandsky/Library/Android/sdk/platform-tools/adb`.
- The tab runs ReVanced microG, not real GMS — Credential Manager Google-ID works ONLY with the type-first + `GoogleIdTokenCredential.createFrom(credential.data)` pattern (4b7b419). `is GoogleIdTokenCredential` checks silently drop microG tokens.
