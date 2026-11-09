package com.ivandsky.puzzlebox.games.queensui.hint

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightGlyphs
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightRole
import com.ivandsky.puzzlebox.games.queensui.QueensBoardDefaults
import com.ivandsky.puzzlebox.games.queensui.drawQueen
import com.ivandsky.puzzlebox.games.queensui.drawX

/**
 * The shapes a Spotlight draws over the Queens board: the board's own crown and × through the shared drawing
 * functions, at the board's own size, so a culprit lands exactly on the glyph it marks. A crown for a target and for a
 * queen culprit, a × for a cross culprit ([culpritIsCross]) and for every excluded cell; evidence draws nothing
 * (the board's glyph already shows). Colour, alpha and the badge belong to the overlay.
 */
internal data class QueensSpotlightGlyphs(val culpritIsCross: Boolean) : SpotlightGlyphs {

    override fun DrawScope.draw(cell: Int, role: SpotlightRole, size: Size, color: Color) {
        when (role) {
            SpotlightRole.TARGET -> crown(color)
            SpotlightRole.CULPRIT -> if (culpritIsCross) cross(color) else crown(color)
            SpotlightRole.EXCLUDED -> cross(color)
            SpotlightRole.EVIDENCE -> Unit
        }
    }

    private fun DrawScope.crown(color: Color) =
        drawQueen(color, QueensBoardDefaults.queenHeightFraction, alpha = 1f)

    private fun DrawScope.cross(color: Color) =
        drawX(color, QueensBoardDefaults.crossWeight.toPx(), QueensBoardDefaults.crossLengthFraction)
}
