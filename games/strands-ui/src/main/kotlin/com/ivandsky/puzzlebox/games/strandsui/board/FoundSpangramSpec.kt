package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.ivandsky.puzzlebox.designsystem.BoardColors
import com.ivandsky.puzzlebox.games.strands.render.StrandsCell
import com.ivandsky.puzzlebox.games.strandsui.FoundEvent
import com.ivandsky.puzzlebox.games.strandsui.StrandsCopy
import com.ivandsky.puzzlebox.games.strandsui.StrandsMotion

/**
 * The found spangram: the gold token with an inset ring, its non-colour cue beside the
 * thicker connector (A32), and the sweep: the settle bounce run edge to edge along the
 * word, [StrandsMotion.SweepStaggerMs] apart, squeezed so the last letter starts within
 * [StrandsMotion.SpangramSweep]'s length however long the word ([StrandsMotion.sweepDelayMs]).
 */
internal object FoundSpangramSpec : StrandsCellSpec {

    override fun fill(colors: BoardColors): Color = colors.strandsSpangramFill

    override fun letter(colors: BoardColors): Color = colors.strandsTileText

    override val stateWord: String = StrandsCopy.STATE_SPANGRAM

    override fun trigger(cell: StrandsCell, found: FoundEvent?): StrandsMotionTrigger? =
        FoundThemeSpec.foundTrigger(cell, found)

    override fun motionMs(cue: StrandsMotionCue): Int = delayMs(cue) + StrandsMotion.FoundSettle.enterDurationMillis

    override fun frame(elapsedMs: Int, cue: StrandsMotionCue, reduceMotion: Boolean): StrandsCellFrame =
        FoundThemeSpec.settle(elapsedMs - delayMs(cue), reduceMotion, StrandsMotion.SpangramSweep.enterEasing)

    override fun DrawScope.decorate(colors: BoardColors, tokenRadius: Float, frame: StrandsCellFrame) {
        val width = StrandsBoardSpec.SpangramRingWidth.toPx()
        val radius = tokenRadius - StrandsBoardSpec.SpangramRingInset.toPx() - width / 2
        drawCircle(
            color = colors.strandsTileText,
            radius = radius * frame.ringScale,
            center = Offset(size.width / 2, size.height / 2),
            alpha = frame.lit,
            style = Stroke(width = width),
        )
    }

    private fun delayMs(cue: StrandsMotionCue): Int = StrandsMotion.sweepDelayMs(cue.order, cue.count)
}
