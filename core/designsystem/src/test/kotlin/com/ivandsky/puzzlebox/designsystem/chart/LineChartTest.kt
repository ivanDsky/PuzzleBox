package com.ivandsky.puzzlebox.designsystem.chart

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.assertIsDisplayed
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LineChartTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the whole chart is one node with the series summary`() {
        composeRule.setContent {
            PuzzleBoxTheme { LineChart(previewTimes(95, 88, 102), "Average solve time", PreviewTime) }
        }

        composeRule.onNodeWithContentDescription(
            "Average solve time, 3 points. First Oct 26 1:35. Last Nov 9 1:42. Lowest Nov 2 1:28. Highest Nov 9 1:42.",
        ).assertIsDisplayed()
    }

    @Test
    fun `its ends are named under the line and the scale beside it`() {
        composeRule.setContent {
            PuzzleBoxTheme { LineChart(previewShares(40, 80), "Hint-free solves", PreviewShare, valueRange = 0.0..1.0) }
        }

        // The chart is one merged node for a screen reader; the unmerged tree still holds its labels.
        composeRule.onAllNodesWithText("100%", useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodesWithText("0%", useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodesWithText("Oct 26", useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodesWithText("Nov 2", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun `fewer than two points draw nothing`() {
        composeRule.setContent {
            PuzzleBoxTheme { Box { LineChart(previewTimes(95), "Average solve time", PreviewTime) } }
        }

        composeRule.onAllNodesWithText("Oct 26", useUnmergedTree = true).assertCountEquals(0)
    }
}
