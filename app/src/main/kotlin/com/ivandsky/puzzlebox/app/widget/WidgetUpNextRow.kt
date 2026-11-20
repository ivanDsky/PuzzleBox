package com.ivandsky.puzzlebox.app.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.ivandsky.puzzlebox.app.R
import com.ivandsky.puzzlebox.app.shortcut.ShortcutIntents

/**
 * The filled Up next button: play triangle (nothing is left to play when all is done) and one line of words, cut with an
 * ellipsis before the button grows. Full width when [layout] is compact, else a pill that hugs its words, centred by the
 * widget. Its tap is its own, so it wins over the widget's open-Today tap behind it.
 */
@Composable
internal fun WidgetUpNextRow(upNext: WidgetUpNext, layout: WidgetLayout, colors: WidgetColors) {
    val context = LocalContext.current
    val button = upNextButton(upNext, context)
    val onPrimary = colors.of(R.color.widget_on_primary)
    val playing = upNext != WidgetUpNext.Done
    val width = if (layout.compact) GlanceModifier.fillMaxWidth() else GlanceModifier
    Row(
        modifier = width
            .height(WidgetLayout.BUTTON_HEIGHT)
            .background(colors.of(R.color.widget_primary))
            .cornerRadius(WidgetLayout.BUTTON_HEIGHT / 2)
            .padding(horizontal = if (layout.compact) 16.dp else 24.dp)
            .semantics { contentDescription = button.label }
            .clickable(actionStartActivity(ShortcutIntents.intent(context, button.target))),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (playing) {
            Image(
                provider = ImageProvider(R.drawable.widget_play),
                contentDescription = null,
                modifier = GlanceModifier.size(20.dp),
                colorFilter = ColorFilter.tint(onPrimary),
            )
        }
        Text(
            text = button.label,
            modifier = if (playing) GlanceModifier.padding(start = 6.dp) else GlanceModifier,
            style = TextStyle(color = onPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
    }
}
