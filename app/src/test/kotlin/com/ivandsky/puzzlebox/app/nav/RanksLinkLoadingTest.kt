package com.ivandsky.puzzlebox.app.nav

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.e2e.E2E_TODAY
import com.ivandsky.puzzlebox.app.e2e.E2eKoinGraph
import com.ivandsky.puzzlebox.app.e2e.ShellRobot
import com.ivandsky.puzzlebox.app.e2e.shellRobot
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.history.api.HistoryRootKey
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * F7: a hub pushed by a link (game page row, Result chip, Today rank row) leaves Loading. Its three view models once shared one store
 * key, so each cleared the one before it and the hub's was dead on arrival: its state never moved off the initial Loading.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w411dp-h891dp")
class RanksLinkLoadingTest {

    private fun open(go: ShellRobot.() -> Unit) = shellRobot(
        puzzleBoxNavigationState(),
        graph = E2eKoinGraph(E2E_TODAY, releaseFlags = FakeReleaseFlags(ReleaseFlag.LeaderboardHub)),
        destinations = ranksTopLevelDestinations,
    ) {
        go()
        awaitTag("route.ranks")
        awaitIdling { rule.onAllNodesWithTag("daily_board.loading", useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag("ranks.board").assertIsDisplayed()
    }

    @Test
    fun `the tab root leaves Loading`() = open { tapTab(HistoryRootKey) }

    @Test
    fun `a game's link with no day leaves Loading`() = open { push(HistoryKey("zip", null)) }

    @Test
    fun `a game's link at today leaves Loading`() = open { push(HistoryKey("zip", E2E_TODAY.toString())) }

    @Test
    fun `a game's link at a past day leaves Loading`() = open { push(HistoryKey("zip", E2E_TODAY.minusDays(1).toString())) }
}
