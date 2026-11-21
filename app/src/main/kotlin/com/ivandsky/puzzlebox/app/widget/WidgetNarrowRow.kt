package com.ivandsky.puzzlebox.app.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.ivandsky.puzzlebox.app.R

/**
 * A row of the narrow list: the ring, the game's name over its status line (the time, with the New best mark, or where the
 * game stands), and at the right the rank over the best time. A null [ring] is a bare track and nothing else.
 */
@Composable
internal fun ColumnScope.WidgetNarrowRow(index: Int, ring: WidgetRing?, layout: WidgetLayout, colors: WidgetColors) {
    val text = ring?.let { rowText(it, LocalContext.current.resources) }
    WidgetListRow(index, ring, layout, colors) {
        WidgetRingArt(ring, layout.look.ring, colors)
        if (ring == null || text == null) return@WidgetListRow
        Spacer(GlanceModifier.width(COLUMN_GAP))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = ring.displayName,
                style = TextStyle(color = colors.of(R.color.widget_text), fontSize = 15.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            if (text.solved) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = text.status,
                        style = TextStyle(color = colors.of(R.color.widget_text), fontSize = 15.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                    )
                    if (ring.newBest) {
                        Spacer(GlanceModifier.width(6.dp))
                        WidgetNewBestChip(ring.game, colors)
                    }
                }
            } else {
                Text(text = text.status, style = TextStyle(color = colors.of(R.color.widget_muted), fontSize = 13.sp), maxLines = 1)
            }
        }
        Spacer(GlanceModifier.width(COLUMN_GAP))
        Column(horizontalAlignment = Alignment.End) {
            val rankColor = if (text.rankMuted) R.color.widget_muted else R.color.widget_text
            Text(text = text.rank, style = TextStyle(color = colors.of(rankColor), fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            Text(text = text.best, style = TextStyle(color = colors.of(R.color.widget_muted), fontSize = 13.sp), maxLines = 1)
        }
    }
}

private val COLUMN_GAP = 10.dp
