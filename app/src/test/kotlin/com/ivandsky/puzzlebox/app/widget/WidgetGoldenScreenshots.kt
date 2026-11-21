package com.ivandsky.puzzlebox.app.widget

import android.app.Activity
import android.graphics.Color
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import com.github.takahirom.roborazzi.captureRoboImage
import com.ivandsky.puzzlebox.app.DrainTestApplication
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The home widget's layouts (#1939, #1940, #1960): the icon-only chips1, chips2 and the lists listNarrow and listWide over every
 * state, drawn through the same RemoteViews path the launcher uses, on a wallpaper-grey backdrop so the corners show. A launcher
 * composes at a declared size ([WidgetSizes]) and lays the views out at the widget's real size, so each capture names both: the
 * 3 cell widths (200dp), the tablet's 4 by 1 to 4 by 3 (about 470 by 100, 200 and 290 to 330), and the bigger widgets, where the
 * block fills its size and the rows, rings and text step up. Light and dark. Recorded with `-Proborazzi.record=true` into
 * `snapshots/widget/` (untracked review captures).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w800dp-h640dp-xhdpi")
class WidgetGoldenScreenshotsLight : WidgetGoldenScreenshots(suffix = "")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w800dp-h640dp-night-xhdpi")
class WidgetGoldenScreenshotsDark : WidgetGoldenScreenshots(suffix = "-dark")

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
abstract class WidgetGoldenScreenshots(private val suffix: String) {
    private val states = mapOf(
        "default" to WidgetSamples.default,
        "mixed" to WidgetSamples.mixed,
        "empty" to WidgetSamples.empty,
        "longest" to WidgetSamples.longest,
        "offline" to WidgetSamples.offline,
        "signedout" to WidgetSamples.signedOut,
        "rankmissing" to WidgetSamples.rankMissing,
    )

    @Test fun `chips1 over every state, at 3 cells, 4 cells and on the tablet`() {
        for ((name, state) in states) {
            capture(state, DpSize(200.dp, 56.dp), "chips1-3wide-$name")
            capture(state, DpSize(250.dp, 90.dp), "chips1-4wide-$name", real = DpSize(276.dp, 100.dp))
            capture(state, DpSize(340.dp, 90.dp), "chips1-tablet-$name", real = DpSize(470.dp, 100.dp))
        }
    }

    @Test fun `chips2 over every state, at 3 cells, 4 cells and on the tablet`() {
        for ((name, state) in states) {
            capture(state, DpSize(200.dp, 160.dp), "chips2-3wide-$name", real = DpSize(200.dp, 200.dp))
            capture(state, DpSize(250.dp, 160.dp), "chips2-$name", real = DpSize(276.dp, 220.dp))
            capture(state, DpSize(420.dp, 200.dp), "chips2-tablet-$name", real = DpSize(470.dp, 200.dp))
        }
    }

    @Test fun `chips2 filling a widget that is wider, taller or only a little taller than its size`() {
        capture(WidgetSamples.default, DpSize(420.dp, 200.dp), "chips2-wide", real = DpSize(620.dp, 220.dp))
        capture(WidgetSamples.default, DpSize(250.dp, 160.dp), "chips2-tall", real = DpSize(250.dp, 400.dp))
        capture(WidgetSamples.mixed, DpSize(200.dp, 160.dp), "chips2-3wide-tall-mixed", real = DpSize(200.dp, 400.dp))
        capture(WidgetSamples.mixed, DpSize(250.dp, 160.dp), "chips2-tablet-170-mixed", real = DpSize(470.dp, 170.dp))
    }

    @Test fun `listNarrow and listWide over every state, at the tablet's 4 by 3`() {
        for ((name, state) in states) {
            capture(state, DpSize(420.dp, 320.dp), "listnarrow-$name", real = DpSize(470.dp, 330.dp))
            capture(state, DpSize(480.dp, 320.dp), "listwide-$name", real = DpSize(485.dp, 330.dp))
        }
    }

    @Test fun `the 4 by 3 at its shortest, 290dp, and the narrow list at 3 cells, short and tall`() {
        capture(WidgetSamples.mixed, DpSize(420.dp, 280.dp), "listnarrow-4x3-290-mixed", real = DpSize(470.dp, 290.dp))
        capture(WidgetSamples.mixed, DpSize(480.dp, 280.dp), "listwide-4x3-290-mixed", real = DpSize(485.dp, 290.dp))
        capture(WidgetSamples.mixed, DpSize(340.dp, 280.dp), "listnarrow-3wide-mixed", real = DpSize(350.dp, 300.dp))
        capture(WidgetSamples.mixed, DpSize(340.dp, 280.dp), "listnarrow-3wide-330-mixed", real = DpSize(350.dp, 330.dp))
        capture(WidgetSamples.mixed, DpSize(340.dp, 360.dp), "listnarrow-3wide-tall-mixed", real = DpSize(350.dp, 420.dp))
    }

    @Test fun `the lists growing to fill a taller widget, then one size up, then another`() {
        capture(WidgetSamples.mixed, DpSize(420.dp, 360.dp), "listnarrow-grow-mixed", real = DpSize(470.dp, 430.dp))
        capture(WidgetSamples.mixed, DpSize(480.dp, 360.dp), "listwide-grow-mixed", real = DpSize(485.dp, 430.dp))
        capture(WidgetSamples.longest, DpSize(480.dp, 360.dp), "listwide-grow-longest", real = DpSize(485.dp, 430.dp))
        for ((name, state) in states) {
            capture(state, DpSize(560.dp, 400.dp), "listwide-up1-$name", real = DpSize(590.dp, 440.dp))
            capture(state, DpSize(700.dp, 500.dp), "listwide-up2-$name", real = DpSize(706.dp, 540.dp))
        }
    }

    // Roborazzi captures a View only inside an Activity window, so the widget is hosted in one, at its exact pixel size.
    // The launcher composes at the matched bucket ([size]) and lays the views out at the real size, so [real] can differ.
    private fun capture(state: WidgetState?, size: DpSize, name: String, real: DpSize = size) {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val density = activity.resources.displayMetrics.density
        val margin = (BACKDROP_MARGIN * density).toInt()
        val width = (real.width.value * density).toInt()
        val height = (real.height.value * density).toInt()
        val views = runBlocking { GlanceRemoteViews().compose(activity, size) { WidgetContent(state) }.remoteViews }
        val backdrop = FrameLayout(activity).apply {
            setBackgroundColor(Color.parseColor(if (suffix.isEmpty()) "#B8C0CC" else "#2A2F3A"))
            addView(views.apply(activity, this), FrameLayout.LayoutParams(width, height).apply { setMargins(margin, margin, margin, margin) })
        }
        activity.setContentView(backdrop, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        backdrop.captureRoboImage(out("$name$suffix"))
    }

    private fun out(name: String): String =
        File(checkNotNull(System.getProperty("user.dir")))
            .resolve("snapshots/widget/$name.png")
            .canonicalFile
            .also { checkNotNull(it.parentFile).mkdirs() }
            .absolutePath

    private companion object {
        const val BACKDROP_MARGIN = 16
    }
}
