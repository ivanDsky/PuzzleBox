package com.ivandsky.puzzlebox.insights.stats

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.insights.domain.stats.GameStanding
import com.ivandsky.puzzlebox.insights.domain.stats.SectionState
import com.ivandsky.puzzlebox.insights.domain.stats.VsFriendsStanding
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The weekday and vs friends sections: a bar per weekday with a solve, the best and weakest rows, the one-game case, the plain line that replaces the section, and Can't load right now in its own place. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class StatsWeekdayAndFriendsSectionsTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun show(state: StatsUiState) = rule.setContent {
        PuzzleBoxTheme {
            StatsScreen(
                state = state,
                games = StatsFixtures.games,
                gameName = StatsFixtures::gameName,
                onSelectRange = {},
                onSelectGame = {},
                onOpenSolve = {},
                onBack = {},
            )
        }
    }

    /** The page is a lazy list: bring the item holding [tag] into the window before looking at it. */
    private fun reveal(tag: String) {
        rule.onNodeWithTag("stats.sections").performScrollToNode(hasTestTag(tag))
    }

    private fun showFriends(state: SectionState<VsFriendsStanding>) {
        show(StatsFixtures.readyWithLines(vsFriends = state))
        // The last item: the list scrolls to its end.
        rule.onNodeWithTag("stats.sections").performScrollToNode(hasTestTag("stats.weekday"))
    }

    @Test
    fun `the weekday section draws a bar chart with only the weekdays that have a solve`() {
        show(StatsFixtures.readyWithLines())
        reveal("stats.weekday")

        rule.onNodeWithText("Weekday pattern").assertIsDisplayed()
        rule.onNodeWithTag("stats.weekday.chart").assertIsDisplayed()
        rule.onAllNodesWithText("Sun", useUnmergedTree = true).assertCountEquals(0)
        rule.onAllNodesWithText("Mon", useUnmergedTree = true).assertCountEquals(1)
        rule.onNodeWithTag("stats.weekday.note").assertTextContains("Quickest on Saturday, slowest on Wednesday")
    }

    @Test
    fun `vs friends shows the best and the weakest game with points and place`() {
        showFriends(SectionState.Ready(StatsFixtures.vsFriends))

        rule.onNodeWithText("Vs friends").assertIsDisplayed()
        rule.onNodeWithTag("stats.vsFriends.best").assertTextContains("Zip", substring = true)
        rule.onNodeWithTag("stats.vsFriends.best").assertTextContains("Best game · place 1 of 6", substring = true)
        rule.onNodeWithTag("stats.vsFriends.best").assertTextContains("1,340 pts", substring = true)
        rule.onNodeWithTag("stats.vsFriends.weakest").assertTextContains("Tango", substring = true)
        rule.onNodeWithTag("stats.vsFriends.weakest").assertTextContains("212 pts", substring = true)
    }

    @Test
    fun `one game shows only the best`() {
        showFriends(SectionState.Ready(VsFriendsStanding.Ranked(GameStanding(StatsFixtures.queens, 90.0, place = 2, players = 3), weakest = null)))

        rule.onNodeWithTag("stats.vsFriends.best").assertIsDisplayed()
        rule.onAllNodesWithTag("stats.vsFriends.weakest").assertCountEquals(0)
    }

    @Test
    fun `signed out is one plain line in the section's place, with no title`() {
        showFriends(SectionState.Ready(VsFriendsStanding.Locked(VsFriendsStanding.Unlock.SignIn)))

        rule.onNodeWithTag("stats.vsFriends.locked").assertTextContains("Sign in and add friends to see how your games compare.")
        rule.onAllNodesWithText("Vs friends").assertCountEquals(0)
    }

    @Test
    fun `no friends is one plain line asking to add friends`() {
        showFriends(SectionState.Ready(VsFriendsStanding.Locked(VsFriendsStanding.Unlock.AddFriends)))

        rule.onNodeWithTag("stats.vsFriends.locked").assertTextContains("Add friends to see how your games compare.")
    }

    @Test
    fun `an unavailable read says Can't load right now in its own place and the rest of the page shows`() {
        showFriends(SectionState.Unavailable)

        rule.onNodeWithTag("stats.vsFriends.unavailable").assertTextContains("Can't load right now")
        rule.onNodeWithText("Vs friends").assertIsDisplayed()
        rule.onNodeWithTag("stats.weekday.chart").assertIsDisplayed()
        reveal("stats.trend.line")
        rule.onNodeWithTag("stats.trend.line").assertIsDisplayed()
    }

    @Test
    fun `a loading read says so quietly`() {
        showFriends(SectionState.Loading)

        rule.onNodeWithTag("stats.vsFriends.loading").assertTextContains("Loading…")
    }
}
