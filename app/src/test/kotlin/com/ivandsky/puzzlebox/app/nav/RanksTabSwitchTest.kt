package com.ivandsky.puzzlebox.app.nav

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.e2e.E2E_TODAY
import com.ivandsky.puzzlebox.app.e2e.E2eKoinGraph
import com.ivandsky.puzzlebox.app.e2e.ShellRobot
import com.ivandsky.puzzlebox.app.e2e.shellRobot
import com.ivandsky.puzzlebox.app.e2e.tabAt
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Ranks
import com.ivandsky.puzzlebox.designsystem.RanksFilled
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.history.api.HistoryRootKey
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The third tab under `ReleaseFlag.LeaderboardHub`: History with the flag off, Ranks with
 * it on, in the same slot and on the same key ([HistoryRootKey]). A pushed [HistoryKey]
 * opens the history browser with the flag off and the hub at that game with it on, and a back stack saved under one state restores
 * under the other, so the release that flips the flag keeps everyone's place. The flag is
 * read once per launch: a flip mid-run changes nothing until the next one.
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w411dp-h891dp")
class RanksTabSwitchTest {

    // Only the icon case composes here; the shell cases bring their own rule inside shellRobot.
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `with the hub off the third tab reads History and opens the history browser`() = shell(hub = false) {
        assertThirdTab(tag = HISTORY_TAB, label = "History", absent = RANKS_TAB)

        tapTab(HistoryRootKey)

        assertRoute(HISTORY)
        assertSelectedTab(HistoryRootKey)
    }

    @Test
    fun `with the hub on the third tab reads Ranks and opens the hub`() = shell(hub = true) {
        assertThirdTab(tag = RANKS_TAB, label = "Ranks", absent = HISTORY_TAB)

        tapTab(HistoryRootKey)

        assertRoute(RANKS)
        assertSelectedTab(HistoryRootKey)
    }

    @Test
    @Config(qualifiers = RAIL)
    fun `on a tablet the rail reads Ranks with the hub on`() = shell(hub = true) {
        assertThirdTab(tag = RANKS_TAB, label = "Ranks", absent = HISTORY_TAB)

        tapTab(HistoryRootKey)

        assertRoute(RANKS)
        assertSelectedTab(HistoryRootKey)
    }

    @Test
    fun `a pushed HistoryKey opens the history browser, or the hub with the flag on`() {
        listOf(false, true).forEach { hub ->
            shell(hub) {
                push(HistoryKey(ZIP))

                assertRoute(if (hub) RANKS else HISTORY)

                back()
                assertRoute(HOME)
            }
        }
    }

    @Test
    fun `a back stack saved on the third tab restores under the same flag state`() {
        listOf(false, true).forEach { hub -> restores(savedUnderHub = hub, restoredUnderHub = hub) }
    }

    @Test
    fun `a back stack saved on the History tab restores on the Ranks tab once the hub is on`() =
        restores(savedUnderHub = false, restoredUnderHub = true)

    @Test
    fun `a back stack saved on the Ranks tab restores on the History tab once the hub is off`() =
        restores(savedUnderHub = true, restoredUnderHub = false)

    @Test
    fun `a debug flip of the hub while the app runs lands on the next launch, tab and root together`() {
        val on = MutableStateFlow(emptySet<ReleaseFlag>())
        val live = object : ReleaseFlags {
            override val enabled = on
        }
        val onRoot = tabAt(HistoryRootKey)
        shellRobot(onRoot, graph = E2eKoinGraph(E2E_TODAY, releaseFlags = live), destinations = ranksTopLevelDestinations) {
            assertRoute(HISTORY)
            assertThirdTab(hub = false)

            on.value = setOf(ReleaseFlag.LeaderboardHub)
            rule.waitForIdle()

            assertRoute(HISTORY)
            assertThirdTab(hub = false)

            relaunch(keepSavedState = true)

            assertRoute(RANKS)
            assertState(onRoot)
            assertThirdTab(hub = true)
        }
    }

