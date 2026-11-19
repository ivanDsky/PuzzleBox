package com.ivandsky.puzzlebox.designsystem.chart

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BarChartTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the whole chart is one node with the bar summary`() {
        composeRule.setContent {
            PuzzleBoxTheme { BarChart(previewWeekdayTimes(95, 88, 102), "Average solve time by weekday", PreviewTime) }
        }

        composeRule.onNodeWithContentDescription(
            "Average solve time by weekday, 3 bars. Mon 1:35. Tue 1:28. Wed 1:42. Highest Wed 1:42. Lowest Tue 1:28.",
        ).assertIsDisplayed()
    }

    @Test
    fun `each bar has its value above and its label below`() {
        composeRule.setContent {
            PuzzleBoxTheme { BarChart(previewWeekdayTimes(95, 88), "Average solve time by weekday", PreviewTime) }
        }

        // The chart is one merged node for a screen reader; the unmerged tree still holds its texts.
        composeRule.onAllNodesWithText("1:35", useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodesWithText("1:28", useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodesWithText("Mon", useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodesWithText("Tue", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun `one bar is a chart, no bars draw nothing`() {
        composeRule.setContent {
            PuzzleBoxTheme {
                Box {
                    BarChart(previewWeekdayTimes(95), "Average solve time by weekday", PreviewTime)
                    BarChart(emptyList(), "Nothing", PreviewTime)
                }
            }
        }

        composeRule.onAllNodesWithText("Mon", useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodesWithText("Nothing", useUnmergedTree = true).assertCountEquals(0)
    }
}
