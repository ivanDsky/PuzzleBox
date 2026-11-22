package com.ivandsky.puzzlebox.designsystem.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Where the charts' labels and dots come from when a caller morphs one dataset into another: the text can be a
 * different dataset's than the drawn shape ([LineChart] `labelPoints` / `labelRange`, [BarChart] `valueText`), the dots
 * fade on their own (`dotAlpha`), and a bar's value rides the animated top of its bar. Each fails if its wiring is
 * taken out of the chart.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class ChartLabelSourceTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val stage = Color.Black
    private var primary: Color = Color.Unspecified
    private var dots by mutableStateOf(1f)
    private var barScale by mutableStateOf(1f)

    private fun texts(text: String) = composeRule.onAllNodesWithText(text, useUnmergedTree = true)

    @Test
    fun `the line's axis values and end labels come from the label points and range`() {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = true) {
                Box(Modifier.size(360.dp, 300.dp)) {
                    LineChart(
                        points = previewTimes(95, 88, 102, 76),
                        what = "Average solve time",
                        valueLabel = { "${it.toInt()}v" },
                        labelPoints = listOf(LineChartPoint(0.0, 1.0, "old start"), LineChartPoint(1.0, 2.0, "old end")),
                        labelRange = 5.0..9.0,
                    )
                }
            }
        }
        texts("9v").assertCountEquals(1)
        texts("5v").assertCountEquals(1)
        texts("old start").assertCountEquals(1)
        texts("old end").assertCountEquals(1)
        texts("102v").assertCountEquals(0)
        texts("76v").assertCountEquals(0)
    }

    @Test
    fun `the dots fade on their own alpha, not the labels'`() {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = true) {
                primary = MaterialTheme.colorScheme.primary
                Box(Modifier.size(360.dp, 300.dp).background(stage).testTag("stage")) {
                    LineChart(previewTimes(95, 88, 102, 76), "Average solve time", PreviewTime, labelAlpha = { 1f }, dotAlpha = { dots })
                }
            }
        }
        composeRule.runOnIdle { dots = 0f }
        composeRule.waitForIdle()
        val without = countPixels(composeRule.onNodeWithTag("stage").captureToImage(), primary)
        composeRule.runOnIdle { dots = 1f }
        composeRule.waitForIdle()
        val with = countPixels(composeRule.onNodeWithTag("stage").captureToImage(), primary)
        assertTrue("the dots add line colour: $without to $with", with > without + 50)
    }

    @Test
    fun `a bar's value text can be another dataset's`() {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = true) {
                Box(Modifier.size(360.dp, 300.dp)) {
                    BarChart(previewWeekdayTimes(100, 50), "Average solve time by weekday", PreviewTime, valueText = { "was $it" })
                }
            }
        }
        texts("was 0").assertCountEquals(1)
        texts("was 1").assertCountEquals(1)
        texts("1:40").assertCountEquals(0)
    }

    @Test
    fun `a bar's value rides the animated top of its bar`() {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = true) {
                primary = MaterialTheme.colorScheme.primary
                Box(Modifier.size(360.dp, 300.dp).background(stage).testTag("stage")) {
                    BarChart(previewWeekdayTimes(100, 50), "Average solve time by weekday", PreviewTime, barScale = { barScale })
                }
            }
        }
        listOf(1f, 0.5f, 0.2f).forEach { scale ->
            composeRule.runOnIdle { barScale = scale }
            composeRule.waitForIdle()
            val label = texts("1:40").onFirst().fetchSemanticsNode().layoutInfo.coordinates.boundsInRoot()
            val map = composeRule.onNodeWithTag("stage").captureToImage().toPixelMap()
            val x = ((label.left + label.right) / 2).toInt()
            val barTop = (0 until map.height).first { y -> map[x, y].let { abs(it.red - primary.red) < 0.02f && abs(it.green - primary.green) < 0.02f && abs(it.blue - primary.blue) < 0.02f } }
            assertTrue("at scale $scale the value ends at the bar top: label bottom ${label.bottom}, bar top $barTop", abs(label.bottom - barTop) <= 3f)
        }
    }
}
