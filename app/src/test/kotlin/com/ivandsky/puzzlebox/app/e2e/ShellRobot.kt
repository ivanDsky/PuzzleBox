package com.ivandsky.puzzlebox.app.e2e

import android.os.Bundle
import android.os.Looper
import android.os.Parcel
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onChildAt
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.Lifecycle
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.app.PuzzleBoxApp
import com.ivandsky.puzzlebox.app.nav.NAVIGATION_STATE_KEY
import com.ivandsky.puzzlebox.app.nav.TopLevelDestination
import com.ivandsky.puzzlebox.app.nav.isImmersive
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.app.nav.puzzleBoxNavigationState
import com.ivandsky.puzzlebox.app.nav.rememberNavigator
import com.ivandsky.puzzlebox.app.nav.topLevelDestinations
import com.ivandsky.puzzlebox.app.play.ZipPlayFixture
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.ObserveMyDuels
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zipui.ZipPlayController
import com.ivandsky.puzzlebox.gameui.DragEvent
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.ActivePlay
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.api.MatchLegKey
import com.ivandsky.puzzlebox.play.api.MatchLegResultKey
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import java.time.Duration
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.koin.core.module.Module
import org.koin.dsl.module
import org.robolectric.Shadows.shadowOf

/**
 * Runs [block] against the real app shell — [PuzzleBoxApp], its Navigator, NavDisplay and
 * every feature entry — over [graph] (the production Koin modules with fakes on the
 * edges), starting at [state]. The width class comes from the real window, so a row's
 * `@Config(qualifiers = …)` picks bar or rail. [destinations] is the tab set the shell
 * shows: the flag-off set by default; a run with NavV2 on in [graph] passes the flag-on
 * one. The caller's test class runs on Robolectric with a plain `Application`
 * (`@Config(application = Application::class)`), so the merged manifest's application
 * never starts a second Koin.
 */
internal fun shellRobot(
    state: NavigationState = puzzleBoxNavigationState(),
    today: LocalDate = E2E_TODAY,
    graph: E2eKoinGraph = E2eKoinGraph(today),
    destinations: List<TopLevelDestination> = topLevelDestinations,
    block: ShellRobot.() -> Unit,
) {
    graph.start()
    try {
        val robot = ShellRobot(graph, state, destinations)
        val run = object : Statement() {
            override fun evaluate() {
                robot.launch()
                robot.block()
            }
        }
        robot.rule
            .apply(run, Description.createTestDescription(ShellRobot::class.java, "shellRobot"))
            .evaluate()
    } finally {
        graph.stop()
    }
}

/** The graph with NavV2 on, plus [also]'s flags. */
internal fun navV2Graph(vararg also: ReleaseFlag, overrides: Module = module {}): E2eKoinGraph =
    E2eKoinGraph(E2E_TODAY, overrides, FakeReleaseFlags(ReleaseFlag.NavV2, *also))

/** [shellRobot] with NavV2 on: the flag-on tab set over [graph], starting at [state]. */
internal fun navV2Robot(
    state: NavigationState = puzzleBoxNavigationState(navV2 = true),
    graph: E2eKoinGraph = navV2Graph(),
    block: ShellRobot.() -> Unit,
) = shellRobot(state, graph = graph, destinations = topLevelDestinations(navV2 = true), block = block)

/** The fresh flag-on state, selected on [tab], whose stack is [tab] then [pushed]. */
internal fun navV2At(tab: NavKey, vararg pushed: NavKey): NavigationState {
    val fresh = puzzleBoxNavigationState(navV2 = true)
    return NavigationState(
        tab = tab,
        stacks = fresh.stacks.map { stack -> if (stack.first() == tab) stack + pushed else stack },
    )
}

