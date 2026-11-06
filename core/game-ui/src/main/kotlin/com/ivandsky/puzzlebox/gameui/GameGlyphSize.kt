package com.ivandsky.puzzlebox.gameui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The sizes a [GameGlyph] renders at (identity §2.5): [box] is the well, [glyph] the
 * drawn icon, [corner] the well's radius. [Inline] has no well (box = glyph); [Chip]'s
 * well is a circle (hub card handoff §Done-today chip).
 */
enum class GameGlyphSize(val box: Dp, val glyph: Dp, val corner: Dp) {
    Inline(16.dp, 16.dp, 0.dp),
    Badge(20.dp, 14.dp, 6.dp),
    Chip(32.dp, 18.dp, 16.dp),
    Tile(40.dp, 24.dp, 12.dp),
    Hero(64.dp, 36.dp, 20.dp),
}
