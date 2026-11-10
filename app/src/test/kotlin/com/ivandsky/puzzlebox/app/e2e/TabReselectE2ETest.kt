package com.ivandsky.puzzlebox.app.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.ivandsky.puzzlebox.app.nav.puzzleBoxNavigationState
import com.ivandsky.puzzlebox.app.nav.topLevelDestinations
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag.NavV2
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Reselect at a root (NavV2): tapping the selected tab while it sits at its root scrolls
 * that root to the top; at a non-root the tap only pops, and nothing scrolls. With the flag
 * off a root reselect stays the no-op it is today. Today's list is the probe: its header
 * sits above the fold once the list is scrolled to its last block, the streak's week strip
 * (with the flag on Today no longer links to History, so the pushed screen is pushed directly).
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application: the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = android.app.Application::class)
class TabReselectE2ETest {

    @Test
    fun `reselecting Today at its root brings its header back`() = flagOnRobot {
        assertRoute(HOME)
        scrollTodayDown()

        tapTab(HomeKey)

        header().assertIsDisplayed()
        assertState(puzzleBoxNavigationState(navV2 = true))
    }

    @Test
    fun `reselecting Today over a pushed screen pops to the root and does not scroll`() = flagOnRobot {
        assertRoute(HOME)
        scrollTodayDown()
        push(HistoryKey())
        assertRoute(HISTORY)

        tapTab(HomeKey)

        assertState(puzzleBoxNavigationState(navV2 = true))
        assertRoute(HOME)
        header().assertIsNotDisplayed()
    }

    @Test
    fun `with the flag off reselecting Home at its root leaves the list scrolled`() = shellRobot {
        assertRoute(HOME)
        scrollTodayDown()

        tapTab(HomeKey)

        header().assertIsNotDisplayed()
    }

    private fun ShellRobot.scrollTodayDown() {
        rule.onNodeWithTag("home.week").performScrollTo()
        header().assertIsNotDisplayed()
    }

    private fun ShellRobot.header() = rule.onNodeWithText("PuzzleBox")

    private fun flagOnRobot(block: ShellRobot.() -> Unit) = shellRobot(
        state = puzzleBoxNavigationState(navV2 = true),
        graph = E2eKoinGraph(E2E_TODAY, releaseFlags = FakeReleaseFlags(NavV2)),
        destinations = topLevelDestinations(navV2 = true),
        block = block,
    )

    private companion object {
        const val HOME = "route.home"
        const val HISTORY = "route.history"
    }
}
