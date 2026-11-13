package com.ivandsky.puzzlebox.app.e2e

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.nav.puzzleBoxNavigationState
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Reselect at a root: tapping the selected tab while it sits at its root scrolls
 * that root to the top; at a non-root the tap only pops, and nothing scrolls. Today's list is
 * the probe: its header sits above the fold once the list is scrolled to its last block, the
 * streak's week strip (Today no longer links to History, so the pushed screen is pushed directly).
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class)
class TabReselectE2ETest {

    @Test
    fun `reselecting Today at its root brings its header back`() = shellRobot {
        assertRoute(HOME)
        scrollTodayDown()

        tapTab(HomeKey)

        header().assertIsDisplayed()
        assertState(puzzleBoxNavigationState())
    }

    @Test
    fun `reselecting Today over a pushed screen pops to the root and does not scroll`() = shellRobot {
        assertRoute(HOME)
        scrollTodayDown()
        push(HistoryKey())
        assertRoute(HISTORY)

        tapTab(HomeKey)

        assertState(puzzleBoxNavigationState())
        assertRoute(HOME)
        header().assertIsNotDisplayed()
    }

    private fun ShellRobot.scrollTodayDown() {
        rule.onNodeWithTag("home.week").performScrollTo()
        header().assertIsNotDisplayed()
    }

    // The header reads "Today", like the tab; the tab item is not the header.
    private fun ShellRobot.header(): SemanticsNodeInteraction {
        val tab = hasTestTag("nav.tab.home")
        return rule.onNode(hasTextExactly("Today") and !tab and !hasAnyAncestor(tab))
    }

    private companion object {
        const val HOME = "route.home"
        const val HISTORY = "route.history"
    }
}
