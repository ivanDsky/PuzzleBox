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
 * The animation hooks of [BarChart], read off the drawn pixels: `barScale` is each bar's height from the baseline and
 * `valueAlpha` the opacity of the values above the bars. Each fails if its wiring is taken out of the chart.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class BarChartMotionParamsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val stage = Color.Black
    private var scale0 by mutableStateOf(1f)
    private var scale1 by mutableStateOf(1f)
    private var values by mutableStateOf(1f)
    private var primary: Color = Color.Unspecified

    private fun show() {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = true) {
                primary = MaterialTheme.colorScheme.primary
                Box(Modifier.size(360.dp, 300.dp).background(stage).testTag("stage")) {
                    BarChart(
                        previewWeekdayTimes(100, 100),
                        "Average solve time by weekday",
                        PreviewTime,
                        barScale = { index -> if (index == 0) scale0 else scale1 },
                        valueAlpha = { values },
                    )
                }
            }
        }
    }

    private fun render(first: Float, second: Float, valueAlpha: Float): ImageBitmap {
        composeRule.runOnIdle { scale0 = first; scale1 = second; values = valueAlpha }
        composeRule.waitForIdle()
        return composeRule.onNodeWithTag("stage").captureToImage()
    }

    @Test
    fun `each bar stands as tall as its scale from the baseline`() {
        show()
        val full = countPixels(render(1f, 1f, valueAlpha = 0f), primary)
        val flat = countPixels(render(0f, 0f, valueAlpha = 0f), primary)
        val half = countPixels(render(0.5f, 0.5f, valueAlpha = 0f), primary)
        val one = countPixels(render(1f, 0f, valueAlpha = 0f), primary)
        assertTrue("two bars stand at scale 1: $full", full > 1_000)
        assertEquals("no bar shows at scale 0", 0, flat)
        assertTrue("half height is about half the bar: $half of $full", half in (full * 0.4).toInt()..(full * 0.6).toInt())
        assertTrue("the scale is per bar: $one of $full", one in (full * 0.4).toInt()..(full * 0.6).toInt())
    }

    @Test
    fun `the value alpha is the opacity of the values above the bars`() {
        show()
        val hidden = countInk(render(1f, 1f, valueAlpha = 0f), stage)
        val half = countInk(render(1f, 1f, valueAlpha = 0.5f), stage)
        val shown = countInk(render(1f, 1f, valueAlpha = 1f), stage)
        assertTrue("the values add ink when shown: $hidden to $shown", shown > hidden + 100)
        assertTrue("half opacity sits between", half in hidden..shown)
    }
}
