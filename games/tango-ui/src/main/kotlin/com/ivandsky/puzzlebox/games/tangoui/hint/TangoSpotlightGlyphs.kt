package com.ivandsky.puzzlebox.games.tangoui.hint

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightGlyphs
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightRole
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightTokens
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tangoui.MoonPaths
import com.ivandsky.puzzlebox.games.tangoui.TangoBoardSpec
import com.ivandsky.puzzlebox.games.tangoui.drawMoon
import com.ivandsky.puzzlebox.games.tangoui.drawSun

/**
 * The shapes a Spotlight draws over the Tango board. [values] maps a cell to the sun or moon the step puts there: the
 * target value for a deduction or reveal, the wrong entry for a mistake. The overlay owns colour, alpha, sequence and
 * the minus badge; this only draws shapes, the board's own sun and moon through the shared shape functions.
 *
 * An [SpotlightRole.EXCLUDED] cell is a count-squeeze what-if cell, which holds no value, so its × needs none.
 */
internal data class TangoSpotlightGlyphs(val n: Int, val values: Map<Int, TangoCell>) : SpotlightGlyphs {

    // Scratch reused by every frame's draw; not part of equality.
    private val moonPaths = MoonPaths()

    override fun DrawScope.draw(cell: Int, role: SpotlightRole, size: Size, color: Color) {
        when (role) {
            SpotlightRole.TARGET -> drawValue(cell, color)
            // The board's own glyph sits under a culprit at 1.0x, its anti-aliased edge a rim of board colour: the
            // 110 % glyph covers the outer edge and a 1.0x outline 2 hairlines wide the rest, the crescent's inner arc
            // included (the Queens recipe).
            SpotlightRole.CULPRIT -> {
                scale(CULPRIT_SCALE) { drawValue(cell, color) }
                drawValue(cell, color, rim = SpotlightTokens.HairlineWidth.toPx())
            }
            SpotlightRole.EXCLUDED -> drawCross(color)
            SpotlightRole.EVIDENCE -> Unit
        }
    }

    private fun DrawScope.drawValue(cell: Int, color: Color, rim: Float = 0f) {
        when (values[cell]) {
            TangoCell.SUN -> drawSun(color, n, rim)
            TangoCell.MOON -> drawMoon(color, n, moonPaths, rim)
            null -> Unit
        }
    }

    /** Two crossing strokes centred in the scope, inside the glyph box the board's sun and moon fill. */
    private fun DrawScope.drawCross(color: Color) {
        val glyph = size.minDimension * TangoBoardSpec.glyphSizeFraction(n)
        val center = Offset(size.width / 2f, size.height / 2f)
        val arm = glyph * CROSS_ARM
        val stroke = glyph * CROSS_STROKE
        drawLine(color, center + Offset(-arm, -arm), center + Offset(arm, arm), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, center + Offset(-arm, arm), center + Offset(arm, -arm), strokeWidth = stroke, cap = StrokeCap.Round)
    }

    private companion object {
        /** Half the × extent and its stroke width, fractions of the glyph box. */
        const val CROSS_ARM = 0.32f
        const val CROSS_STROKE = 0.12f

        /** The culprit's size over the board glyph's, as Sudoku's and Queens' culprits. */
        const val CULPRIT_SCALE = 1.1f
    }
}