    @Test
    fun `the Ranks set differs from today's only in the third tab's label, icons and tag`() {
        fun TopLevelDestination.placed() = Triple(key, tab, label.takeUnless { key == HistoryRootKey })

        assertEquals(topLevelDestinations.map { it.placed() }, ranksTopLevelDestinations.map { it.placed() })
        val ranks = ranksTopLevelDestinations.single { it.key == HistoryRootKey }
        assertEquals("Ranks", ranks.label)
        assertEquals(RANKS_TAB, ranks.tag)
        assertEquals(Tab.History, ranks.tab)
    }

    @Test
    fun `the Ranks icon swaps to a different glyph when selected`() {
        assertNotEquals(PuzzleIcons.Ranks.pathData(), PuzzleIcons.RanksFilled.pathData())
    }

    // The glyphs differing is not enough: the tab must hand the bar the outline one unselected and the filled one selected.
    @Test
    fun `the Ranks tab draws the outline mark unselected, the filled mark selected`() {
        val ranks = ranksTopLevelDestinations.single { it.key == HistoryRootKey }
        var drawn = emptyList<Any>() to emptyList<Any>()
        composeRule.setContent { drawn = ranks.icon().pathData() to ranks.selectedIcon().pathData() }
        composeRule.waitForIdle()

        assertEquals(PuzzleIcons.Ranks.pathData() to PuzzleIcons.RanksFilled.pathData(), drawn)
    }

    /**
     * Process death with the third tab selected and a history page pushed on it, saved with the
     * hub [savedUnderHub] and brought back with it [restoredUnderHub].
     */
    private fun restores(savedUnderHub: Boolean, restoredUnderHub: Boolean) {
        val seed = tabAt(HistoryRootKey, HistoryKey(ZIP))
        var hub = savedUnderHub
        // Koin builds the single on first use in each started graph, so a relaunch reads the
        // flag as it stands then, while the running shell keeps the value it launched with.
        val graph = E2eKoinGraph(E2E_TODAY, overrides = module { single<ReleaseFlags> { flags(hub) } })
        shellRobot(seed, graph = graph, destinations = destinations(restoredUnderHub)) {
            assertRoute(if (savedUnderHub) RANKS else HISTORY)
            assertThirdTab(savedUnderHub)

            hub = restoredUnderHub
            relaunch(keepSavedState = true)

            assertRoute(if (restoredUnderHub) RANKS else HISTORY)
            assertState(seed)
            assertThirdTab(restoredUnderHub)
            back()
            assertRoute(if (restoredUnderHub) RANKS else HISTORY)
            assertState(tabAt(HistoryRootKey))
        }
    }

    private fun shell(
        hub: Boolean,
        state: NavigationState = puzzleBoxNavigationState(),
        block: ShellRobot.() -> Unit,
    ) = shellRobot(state, graph = E2eKoinGraph(E2E_TODAY, releaseFlags = flags(hub)), destinations = destinations(hub), block = block)

    private fun flags(hub: Boolean) = if (hub) FakeReleaseFlags(ReleaseFlag.LeaderboardHub) else FakeReleaseFlags()

    private fun destinations(hub: Boolean) = if (hub) ranksTopLevelDestinations else topLevelDestinations

    private fun ShellRobot.assertThirdTab(hub: Boolean) = thirdTab(hub).let { assertThirdTab(it.tag, it.label, it.absent) }

    private fun ShellRobot.assertThirdTab(tag: String, label: String, absent: String) {
        rule.onNodeWithTag(tag).assert(hasText(label))
        assertFalse("$absent shows beside $tag", shows(absent))
    }

    private fun thirdTab(hub: Boolean) =
        if (hub) ThirdTab(RANKS_TAB, "Ranks", absent = HISTORY_TAB) else ThirdTab(HISTORY_TAB, "History", absent = RANKS_TAB)

    private class ThirdTab(val tag: String, val label: String, val absent: String)

    private fun ImageVector.pathData() = root.mapNotNull { (it as? VectorPath)?.pathData }

    private companion object {
        const val RAIL = "w1280dp-h800dp-land"
        const val ZIP = "zip"
        const val HOME = "route.home"
        const val HISTORY = "route.history"
        const val RANKS = "route.ranks"
        const val HISTORY_TAB = "nav.tab.history"
        const val RANKS_TAB = "nav.tab.ranks"
    }
}
