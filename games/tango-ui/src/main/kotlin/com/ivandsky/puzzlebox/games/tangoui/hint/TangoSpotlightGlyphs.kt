package com.ivandsky.puzzlebox.games.tangoui.hint

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightGlyphs
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightRole
import com.ivandsky.puzzlebox.games.tango.TangoCell
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

    override fun DrawScope.draw(cell: Int, role: SpotlightRole, size: Size, color: Color) {
        when (role) {
            SpotlightRole.TARGET, SpotlightRole.CULPRIT -> when (values[cell]) {
                TangoCell.SUN -> drawSun(color, n)
                TangoCell.MOON -> drawMoon(color, n)
                null -> Unit
            }
            SpotlightRole.EXCLUDED -> drawCross(color)
            SpotlightRole.EVIDENCE -> Unit
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
    }
}
