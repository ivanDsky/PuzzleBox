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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The animation hooks of [LineChart], read off the drawn pixels: `drawProgress` is how much of the line is drawn,
 * `labelAlpha` the opacity of its labels and dots, and `summaryPoints` what the screen reader is told. Each fails if
 * its wiring is taken out of the chart.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class LineChartMotionParamsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val series = previewTimes(95, 88, 102, 76, 71, 64)
    private val stage = Color.Black
    private var progress by mutableStateOf(1f)
    private var alpha by mutableStateOf(1f)
    private var primary: Color = Color.Unspecified

    private fun show(summaryPoints: List<LineChartPoint> = series) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = true) {
                primary = MaterialTheme.colorScheme.primary
                Box(Modifier.size(360.dp, 300.dp).background(stage).testTag("stage")) {
                    LineChart(
                        series,
                        "Average solve time",
                        PreviewTime,
                        drawProgress = { progress },
                        labelAlpha = { alpha },
                        summaryPoints = summaryPoints,
                    )
                }
            }
        }
    }

    private fun render(drawProgress: Float, labelAlpha: Float): ImageBitmap {
        composeRule.runOnIdle { progress = drawProgress; alpha = labelAlpha }
        composeRule.waitForIdle()
        return composeRule.onNodeWithTag("stage").captureToImage()
    }

    @Test
    fun `the draw progress is how much of the line is drawn`() {
        show()
        val none = countPixels(render(0f, labelAlpha = 0f), primary)
        val half = countPixels(render(0.5f, labelAlpha = 0f), primary)
        val all = countPixels(render(1f, labelAlpha = 0f), primary)
        assertEquals("nothing is drawn at progress 0", 0, none)
        assertTrue("the whole line is drawn at 1: $all", all > 200)
        assertTrue("about half of it at 0.5: $half of $all", half in (all * 0.3).toInt()..(all * 0.7).toInt())
    }

    @Test
    fun `the label alpha is the opacity of the labels and the dots`() {
        show()
        val hidden = render(1f, labelAlpha = 0f)
        val half = render(1f, labelAlpha = 0.5f)
        val shown = render(1f, labelAlpha = 1f)
        assertTrue("the dots add line colour when shown", countPixels(shown, primary) > countPixels(hidden, primary))
        assertTrue("the text adds ink when shown", countInk(shown, stage) > countInk(hidden, stage) + 100)
        assertTrue("half opacity sits between", countInk(half, stage) in countInk(hidden, stage)..countInk(shown, stage))
        // The line itself is not faded by it: with the labels hidden it is still drawn.
        assertTrue(countPixels(hidden, primary) > 200)
    }

    @Test
    fun `the summary describes the summary points, not the points as drawn`() {
        val told = previewTimes(10, 20, 30)
        show(summaryPoints = told)
        val description = composeRule.onNodeWithTag("stage").fetchSemanticsNode().children.single().config[SemanticsProperties.ContentDescription]
        assertEquals(lineChartSummary("Average solve time", told, PreviewTime), description.joinToString())
    }
}
