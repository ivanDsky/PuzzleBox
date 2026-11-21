package com.ivandsky.puzzlebox.app.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Row
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.ivandsky.puzzlebox.app.R
import com.ivandsky.puzzlebox.model.GameId

/** The "New best" mark beside a solved time: [game]'s accent behind its deep shade of text. */
@Composable
internal fun WidgetNewBestChip(game: GameId, colors: WidgetColors) {
    Row(
        modifier = GlanceModifier
            .background(colors.of(WidgetArt.accent(game)))
            .cornerRadius(8.dp)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(
            text = LocalContext.current.resources.getString(R.string.widget_new_best),
            style = TextStyle(color = colors.of(WidgetArt.onAccent(game)), fontSize = 12.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
    }
}
