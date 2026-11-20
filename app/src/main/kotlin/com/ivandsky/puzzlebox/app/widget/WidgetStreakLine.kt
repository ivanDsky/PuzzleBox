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
 * The flame, the streak text and, only when a freeze count is known, the snowflake with it. A null [streak] (the read failed)
 * keeps the line's height and draws nothing, so the rings do not jump.
 */
@Composable
internal fun WidgetStreakLine(streak: WidgetStreak?, colors: WidgetColors) {
    val resources = LocalContext.current.resources
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(LINE_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (streak == null) return@Row
        Image(
            provider = ImageProvider(R.drawable.widget_flame),
            contentDescription = null,
            modifier = GlanceModifier.size(20.dp),
            colorFilter = ColorFilter.tint(colors.of(R.color.widget_primary)),
        )
        Text(
            text = resources.getQuantityString(R.plurals.widget_streak, streak.days, streak.days, streak.best),
            modifier = GlanceModifier.padding(start = 8.dp),
            style = TextStyle(color = colors.of(R.color.widget_text), fontSize = 16.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        streak.freezesHeld?.let { held ->
            // A spacer, not padding: Glance pads inside a view's size, so padding would shrink the 16dp image.
            Spacer(modifier = GlanceModifier.width(12.dp))
            Image(
                provider = ImageProvider(R.drawable.widget_snowflake),
                contentDescription = null,
                modifier = GlanceModifier.size(16.dp),
                colorFilter = ColorFilter.tint(colors.of(R.color.widget_accent_blue)),
            )
            Text(
                text = held.toString(),
                modifier = GlanceModifier.padding(start = 4.dp),
                style = TextStyle(color = colors.of(R.color.widget_text), fontSize = 14.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
        }
    }
}

private val LINE_HEIGHT = 24.dp
