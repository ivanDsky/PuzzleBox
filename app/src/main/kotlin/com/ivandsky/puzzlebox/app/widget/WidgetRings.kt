package com.ivandsky.puzzlebox.app.widget

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.ivandsky.puzzlebox.app.R
import com.ivandsky.puzzlebox.app.shortcut.ShortcutIntents
import com.ivandsky.puzzlebox.app.shortcut.ShortcutTarget
import com.ivandsky.puzzlebox.domain.TodayStatus

/**
 * Today's rings in a row at [layout]'s size, each in one of Today's four looks, with the game's name under it when the layout
 * has room. A ring opens its game, whatever its status, the way a Today tile does. A null [rings] (the read failed) is five
 * bare tracks: no glyph, no name, no tap, nothing to claim about a game.
 */
@Composable
internal fun WidgetRings(rings: List<WidgetRing>?, layout: WidgetLayout, colors: WidgetColors) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (rings == null) {
            repeat(EMPTY_SLOTS) { EmptyRing(layout.ring, colors) }
        } else {
            rings.forEach { Ring(it, layout, colors) }
        }
    }
}

@Composable
private fun RowScope.EmptyRing(box: Dp, colors: WidgetColors) {
    Column(modifier = GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
        Layer(colors, R.drawable.widget_ring_track, R.color.widget_track, box)
    }
}

@Composable
private fun RowScope.Ring(ring: WidgetRing, layout: WidgetLayout, colors: WidgetColors) {
    val context = LocalContext.current
    val status = ring.status
    val statusWords = when (status) {
        is TodayStatus.Solved -> R.string.widget_ring_solved
        is TodayStatus.InProgress -> R.string.widget_ring_in_progress
        TodayStatus.NotStarted -> R.string.widget_ring_not_started
        TodayStatus.Offline -> R.string.widget_ring_offline
    }
    val accent = WidgetArt.accent(ring.game)
    val box = layout.ring
    Column(
        modifier = GlanceModifier.defaultWeight()
            .clickable(actionStartActivity(ShortcutIntents.intent(context, ShortcutTarget.Game(ring.game)))),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = GlanceModifier.size(box), contentAlignment = Alignment.Center) {
            when (status) {
                is TodayStatus.Solved -> Layer(colors, R.drawable.widget_ring_disc, accent, box)
                is TodayStatus.InProgress -> {
                    Layer(colors, R.drawable.widget_ring_track, R.color.widget_track, box)
                    Layer(colors, R.drawable.widget_ring_half, accent, box)
                }
                TodayStatus.NotStarted -> Layer(colors, R.drawable.widget_ring_track, R.color.widget_track, box)
                TodayStatus.Offline -> Layer(colors, R.drawable.widget_ring_dashed, R.color.widget_muted, box)
            }
            // The glyph carries the spoken label ("Zip, solved"); the layers under it are decoration.
            Layer(
                colors = colors,
                drawable = WidgetArt.glyph(ring.game),
                tint = when (status) {
                    is TodayStatus.Solved -> WidgetArt.onAccent(ring.game)
                    TodayStatus.Offline -> R.color.widget_muted
                    else -> accent
                },
                edge = layout.glyph,
                description = context.getString(R.string.widget_ring_description, ring.displayName, context.getString(statusWords)),
            )
        }
        if (layout.showNames) {
            Text(
                text = ring.displayName,
                modifier = GlanceModifier.padding(top = 4.dp),
                style = TextStyle(color = colors.of(R.color.widget_text), fontSize = layout.nameSize, textAlign = TextAlign.Center),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun Layer(colors: WidgetColors, @DrawableRes drawable: Int, @ColorRes tint: Int, edge: Dp, description: String? = null) {
    Image(
        provider = ImageProvider(drawable),
        contentDescription = description,
        modifier = GlanceModifier.size(edge),
        colorFilter = ColorFilter.tint(colors.of(tint)),
    )
}

private const val EMPTY_SLOTS = 5
