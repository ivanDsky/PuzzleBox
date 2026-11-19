# PROFILE.md — written by init-profile, read by /start
# 1.0.0 = public release of ALL games (Zip, Queens, Tango, MiniSudoku, Strands) in a
# good tested state. Until then the app is ALPHA: 0.x.y forever; minor = phase/feature
# milestone, patch = a user-requested release of accumulated fixes.

## Version
- app-id: com.ivandsky.puzzlebox
- app-name: PuzzleBox
- version: 0.38.0  # semver source of truth; main's release line; every merge to main publishes GitHub release v<version> (.github/workflows/release.yml, skipped when the tag exists). Bugfixes merge without a bump and ship in the next release the user asks for (user rule 2026-11-08); a release bumps this line and the Gradle versionName fallback and adds its entry at the top of docs/release-history.md, in one commit. Installs use deliver build_identity.py: <semver>-<slug>.<n>, APK PuzzleBox-<versionName>-<code>-<variant>.apk. Gradle versionCode fallback 100 (2026-11-05: headroom above every worktree's hand-set codes, max 23). Latest: 0.38.0 = SheetScreens, FilterSheets, HintPolish and RanksFreshness ship on, plus backlog fixes. Release history: docs/release-history.md.

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
- release: .github/workflows/release.yml  # workflow "Main": push to main → two parallel test jobs (assembleDebug testCore, testUi; no goldens in the default run) beside the signed release build; publish to GitHub releases only when all are green; local release builds sign with the release key via ~/.gradle/gradle.properties
# releases run in one queue; a 3rd quick merge replaces the 2nd's pending run (that version is skipped; next run releases HEAD — re-dispatch from main to publish now)
# debug installs are com.ivandsky.puzzlebox.debug ("PuzzleBox Dev") beside the release app com.ivandsky.puzzlebox
- tailscale: wireless adb over the user's tailnet; tailnet name, device names and addresses live in the gitignored `PROFILE.local.md` (this repo is public: never commit addresses, ports, serials or tailnet names)
  - target device: the machine name and address are only in `PROFILE.local.md`, as one line `- device: <machine-name> @ <tailscale-ip>` (read by ship_apk.py)
  - logs: wireless adb, already paired: `adb connect <tab address>:<port from Wireless debugging>`  # verified 2026-11-05
# - firebase: <project id>  # activate once the Firebase project is created; deliver moves off tailscale then

## Review site publish
off  # user ruling 2026-11-05 (replaces 2026-10-31 `always`): publish the review site ONLY when the user explicitly asks. Explicit user no — also overrides the thorough-effort default. REVIEW.md is still written every review; deliver gives its file path only.

## Delivery
- changes: trunk   # user ruling 2026-11-07 (replaces 2026-11-05 `pr`): ship-to-trunk — one small PR per ticket off fresh origin/main, unfinished behavior merges dark behind a release flag, the flip PR (flag on + version bump) is the release
- pr-check: none   # merge gate: `./gradlew assembleDebug test -x :app:testReleaseUnitTest -x :app:testBenchmarkUnitTest` green on the PR branch rebased onto fresh origin/main. User ruling 2026-11-07 (ADR-0023, replaces the required pr-check): PRs run no CI and main requires no status check. Main's workflow re-runs the full suite (release host tests included) after every merge; red main → stop merging, revert the culprit or fix it forward in one line. Skip `gh pr checks` waits (there are none). Debug only (user ruling 2026-11-17, ADR-0023 amendment): the gate skips `:app`'s release host tests and the disabled benchmark test task, which together compile every module a second time in release; a ticket that touches release-only wiring (`app/src/testRelease`, `app/src/release`, `buildTypeGameModules`, release flags or build type config) also runs `./gradlew :app:testReleaseUnitTest`. Screenshots are never a gate (user ruling 2026-11-17, ADR-0033): a ticket that changes a UI module renders it with `./gradlew :<module>:testDebugUnitTest -Proborazzi.record=true` and looks at the PNGs of the screens it changed; nothing is committed, verified or proven on clean main. The gate skips `:app`'s e2e tier (ADR-0035): the lane runs it through `e2e:` below, and a change to `:app`, `:core:navigation` or a `:feature:*:api` module made outside the lane runs `./gradlew :app:e2eTest` before its PR
- e2e: ./gradlew :app:e2eTest   # when: app, core:navigation, feature:*:api; user ruling 2026-11-19 (architecture review item 5, ADR-0035): `:app`'s e2e classes run in their own task, which the merge lane appends to a batch's suite only when the batch touches one of these modules; main's CI runs it after every merge
- release flags: `ReleaseFlag.kt` is the merge hotspot (41 PRs in 3 days). A slice that adds, flips or removes a flag rebases onto fresh origin/main right before its gate, and flag-touching PRs merge one at a time through the merge lane (ship-to-trunk's `merge_lane.py`, which stacks ready PRs and tests them with one suite run), never two in one lane batch (skill-doctor audit 2026-11-18)
- project map: feature, fix and cleanup PRs never edit `docs/agents/project-map.md`; the milestone docs PR updates it once (26 PRs in 3 days touched it; skill-doctor audit 2026-11-18)
- parallel trunk sessions: at most 3 at a time. core:designsystem, core:game-ui and core:common sit upstream of nearly every module, so merges from parallel sessions keep restarting each other's suites (skill-doctor audit 2026-11-18)
- builds: never `pkill`/`killall` build-slot, Gradle or java, and never `./gradlew --stop`: a pattern kill matches every session's build on the machine, merge-lane suites included, and a killed suite reports as a failure. To cancel your own build, stop only your own background job or PID (user ruling 2026-11-19, after three `pkill -f build-slot` runs from worker sessions killed other sessions' gates and one lane suite)

