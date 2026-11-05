package com.ivandsky.puzzlebox.games.strandsui

import com.ivandsky.puzzlebox.designsystem.CelebrationMotion
import com.ivandsky.puzzlebox.designsystem.MotionPattern
import com.ivandsky.puzzlebox.designsystem.MotionTokens

/**
 * Strands motion (spec A33, research 03): the only place its numbers live; the board and
 * chrome animate with these, and each motion's reduced form is an instant state change.
 */
internal object StrandsMotion {
    /** A letter joining the trace dips to this scale and back (NYT `Pulse`). */
    const val PressScale = 0.85f
    val PressMs: Int = MotionTokens.DurationShort

    /** A rejected word shakes sideways (NYT `shake`, ease-in): amplitude, one cycle, cycles. */
    const val ShakePx = 5f
    const val ShakeMs = 200
    const val ShakeCycles = 1.5f

    /** Per-letter delays: theme settle, spangram sweep, hint order reveal. */
    const val SettleStaggerMs = 50
    const val SweepStaggerMs = 75
    const val HintRevealStaggerMs = 300

    val PressPulse = MotionPattern(
        name = "PressPulse",
        description = "A letter added to the trace dips to $PressScale and springs back.",
        enterDurationMillis = PressMs,
        enterEasing = MotionTokens.EasingStandard,
        exitDurationMillis = PressMs,
        exitEasing = MotionTokens.EasingStandard,
    )

    val RejectShake = MotionPattern(
        name = "RejectShake",
        description = "A rejected word shakes $ShakePx px side to side, $ShakeCycles cycles.",
        enterDurationMillis = ShakeMs,
        enterEasing = MotionTokens.EasingEmphasizedAccelerate,
        exitDurationMillis = ShakeMs,
        exitEasing = MotionTokens.EasingEmphasizedAccelerate,
    )

    val FoundSettle = MotionPattern(
        name = "FoundSettle",
        description = "A found theme word's letters bounce in turn, $SettleStaggerMs ms apart.",
        enterDurationMillis = CelebrationMotion.SettleMs,
        enterEasing = MotionTokens.EasingStandard,
        exitDurationMillis = MotionTokens.DurationShort,
        exitEasing = MotionTokens.EasingStandard,
    )

    val SpangramSweep = MotionPattern(
        name = "SpangramSweep",
        description = "The spangram lights edge to edge, $SweepStaggerMs ms per letter, capped in total.",
        enterDurationMillis = CelebrationMotion.SweepMaxMs,
        enterEasing = MotionTokens.EasingStandard,
        exitDurationMillis = MotionTokens.DurationShort,
        exitEasing = MotionTokens.EasingStandard,
    )

    val HintPulse = MotionPattern(
        name = "HintPulse",
        description = "Every outlined letter pulses once, together, so the order stays hidden.",
        enterDurationMillis = CelebrationMotion.HintPulseMs,
        enterEasing = MotionTokens.EasingStandard,
        exitDurationMillis = MotionTokens.DurationShort,
        exitEasing = MotionTokens.EasingStandard,
    )

    /** Letter n of the spangram starts at n × [SweepStaggerMs], squeezed so the last starts within the cap. */
    fun sweepDelayMs(index: Int, letters: Int): Int {
        if (letters <= 1) return 0
        val stagger = minOf(SweepStaggerMs, CelebrationMotion.SweepMaxMs / (letters - 1))
        return index * stagger
    }
}
