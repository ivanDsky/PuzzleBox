package com.ivandsky.puzzlebox.insights.stats.grouped

import android.content.Context
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.chart.BarChartBar
import com.ivandsky.puzzlebox.designsystem.chart.LineChartPoint
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The motion wiring on the test clock, read off the drawn pixels: a chart arrives once per [StatsMotion] and not
 * again when it is composed again, a change morphs from the chart on screen (never an empty chart), and a block waits
 * for the screen and for its stagger only when it arrives with the others. Each fails if its wiring is removed.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class StatsMotionWiringTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val week = listOf(95.0, 88.0, 102.0, 76.0, 81.0, 70.0, 64.0)
    private val month = listOf(110.0, 104.0, 99.0, 93.0, 96.0, 90.0, 84.0, 79.0, 75.0)
    private val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val stage = Color.Black
    private var primary: Color = Color.Unspecified

    private fun line(values: List<Double>) = values.mapIndexed { i, v -> LineChartPoint(i.toDouble(), v, "d$i") }
    private fun bars(values: List<Double>) = values.take(days.size).mapIndexed { i, v -> BarChartBar(days[i], v) }

    private fun animations() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        rule.mainClock.autoAdvance = false
    }

    private fun primaryPixels(tag: String): Int = count(rule.onNodeWithTag(tag).captureToImage()) { near(it, primary) }

    private fun near(a: Color, b: Color) = abs(a.red - b.red) < 0.02f && abs(a.green - b.green) < 0.02f && abs(a.blue - b.blue) < 0.02f

    private fun count(image: ImageBitmap, test: (Color) -> Boolean): Int {
        val map = image.toPixelMap()
        var n = 0
        for (y in 0 until map.height) for (x in 0 until map.width) if (test(map[x, y])) n++
        return n
    }

    private fun redAtCenter(tag: String): Float = rule.onNodeWithTag(tag).captureToImage().toPixelMap().let { it[it.width / 2, it.height / 2].red }

    private fun change(block: () -> Unit) {
        rule.runOnIdle { block(); Snapshot.sendApplyNotifications() }
    }

    @Test
    fun `a chart arrives once under one page motion and again under a fresh one`() {
        animations()
        var present by mutableStateOf(true)
        var page by mutableStateOf(0)
        rule.setContent {
            PuzzleBoxTheme(darkTheme = true) {
                primary = MaterialTheme.colorScheme.primary
                key(page) {
                    CompositionLocalProvider(LocalStatsMotion provides remember(page) { sharedMotion.getOrPut(page) { StatsMotion() } }) {
                        Column {
                            if (present) {
                                Box(Modifier.size(360.dp, 120.dp).background(stage).testTag("line")) {
                                    StatsLineChart(line(week), "Time", { "${it.toInt()}s" }, key = "line")
                                }
                                Box(Modifier.size(360.dp, 160.dp).background(stage).testTag("bars")) {
                                    StatsBarChart(bars(week), "Time by weekday", { "${it.toInt()}s" }, key = "bars")
                                }
                            }
                        }
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(16)
        val lineEarly = primaryPixels("line")
        val barsEarly = primaryPixels("bars")
        rule.mainClock.advanceTimeBy(1_500)
        val lineFull = primaryPixels("line")
        val barsFull = primaryPixels("bars")
        assertTrue("the line draws in from little: $lineEarly of $lineFull", lineEarly < lineFull / 3)
        assertTrue("the bars grow from little: $barsEarly of $barsFull", barsEarly < barsFull / 3)

        // Scrolled away and back: composed again under the same motion, it is there at once.
        change { present = false }
        rule.mainClock.advanceTimeBy(100)
        change { present = true }
        rule.mainClock.advanceTimeBy(16)
        assertTrue("no replay of the line: ${primaryPixels("line")} of $lineFull", primaryPixels("line") > lineFull * 0.9)
        assertTrue("no replay of the bars: ${primaryPixels("bars")} of $barsFull", primaryPixels("bars") > barsFull * 0.9)

        // A fresh page motion (a new page) plays them again.
        change { page = 1 }
        rule.mainClock.advanceTimeBy(16)
        assertTrue("replay of the line: ${primaryPixels("line")} of $lineFull", primaryPixels("line") < lineFull / 3)
        assertTrue("replay of the bars: ${primaryPixels("bars")} of $barsFull", primaryPixels("bars") < barsFull / 3)
    }

    private val sharedMotion = mutableMapOf<Int, StatsMotion>()

    @Test
    fun `a change shows the old chart at its first frame and never an empty one`() {
        animations()
        var longer by mutableStateOf(false)
        rule.setContent {
            PuzzleBoxTheme(darkTheme = true) {
                primary = MaterialTheme.colorScheme.primary
                CompositionLocalProvider(LocalStatsMotion provides remember { StatsMotion() }) {
                    Column {
                        Box(Modifier.size(360.dp, 120.dp).background(stage).testTag("line")) {
                            StatsLineChart(line(if (longer) month else week), "Time", { "${it.toInt()}s" }, key = "line")
                        }
                        Box(Modifier.size(360.dp, 160.dp).background(stage).testTag("bars")) {
                            StatsBarChart(bars(if (longer) month else week), "Time by weekday", { "${it.toInt()}s" }, key = "bars")
                        }
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(1_500)
        val lineBefore = primaryPixels("line")
        val barsBefore = primaryPixels("bars")
        change { longer = true }
        listOf(0L, 8L, 8L, 16L).forEach { step ->
            rule.mainClock.advanceTimeBy(step)
            assertTrue("the line is on screen: ${primaryPixels("line")} of $lineBefore", primaryPixels("line") > lineBefore * 0.5)
            assertTrue("the bars are on screen: ${primaryPixels("bars")} of $barsBefore", primaryPixels("bars") > barsBefore * 0.5)
        }
    }

    @Test
    fun `a block waits for the screen before it arrives`() {
        animations()
        var onScreen by mutableStateOf(false)
        rule.setContent {
            PuzzleBoxTheme(darkTheme = true) {
                CompositionLocalProvider(LocalStatsMotion provides remember { StatsMotion() }) {
                    Box(Modifier.size(100.dp).background(stage)) {
                        Box(Modifier.size(100.dp).statsArrival(0, "block", visible = { onScreen }).background(Color.White).testTag("block"))
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(1_000)
        assertEquals("hidden while off screen", 0f, redAtCenter("block"), 0.01f)
        change { onScreen = true }
        rule.mainClock.advanceTimeBy(1_000)
        assertEquals("shown once it is on screen", 1f, redAtCenter("block"), 0.01f)
    }

    @Test
    fun `only blocks that arrive together wait for a stagger`() {
        animations()
        fun redAfter(motion: StatsMotion): Float {
            val tag = "block"
            rule.setContent {
                PuzzleBoxTheme(darkTheme = true) {
                    CompositionLocalProvider(LocalStatsMotion provides motion) {
                        Box(Modifier.size(100.dp).background(stage)) {
                            Box(Modifier.size(100.dp).statsArrival(index = 3, key = "block").background(Color.White).testTag(tag))
                        }
                    }
                }
            }
            rule.mainClock.advanceTimeBy(100)
            return redAtCenter(tag)
        }
        val together = redAfter(StatsMotion())
        assertEquals("the fourth block of the first pass waits for its stagger: $together", 0f, together, 0.02f)
    }

    @Test
    fun `a block that arrives later starts at once`() {
        animations()
        val motion = StatsMotion().apply { firstPassDone = true }
        rule.setContent {
            PuzzleBoxTheme(darkTheme = true) {
                CompositionLocalProvider(LocalStatsMotion provides motion) {
                    Box(Modifier.size(100.dp).background(stage)) {
                        Box(Modifier.size(100.dp).statsArrival(index = 3, key = "block").background(Color.White).testTag("block"))
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(100)
        val later = redAtCenter("block")
        assertTrue("a later block is already fading in: $later", later > 0.05f)
    }
}
