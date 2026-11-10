package com.ivandsky.puzzlebox.games.strandsui.board

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

    override fun lip(colors: BoardColors): Color = colors.strandsSpangramLip

    override fun letter(colors: BoardColors): Color = colors.strandsTileText

    override val stateWord: String = StrandsCopy.STATE_SPANGRAM

    override fun trigger(cell: StrandsCell, found: FoundEvent?): StrandsMotionTrigger? =
        FoundThemeSpec.foundTrigger(cell, found)

    override fun motionMs(cue: StrandsMotionCue): Int = delayMs(cue) + StrandsMotion.FoundSettle.enterDurationMillis

    override fun frame(elapsedMs: Int, cue: StrandsMotionCue, reduceMotion: Boolean): StrandsCellFrame =
        FoundThemeSpec.settle(elapsedMs - delayMs(cue), reduceMotion, StrandsMotion.SpangramSweep.enterEasing)

    override fun DrawScope.decorate(colors: BoardColors, tokenRadius: Float, frame: StrandsCellFrame) {
        val width = tokenStroke(StrandsBoardSpec.SpangramRingWidth, tokenRadius)
        val half = tokenRadius - tokenStroke(StrandsBoardSpec.SpangramRingInset, tokenRadius) - width / 2
        drawTokenSquare(
            color = colors.strandsTileText,
            half = half * frame.ringScale,
            alpha = frame.lit,
            style = Stroke(width = width),
        )
    }

    private fun delayMs(cue: StrandsMotionCue): Int = StrandsMotion.sweepDelayMs(cue.order, cue.count)
}
