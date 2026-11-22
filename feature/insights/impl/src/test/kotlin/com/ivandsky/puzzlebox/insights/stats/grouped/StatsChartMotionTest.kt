package com.ivandsky.puzzlebox.insights.stats.grouped

import android.content.Context
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.chart.BarChartBar
import com.ivandsky.puzzlebox.designsystem.chart.LineChartPoint
import com.ivandsky.puzzlebox.designsystem.chart.barChartSummary
import com.ivandsky.puzzlebox.designsystem.chart.lineChartSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The motion wrappers on the test clock: they start from nothing, end on the data they were given, and follow it when it changes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class StatsChartMotionTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val a = listOf(LineChartPoint(0.0, 10.0, "Nov 1"), LineChartPoint(1.0, 20.0, "Nov 2"), LineChartPoint(2.0, 15.0, "Nov 3"))
    private val b = listOf(LineChartPoint(0.0, 100.0, "Oct 1"), LineChartPoint(1.0, 140.0, "Oct 2"))
    private val label: (Double) -> String = { it.toInt().toString() }

    private fun animations(scale: Float) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
        rule.mainClock.autoAdvance = false
    }

    private fun description(tag: String): String =
        rule.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString()

    private fun lineIn(scale: Float) {
        animations(scale)
        var points by mutableStateOf(a)
        rule.setContent {
            PuzzleBoxTheme {
                CompositionLocalProvider(LocalStatsMotion provides StatsMotion()) {
                    StatsLineChart(points, "Time", label, key = "line", modifier = Modifier.testTag("line"))
                }
            }
        }
        rule.mainClock.advanceTimeBy(1_000)
        assertEquals(lineChartSummary("Time", a, label), description("line"))
        rule.runOnIdle { points = b; Snapshot.sendApplyNotifications() }
        rule.mainClock.advanceTimeBy(100)
        assertNotEquals("mid-morph is neither line", lineChartSummary("Time", a, label), description("line"))
        rule.mainClock.advanceTimeBy(1_000)
        assertEquals("the morph ends on the new line", lineChartSummary("Time", b, label), description("line"))
    }

    @Test
    fun `a line morphs to the new data and ends on exactly it`() = lineIn(scale = 1f)

    @Test
    fun `a line follows new data under reduced motion too`() = lineIn(scale = 0f)

    @Test
    fun `bars end on the data they were given after a change`() {
        animations(1f)
        val week = listOf(BarChartBar("Mon", 100.0), BarChartBar("Tue", 50.0))
        val next = listOf(BarChartBar("Mon", 40.0), BarChartBar("Tue", 80.0))
        var bars by mutableStateOf(week)
        rule.setContent {
            PuzzleBoxTheme {
                CompositionLocalProvider(LocalStatsMotion provides StatsMotion()) {
                    StatsBarChart(bars, "Time", label, key = "bars", modifier = Modifier.testTag("bars"))
                }
            }
        }
        rule.mainClock.advanceTimeBy(1_000)
        rule.runOnIdle { bars = next; Snapshot.sendApplyNotifications() }
        rule.mainClock.advanceTimeBy(1_000)
        assertEquals(barChartSummary("Time", next, label), description("bars"))
    }

    @Test
    fun `a headline counts up from zero to its value`() {
        animations(1f)
        rule.setContent {
            PuzzleBoxTheme {
                CompositionLocalProvider(LocalStatsMotion provides StatsMotion()) {
                    StatsCountUpText("1:35", key = "headline")
                }
            }
        }
        rule.mainClock.advanceTimeBy(16)
        rule.onNodeWithText("0:00").assertExists()
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithText("1:35").assertExists()
    }

    @Test
    fun `a headline shows its value at once under reduced motion`() {
        animations(0f)
        rule.setContent {
            PuzzleBoxTheme {
                CompositionLocalProvider(LocalStatsMotion provides StatsMotion()) {
                    StatsCountUpText("1:35", key = "headline")
                }
            }
        }
        rule.mainClock.advanceTimeBy(16)
        rule.onNodeWithText("1:35").assertExists()
    }

    @Test
    fun `without the page's motion a headline is plain text from the first frame`() {
        animations(1f)
        rule.setContent { PuzzleBoxTheme { StatsCountUpText("1:35", key = "headline") } }
        rule.onNodeWithText("1:35").assertExists()
    }
}
