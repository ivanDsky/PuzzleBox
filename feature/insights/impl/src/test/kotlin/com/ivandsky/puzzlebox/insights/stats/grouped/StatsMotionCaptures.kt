package com.ivandsky.puzzlebox.insights.stats.grouped

import android.content.Context
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.chart.BarChartBar
import com.ivandsky.puzzlebox.designsystem.chart.LineChartPoint
import com.ivandsky.puzzlebox.insights.domain.stats.StatsRange
import com.ivandsky.puzzlebox.insights.stats.StatsBody
import com.ivandsky.puzzlebox.insights.stats.StatsFixtures
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Review frames of the stats motion on the test clock, in the dark theme: the arrival (line draws in, bars grow,
 * numbers count up), the Since change (line and bars morph, frames from the change on at +0, +8, +16 and +32 ms and
 * then to its end), the grouped page's block arrival (fade and rise) and a change under reduced motion, to
 * `snapshots/stats-motion/<name>.png`
 * (untracked, ADR-0033; written when run with `-Proborazzi.record=true`). The assertions on what moves are in
 * [StatsChartMotionTest] and [StatsArrivalReducedTest]; this is for the eye.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class StatsMotionCaptures {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val week = listOf(95.0, 88.0, 102.0, 76.0, 81.0, 70.0, 64.0)
    private val month = listOf(110.0, 104.0, 99.0, 93.0, 96.0, 90.0, 84.0, 79.0, 75.0)
    private val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    private fun line(values: List<Double>) = values.mapIndexed { i, v -> LineChartPoint(i.toDouble(), v, "d$i") }
    private fun bars(values: List<Double>) = values.take(days.size).mapIndexed { i, v -> BarChartBar(days[i], v) }

    private fun animations(scale: Float) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
        rule.mainClock.autoAdvance = false
    }

    private fun frame(name: String) {
        val file = File(checkNotNull(System.getProperty("user.dir"))).resolve("snapshots/stats-motion/$name.png").canonicalFile
        checkNotNull(file.parentFile).mkdirs()
        captureScreenRoboImage(file.absolutePath)
    }

    private fun setCharts(longer: () -> Boolean) = rule.setContent {
        PuzzleBoxTheme(darkTheme = true) {
            Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
                CompositionLocalProvider(LocalStatsMotion provides StatsMotion()) {
                    Column(Modifier.padding(16.dp)) {
                        StatsCountUpText(if (longer()) "1,284" else "312", key = "count", style = MaterialTheme.typography.headlineMedium)
                        StatsLineChart(line(if (longer()) month else week), "Average solve time", { "${it.toInt()}s" }, key = "line")
                        StatsBarChart(bars(if (longer()) month else week), "Average solve time by weekday", { "${it.toInt()}s" }, key = "bars")
                    }
                }
            }
        }
    }

    @Test
    fun arrivalAndMorphFrames() {
        animations(1f)
        var longer by mutableStateOf(false)
        setCharts { longer }
        frame("arrival-1-start")
        rule.mainClock.advanceTimeBy(80)
        frame("arrival-1b-early")
        rule.mainClock.advanceTimeBy(120)
        frame("arrival-2-mid")
        rule.mainClock.advanceTimeBy(1_500)
        frame("arrival-3-end")
        rule.runOnIdle { longer = true; Snapshot.sendApplyNotifications() }
        var at = 0L
        listOf(0L, 8L, 16L, 32L).forEach { ms ->
            rule.mainClock.advanceTimeBy(ms - at)
            at = ms
            frame("morph-0-plus%02d".format(ms))
        }
        rule.mainClock.advanceTimeBy(68)
        frame("morph-1b-early")
        rule.mainClock.advanceTimeBy(70)
        frame("morph-2-mid")
        rule.mainClock.advanceTimeBy(1_000)
        frame("morph-3-end")
    }

    @Test
    fun reducedMotionChangeFrames() {
        animations(0f)
        var longer by mutableStateOf(false)
        setCharts { longer }
        rule.mainClock.advanceTimeBy(100)
        frame("reduced-1-before")
        rule.runOnIdle { longer = true; Snapshot.sendApplyNotifications() }
        rule.mainClock.advanceTimeBy(16)
        frame("reduced-2-after-16ms")
        rule.mainClock.advanceTimeBy(300)
        frame("reduced-3-after-316ms")
    }

    @Test
    fun groupedPageArrivalFrames() {
        animations(1f)
        val state = StatsFixtures.readyWithLines(range = StatsRange.Week, game = null).copy(filters = true, since = StatsFixtures.since)
        rule.setContent {
            PuzzleBoxTheme(darkTheme = true) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    StatsGroupedList(
                        sections = (state.body as StatsBody.Ready).sections,
                        game = null,
                        gameName = StatsFixtures::gameName,
                        onOpenSolve = {},
                    )
                }
            }
        }
        frame("page-1-start")
        rule.mainClock.advanceTimeBy(100)
        frame("page-2-early")
        rule.mainClock.advanceTimeBy(150)
        frame("page-3-mid")
        rule.mainClock.advanceTimeBy(1_500)
        frame("page-4-end")
    }
}
