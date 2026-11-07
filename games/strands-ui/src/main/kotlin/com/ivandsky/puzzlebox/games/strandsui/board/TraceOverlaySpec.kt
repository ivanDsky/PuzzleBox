package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.ui.graphics.Color
import com.ivandsky.puzzlebox.designsystem.BoardColors
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.games.strandsui.StrandsCopy

/**
 * A letter in the live trace, over any base look: the blue selection token. A letter
 * joining it fills in over [MotionTokens.Trace.GrowDuration] ([StrandsCellFrame.lit] 0 → 1,
 * the connector growing in beside it) and pops 1 → [MotionTokens.Trace.PopScale] → 1, the
 * same beat as the line's head.
 */
internal object TraceOverlaySpec : StrandsCellSpec {
    private val PopAt = floatArrayOf(0f, MotionTokens.Trace.PopRiseFraction, 1f)
    private val PopScale = floatArrayOf(1f, MotionTokens.Trace.PopScale, 1f)

    override fun fill(colors: BoardColors): Color = colors.strandsSelectionFill

    override fun letter(colors: BoardColors): Color = colors.strandsTileText

    override val stateWord: String = StrandsCopy.SELECTED

    override fun motionMs(cue: StrandsMotionCue): Int = maxOf(MotionTokens.Trace.GrowDuration, MotionTokens.Trace.PopDuration)

    override fun frame(elapsedMs: Int, cue: StrandsMotionCue, reduceMotion: Boolean): StrandsCellFrame {
        if (reduceMotion || elapsedMs >= motionMs(cue)) return StrandsCellFrame.Rest
        val fill = MotionTokens.Trace.EasingOut.transform((elapsedMs.toFloat() / MotionTokens.Trace.GrowDuration).coerceAtMost(1f))
        val pop = elapsedMs.toFloat() / MotionTokens.Trace.PopDuration
        val scale = if (pop < MotionTokens.Trace.PopRiseFraction) {
            keyframes(pop, PopAt, PopScale, MotionTokens.Trace.EasingOut)
        } else {
            keyframes(pop, PopAt, PopScale, MotionTokens.EasingStandard)
        }
        return StrandsCellFrame(scale = scale, lit = fill)
    }
}
