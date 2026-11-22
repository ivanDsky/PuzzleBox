package com.ivandsky.puzzlebox.insights.stats.grouped

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.insights.domain.stats.StatsRange
import com.ivandsky.puzzlebox.insights.stats.StatsFixtures
import com.ivandsky.puzzlebox.insights.stats.StatsScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The grouped stats page on a tablet (StatsFilters on): a group lays its chart and side block two-up at 1.3 : 1. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class StatsGroupedTabletTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `the Speed chart and its records sit side by side at 1_3 to 1`() {
        rule.setContent {
            PuzzleBoxTheme {
                StatsScreen(
                    state = StatsFixtures.readyWithLines(range = StatsRange.Week, game = StatsFixtures.zip).copy(filters = true, since = StatsFixtures.since),
                    games = StatsFixtures.games,
                    gameName = StatsFixtures::gameName,
                    onSelectRange = {},
                    onSelectGame = {},
                    onOpenSolve = {},
                    onBack = {},
                )
            }
        }

        val chart = rule.onNodeWithTag("stats.trend").assertIsDisplayed().getUnclippedBoundsInRoot()
        val records = rule.onNodeWithTag("stats.group.records.speed").assertIsDisplayed().getUnclippedBoundsInRoot()

        assertEquals("same row", chart.top.value, records.top.value, 1f)
        assertEquals("chart left of records", true, chart.right <= records.left)
        assertEquals(1.3f, (chart.right - chart.left).value / (records.right - records.left).value, 0.02f)
        rule.onNodeWithTag("stats.filters").assertIsDisplayed()
    }

    @Test
    fun `the records column is wide enough to keep a date range on one line`() {
        rule.setContent {
            PuzzleBoxTheme {
                StatsScreen(
                    state = StatsFixtures.readyWithLines(range = StatsRange.Week, game = StatsFixtures.zip).copy(filters = true, since = StatsFixtures.since),
                    games = StatsFixtures.games,
                    gameName = StatsFixtures::gameName,
                    onSelectRange = {},
                    onSelectGame = {},
                    onOpenSolve = {},
                    onBack = {},
                )
            }
        }

        val records = rule.onNodeWithTag("stats.group.records.speed").getUnclippedBoundsInRoot()
        assertTrue("records are ${(records.right - records.left).value}dp wide", (records.right - records.left).value >= 360f)
    }
}
