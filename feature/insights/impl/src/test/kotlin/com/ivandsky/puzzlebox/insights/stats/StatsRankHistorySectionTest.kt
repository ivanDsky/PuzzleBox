package com.ivandsky.puzzlebox.insights.stats

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.insights.domain.stats.RankHistoryStanding
import com.ivandsky.puzzlebox.insights.domain.stats.SectionState
import com.ivandsky.puzzlebox.insights.domain.stats.VsFriendsStanding
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The rank history section: a line from 2 kept weeks, one number with 1, a plain note with none, the line that replaces it, and Can't load right now in its own place. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class StatsRankHistorySectionTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun show(state: SectionState<RankHistoryStanding>, game: GameId = StatsFixtures.zip, scrollTo: String = "stats.rankHistory") {
        rule.setContent {
            PuzzleBoxTheme {
                StatsScreen(
                    state = StatsFixtures.readyWithLines(game = game, rankHistory = state),
                    games = StatsFixtures.games,
                    gameName = StatsFixtures::gameName,
                    onSelectRange = {},
                    onSelectGame = {},
                    onOpenSolve = {},
                    onBack = {},
                )
            }
        }
        rule.onNodeWithTag("stats.sections").performScrollToNode(hasTestTag(scrollTo))
    }

    @Test
    fun `several kept weeks are a line labelled among your friends that week`() {
        show(SectionState.Ready(StatsFixtures.rankHistory))

        rule.onNodeWithText("Rank history").assertIsDisplayed()
        rule.onNodeWithTag("stats.rankHistory.line").assertIsDisplayed()
        rule.onNodeWithTag("stats.rankHistory.caption").assertTextContains("among your friends that week", substring = true)
        rule.onAllNodesWithTag("stats.rankHistory.single").assertCountEquals(0)
    }

    @Test
    fun `one kept week is a number with one decimal and its week`() {
        show(SectionState.Ready(RankHistoryStanding.Kept(StatsFixtures.rankHistory.weeks.take(1))))

        rule.onNodeWithTag("stats.rankHistory.single").assertTextContains("142.5", substring = true)
        rule.onNodeWithTag("stats.rankHistory.single").assertTextContains("among your friends that week", substring = true)
        rule.onNodeWithTag("stats.rankHistory.single").assertTextContains("Week of Nov 2, 2026", substring = true)
        rule.onAllNodesWithTag("stats.rankHistory.line").assertCountEquals(0)
    }

    @Test
    fun `no kept week yet says when weeks appear`() {
        show(SectionState.Ready(RankHistoryStanding.Kept(emptyList())))

        rule.onNodeWithTag("stats.rankHistory.empty").assertTextContains("Your weeks show here once a week you played with friends has ended.")
        rule.onAllNodesWithTag("stats.rankHistory.line").assertCountEquals(0)
    }

    @Test
    fun `signed out is one plain line in the section's place, with no title`() {
        show(SectionState.Ready(RankHistoryStanding.Locked(VsFriendsStanding.Unlock.SignIn)), scrollTo = "stats.rankHistory.locked")

        rule.onNodeWithTag("stats.rankHistory.locked").assertTextContains("Sign in and add friends to see your Friends points week by week.")
        rule.onAllNodesWithText("Rank history").assertCountEquals(0)
    }

    @Test
    fun `no friends is one plain line asking to add friends`() {
        show(SectionState.Ready(RankHistoryStanding.Locked(VsFriendsStanding.Unlock.AddFriends)), scrollTo = "stats.rankHistory.locked")

        rule.onNodeWithTag("stats.rankHistory.locked").assertTextContains("Add friends to see your Friends points week by week.")
    }

    @Test
    fun `an unavailable read says Can't load right now in its own place and the rest of the page shows`() {
        show(SectionState.Unavailable)

        rule.onNodeWithTag("stats.rankHistory.unavailable").assertTextContains("Can't load right now")
        rule.onNodeWithText("Rank history").assertIsDisplayed()
        rule.onNodeWithTag("stats.weekday.chart").assertIsDisplayed()
    }

    @Test
    fun `a loading read says so quietly`() {
        show(SectionState.Loading)

        rule.onNodeWithTag("stats.rankHistory.loading").assertTextContains("Loading…")
    }
}
