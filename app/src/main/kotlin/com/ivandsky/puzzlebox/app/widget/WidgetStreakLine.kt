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
 * The flame, the streak text and, only when a freeze count is known, the snowflake with it, at the step [layout] gives it: top-left
 * when compact, centred over the rings above that. A null [streak] (the read failed) keeps the line's height and draws nothing,
 * so the rings do not jump.
 */
@Composable
internal fun WidgetStreakLine(streak: WidgetStreak?, layout: WidgetLayout, colors: WidgetColors) {
    val resources = LocalContext.current.resources
    val sizes = layout.streak
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(sizes.line),
        horizontalAlignment = if (layout.compact) Alignment.Start else Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (streak == null) return@Row
        Image(
            provider = ImageProvider(R.drawable.widget_flame),
            contentDescription = null,
            modifier = GlanceModifier.size(sizes.flame),
            colorFilter = ColorFilter.tint(colors.of(R.color.widget_primary)),
        )
        Text(
            text = resources.getQuantityString(R.plurals.widget_streak, streak.days, streak.days, streak.best),
            modifier = GlanceModifier.padding(start = 8.dp),
            style = TextStyle(color = colors.of(R.color.widget_text), fontSize = sizes.text, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        streak.freezesHeld?.let { held ->
            // A spacer, not padding: Glance pads inside a view's size, so padding would shrink the image.
            Spacer(modifier = GlanceModifier.width(12.dp))
            Image(
                provider = ImageProvider(R.drawable.widget_snowflake),
                contentDescription = null,
                modifier = GlanceModifier.size(sizes.snowflake),
                colorFilter = ColorFilter.tint(colors.of(R.color.widget_accent_blue)),
            )
            Text(
                text = held.toString(),
                modifier = GlanceModifier.padding(start = 4.dp),
                style = TextStyle(color = colors.of(R.color.widget_text), fontSize = sizes.freezeText, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
        }
    }
}
