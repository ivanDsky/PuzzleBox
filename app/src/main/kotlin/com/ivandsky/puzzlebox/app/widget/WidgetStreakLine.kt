package com.ivandsky.puzzlebox.app.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.ivandsky.puzzlebox.app.R

/**
 * The streak line of a chip row: the flame and the streak ("658 days", or "658 days in a row" in a long [look]), the
 * snowflake with its count when a freeze count is known, and [done] ("5 of 5 done") at the right when the read is known. A
 * null [streak] (the read failed) keeps the line's height and draws nothing, so the chips do not jump.
 */
@Composable
internal fun WidgetStreakLine(streak: WidgetStreak?, done: String?, look: WidgetLayout.ChipLook, colors: WidgetColors) {
    val resources = LocalContext.current.resources
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(look.flame + 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (streak == null) return@Row
        Row(modifier = GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
            Image(
                provider = ImageProvider(R.drawable.widget_flame),
                contentDescription = null,
                modifier = GlanceModifier.size(look.flame),
                colorFilter = ColorFilter.tint(colors.of(R.color.widget_primary)),
            )
            val words = if (look.longStreak) R.plurals.widget_streak_long else R.plurals.widget_streak_short
            Text(
                text = resources.getQuantityString(words, streak.days, streak.days),
                modifier = GlanceModifier.padding(start = 6.dp),
                style = TextStyle(color = colors.of(R.color.widget_text), fontSize = look.streakText, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            streak.freezesHeld?.let { held ->
                // A spacer, not padding: Glance pads inside a view's size, so padding would shrink the image.
                Spacer(modifier = GlanceModifier.width(12.dp))
                Image(
                    provider = ImageProvider(R.drawable.widget_snowflake),
                    contentDescription = resources.getQuantityString(R.plurals.widget_freezes, held, held),
                    modifier = GlanceModifier.size(WidgetLayout.SNOWFLAKE),
                    colorFilter = ColorFilter.tint(colors.of(R.color.widget_accent_blue)),
                )
                Text(
                    text = held.toString(),
                    modifier = GlanceModifier.padding(start = 4.dp),
                    style = TextStyle(color = colors.of(R.color.widget_muted), fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
            }
        }
        if (done != null) {
            Text(
                text = done,
                modifier = GlanceModifier.padding(start = 8.dp),
                style = TextStyle(color = colors.of(R.color.widget_text), fontSize = 13.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
        }
    }
}
