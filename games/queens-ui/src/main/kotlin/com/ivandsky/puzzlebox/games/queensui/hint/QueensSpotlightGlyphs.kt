package com.ivandsky.puzzlebox.games.queensui.hint

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightGlyphs
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightRole
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightTokens
import com.ivandsky.puzzlebox.games.queensui.QueensBoardDefaults
import com.ivandsky.puzzlebox.games.queensui.drawQueen
import com.ivandsky.puzzlebox.games.queensui.drawX

/**
 * The shapes a Spotlight draws over the Queens board: the board's own crown and × through the shared drawing
 * functions. A crown for a target and for a queen culprit, a × for a cross culprit ([culpritIsCross]) and for every
 * excluded cell; evidence draws nothing (the board's glyph already shows). Colour, alpha and the badge belong to the
 * overlay.
 *
 * A culprit is drawn [CULPRIT_SCALE] larger than the board's glyph about the cell centre, plus a stroke of the same
 * shape, so it fully covers the glyph it marks and no board-ink rim survives at its edge. The crown is not star-shaped
 * (a gap lies between its body and its band), so scaling alone leaves the band's top edge showing; the stroke, twice
 * the hairline wide, closes that gap.
 */
internal data class QueensSpotlightGlyphs(val culpritIsCross: Boolean) : SpotlightGlyphs {

    // Scratch reused by every frame's draw; not part of equality.
    private val crownPath = Path()

    override fun DrawScope.draw(cell: Int, role: SpotlightRole, size: Size, color: Color) {
        when (role) {
            SpotlightRole.TARGET -> crown(color, scale = 1f)
            SpotlightRole.CULPRIT -> if (culpritIsCross) cross(color, CULPRIT_SCALE, hairline = true) else crownCovering(color)
            SpotlightRole.EXCLUDED -> cross(color, scale = 1f, hairline = false)
            SpotlightRole.EVIDENCE -> Unit
        }
    }

    private fun DrawScope.crown(color: Color, scale: Float) =
        drawQueen(color, QueensBoardDefaults.queenHeightFraction * scale, alpha = 1f, path = crownPath)

    private fun DrawScope.crownCovering(color: Color) {
        crown(color, CULPRIT_SCALE)
        drawQueen(
            color,
            QueensBoardDefaults.queenHeightFraction * CULPRIT_SCALE,
            alpha = 1f,
            path = crownPath,
            style = Stroke(SpotlightTokens.HairlineWidth.toPx() * 2f, join = StrokeJoin.Round),
        )
    }

    private fun DrawScope.cross(color: Color, scale: Float, hairline: Boolean) {
        val rim = if (hairline) SpotlightTokens.HairlineWidth.toPx() else 0f
        drawX(color, QueensBoardDefaults.crossWeight.toPx() + rim, QueensBoardDefaults.crossLengthFraction, scale = scale)
    }

    private companion object {
        /** How much larger than the board's glyph a culprit is drawn. */
        const val CULPRIT_SCALE = 1.1f
    }
}
