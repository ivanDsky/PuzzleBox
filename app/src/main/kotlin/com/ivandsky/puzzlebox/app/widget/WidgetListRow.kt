package com.ivandsky.puzzlebox.app.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import com.ivandsky.puzzlebox.app.R
import com.ivandsky.puzzlebox.app.shortcut.ShortcutIntents
import com.ivandsky.puzzlebox.app.shortcut.ShortcutTarget

/**
 * One list row's frame: a thin track line above every row but the first, then the row at the layout's height, its [content]
 * centred up and down. The row opens its [ring]'s game, whatever its status; with none (the read failed) it is bare and has no
 * tap, so the widget's open-Today tap behind it takes it.
 */
@Composable
internal fun WidgetListRow(index: Int, ring: WidgetRing?, layout: WidgetLayout, colors: WidgetColors, content: @Composable RowScope.() -> Unit) {
    val context = LocalContext.current
    val divider = if (index > 0) DIVIDER else 0.dp
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        if (index > 0) Box(modifier = GlanceModifier.fillMaxWidth().height(DIVIDER).background(colors.of(R.color.widget_track))) {}
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(layout.rowHeight - divider)
                .let { if (ring == null) it else it.clickable(actionStartActivity(ShortcutIntents.intent(context, ShortcutTarget.Game(ring.game)))) },
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

private val DIVIDER = 1.dp
