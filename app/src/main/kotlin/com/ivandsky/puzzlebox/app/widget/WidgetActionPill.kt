package com.ivandsky.puzzlebox.app.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
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
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.ivandsky.puzzlebox.app.R
import com.ivandsky.puzzlebox.app.shortcut.ShortcutIntents

/**
 * The 48dp action pill: one line of [button]'s words, cut with an ellipsis, in the green fill, or in the quiet track fill when
 * [UpNextButton.tonal]. It spans the width, or with [fullWidth] false hugs its words, as in the wide list's footer. Its tap is
 * its own, so it wins over the widget's open-Today tap behind it.
 */
@Composable
internal fun WidgetActionPill(button: UpNextButton, colors: WidgetColors, fullWidth: Boolean = true) {
    val context = LocalContext.current
    val fill = if (button.tonal) R.color.widget_track else R.color.widget_primary
    val text = if (button.tonal) R.color.widget_text else R.color.widget_on_primary
    Row(
        modifier = GlanceModifier
            .let { if (fullWidth) it.fillMaxWidth() else it }
            .height(WidgetLayout.PILL_HEIGHT)
            .background(colors.of(fill))
            .cornerRadius(WidgetLayout.PILL_HEIGHT / 2)
            .padding(horizontal = if (fullWidth) 16.dp else 20.dp)
            .semantics { contentDescription = button.label }
            .clickable(actionStartActivity(ShortcutIntents.intent(context, button.target))),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = button.label,
            style = TextStyle(color = colors.of(text), fontSize = 16.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
    }
}
