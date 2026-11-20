package com.ivandsky.puzzlebox.app.widget

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
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
import com.ivandsky.puzzlebox.domain.TodayStatus

/**
 * Today's rings in a row, each in one of Today's four looks, with the game's name under it when [large]. A null [rings] (the
 * read failed) is five bare tracks: no glyph, no name, nothing to claim about a game.
 */
@Composable
internal fun WidgetRings(rings: List<WidgetRing>?, large: Boolean, colors: WidgetColors, modifier: GlanceModifier = GlanceModifier) {
    val size = if (large) RingSize.Large else RingSize.Default
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (rings == null) {
            repeat(EMPTY_SLOTS) { EmptyRing(size, colors) }
        } else {
            rings.forEach { Ring(it, size, colors, showName = large) }
        }
    }
}

/** Ring box and glyph edge for each widget size. */
private enum class RingSize(val box: Dp, val glyph: Dp) {
    Default(box = 44.dp, glyph = 20.dp),
    Large(box = 56.dp, glyph = 26.dp),
}

@Composable
private fun RowScope.EmptyRing(size: RingSize, colors: WidgetColors) {
    Column(modifier = GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
        Layer(colors, R.drawable.widget_ring_track, R.color.widget_track, size.box)
    }
}

@Composable
private fun RowScope.Ring(ring: WidgetRing, size: RingSize, colors: WidgetColors, showName: Boolean) {
    val context = LocalContext.current
    val status = ring.status
    val statusWords = when (status) {
        is TodayStatus.Solved -> R.string.widget_ring_solved
        is TodayStatus.InProgress -> R.string.widget_ring_in_progress
        TodayStatus.NotStarted -> R.string.widget_ring_not_started
        TodayStatus.Offline -> R.string.widget_ring_offline
    }
    val accent = WidgetArt.accent(ring.game)
    Column(modifier = GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = GlanceModifier.size(size.box), contentAlignment = Alignment.Center) {
            when (status) {
                is TodayStatus.Solved -> Layer(colors, R.drawable.widget_ring_disc, accent, size.box)
                is TodayStatus.InProgress -> {
                    Layer(colors, R.drawable.widget_ring_track, R.color.widget_track, size.box)
                    Layer(colors, R.drawable.widget_ring_half, accent, size.box)
                }
                TodayStatus.NotStarted -> Layer(colors, R.drawable.widget_ring_track, R.color.widget_track, size.box)
                TodayStatus.Offline -> Layer(colors, R.drawable.widget_ring_dashed, R.color.widget_muted, size.box)
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
                edge = size.glyph,
                description = context.getString(R.string.widget_ring_description, ring.displayName, context.getString(statusWords)),
            )
        }
        if (showName) {
            Text(
                text = ring.displayName,
                modifier = GlanceModifier.padding(top = 4.dp),
                style = TextStyle(color = colors.of(R.color.widget_text), fontSize = 12.sp, textAlign = TextAlign.Center),
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
