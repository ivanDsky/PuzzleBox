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
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import com.ivandsky.puzzlebox.app.R
import com.ivandsky.puzzlebox.app.shortcut.ShortcutIntents
import com.ivandsky.puzzlebox.app.shortcut.ShortcutTarget

/**
 * One list row's frame: a thin track line above every row but the first, then the row, its [content] centred up and down. In
 * [WidgetLayout.fixedRows] the row is the layout's height; otherwise the five rows share the block's height, the way the real
 * widget, larger than the size the layout was made for, has more height than the layout counts on. The row opens its [ring]'s
 * game, whatever its status; with none (the read failed) it is bare and has no tap, so the widget's open-Today tap behind it
 * takes it.
 */
@Composable
internal fun ColumnScope.WidgetListRow(index: Int, ring: WidgetRing?, layout: WidgetLayout, colors: WidgetColors, content: @Composable RowScope.() -> Unit) {
    val context = LocalContext.current
    val divider = if (index > 0) DIVIDER else 0.dp
    Column(modifier = GlanceModifier.fillMaxWidth().let { if (layout.fixedRows) it else it.defaultWeight() }) {
        if (index > 0) Box(modifier = GlanceModifier.fillMaxWidth().height(DIVIDER).background(colors.of(R.color.widget_track))) {}
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .let { if (layout.fixedRows) it.height(layout.rowHeight - divider) else it.defaultWeight() }
                .let { if (ring == null) it else it.clickable(actionStartActivity(ShortcutIntents.intent(context, ShortcutTarget.Game(ring.game)))) },
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

private val DIVIDER = 1.dp
