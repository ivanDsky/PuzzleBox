package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The hub's sizes in one place; the design system has no token of these values. */
internal object RanksMetrics {
    /** The icon in a hub menu or list row. */
    val IconSize: Dp = 20.dp

    /** The tablet game list's rows are at least this tall. */
    val ListItemHeight: Dp = 52.dp

    /** The title is at least this tall, to be a touch target. */
    val TitleMinHeight: Dp = 48.dp

    /** The place in a title menu row keeps this width, so the rows line up as the places change. */
    val PlaceMinWidth: Dp = 48.dp

    /** The world's ghost glyph: its size, how far it drifts as the boards scrub, and where it sits from the bottom end. */
    val GlyphSize: Dp = 330.dp
    val GlyphSizeWide: Dp = 480.dp
    val GlyphDrift: Dp = 130.dp
    val GlyphDriftWide: Dp = 200.dp
    val GlyphOutset: Dp = 50.dp
    val GlyphLift: Dp = 40.dp
    val GlyphOutsetWide: Dp = 30.dp
    val GlyphLiftWide: Dp = (-30).dp
}
