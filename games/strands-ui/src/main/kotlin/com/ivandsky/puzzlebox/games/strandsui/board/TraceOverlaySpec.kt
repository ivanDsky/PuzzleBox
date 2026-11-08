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

/**
 * [TraceOverlaySpec] under the Snap flag: the fill and the letter colour ramp over
 * [MotionTokens.Trace.SnapGrowDuration], matching the line's grow, and the token never scales.
 */
internal object SnapTraceOverlaySpec : StrandsCellSpec by TraceOverlaySpec {
    override fun motionMs(cue: StrandsMotionCue): Int = MotionTokens.Trace.SnapGrowDuration

    override fun frame(elapsedMs: Int, cue: StrandsMotionCue, reduceMotion: Boolean): StrandsCellFrame {
        if (reduceMotion || elapsedMs >= motionMs(cue)) return StrandsCellFrame.Rest
        val progress = (elapsedMs.toFloat() / MotionTokens.Trace.SnapGrowDuration).coerceIn(0f, 1f)
        return StrandsCellFrame(lit = MotionTokens.Trace.EasingOut.transform(progress))
    }
}

/** The live trace's token look: [SnapTraceOverlaySpec] when [snap] (flag SnapTrace), else [TraceOverlaySpec]. */
internal fun traceOverlaySpec(snap: Boolean): StrandsCellSpec = if (snap) SnapTraceOverlaySpec else TraceOverlaySpec
