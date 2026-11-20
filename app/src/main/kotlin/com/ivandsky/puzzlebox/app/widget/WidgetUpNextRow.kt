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
 * The wide filled Up next button: play triangle and one line of words, cut with an ellipsis before the button grows. Its tap
 * is its own, so it wins over the widget's open-Today tap behind it.
 */
@Composable
internal fun WidgetUpNextRow(upNext: WidgetUpNext, colors: WidgetColors, modifier: GlanceModifier = GlanceModifier) {
    val context = LocalContext.current
    val button = upNextButton(upNext, context)
    val onPrimary = colors.of(R.color.widget_on_primary)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(BUTTON_HEIGHT)
            .background(colors.of(R.color.widget_primary))
            .cornerRadius(BUTTON_HEIGHT / 2)
            .padding(horizontal = 16.dp)
            .semantics { contentDescription = button.label }
            .clickable(actionStartActivity(ShortcutIntents.intent(context, button.target))),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(R.drawable.widget_play),
            contentDescription = null,
            modifier = GlanceModifier.size(20.dp),
            colorFilter = ColorFilter.tint(onPrimary),
        )
        Text(
            text = button.label,
            modifier = GlanceModifier.padding(start = 6.dp),
            style = TextStyle(color = onPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
    }
}

/** The 48dp touch-target floor; also the pill's height. */
private val BUTTON_HEIGHT = 48.dp
