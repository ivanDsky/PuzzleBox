package com.ivandsky.puzzlebox.app.widget

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.ivandsky.puzzlebox.app.R

/**
 * A one-line row of the wide list, in fixed columns: the ring, the name, the rank (left out when [showRank] is false, so a
 * signed-out player does not see an empty column), the time, which takes what is left, and the best. The New best mark
 * only follows a short time, so a long one never pushes it into the best column; Glance cannot measure text to know better.
 * A null [ring] is a bare track and nothing else.
 */
@Composable
internal fun WidgetWideRow(index: Int, ring: WidgetRing?, showRank: Boolean, layout: WidgetLayout, colors: WidgetColors) {
    val resources = LocalContext.current.resources
    val text = ring?.let { rowText(it, resources) }
    WidgetListRow(index, ring, layout, colors) {
        WidgetRingArt(ring, layout.look.ring, colors)
        if (ring == null || text == null) return@WidgetListRow
        Spacer(GlanceModifier.width(COLUMN_GAP))
        Text(
            text = ring.displayName,
            modifier = GlanceModifier.width(NAME_WIDTH),
            style = TextStyle(color = colors.of(R.color.widget_text), fontSize = 16.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        if (showRank) {
            Spacer(GlanceModifier.width(COLUMN_GAP))
            val rankColor = if (text.rankMuted) R.color.widget_muted else R.color.widget_text
            Text(
                text = wideRank(ring, text.rank, resources),
                modifier = GlanceModifier.width(RANK_WIDTH),
                style = TextStyle(color = colors.of(rankColor), fontSize = 16.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.width(COLUMN_GAP))
        Row(modifier = GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
            if (text.solved) {
                Text(
                    text = text.status,
                    style = TextStyle(color = colors.of(R.color.widget_text), fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                if (ring.newBest && text.status.length <= NEW_BEST_MAX_TIME_CHARS) {
                    Spacer(GlanceModifier.width(8.dp))
                    WidgetNewBestChip(ring.game, colors)
                }
            } else {
                Text(text = text.status, style = TextStyle(color = colors.of(R.color.widget_muted), fontSize = 14.sp), maxLines = 1)
            }
        }
        Spacer(GlanceModifier.width(COLUMN_GAP))
        Text(
            text = text.best,
            modifier = GlanceModifier.width(BEST_WIDTH),
            style = TextStyle(color = colors.of(R.color.widget_muted), fontSize = 14.sp, textAlign = TextAlign.End),
            maxLines = 1,
        )
    }
}

/** The 70dp rank column fits "#12 of 40" (9 characters); a longer place ("#112 of 120") falls back to the chip's "#112". */
private fun wideRank(ring: WidgetRing, full: String, resources: Resources): String {
    val place = ring.rank as? WidgetRank.Ranked
    return if (place != null && full.length > MAX_RANK_CHARS) resources.getString(R.string.widget_chip_rank, place.position) else full
}

private val COLUMN_GAP = 10.dp
private val NAME_WIDTH = 116.dp
private val RANK_WIDTH = 70.dp
private val BEST_WIDTH = 84.dp
private const val NEW_BEST_MAX_TIME_CHARS = 5
private const val MAX_RANK_CHARS = 9
