package com.ivandsky.puzzlebox.app.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import com.ivandsky.puzzlebox.app.R
import com.ivandsky.puzzlebox.app.shortcut.ShortcutIntents
import com.ivandsky.puzzlebox.app.shortcut.ShortcutTarget

/**
 * The home-screen widget, and the only class that draws it: the streak line, Today's chips and the action pill, drawn once
 * per render from one [reader] read, in the layout the size the launcher gives it asks for ([WidgetStep]). Nothing in it
 * changes on its own; a redraw is [WidgetRefresher]. A chip opens its game and the pill opens what it says; a tap anywhere
 * else opens Today.
 */
class PuzzleBoxWidget(private val reader: WidgetStateReader) : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = reader.read()
        provideContent { WidgetContent(state) }
    }
}

@Composable
internal fun WidgetContent(state: WidgetState?) {
    val context = LocalContext.current
    val colors = remember(context) { WidgetColors(context) }
    val layout = WidgetLayout.of(LocalSize.current)
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(colors.of(R.color.widget_surface))
            .rounded()
            .padding(horizontal = layout.look.horizontalPadding, vertical = layout.look.verticalPadding)
            .clickable(actionStartActivity(ShortcutIntents.intent(context, ShortcutTarget.Today))),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.width(layout.contentWidth).height(layout.contentHeight)) {
            when (layout.step) {
                WidgetStep.Chips1, WidgetStep.Chips2 -> WidgetChipsStep(state, layout.look, colors)
                // The list layouts are not drawn yet: until they are, a tall widget shows the chips2 block, centred.
                WidgetStep.ListNarrow, WidgetStep.ListWide -> WidgetChipsStep(state, layout.look, colors)
            }
        }
    }
}

private fun GlanceModifier.rounded(): GlanceModifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        cornerRadius(android.R.dimen.system_app_widget_background_radius)
    } else {
        cornerRadius(16.dp)
    }
