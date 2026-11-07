package com.ivandsky.puzzlebox.gameui

import com.ivandsky.puzzlebox.designsystem.MotionTokens
import kotlin.math.exp

/** The live tip: [extent] of the step from the tail's centre toward the next cell along [direction]. */
data class TraceTip<D>(val kind: TipKind, val direction: D, val extent: Float) {
    companion object {
        /** 1:1 up to the follow distance, then a soft approach to the reach cap. */
        fun follow(push: Float): Float {
            val linear = MotionTokens.Trace.TipFollowCells
            val span = MotionTokens.Trace.TipReachCells - linear
            return if (push <= linear) push else linear + span * (1 - exp(-(push - linear) / span))
        }

        fun nudge(push: Float): Float =
            MotionTokens.Trace.TipNudgeCells * (1 - exp(-push / MotionTokens.Trace.TipNudgeRiseCells))
    }
}
