package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.ui.graphics.Color
import com.ivandsky.puzzlebox.designsystem.BoardColors
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.tracePopScale
import com.ivandsky.puzzlebox.games.strandsui.StrandsCopy

/**
 * A letter in the live trace, over any base look: the blue selection token. A letter
 * joining it fills in over [MotionTokens.Trace.GrowDuration] ([StrandsCellFrame.lit] 0 → 1,
 * the connector growing in beside it) and pops 1 → [MotionTokens.Trace.PopScale] → 1, the
 * same beat as the line's head.
 */
internal object TraceOverlaySpec : StrandsCellSpec {
    override fun fill(colors: BoardColors): Color = colors.strandsSelectionFill

    override fun lip(colors: BoardColors): Color = colors.strandsSelectionLip

    override fun letter(colors: BoardColors): Color = colors.strandsTileText

    override val stateWord: String = StrandsCopy.SELECTED

    override fun motionMs(cue: StrandsMotionCue): Int = maxOf(MotionTokens.Trace.GrowDuration, MotionTokens.Trace.PopDuration)

    override fun frame(elapsedMs: Int, cue: StrandsMotionCue, reduceMotion: Boolean): StrandsCellFrame {
        if (reduceMotion || elapsedMs >= motionMs(cue)) return StrandsCellFrame.Rest
        val fill = MotionTokens.Trace.EasingOut.transform((elapsedMs.toFloat() / MotionTokens.Trace.GrowDuration).coerceAtMost(1f))
        return StrandsCellFrame(scale = tracePopScale(elapsedMs.toFloat() / MotionTokens.Trace.PopDuration), lit = fill)
    }
}