/**
 * The E2E vocabulary: user gestures (tabs, taps, system and predictive back), lifecycle
 * (background, saved-state restore, relaunch) and the row assertions. Every assertion of
 * a state also checks that exactly one tab is selected (the state's tab; no tab at all in
 * immersive play); every route
 * assertion checks that the tag is the only `route.*` on screen. Failures print
 * states in a compact notation (`**Home**[Home] Play[Play] Profile[Profile]`).
 */
class ShellRobot internal constructor(
    val graph: E2eKoinGraph,
    private val initial: NavigationState,
    private val destinations: List<TopLevelDestination>,
) {
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val restoration = StateRestorationTester(rule)
    private lateinit var navigator: Navigator
    private lateinit var registry: SaveableStateRegistry
    private var shown by mutableStateOf(true)
    private var generation by mutableIntStateOf(0)
    private var pendingLegacy: Bundle? = null
    private var ignoreRestored = false
    private var restoredState: MutableMap<String, MutableList<Any?>>? = null
    private var exits = 0

    /** The live navigation state. */
    val state: NavigationState get() = rule.runOnIdle { navigator.state }

    internal fun launch() {
        restoration.setContent {
            // The system's stand-in: registered before the shell, so it only sees a back
            // the shell leaves unhandled — the press that would leave the app.
            BackHandler { exits++ }
            // Read outside the `if`, so relaunch()'s bump recomposes even while hidden. Read, not keyed: a key
            // would change every saveable key beneath it, and relaunch(keepSavedState) hands those back.
            generation.let { if (shown) Shell() }
        }
        rule.waitForIdle()
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    @Composable
    private fun Shell() {
        val parent = checkNotNull(LocalSaveableStateRegistry.current)
        val swapping = remember(parent) { SwappingRegistry(parent) }
        registry = swapping
        CompositionLocalProvider(LocalSaveableStateRegistry provides swapping) {
            PuzzleBoxTheme(darkTheme = false) {
                PuzzleBoxApp(
                    windowWidthSizeClass = calculateWindowSizeClass(rule.activity).widthSizeClass,
                    navigator = rememberNavigator(initial).also { navigator = it },
                )
            }
        }
    }

    fun tapTab(key: NavKey) {
        tap(destinations.single { it.key == key }.tag)
    }

    /**
     * A tab switch from a board or a result, where immersive play hides the bar: the
     * same [Navigator.selectTab] the bar's tap makes, for rows that hop away from live play.
     */
    fun switchTab(key: NavKey) {
        check(state.immersive) { "the bar shows on $state: tapTab($key)" }
        rule.runOnIdle { navigator.selectTab(key) }
        rule.waitForIdle()
    }

    /** A push of [key] on the current tab, for rows whose screen no longer offers a link to it. */
    fun push(key: NavKey) {
        rule.runOnIdle { navigator.push(key) }
        rule.waitForIdle()
    }

    /** Taps the node tagged [tag], scrolling it into view first when it sits in a scrolling screen. */
    fun tap(tag: String) {
        scrollIntoView(tag)
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    /**
     * Holds the main clock, then taps [tag]: from here on only [advanceUntilShown] and [advanceFrame] move
     * time, so a timed sequence such as the reveal can be watched step by step.
     */
    fun holdClockAndTap(tag: String) {
        scrollIntoView(tag)
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun scrollIntoView(tag: String) {
        awaitTag(tag)
        val scrolls = rule.onAllNodes(hasTestTag(tag) and hasAnyAncestor(hasScrollAction())).fetchSemanticsNodes().isNotEmpty()
        if (scrolls) rule.onNodeWithTag(tag).performScrollTo()
    }

    /** Whether a node tagged [tag] is on screen now. */
    fun shows(tag: String): Boolean =
        rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** Taps the node showing [text], scrolling its screen to it first. */
    fun tapText(text: String) {
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(text).performScrollTo().performClick()
        rule.waitForIdle()
    }

    /**
     * Opens [gameId]'s page in the Play library (a phone first leaves another game's page,
     * since back from a board returns to the page it started from) and plays today once
     * today has loaded. [landing] is the route that opens: a finished day lands on its result.
     */
    fun openGame(gameId: String, landing: String = GAME_ROUTE) {
        val page = "library.page.$gameId"
        if (!shows(page)) {
            // A duel screen opened from the Duels pill goes back to the library, onto its Dailies.
            if (shows("duel.back")) tap("duel.back")
            if (shows("library.page.back")) tap("library.page.back")
            if (shows("library.tab.dailies") && !shows("library.row.$gameId")) tap("library.tab.dailies")
            tap("library.row.$gameId")
        }
        awaitTag(page)
        // Playable once today has loaded: a loading card's action is disabled, an offline one retries.
        val offlineCard = hasTestTag("card.status_mark.offline") and hasAnyAncestor(hasTestTag("library.day_card"))
        rule.waitUntil(TIMEOUT_MS) {
            rule.onAllNodes(hasTestTag("library.play") and isEnabled()).fetchSemanticsNodes().isNotEmpty() &&
                rule.onAllNodes(offlineCard, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
        tap("library.play")
        awaitTag(landing)
    }

    /**
     * Opens duel puzzle [key] as the player does: the duel's screen from the Duels pill (unless it is already
     * open), then the puzzle's Start or Resume; waits for [landing], its board or the result of a puzzle already
     * played. A played puzzle has no button on the phone's duel screen, so its key is pushed, as a stale open of it
     * would be.
     */
    fun openDuelPuzzle(key: MatchLegKey, landing: String = GAME_ROUTE) {
        if (!shows("duel.legs.title")) openDuel(checkNotNull(ShareCode.parse(key.code)))
        val action = "duel.legs.action.${key.legIndex}"
        if (shows(action)) tap(action) else rule.runOnIdle { navigator.push(key) }
        awaitTag(landing)
    }

    /** The Play library's Duels pill, from the library or one of its game pages. */
    fun openDuelsTab() {
        if (shows("library.page.back")) tap("library.page.back")
        tap("library.tab.duels")
        awaitTag(DUELS_LIST)
    }

    /** The duel screen of [code], through its row under the Duels pill. */
    fun openDuel(code: ShareCode) {
        openDuelsTab()
        tapDuelRow(code)
        awaitTag("duel.legs.title")
    }

    /**
     * Taps [code]'s row (`duel.row.<code>`) under the Duels pill (already open), holding the clock first when
     * [holdClock] (see [holdClockAndTap]).
     */
    fun tapDuelRow(code: ShareCode, holdClock: Boolean = false) {
        awaitTag(DUELS_LIST)
        val row = "duel.row.${code.value}"
        check(shows(row) || duels().rows.any { it.code == code }) { "no duel row for ${code.value}: ${duels().rows}" }
        if (holdClock) holdClockAndTap(row) else tap(row)
    }

    /**
     * Creates a Daily duel over today's [game] alone through the New duel sheet (the Duels pill's challenge card),
     * and leaves its Created sheet open. Returns the new duel's code.
     */
    fun createDuel(game: GameId): ShareCode {
        val mine = "duel.game.${game.value}"
        return createDuelFrom("duel.preset.daily") {
            rule.onAllNodes(isDuelGame).fetchSemanticsNodes()
                .map { it.config[SemanticsProperties.TestTag] to (it.config.getOrNull(SemanticsProperties.Selected) == true) }
                .filter { (tag, selected) -> selected != (tag == mine) }
                .forEach { (tag, _) -> tap(tag) }
        }
    }

    /** Creates a Quick duel (best of 3, mixed past dailies, 24 h) with the preset's own settings; leaves its Created sheet open. */
    fun createQuickDuel(): ShareCode = createDuelFrom("duel.preset.quick") {}

    private fun createDuelFrom(preset: String, adjust: () -> Unit): ShareCode {
        val before = graph.matches.matches.keys
        openDuelsTab()
        tap("duels.challenge")
        awaitTag("duel.new.sheet")
        tap(preset)
        adjust()
        tap("duel.create")
        awaitTag("duel.created.sheet")
        return (graph.matches.matches.keys - before).single()
    }

    /** What the Duels pill lists now. */
    fun duels() = runBlocking { graph.koin.get<ObserveMyDuels>()().first() }

    /** A tap on the reveal's stage, off its Skip button: it speeds the reveal up. */
    fun tapRevealStage() {
        rule.onNodeWithTag("reveal.stage").performTouchInput { click() }
        rule.waitForIdle()
    }

    /**
     * Moves the held clock (see [holdClockAndTap]) on in small steps until [tag] shows, and returns the clock time
     * that took. Fails if any of [never] shows first, or once [budgetMs] of clock time has passed.
     */
    fun advanceUntilShown(tag: String, never: Collection<String> = emptyList(), budgetMs: Long = CLOCK_BUDGET_MS): Long {
        check(!rule.mainClock.autoAdvance) { "the clock is not held" }
        var elapsed = 0L
        while (true) {
            never.firstOrNull(::shows)?.let { fail("$it showed before $tag, after $elapsed ms (state $state)") }
            if (shows(tag)) return elapsed
            if (elapsed >= budgetMs) fail("$tag did not show within $budgetMs ms (state $state)")
            rule.mainClock.advanceTimeBy(CLOCK_STEP_MS)
            rule.waitForIdle()
            elapsed += CLOCK_STEP_MS
        }
    }

    /** One frame of the held clock. */
    fun advanceFrame() {
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
    }

    /** [key]'s live play once [ready] holds for it, e.g. once its board's session has loaded. */
    fun awaitDuelPlay(key: MatchLegKey, ready: (ActivePlay) -> Boolean): ActivePlay {
        val store = graph.koin.get<PlaySessionStore>()
        val slot = PlaySlot.Leg(GameId(key.gameId), key.code, key.legIndex)
        rule.waitUntil(TIMEOUT_MS) { rule.runOnIdle { store.peek(slot)?.let(ready) } == true }
        return checkNotNull(rule.runOnIdle { store.peek(slot) })
    }

    /**
     * Solves the open duel puzzle [key]: once its play is [ready], [moves] run on it in one
     * frame. Returns the duel result the solve opened.
     */
    fun solveDuelPuzzle(key: MatchLegKey, ready: (ActivePlay) -> Boolean, moves: (ActivePlay) -> Unit): MatchLegResultKey {
        val play = awaitDuelPlay(key, ready)
        rule.runOnIdle { moves(play) }
        rule.waitUntil(TIMEOUT_MS) {
            // A bare waitUntil drives no frame here; pump one so the win's handoff runs.
            rule.runOnIdle { }
            rule.onAllNodesWithTag(RESULT_ROUTE, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        val result = state.currentStack.last()
        check(result is MatchLegResultKey) { "the solve of $key opened $result" }
        return result
    }

    /**
     * Solves today's open Zip: its live controller, once its session has loaded, takes the
     * fixture's solution as drag events in one frame, and the wait ends on `route.result`.
     */
    fun solveTodaysZip() {
        val store = graph.koin.get<PlaySessionStore>()
        val slot = PlaySlot.Daily(ZipDefinition.id, E2E_TODAY)
        fun zip() = rule.runOnIdle { store.peek(slot) }?.controller as? ZipPlayController
        rule.waitUntil(TIMEOUT_MS) { rule.runOnIdle { zip()?.session } != null }
        val controller = checkNotNull(zip()) { "no live zip play for $E2E_TODAY" }
        rule.runOnIdle {
            ZipPlayFixture.solution.forEach { controller.onDragEvent(DragEvent.Extend(GridCell(it.idx / ZIP_SIZE, it.idx % ZIP_SIZE))) }
        }
        awaitTag(RESULT_ROUTE)
    }

    fun awaitTag(tag: String) {
        rule.waitUntil(TIMEOUT_MS) {
            rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * [awaitTag] for state behind a coroutine hop to the main looper, such as a Room commit
     * handed back to the play store or a throttle window. The compose rule's wait only
     * advances frames, and a stopped activity draws none, so that hop would never run.
     * Each poll idles the main looper first.
     */
    fun awaitIdling(condition: () -> Boolean) {
        rule.waitUntil(TIMEOUT_MS) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(IDLE_STEP_MS))
            condition()
        }
    }

    /** System back: one press through the activity's dispatcher. */
    fun back() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    /** A predictive back gesture from the left edge, scrubbed halfway, then [commit]ted or cancelled. */
    fun predictiveBack(commit: Boolean) {
        val dispatcher = rule.activity.onBackPressedDispatcher
        rule.runOnUiThread { dispatcher.dispatchOnBackStarted(backEvent(0f)) }
        rule.waitForIdle()
        rule.runOnUiThread { dispatcher.dispatchOnBackProgressed(backEvent(0.5f)) }
        rule.waitForIdle()
        rule.runOnUiThread {
            if (commit) dispatcher.onBackPressed() else dispatcher.dispatchOnBackCancelled()
        }
        rule.waitForIdle()
    }

    /** Home button: the activity stops (the shell flushes saves on ON_STOP). */
    fun background() {
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        // A stopped activity draws no frames: idle the main looper, not the composition.
        shadowOf(Looper.getMainLooper()).idle()
    }

    fun foreground() {
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitForIdle()
    }

    /** Saved-instance-state round trip (rotation or process death with a saved bundle). */
    fun restoreState() {
        ignoreRestored = false
        restoredState = null
        restoration.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
    }

    /** As [restoreState], with [legacy] handed back in the navigator's saved slot. */
    fun restoreLegacy(legacy: Bundle) {
        pendingLegacy = legacy
        restoreState()
        check(pendingLegacy == null) { "no saved navigation slot was restored" }
    }

    /**
     * Process death: the composition, every ViewModel and the Koin graph are discarded; the fakes (the "disk")
     * survive. Without [keepSavedState] the shell relaunches at the seed; with it, the shell's saved state comes
     * back as the system would hand it to the new process. Works with the clock held, one frame per step.
     *
     * The generation bump rides with the ViewModel clear so a recomposer frame follows it.
     * After a background/foreground, Compose 1.12.1's LifecycleRetainedValuesStoreOwner
     * cancels its spent frame-end handle in onCleared; when that lands a multiple of 16
     * frames later (AwaiterQueue's 4-bit version wraps), it underflows the Recomposer's
     * frame-end awaiter count, hasPendingWork stays true and Compose never idles. Only a
     * frame's end resets the count (cf. issuetracker.google.com/issues/544987094).
     */
    fun relaunch(keepSavedState: Boolean = false) {
        val saved = if (keepSavedState) rule.runOnIdle { registry.performSave() } else null
        rule.runOnIdle { shown = false }
        settle()
        rule.runOnIdle {
            rule.activity.viewModelStore.clear()
            generation++
        }
        graph.stop()
        graph.start()
        restoredState = saved?.mapValuesTo(mutableMapOf()) { it.value.toMutableList() }
        ignoreRestored = saved == null
        rule.runOnIdle { shown = true }
        settle()
    }

    /** Idles, first drawing the frame a state change needs when the clock is held. */
    private fun settle() {
        if (!rule.mainClock.autoAdvance) rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
    }

    /** Size of the shell's saved state, parceled as the activity would hand it to the system. */
    fun savedStateBytes(): Int {
        val saved = rule.runOnIdle { registry.performSave() }
        val parcel = Parcel.obtain()
        return try {
            parcel.writeMap(saved)
            parcel.dataSize()
        } finally {
            parcel.recycle()
        }
    }

    fun assertState(expected: NavigationState) {
        val actual = state
        assertEquals("expected $expected but was $actual", expected, actual)
        assertSelectedTab(expected.tab)
    }

    /**
     * Every tab shows once, and exactly [tab] is selected — or, while the live state is
     * immersive (a board or a result on top), no tab shows at all.
     */
    fun assertSelectedTab(tab: NavKey) {
        if (state.immersive) {
            assertNoNavigation()
            return
        }
        val selected = destinations.filter { destination ->
            val nodes = rule.onAllNodesWithTag(destination.tag).fetchSemanticsNodes()
            assertEquals("tab ${destination.label} shown once (state $state)", 1, nodes.size)
            nodes.single().config.getOrNull(SemanticsProperties.Selected) == true
        }
        assertEquals("selected tabs (state $state)", listOf(tab), selected.map { it.key })
    }

    /** Neither bar nor rail shows a tab. */
    fun assertNoNavigation() {
        destinations.forEach { destination ->
            val nodes = rule.onAllNodesWithTag(destination.tag).fetchSemanticsNodes()
            assertEquals("tab ${destination.label} hidden (state $state)", 0, nodes.size)
        }
    }

    /** [tag] is displayed and is the only `route.*` tag on screen. */
    fun assertRoute(tag: String) {
        awaitTag(tag)
        rule.onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed()
        val routes = rule.onAllNodes(isRoute, useUnmergedTree = true).fetchSemanticsNodes()
            .map { it.config[SemanticsProperties.TestTag] }
        assertEquals("routes on screen (state $state)", listOf(tag), routes)
    }

    /** The press fell through the shell: the app would have exited. */
    fun assertExited() {
        assertEquals("presses that left the app (state $state)", 1, exits)
    }

    /** Compact: the tabs sit side by side along the window's bottom edge. */
    fun assertBottomBar() {
        val tabs = tabBounds()
        val window = rule.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("tabs share one row: $tabs (state $state)", tabs.all { it.top == tabs.first().top })
        assertTrue("tabs run left to right: $tabs", tabs.zipWithNext().all { (a, b) -> a.right <= b.left })
        assertTrue("bar hugs the bottom: $tabs in $window", tabs.first().bottom >= window.bottom - window.height / 4)
    }

    /** Expanded: the tabs stack in one column along the window's start edge. */
    fun assertRail() {
        val tabs = tabBounds()
        val window = rule.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("tabs share one column: $tabs (state $state)", tabs.all { it.left == tabs.first().left })
        assertTrue("tabs run top to bottom: $tabs", tabs.zipWithNext().all { (a, b) -> a.bottom <= b.top })
        assertTrue("rail hugs the start: $tabs in $window", tabs.first().right <= window.left + window.width / 4)
    }

    /** NavV2: the avatar (the rail's bottom, or Today's header on a bar), which opens Profile on the current tab. */
    fun tapAvatar() = tap(AVATAR)

    /** NavV2: Profile's top-right gear, which opens Settings above Profile. */
    fun tapGear() = tap(PROFILE_GEAR)

    /**
     * NavV2 placement of the avatar (Settings opens from Profile's gear): on a [rail] it shows once wherever the rail
     * shows (every screen but the immersive ones); on the bar only Today's root shows it.
     */
    fun assertChromeFor(rail: Boolean) {
        val top = state.currentStack.last()
        val shown = if (rail) !isImmersive(top) else top == HomeKey
        if (shown) assertChromeActions() else assertNoChromeActions()
        if (shown && rail) {
            val avatar = rule.onNodeWithTag(AVATAR).fetchSemanticsNode().boundsInRoot
            val lowestTab = tabBounds().maxOf { it.bottom }
            assertTrue("avatar sits below the tabs: $avatar vs $lowestTab", avatar.top >= lowestTab)
        }
    }

    /** The avatar shows exactly once. */
    fun assertChromeActions() {
        awaitTag(AVATAR)
        assertEquals("avatars (state $state)", 1, rule.onAllNodesWithTag(AVATAR).fetchSemanticsNodes().size)
    }

    /** No avatar shows: immersive play, a bar screen but Today, or the flag off. */
    fun assertNoChromeActions() {
        assertEquals("avatars (state $state)", 0, rule.onAllNodesWithTag(AVATAR).fetchSemanticsNodes().size)
    }

    /**
     * The NavV2 rail's geometry: the tabs' block (first tab's top to last tab's bottom) is centred on the window's
     * height within [RAIL_CENTRE_SLACK_DP] (the approved rail sits 2dp above it), and the avatar is a target of at least 48dp x 48dp below the last tab.
     */
    fun assertRailGeometry() {
        awaitTag(AVATAR)
        val density = rule.density
        val tabs = tabBounds()
        val window = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val node = rule.onNodeWithTag(AVATAR).fetchSemanticsNode()
        val avatar = node.boundsInRoot
        // The touch target, not the drawn button: a 40dp button still takes touches in a 48dp square.
        val target = node.touchBoundsInRoot
        with(density) {
            assertTrue("avatar target is ${target.width.toDp()} x ${target.height.toDp()}, under 48dp (state $state)", target.width.toDp().value >= 47.5f && target.height.toDp().value >= 47.5f)
        }
        // The drawn avatar is 36dp, whether it is the photo or the Profile icon.
        rule.onNodeWithTag(AVATAR_IMAGE, useUnmergedTree = true).assertWidthIsEqualTo(36.dp).assertHeightIsEqualTo(36.dp)
        val blockCentre = (tabs.first().top + tabs.last().bottom) / 2
        val offDp = with(density) { (blockCentre - window.height / 2).toDp().value }
        assertTrue("tabs' block is ${offDp}dp off the window's centre: $tabs in $window (state $state)", kotlin.math.abs(offDp) <= RAIL_CENTRE_SLACK_DP)
        assertTrue("avatar sits below the last tab: $avatar vs $tabs (state $state)", avatar.top >= tabs.last().bottom)
    }

    private fun tabBounds(): List<Rect> = destinations.map {
        rule.onNodeWithTag(it.tag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
    }

    private fun backEvent(progress: Float) =
        BackEventCompat(0f, 0f, progress, BackEventCompat.EDGE_LEFT)

    /**
     * The tester's registry, seen by the shell. On restore it can hand back a legacy bundle
     * in place of the navigator's saved one, nothing at all after a relaunch, or the state
     * saved before a relaunch that kept it.
     */
    private inner class SwappingRegistry(
        private val parent: SaveableStateRegistry,
    ) : SaveableStateRegistry by parent {
        override fun consumeRestored(key: String): Any? {
            val restored = parent.consumeRestored(key)
            val legacy = pendingLegacy
            val kept = restoredState
            return when {
                kept != null -> kept[key]?.takeIf { it.isNotEmpty() }?.removeAt(0)
                ignoreRestored -> null
                legacy != null && (restored as? Bundle)?.containsKey(NAVIGATION_STATE_KEY) == true -> {
                    pendingLegacy = null
                    legacy
                }
                else -> restored
            }
        }
    }

    /** Immersive play, as the shell decides it: the selected tab's top is immersive ([isImmersive]). */
    private val NavigationState.immersive: Boolean
        get() = isImmersive(currentStack.last())

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val IDLE_STEP_MS = 100L
        const val CLOCK_STEP_MS = 50L
        const val CLOCK_BUDGET_MS = 60_000L
        const val RAIL_CENTRE_SLACK_DP = 2.5f
        const val AVATAR = "shell.avatar"
        const val AVATAR_IMAGE = "shell.avatar.image"
        const val PROFILE_GEAR = "profile.settings"
        const val GAME_ROUTE = "route.game"
        const val ZIP_SIZE = 5
        const val RESULT_ROUTE = "route.result"
        const val DUELS_LIST = "duels.tab"
        val isDuelGame = SemanticsMatcher("test tag duel.game.*") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("duel.game.") == true
        }
        val isRoute = SemanticsMatcher("test tag route.*") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("route.") == true
        }
    }
}
