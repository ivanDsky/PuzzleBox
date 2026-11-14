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
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Ranks
import com.ivandsky.puzzlebox.designsystem.RanksFilled
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.history.api.HistoryRootKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The third tab is Ranks, on the key [HistoryRootKey] (kept from when it was the History tab, so a back stack saved by an
 * older build restores here). A pushed [HistoryKey] opens the hub at that game, and a stack saved on the tab restores on it.
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w411dp-h891dp")
class RanksTabTest {

    // Only the icon case composes here; the shell cases bring their own rule inside shellRobot.
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the third tab reads Ranks and opens the hub`() = shell {
        rule.onNodeWithTag(RANKS_TAB).assert(hasText("Ranks"))

        tapTab(HistoryRootKey)

        assertRoute(RANKS)
        assertSelectedTab(HistoryRootKey)
    }

    @Test
    @Config(qualifiers = RAIL)
    fun `on a tablet the rail reads Ranks`() = shell {
        rule.onNodeWithTag(RANKS_TAB).assert(hasText("Ranks"))

        tapTab(HistoryRootKey)

        assertRoute(RANKS)
        assertSelectedTab(HistoryRootKey)
    }

    @Test
    fun `a pushed HistoryKey opens the hub at that game`() = shell {
        push(HistoryKey(ZIP))

        assertRoute(RANKS)

        back()
        assertRoute(HOME)
    }

    @Test
    fun `a back stack saved on the Ranks tab restores on it`() {
        val seed = tabAt(HistoryRootKey, HistoryKey(ZIP))
        shellRobot(seed, graph = E2eKoinGraph(E2E_TODAY), destinations = topLevelDestinations) {
            assertRoute(RANKS)

            relaunch(keepSavedState = true)

            assertRoute(RANKS)
            assertState(seed)
            back()
            assertRoute(RANKS)
            assertState(tabAt(HistoryRootKey))
        }
    }

    @Test
    fun `the Ranks destination keeps the History root key and tab`() {
        val ranks = topLevelDestinations.single { it.key == HistoryRootKey }
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
        val ranks = topLevelDestinations.single { it.key == HistoryRootKey }
        var drawn = emptyList<Any>() to emptyList<Any>()
        composeRule.setContent { drawn = ranks.icon().pathData() to ranks.selectedIcon().pathData() }
        composeRule.waitForIdle()

        assertEquals(PuzzleIcons.Ranks.pathData() to PuzzleIcons.RanksFilled.pathData(), drawn)
    }

    private fun shell(block: ShellRobot.() -> Unit) =
        shellRobot(graph = E2eKoinGraph(E2E_TODAY), destinations = topLevelDestinations, block = block)

    private fun ImageVector.pathData() = root.mapNotNull { (it as? VectorPath)?.pathData }

    private companion object {
        const val RAIL = "w1280dp-h800dp-land"
        const val ZIP = "zip"
        const val HOME = "route.home"
        const val RANKS = "route.ranks"
        const val RANKS_TAB = "nav.tab.ranks"
    }
}
