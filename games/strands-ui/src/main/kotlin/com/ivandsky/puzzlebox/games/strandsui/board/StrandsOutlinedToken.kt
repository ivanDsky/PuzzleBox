package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.ivandsky.puzzlebox.designsystem.BoardColors

/**
 * One token of the outlined look: its fill (none for an idle or hinted letter), then its outline. An idle letter
 * wears the thin `outline` stroke; joining the trace turns it into the thick selection stroke over the tint as the
 * fill comes in ([join]); a found letter keeps no stroke, its selection stroke fading out as it lights ([frame]).
 * A submitted word ([ended]) keeps its selection look as it fades, or goes red on an error. The strokes sit inside
 * the token's edge, so a letter keeps the 78% square it is laid out as. The spec's own decoration (the hint's dashed
 * ring, the spangram's ring) is the caller's.
 */
internal fun DrawScope.drawOutlinedToken(
    spec: StrandsCellSpec,
    colors: BoardColors,
    altTheme: Boolean,
    traced: Boolean,
    frame: StrandsCellFrame,
    join: StrandsCellFrame,
    ended: TraceEndFrame?,
) {
    val half = size.minDimension / 2
    val selectionWidth = StrandsOutlinedSpec.SelectionStroke.toPx()
    if (ended != null) {
        drawTokenSquare(ended.fill(colors), half)
        if (!ended.error) drawInsetOutline(colors.strandsFlatSelection, half, selectionWidth)
        return
    }
    val base = if (altTheme) colors.strandsThemeAltFill else spec.fill(colors)
    tokenColor(base, TraceOverlaySpec.fill(colors), traced, frame, join)?.let { drawTokenSquare(it, half) }
    val idle = spec === IdleSpec
    val idleWidth = StrandsOutlinedSpec.IdleStroke.toPx()
    when {
        traced && idle -> drawInsetOutline(
            lerp(colors.strandsFlatOutline, colors.strandsFlatSelection, join.lit),
            half,
            idleWidth + (selectionWidth - idleWidth) * join.lit,
        )
        traced -> drawInsetOutline(colors.strandsFlatSelection.withAlpha(join.lit), half, selectionWidth)
        idle -> drawInsetOutline(colors.strandsFlatOutline, half, idleWidth)
        frame.lit < 1f -> drawInsetOutline(colors.strandsFlatSelection.withAlpha(1f - frame.lit), half, selectionWidth)
    }
}

/** What a letter on the outlined look reads in: ink on the tints, black on the gold spangram, white-on-red when refused. */
internal fun outlinedLetterColor(spec: StrandsCellSpec, colors: BoardColors, traced: Boolean, ended: TraceEndFrame?): Color = when {
    ended != null -> if (ended.error) ended.letter(colors) else colors.boardInk
    traced -> colors.boardInk
    spec === FoundSpangramSpec -> colors.strandsTileText
    else -> colors.boardInk
}

private fun Color.withAlpha(alpha: Float): Color = copy(alpha = this.alpha * alpha)

/** A [width] stroke whose outer edge is the token's own, of half-side [half]: the stroke's centre line runs half a width in. */
private fun DrawScope.drawInsetOutline(color: Color, half: Float, width: Float) {
    val reach = half - width / 2
    drawRoundRect(
        color = color,
        topLeft = Offset(center.x - reach, center.y - reach),
        size = Size(reach * 2, reach * 2),
        cornerRadius = CornerRadius((tokenCorner(half * 2) - width / 2).coerceAtLeast(0f)),
        style = Stroke(width),
    )
}
