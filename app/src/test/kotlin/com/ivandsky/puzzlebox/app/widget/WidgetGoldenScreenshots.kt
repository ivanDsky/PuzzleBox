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
 * The home widget's chip layouts (#1939): chips1 (4 by 1) and chips2 (4 by 2) over every state, drawn through the same
 * RemoteViews path the launcher uses, on a wallpaper-grey backdrop so the corners show. Also chips2 at a wide and a tall size,
 * where the block is capped and centred. Light and dark. Recorded with `-Proborazzi.record=true` into `snapshots/widget/`
 * (untracked review captures).
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

    @Test fun `chips1 and chips2 over every state`() {
        for ((name, state) in states) {
            capture(state, CHIPS1, "chips1-$name")
            capture(state, CHIPS2, "chips2-$name")
        }
    }

    @Test fun `the capped block is centred in a wide and a tall widget`() {
        capture(WidgetSamples.default, DpSize(620.dp, 220.dp), "chips2-wide")
        capture(WidgetSamples.mixed, DpSize(620.dp, 430.dp), "tall-mixed")
    }

    // Roborazzi captures a View only inside an Activity window, so the widget is hosted in one, at its exact pixel size.
    private fun capture(state: WidgetState?, size: DpSize, name: String) {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val density = activity.resources.displayMetrics.density
        val margin = (BACKDROP_MARGIN * density).toInt()
        val width = (size.width.value * density).toInt()
        val height = (size.height.value * density).toInt()
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
        val CHIPS1 = DpSize(276.dp, 102.dp)
        val CHIPS2 = DpSize(276.dp, 220.dp)
        const val BACKDROP_MARGIN = 16
    }
}
