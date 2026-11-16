package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.ivandsky.puzzlebox.designsystem.BoardColors
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.tracePopScale
import com.ivandsky.puzzlebox.games.strandsui.StrandsCopy

/**
 * A letter in the live trace, over any base look: the selection token. A letter
 * joining it fills in over [MotionTokens.Trace.GrowDuration] ([StrandsCellFrame.lit] 0 → 1,
 * the connector growing in beside it) and pops 1 → [MotionTokens.Trace.PopScale] → 1, the
 * same beat as the line's head. On a readable board it also wears a ring ([decorate]).
 */
internal object TraceOverlaySpec : StrandsCellSpec {
    override fun fill(colors: BoardColors): Color = colors.strandsSelectionFill

    override fun lip(colors: BoardColors): Color = colors.strandsSelectionLip

    override fun letter(colors: BoardColors): Color = colors.strandsTileText

    override val stateWord: String = StrandsCopy.SELECTED

    /**
     * The selection ring in the shared target colour, drawn only where the board's geometry asks
     * for a ring: it sits on the bare board a gap outside the token and comes in with the fill.
     */
    override fun DrawScope.decorate(colors: BoardColors, tokenRadius: Float, frame: StrandsCellFrame) {
        val width = tokenStroke(StrandsBoardSpec.SelectionRingWidth, tokenRadius)
        val half = tokenRadius + tokenStroke(StrandsBoardSpec.SelectionRingGap, tokenRadius) + width / 2
        drawRoundRect(
            color = colors.boardTarget,
            topLeft = Offset(center.x - half, center.y - half),
            size = Size(half * 2, half * 2),
            cornerRadius = CornerRadius(tokenCorner(half * 2)),
            alpha = frame.lit,
            style = Stroke(width),
        )
    }

    override fun motionMs(cue: StrandsMotionCue): Int = maxOf(MotionTokens.Trace.GrowDuration, MotionTokens.Trace.PopDuration)

    override fun frame(elapsedMs: Int, cue: StrandsMotionCue, reduceMotion: Boolean): StrandsCellFrame {
        if (reduceMotion || elapsedMs >= motionMs(cue)) return StrandsCellFrame.Rest
        val fill = MotionTokens.Trace.EasingOut.transform((elapsedMs.toFloat() / MotionTokens.Trace.GrowDuration).coerceAtMost(1f))
        return StrandsCellFrame(scale = tracePopScale(elapsedMs.toFloat() / MotionTokens.Trace.PopDuration), lit = fill)
    }
}
