# PROFILE.md — written by init-profile, read by /start
# 1.0.0 = public release of ALL games (Zip, Queens, Tango, MiniSudoku, Strands) in a
# good tested state. Until then the app is ALPHA: 0.x.y forever; minor = phase/feature
# milestone, patch = fix rounds.

## Version
- app-id: com.ivandsky.puzzlebox
- app-name: PuzzleBox
- version: 0.13.0  # semver source of truth; bumped ONLY on a user-requested release (on main, with the Gradle versionName fallback in one commit). Installs use deliver build_identity.py: <semver>-<slug>.<n>. History: 0.13.0 = Mini Sudoku (LinkedIn daily) + debug 9x9 Sudoku on one engine; 0.12.0 = NYT Strands (fifth game: drag board, hints, rating) + past puzzles by date for archive games, per-game rank order, per-game saved-play cap; 0.11.0 = per-tab back stacks, games hub, saved plays per (game, day) that resume after process death (replaces 0.9.0's kept sessions); 0.10.1 = Queens lesson 2 taps, dashed ghost crowns, Tango lesson 4 copy; 0.10.0 = first-play tutorials + How to play, Home Today card, plain copy; 0.9.0 = kept sessions + Continue, feedback caption slot, contrast floors, honest best; 0.8.0 = Play tab opens the game picker; core|game chrome split; command-based undo/redo; 0.7.0 = merge of Queens and Tango runs

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

## Effort
- level: thorough            # restored 2026-11-01 after brief downgrade: run default only; PER-TASK EFFORT IS THE RULE (effort-mapping.md): fast = contained fixes, standard = ordinary feature tickets, thorough = architecture/module/cross-cutting only; agent flags each ticket's level in the run plan
- debug: off

## Platform & stack (deltas only)
# Preferred stack + default platform live in ~/.agents/AGENTS.md.
# platform: (none — matches user default, Android; mobile + tablet support)
# stack: (none — matches preferred stack)

## Deploy targets (in order; empty list = build-only)
- local: ./gradlew :app:assembleDebug
- tailscale: ivandsky.github
  - device: ivans-tab-s9-ultra @ 100.93.158.113  # verified 2026-11-01 (only Android node on tailnet)
# - firebase: <project id>  # activate once the Firebase project is created; deliver moves off tailscale then

## Review site publish
always  # user ruling 2026-10-31: every review gate publishes a localhost HTML review page (REVIEW.md contract + archify diagrams); served on LAN :8777

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
