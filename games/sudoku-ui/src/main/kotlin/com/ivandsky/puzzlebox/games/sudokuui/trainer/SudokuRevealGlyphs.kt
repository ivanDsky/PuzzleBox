package com.ivandsky.puzzlebox.games.sudokuui.trainer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.ivandsky.puzzlebox.designsystem.spotlight.Spotlight
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightGlyphs
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightRole
import com.ivandsky.puzzlebox.games.sudokuui.GridDigits
import com.ivandsky.puzzlebox.games.sudokuui.hint.SudokuSpotlightGlyphs
import com.ivandsky.puzzlebox.games.sudokuui.hint.StyledSudokuGlyphs

/**
 * The digit look a snippet board measured ([GridDigits]: text measurer, digit style, cell pitch), published by the
 * board and read by [SudokuRevealGlyphs] when it draws. Null until the board has composed.
 *
 * The round asks the game for the reveal's Spotlight outside composition and keeps it for the snippet, while the digit
 * look needs the theme, the density and the board's size. Handing the look over through observable state means a
 * resize or a theme change reaches the reveal without asking for a new Spotlight.
 */
internal class SudokuDigitStyleHolder {
    var digits: GridDigits? by mutableStateOf(null)
}

/**
 * The reveal's digits: [texts] (the target digit, the struck digits) for the cells of [roles], drawn in the board's own
 * digit style once [holder] has it, so they sit where the board draws a digit. It is [StyledSudokuGlyphs], the glyphs
 * the hint's grid swaps in, built the first time a draw sees a look and again when the look changes; it draws nothing
 * before the board has published one. Equal by its parts (the holder by identity), so asking again gives an equal
 * Spotlight.
 */
internal data class SudokuRevealGlyphs(
    val texts: Map<Int, String>,
    val roles: Map<Int, SpotlightRole>,
    val holder: SudokuDigitStyleHolder,
) : SpotlightGlyphs {

    // The glyphs built for the look last drawn with; derived state, so not part of equality.
    private var built: Pair<GridDigits, StyledSudokuGlyphs>? = null

    override fun DrawScope.draw(cell: Int, role: SpotlightRole, size: Size, color: Color) {
        val digits = holder.digits ?: return
        val styled = built?.takeIf { it.first === digits }?.second
            ?: StyledSudokuGlyphs(texts, roles, digits.measurer, digits.style, digits.cellPx).also { built = digits to it }
        with(styled) { this@draw.draw(cell, role, size, color) }
    }
}

/** [this], the hint mapper's Spotlight for a step, with its glyphs drawn in the digit look [holder] receives. */
internal fun Spotlight.revealedIn(holder: SudokuDigitStyleHolder): Spotlight {
    val texts = (glyphs as SudokuSpotlightGlyphs).texts
    return copy(glyphs = SudokuRevealGlyphs(texts, roles, holder))
}
