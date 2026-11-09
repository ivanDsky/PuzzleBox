package com.ivandsky.puzzlebox.games.sudokuui.hint

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import com.ivandsky.puzzlebox.designsystem.spotlight.Spotlight
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightGlyphs
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightRole

/**
 * The digit text a Spotlight draws over the grid. [texts] maps a cell to its text: the digit for a target or
 * culprit, the struck digits for an excluded cell. The mapper knows no fonts, so this draws nothing itself: the
 * grid swaps in [StyledSudokuGlyphs] through [withDigitStyle] before the overlay sees it.
 */
internal data class SudokuSpotlightGlyphs(val texts: Map<Int, String>) : SpotlightGlyphs {
    override fun DrawScope.draw(cell: Int, role: SpotlightRole, size: Size, color: Color) = Unit
}

/** [this] with its Sudoku digits measured in the grid's [digit] style for a cell [cellPx] wide; other glyphs pass through. */
internal fun Spotlight.withDigitStyle(measurer: TextMeasurer, digit: TextStyle, cellPx: Float): Spotlight {
    val texts = (glyphs as? SudokuSpotlightGlyphs)?.texts ?: return this
    return copy(glyphs = StyledSudokuGlyphs(texts, roles, measurer, digit, cellPx))
}

/**
 * [texts] in the board's own digit font and metrics ([digit] is the grid's cell digit style), so a glyph sits exactly
 * where the board draws that digit. A culprit is the same digit heavier and 10 % bigger (prototype 07 `errGlyph`), so
 * it covers the board's red digit under it. Each lit cell is measured once here, for its role in [roles]; drawing
 * only places the measured text, scaled by the overlay's pulse (the size it is given over [cellPx]).
 */
internal data class StyledSudokuGlyphs(
    val texts: Map<Int, String>,
    val roles: Map<Int, SpotlightRole>,
    val measurer: TextMeasurer,
    val digit: TextStyle,
    val cellPx: Float,
) : SpotlightGlyphs {

    // Derived from the constructor values, so not part of equality.
    private val layouts: Map<Int, TextLayoutResult> = buildMap {
        for ((cell, text) in texts) {
            val style = styleFor(roles[cell] ?: continue, text) ?: continue
            put(cell, measurer.measure(text, style))
        }
    }

    private fun styleFor(role: SpotlightRole, text: String): TextStyle? = when (role) {
        SpotlightRole.TARGET -> digit
        SpotlightRole.CULPRIT -> digit.scaled(CULPRIT_SCALE).copy(fontWeight = FontWeight.ExtraBold)
        // One struck digit reads big, several read small, like the prototype's 3x2 candidates.
        SpotlightRole.EXCLUDED -> if (text.length == 1) digit else digit.scaled(CANDIDATES_SCALE)
        SpotlightRole.EVIDENCE -> null
    }

    override fun DrawScope.draw(cell: Int, role: SpotlightRole, size: Size, color: Color) {
        if (role != roles[cell]) return
        val layout = layouts[cell] ?: return
        val width = layout.size.width.toFloat()
        val left = (size.width - width) / 2
        scale(size.width / cellPx, pivot = center) {
            drawText(layout, color, topLeft = Offset(left, (size.height - layout.size.height) / 2))
            if (role == SpotlightRole.EXCLUDED) {
                val stroke = layout.layoutInput.style.fontSize.toPx() * STRIKE_WIDTH
                drawLine(color, Offset(left, center.y), Offset(left + width, center.y), strokeWidth = stroke)
            }
        }
    }

    private companion object {
        const val CULPRIT_SCALE = 1.1f
        /** Several candidates at half the digit: 0.3 of the cell against the digit's 0.6. */
        const val CANDIDATES_SCALE = 0.5f
        const val STRIKE_WIDTH = 0.08f
    }
}

private fun TextStyle.scaled(factor: Float): TextStyle = copy(fontSize = fontSize * factor, lineHeight = lineHeight * factor)
