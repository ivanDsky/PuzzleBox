package com.ivandsky.puzzlebox.designsystem.spotlight

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * The game supplies only the SHAPE of its native glyph; colour, alpha, sequence and badge are the overlay's.
 * Implementations must be data classes or objects: [Spotlight] is compared by equality, so a lambda or a
 * class with identity equality would make every press look like a new Spotlight.
 */
fun interface SpotlightGlyphs {
    /**
     * Draw the glyph of [cell] for [role] filling [size] in [color] (TARGET: ghost ink at SpotlightTokens alpha;
     * CULPRIT: error colour, drawn over the board's own glyph; EXCLUDED: the game's × at half alpha).
     * Return without drawing when the cell has no glyph.
     */
    fun DrawScope.draw(cell: Int, role: SpotlightRole, size: Size, color: Color)
}
