package com.ivandsky.puzzlebox.games.strandsui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.BoardColors
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraft
import com.ivandsky.puzzlebox.games.strandsui.board.StrandsBoardSpec

/** The gap between a letter token's edge and the end ring round it. */
private val RingGap = 3.dp

/** The end ring's stroke. */
private val RingWidth = 2.dp

/** The first and last cells of the path at [index]: the ends a drag shortens or extends ([StrandsEndDrag]). */
internal fun StrandsDraft.endsOf(index: Int?): Set<Int> =
    index?.let { paths.getOrNull(it) }?.cells?.let { setOf(it.first(), it.last()) }.orEmpty()

/**
 * The path at [index]'s own tint, the colour its line draws in: the spangram's, else its theme
 * word's, every second theme word taking the alt as the board alternates them.
 */
internal fun BoardColors.tintOf(board: StrandsDraft, index: Int): Color = when {
    index == board.spangram -> strandsSpangramConnector
    (0 until index).count { it != board.spangram } % 2 == 1 -> strandsThemeAltConnector
    else -> strandsThemeConnector
}

/** A solid ring just outside the cell's letter token, in [color]: the mark of a draggable end. */
internal fun DrawScope.drawEndRing(color: Color) {
    val width = RingWidth.toPx()
    val radius = minOf(size.width, size.height) * StrandsBoardSpec.TokenFraction / 2 + RingGap.toPx() + width / 2
    drawCircle(color = color, radius = radius, style = Stroke(width))
}
