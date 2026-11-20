package com.ivandsky.puzzlebox.app.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import com.ivandsky.puzzlebox.app.R
import com.ivandsky.puzzlebox.app.shortcut.ShortcutIntents
import com.ivandsky.puzzlebox.app.shortcut.ShortcutTarget

/**
 * The home-screen widget, and the only class that draws it: the streak line and Today's rings, drawn once per render from one
 * [reader] read. Nothing in it changes on its own; a redraw is [WidgetRefresher]. Tapping anywhere opens Today.
 */
class PuzzleBoxWidget(private val reader: WidgetStateReader) : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(DEFAULT_SIZE, LARGE_SIZE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = reader.read()
        provideContent { WidgetContent(state) }
    }
}

/** 4 by 2 cells, by the launcher's 70n - 30 rule. */
private val DEFAULT_SIZE = DpSize(250.dp, 110.dp)

/** About 5 by 3 cells and up: room for each game's name under its ring. */
private val LARGE_SIZE = DpSize(320.dp, 180.dp)

@Composable
internal fun WidgetContent(state: WidgetState?) {
    val context = LocalContext.current
    val colors = remember(context) { WidgetColors(context) }
    // Responsive hands the content one of the declared sizes, the largest that fits.
    val size = LocalSize.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(colors.of(R.color.widget_surface))
            .rounded()
            .padding(12.dp)
            .clickable(actionStartActivity(ShortcutIntents.intent(context, ShortcutTarget.Today))),
    ) {
        WidgetStreakLine(state?.streak, colors)
        WidgetRings(
            rings = state?.rings,
            large = size.width >= LARGE_SIZE.width && size.height >= LARGE_SIZE.height,
            colors = colors,
            modifier = GlanceModifier.defaultWeight(),
        )
    }
}

private fun GlanceModifier.rounded(): GlanceModifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        cornerRadius(android.R.dimen.system_app_widget_background_radius)
    } else {
        cornerRadius(16.dp)
    }