## Tracker                   # shared backlog: file-tickets files, work-tickets claims (skills #44)
- backend: github            # user ruling 2026-11-16: GitHub Issues on the origin remote (ivanDsky/PuzzleBox), state:/type:/area:/epic: labels. Backlog-worker PRs (work-tickets) merge under the Consent ledger like any slice: no PR waits for the user's merge (user ruling 2026-11-17, replaces 2026-11-16 "opened for the user to merge")
- check batches: hands-on tickets merged since the last check accumulate; once about 5–8 are waiting, build one check APK from origin/main (their flags forced on, ship_apk.py) and ask the user to review it. Never one APK per merge or per lane batch. The ask never blocks other work, except after the run's last batch, where the run waits for the answer (user ruling 2026-11-17). This ruling overrides the work-tickets "next pack, then stop" step and the file-tickets check-batch step, which leave the check to the feedback session: here the worker session that merged the batch builds it and asks

## Consent ledger
- pre-approved: merge dark slices on green   # user ruling 2026-11-07: agent merges dark/non-behavioral slice PRs on a green LOCAL suite (the `pr-check` gate command on the rebased branch, ADR-0023) + clean fast review; flip PRs, live changes (migrations, persisted formats, manifest/permissions, dependency/SDK bumps, GitHub Actions version bumps) still wait for the user
- pre-approved: render screenshots (`-Proborazzi.record=true`) of any module   # review captures in gitignored `snapshots/`, never committed, never a gate (ADR-0033)
- pre-approved: merge order of ready dark slices
- pre-approved: disk cleanup limited to worktree `build/` dirs and stale merged worktrees   # never ~/.gradle, ~/Library, ~/.android
- pre-approved: Firebase rules deploys to the dev project only
- pre-approved: taste picks the user explicitly delegated in the current run
- pre-approved: versionCode via build_identity
- pre-approved: CI cache steps (actions/cache or prefetch steps, no action version bump) merge on a green gate   # user ruling 2026-11-18
# never pre-approved, however delegated: flag flip, release, production rules deploy, data deletion, anything public

## Taste rules
- no large empty space below content
- dates and stats share one baseline
- chips in a row share one layout structure
- tablet uses the app's two-pane convention, never a stretched phone layout
- rearrangement reuses existing animations, never clear-and-repaint
- redesigning a surface never removes existing features (write a parity checklist first)
- leaderboards are the primary content on Ranks surfaces
- touch targets ≥48dp
- plain words in UI and status text
- no design-variant toggles left in Settings after a decision; Debug and Release sections stay separate

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
- Tab supports wireless adb: user opens Developer options → Wireless debugging → "Pair device with pairing code" (code+port rotate EVERY open — pair within seconds of receiving), then `adb connect <tab address>:<debug-port-from-main-screen>`. Paired 2026-11-02, persists.
- Supervised-process PATH lacks sdk tools: use the full path `$ANDROID_HOME/platform-tools/adb` (the SDK is in the default `~/Library/Android/sdk`).
- Phone: Pixel 8 Pro, Android 17, 120 Hz, wireless adb, paired 2026-11-15 (persists). Connect: `adb connect <phone address>:<port from Wireless debugging main screen>`; the port changes when wireless debugging restarts — ask the user for it, or find it with a parallel `nc -z` scan of 30000-49999. Used for agent benchmarks and reviews; user checks still run on the tab.
- Agent measurements never run on the release package `com.ivandsky.puzzlebox` (the user's real account; a 2026-11-15 run left today's daily in progress). Use the benchmark install.
- The tab runs ReVanced microG, not real GMS — Credential Manager Google-ID works ONLY with the type-first + `GoogleIdTokenCredential.createFrom(credential.data)` pattern (4b7b419). `is GoogleIdTokenCredential` checks silently drop microG tokens.
- User checks on the tablet only — no emulator, no Mac manual testing.
- APKs ship via ship_apk.py only.
