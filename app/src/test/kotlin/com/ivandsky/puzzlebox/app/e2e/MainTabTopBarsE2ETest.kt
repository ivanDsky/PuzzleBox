package com.ivandsky.puzzlebox.app.e2e

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.common.flags.TopBarFlag
import com.ivandsky.puzzlebox.history.api.HistoryRootKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.play.api.CommunityKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * MainTabsLargeBar over the real shell (#1597): Today, Games, Community and Ranks share Today's large top bar, so
 * the title, the bar's bottom and the first content sit at the same heights on every tab; the phone's avatar rides in
 * every tab's bar, and the tablet keeps it on the rail. Ranks' board menu still opens from the new bar.
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w360dp-h800dp")
class MainTabTopBarsPhoneE2ETest : MainTabTopBarsE2ETest(phone = true) {

    @Test
    fun `the Ranks title opens the board menu`() = shellRobot(graph = largeBarGraph()) {
        tapTab(HistoryRootKey)
        tap("ranks.title")
        awaitTag("ranks.menu")
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w1280dp-h800dp")
class MainTabTopBarsTabletE2ETest : MainTabTopBarsE2ETest(phone = false)

abstract class MainTabTopBarsE2ETest(private val phone: Boolean) {

    @Test
    fun `every main tab's title, bar and first content line up with Today's`() = shellRobot(graph = largeBarGraph()) {
        val today = measure(HomeKey)
        for (key in listOf(PlayKey, CommunityKey, HistoryRootKey)) {
            val tab = measure(key)
            assertEquals("$key title top", today.title.top, tab.title.top, TOLERANCE_PX)
            assertEquals("$key title height", today.title.height, tab.title.height, TOLERANCE_PX)
            assertEquals("$key bar bottom", today.bar.bottom, tab.bar.bottom, TOLERANCE_PX)
            assertEquals("$key first content top", today.content.top, tab.content.top, TOLERANCE_PX)
        }
    }

    private class Bars(val title: Rect, val bar: Rect, val content: Rect)

    private fun ShellRobot.measure(key: NavKey): Bars {
        tapTab(key)
        val route = ROUTES.getValue(key)
        val avatarsInBar = nodes(hasTestTag(AVATAR) and hasAnyAncestor(hasTestTag("topbar.large")) and inRoute(route)).size
        // The phone's avatar rides in each tab's own bar; the tablet's stays on the rail.
        assertEquals("$key avatars in the bar", if (phone) 1 else 0, avatarsInBar)
        if (phone) assertChromeActions()
        // Ranks opens on its filter chip row on the phone; the tablet's chips sit in the other pane, so its calendar is first.
        val first = if (key == HistoryRootKey && phone) RANKS_FILTERS else FIRST_CONTENT.getValue(key)
        return Bars(bounds(route, "topbar.title"), bounds(route, "topbar.large"), bounds(route, first))
    }

    private fun ShellRobot.bounds(route: String, tag: String): Rect {
        val matcher = hasTestTag(tag) and inRoute(route)
        rule.waitUntil(TIMEOUT_MS) { nodes(matcher).isNotEmpty() }
        val found = nodes(matcher)
        assertTrue("one $tag under $route, found ${found.size}", found.size == 1)
        return found.single().boundsInRoot
    }

    private fun ShellRobot.nodes(matcher: SemanticsMatcher) = rule.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes()

    private fun inRoute(route: String) = hasAnyAncestor(hasTestTag(route))

    protected fun largeBarGraph() = E2eKoinGraph(E2E_TODAY, module {}, FakeReleaseFlags(TopBarFlag.MainTabsLargeBar))

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val AVATAR = "shell.avatar"
        const val RANKS_FILTERS = "ranks.filters"

        // Sub-pixel layout rounding only.
        const val TOLERANCE_PX = 1f

        val ROUTES: Map<NavKey, String> = mapOf(
            HomeKey to "route.home",
            PlayKey to "route.library",
            CommunityKey to "route.community",
            HistoryRootKey to "route.ranks",
        )

        // What each tab shows first under its bar: Today's summary card, the underline tabs, Ranks' calendar (its chip row on the phone).
        val FIRST_CONTENT: Map<NavKey, String> = mapOf(
            HomeKey to "home.today",
            PlayKey to "underline.tabs",
            CommunityKey to "underline.tabs",
            HistoryRootKey to "ranks.calendar",
        )
    }
}
