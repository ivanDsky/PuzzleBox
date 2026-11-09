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
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.app.PuzzleBoxApp
import com.ivandsky.puzzlebox.app.nav.NAVIGATION_STATE_KEY
import com.ivandsky.puzzlebox.app.nav.puzzleBoxNavigationState
import com.ivandsky.puzzlebox.app.nav.rememberNavigator
import com.ivandsky.puzzlebox.app.nav.topLevelDestinations
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.CustomGameKey
import com.ivandsky.puzzlebox.play.api.CustomResultKey
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import java.time.Duration
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.robolectric.Shadows.shadowOf

/**
 * Runs [block] against the real app shell — [PuzzleBoxApp], its Navigator, NavDisplay and
 * every feature entry — over [graph] (the production Koin modules with fakes on the
 * edges), starting at [state]. The width class comes from the real window, so a row's
 * `@Config(qualifiers = …)` picks bar or rail. The caller's test class runs on
 * Robolectric with a plain `Application` (`@Config(application = Application::class)`),
 * so the merged manifest's application never starts a second Koin.
 */
fun shellRobot(
    state: NavigationState = puzzleBoxNavigationState(),
    today: LocalDate = E2E_TODAY,
    graph: E2eKoinGraph = E2eKoinGraph(today),
    block: ShellRobot.() -> Unit,
) {
    graph.start()
    try {
        val robot = ShellRobot(graph, state)
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

/**
 * The E2E vocabulary: user gestures (tabs, taps, system and predictive back), lifecycle
 * (background, saved-state restore, relaunch) and the row assertions. Every assertion of
 * a state also checks SC2 (exactly one tab selected, the state's tab; no tab at all in
 * immersive play, N2); every route
 * assertion checks that the tag is the only `route.*` on screen (SC1). Failures print
 * states in spec notation (`**Home**[Home] Play[Play] Profile[Profile]`).
 */
class ShellRobot internal constructor(
    val graph: E2eKoinGraph,
    private val initial: NavigationState,
) {
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val restoration = StateRestorationTester(rule)
    private lateinit var navigator: Navigator
    private lateinit var registry: SaveableStateRegistry
    private var shown by mutableStateOf(true)
    private var generation by mutableIntStateOf(0)
    private var pendingLegacy: Bundle? = null
    private var ignoreRestored = false
    private var exits = 0

    /** The live navigation state. */
    val state: NavigationState get() = rule.runOnIdle { navigator.state }

    internal fun launch() {
        restoration.setContent {
            // The system's stand-in: registered before the shell, so it only sees a back
            // the shell leaves unhandled — the press that would leave the app.
            BackHandler { exits++ }
            // Keyed outside the `if`, so relaunch()'s bump recomposes even while hidden.
            key(generation) { if (shown) Shell() }
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
        tap(topLevelDestinations.single { it.key == key }.tag)
    }

    /**
     * A tab switch from a board or a result, where immersive play (N2) hides the bar: the
     * same [Navigator.selectTab] the bar's tap makes, for rows that hop away from live play.
     */
    fun switchTab(key: NavKey) {
        check(state.immersive) { "the bar shows on $state: tapTab($key)" }
        rule.runOnIdle { navigator.selectTab(key) }
        rule.waitForIdle()
    }

    /** Taps the node tagged [tag], scrolling it into view first when it sits in a scrolling screen. */
    fun tap(tag: String) {
        awaitTag(tag)
        val node = rule.onNodeWithTag(tag)
        val scrolls = rule.onAllNodes(hasTestTag(tag) and hasAnyAncestor(hasScrollAction())).fetchSemanticsNodes().isNotEmpty()
        if (scrolls) node.performScrollTo()
        node.performClick()
        rule.waitForIdle()
    }

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
        if (rule.onAllNodesWithTag(page).fetchSemanticsNodes().isEmpty()) {
            if (rule.onAllNodesWithTag("library.page.back").fetchSemanticsNodes().isNotEmpty()) tap("library.page.back")
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
     * Process death without saved state: the composition, every ViewModel and the Koin graph
     * are discarded; the fakes (the "disk") survive; the shell relaunches at the seed.
     *
     * The generation bump rides with the ViewModel clear so a recomposer frame follows it.
     * After a background/foreground, Compose 1.12.1's LifecycleRetainedValuesStoreOwner
     * cancels its spent frame-end handle in onCleared; when that lands a multiple of 16
     * frames later (AwaiterQueue's 4-bit version wraps), it underflows the Recomposer's
     * frame-end awaiter count, hasPendingWork stays true and Compose never idles. Only a
     * frame's end resets the count (cf. issuetracker.google.com/issues/544987094).
     */
    fun relaunch() {
        rule.runOnIdle { shown = false }
        rule.waitForIdle()
        rule.runOnIdle {
            rule.activity.viewModelStore.clear()
            generation++
        }
        graph.stop()
        graph.start()
        ignoreRestored = true
        rule.runOnIdle { shown = true }
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
     * SC2: every tab shows once, and exactly [tab] is selected — or, while the live state is
     * immersive (N2: a board or a result on top), no tab shows at all.
     */
    fun assertSelectedTab(tab: NavKey) {
        if (state.immersive) {
            assertNoNavigation()
            return
        }
        val selected = topLevelDestinations.filter { destination ->
            val nodes = rule.onAllNodesWithTag(destination.tag).fetchSemanticsNodes()
            assertEquals("tab ${destination.label} shown once (state $state)", 1, nodes.size)
            nodes.single().config.getOrNull(SemanticsProperties.Selected) == true
        }
        assertEquals("selected tabs (state $state)", listOf(tab), selected.map { it.key })
    }

    /** N2: neither bar nor rail shows a tab. */
    fun assertNoNavigation() {
        topLevelDestinations.forEach { destination ->
            val nodes = rule.onAllNodesWithTag(destination.tag).fetchSemanticsNodes()
            assertEquals("tab ${destination.label} hidden (state $state)", 0, nodes.size)
        }
    }

    /** SC1: [tag] is displayed and is the only `route.*` tag on screen. */
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

    private fun tabBounds(): List<Rect> = topLevelDestinations.map {
        rule.onNodeWithTag(it.tag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
    }

    private fun backEvent(progress: Float) =
        BackEventCompat(0f, 0f, progress, BackEventCompat.EDGE_LEFT)

    /**
     * The tester's registry, seen by the shell. On restore it can hand back a legacy bundle
     * in place of the navigator's saved one, or nothing at all after a relaunch.
     */
    private inner class SwappingRegistry(
        private val parent: SaveableStateRegistry,
    ) : SaveableStateRegistry by parent {
        override fun consumeRestored(key: String): Any? {
            val restored = parent.consumeRestored(key)
            val legacy = pendingLegacy
            return when {
                ignoreRestored -> null
                legacy != null && (restored as? Bundle)?.containsKey(NAVIGATION_STATE_KEY) == true -> {
                    pendingLegacy = null
                    legacy
                }
                else -> restored
            }
        }
    }

    /** Immersive play (N2), as the shell decides it: the selected tab's top is a board or a result, a daily's or a friend's. */
    private val NavigationState.immersive: Boolean
        get() = currentStack.last().let { it is GameKey || it is CustomGameKey || it is ResultKey || it is CustomResultKey }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val IDLE_STEP_MS = 100L
        const val GAME_ROUTE = "route.game"
        val isRoute = SemanticsMatcher("test tag route.*") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("route.") == true
        }
    }
}
