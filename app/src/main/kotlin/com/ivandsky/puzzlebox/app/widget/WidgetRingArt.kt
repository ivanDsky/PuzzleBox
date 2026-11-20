package com.ivandsky.puzzlebox.app.widget

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.size
import com.ivandsky.puzzlebox.app.R
import com.ivandsky.puzzlebox.domain.TodayStatus

/**
 * One game's ring, [edge] wide, in one of Today's four looks, with the game's glyph at half the edge. The glyph carries the
 * spoken label; the layers under it are decoration. A null [ring] (the read failed) is a bare track: no glyph, nothing to
 * claim about a game.
 */
@Composable
internal fun WidgetRingArt(ring: WidgetRing?, edge: Dp, colors: WidgetColors) {
    if (ring == null) {
        Layer(colors, R.drawable.widget_ring_track, R.color.widget_track, edge)
        return
    }
    val accent = WidgetArt.accent(ring.game)
    val status = ring.status
    Box(modifier = GlanceModifier.size(edge), contentAlignment = Alignment.Center) {
        when (status) {
            is TodayStatus.Solved -> Layer(colors, R.drawable.widget_ring_disc, accent, edge)
            is TodayStatus.InProgress -> {
                Layer(colors, R.drawable.widget_ring_track, R.color.widget_track, edge)
                Layer(colors, R.drawable.widget_ring_half, accent, edge)
            }
            TodayStatus.NotStarted -> Layer(colors, R.drawable.widget_ring_track, R.color.widget_track, edge)
            TodayStatus.Offline -> Layer(colors, R.drawable.widget_ring_dashed, R.color.widget_muted, edge)
        }
        Layer(
            colors = colors,
            drawable = WidgetArt.glyph(ring.game),
            tint = when (status) {
                is TodayStatus.Solved -> WidgetArt.onAccent(ring.game)
                TodayStatus.Offline -> R.color.widget_muted
                else -> accent
            },
            edge = edge / 2,
            description = ringDescription(ring, LocalContext.current.resources),
        )
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
