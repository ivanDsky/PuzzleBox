package com.ivandsky.puzzlebox.app.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
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
 * The wide list's header, on one line: the flame and "658 days in a row", the freezes held with their snowflake, the best
 * streak, and [done] ("5 of 5 done") at the right when the read is known; its text steps up with the look's
 * [WidgetLayout.Look.scale]. A null [streak] (the read failed) keeps the line's height and draws nothing, so the rows do not
 * jump.
 */
@Composable
internal fun WidgetWideHeader(streak: WidgetStreak?, done: String?, look: WidgetLayout.Look, colors: WidgetColors) {
    val resources = LocalContext.current.resources
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(look.streakHeight),
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
            Text(
                text = resources.getQuantityString(R.plurals.widget_streak_long, streak.days, streak.days),
                modifier = GlanceModifier.padding(start = 6.dp),
                style = TextStyle(color = colors.of(R.color.widget_text), fontSize = look.streakText, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            streak.freezesHeld?.let { held ->
                Spacer(GlanceModifier.width(GROUP_GAP))
                Image(
                    provider = ImageProvider(R.drawable.widget_snowflake),
                    contentDescription = null,
                    modifier = GlanceModifier.size(look.size(WidgetLayout.SNOWFLAKE)),
                    colorFilter = ColorFilter.tint(colors.of(R.color.widget_accent_blue)),
                )
                Text(
                    text = resources.getQuantityString(R.plurals.widget_freezes, held, held),
                    modifier = GlanceModifier.padding(start = 4.dp),
                    style = TextStyle(color = colors.of(R.color.widget_muted), fontSize = look.text(HEADER_TEXT)),
                    maxLines = 1,
                )
            }
            Spacer(GlanceModifier.width(GROUP_GAP))
            Text(
                text = resources.getString(R.string.widget_best, streak.best.toString()),
                style = TextStyle(color = colors.of(R.color.widget_muted), fontSize = look.text(HEADER_TEXT)),
                maxLines = 1,
            )
        }
        if (done != null) {
            Text(
                text = done,
                modifier = GlanceModifier.padding(start = 8.dp),
                style = TextStyle(color = colors.of(R.color.widget_text), fontSize = look.text(HEADER_TEXT), fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
        }
    }
}

private val GROUP_GAP = 14.dp
private const val HEADER_TEXT = 13
