package com.ivandsky.puzzlebox.app.widget

import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.ivandsky.puzzlebox.app.R
import com.ivandsky.puzzlebox.app.shortcut.ShortcutIntents
import com.ivandsky.puzzlebox.app.shortcut.ShortcutTarget

/**
 * Today's five chips in a row, one equal share of the width each: the ring, then the rank, or the time when there is no
 * rank ([chipText]), and under it, in [look]s with a time line, the time. A chip opens its game, whatever its status, the way
 * a Today tile does. A null [rings] (the read failed) is five bare tracks: no glyph, no text, no tap.
 */
@Composable
internal fun WidgetChips(rings: List<WidgetRing>?, look: WidgetLayout.Look, colors: WidgetColors) {
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        if (rings == null) repeat(WidgetLayout.SLOTS) { Chip(null, look, colors) } else rings.forEach { Chip(it, look, colors) }
    }
}

@Composable
private fun RowScope.Chip(ring: WidgetRing?, look: WidgetLayout.Look, colors: WidgetColors) {
    val context = LocalContext.current
    val text = ring?.let { chipText(it, context.resources) } ?: ChipText("", "")
    val tap = ring?.let { actionStartActivity(ShortcutIntents.intent(context, ShortcutTarget.Game(it.game))) }
    Column(
        modifier = GlanceModifier.defaultWeight().let { if (tap == null) it else it.clickable(tap) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        WidgetRingArt(ring, look.ring, colors)
        Spacer(GlanceModifier.height(WidgetLayout.CHIP_TEXT_GAP))
        Text(
            text = text.first,
            style = TextStyle(color = colors.of(R.color.widget_text), fontSize = look.chipFirstText, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
            maxLines = 1,
        )
        if (look.timeLine) {
            Spacer(GlanceModifier.height(WidgetLayout.CHIP_TEXT_GAP))
            Text(
                text = text.second,
                style = TextStyle(color = colors.of(R.color.widget_muted), fontSize = look.chipSecondText, textAlign = TextAlign.Center),
                maxLines = 1,
            )
        }
    }
}
