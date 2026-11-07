# PROFILE.md — written by init-profile, read by /start
# 1.0.0 = public release of ALL games (Zip, Queens, Tango, MiniSudoku, Strands) in a
# good tested state. Until then the app is ALPHA: 0.x.y forever; minor = phase/feature
# milestone, patch = fix rounds.

## Version
- app-id: com.ivandsky.puzzlebox
- app-name: PuzzleBox
- version: 0.16.0  # semver source of truth; main's release line; every merge to main publishes GitHub release v<version> (.github/workflows/release.yml, skipped when the tag exists). Every bugfix bumps patch (user rule), with the Gradle versionName fallback in the same commit. Installs use deliver build_identity.py: <semver>-<slug>.<n>, APK PuzzleBox-<versionName>-<code>-<variant>.apk. Gradle versionCode fallback 100 (2026-11-05: headroom above every worktree's hand-set codes, max 23). History: 0.16.0 = polish: bigger Sudoku numpad, smoother Zip/Strands tracing, Splash colours with lips, chunky icon game buttons, Queens Candy palette + small crosses + satisfaction motion, softer nav, full-width tablet play with stats side panel; 0.15.0 = release pipeline (signed APK per merge to main, release key ~/.android/puzzlebox-release.jks) + Home "Update available" pill in release builds; debug installs as com.ivandsky.puzzlebox.debug "PuzzleBox Dev"; 0.14.0 = Home summary card with status badges + tablet streak & stats pane; Play is a games library (list + game page: day card, month calendar back to #1, stats, how to play with the lesson); History is the All timeline only; every game's own brand colour and glyph; Strands rolls over at LA midnight and ranks bests hints first; debug 9x9 Sudoku only from debug settings; LinkedIn archives from #1 merged in; board thumbnails retired; 0.13.6 = a segmented ring around each day replaces the day dots, Play hub goes to a 2-column grid on phones at 5+ games; 0.13.5 = versionCode 22 so it installs over other builds; 0.13.4 = Play hub without buttons (tap a card showing your live board thumbnail), History calendar folds to one week; 0.13.3 = versionCode 20 to sit above the parallel strands/sudoku builds (vc18/19) so it installs over them; 0.13.2 = Tango glyph is the board's rayed sun only; 0.13.1 = History month calendar with tappable days + month paging, cleaner History pills and buttons, Tango sun-and-moon glyph; 0.13.0 = surfaces: Home tiles + one next-puzzle button + streak week, hub brand cards, immersive play with one chrome and solve beat, History game tabs + stats + weeks, Result card with Share, lesson cleared beat, Profile record + Appearance; 0.12.0 = identity layer: game brands + accent slots, 9-role type, pill buttons, icon set + game glyphs, nav icon states, motion tokens + solve sweep/stamp, week strip, debug design settings; 0.11.1 = solved-day streak, Home recent grouped by day, puzzle-day clock, m:ss everywhere, Tango Practice, plain copy; main: 0.13.0 = Mini Sudoku (LinkedIn daily) + debug 9x9 Sudoku on one engine; main: 0.12.0 = NYT Strands (fifth game: drag board, hints, rating) + past puzzles by date for archive games, per-game rank order, per-game saved-play cap; 0.11.0 = per-tab back stacks, games hub, saved plays per (game, day) that resume after process death (replaces 0.9.0's kept sessions); 0.10.1 = Queens lesson 2 taps, dashed ghost crowns, Tango lesson 4 copy; 0.10.0 = first-play tutorials + How to play, Home Today card, plain copy; 0.9.0 = kept sessions + Continue, feedback caption slot, contrast floors, honest best; 0.8.0 = Play tab opens the game picker; core|game chrome split; command-based undo/redo; 0.7.0 = merge of Queens and Tango runs

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
- release: .github/workflows/release.yml  # push to main → signed release APK on GitHub releases; local release builds sign with the release key via ~/.gradle/gradle.properties
# releases run in one queue; a 3rd quick merge replaces the 2nd's pending run (that version is skipped; next run releases HEAD — re-dispatch from main to publish now)
# debug installs are com.ivandsky.puzzlebox.debug ("PuzzleBox Dev") beside the release app com.ivandsky.puzzlebox
- tailscale: ivandsky.github
  - device: ivans-tab-s9-ultra @ 100.93.158.113  # verified 2026-11-01 (only Android node on tailnet)
  - logs: wireless adb over tailscale, already paired: `adb connect 100.93.158.113:<port from Wireless debugging>`  # verified 2026-11-05
# - firebase: <project id>  # activate once the Firebase project is created; deliver moves off tailscale then

## Review site publish
off  # user ruling 2026-11-05 (replaces 2026-10-31 `always`): publish the review site ONLY when the user explicitly asks. Explicit user no — also overrides the thorough-effort default. REVIEW.md is still written every review; deliver gives its file path only.

## Delivery
- changes: pr   # user ruling 2026-11-05 (strands run): runs land as a PR from the run branch into main

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
