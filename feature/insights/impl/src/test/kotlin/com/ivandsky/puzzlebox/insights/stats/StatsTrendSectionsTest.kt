package com.ivandsky.puzzlebox.insights.stats

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.insights.domain.stats.HintFreeRateSeries
import com.ivandsky.puzzlebox.insights.domain.stats.StatsGranularity
import com.ivandsky.puzzlebox.insights.domain.stats.TimeTrendSeries
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The time trend and hint-free sections: a line from 2 points, a number below that, and their place on the page. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class StatsTrendSectionsTest {

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

    @Test
    fun `two points or more draw a line in each section`() {
        show(StatsFixtures.readyWithLines())

        rule.onNodeWithTag("stats.trend.line").assertIsDisplayed()
        rule.onNodeWithTag("stats.hintFree.line", useUnmergedTree = false).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `one point is a number, not a line`() {
        show(
            StatsFixtures.readyWithLines(
                trend = TimeTrendSeries(StatsGranularity.Week, StatsFixtures.trend.points.take(1)),
                hintFree = HintFreeRateSeries(StatsGranularity.Week, StatsFixtures.hintFreeRate.points.take(1)),
            ),
        )

        rule.onAllNodesWithTag("stats.trend.line").assertCountEquals(0)
        rule.onNodeWithTag("stats.trend.single").assertTextContains("1:35", substring = true)
        rule.onNodeWithTag("stats.trend.single").assertTextContains("Week of Nov 2, 2026", substring = true)
        rule.onNodeWithTag("stats.hintFree.single").performScrollTo().assertTextContains("40%", substring = true)
        rule.onNodeWithTag("stats.hintFree.single").assertTextContains("2 of 5 solves", substring = true)
    }

    @Test
    fun `the sections have their titles`() {
        show(StatsFixtures.readyWithLines())

        rule.onNodeWithText("Time trend").assertIsDisplayed()
        rule.onNodeWithText("Hint-free rate").performScrollTo().assertIsDisplayed()
    }
}
