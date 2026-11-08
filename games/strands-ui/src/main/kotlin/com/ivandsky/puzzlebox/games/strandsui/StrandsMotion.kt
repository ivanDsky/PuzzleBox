package com.ivandsky.puzzlebox.games.strandsui

import com.ivandsky.puzzlebox.designsystem.CelebrationMotion
import com.ivandsky.puzzlebox.designsystem.MotionPattern
import com.ivandsky.puzzlebox.designsystem.MotionTokens

/**
 * Strands motion (spec A33, research 03): the only place its numbers live; the board and
 * chrome animate with these, and each motion's reduced form is an instant state change.
 */
internal object StrandsMotion {
    /** The hint pulse's dip (NYT `Pulse`). A letter joining the trace pops instead, on [MotionTokens.Trace]. */
    const val PressScale = 0.85f

    /** Per-letter delays: theme settle, spangram sweep, hint order reveal. */
    const val SettleStaggerMs = 50
    const val SweepStaggerMs = 75
    const val HintRevealStaggerMs = 300

    /** NYT `Pulser bounce` on a found letter: 1 → dip → overshoot → rebound → 1, keyframes evenly spaced over [CelebrationMotion.SettleMs]. */
    const val SettleDipScale = 0.62f
    const val SettleOvershootScale = 1.13f
    const val SettleReboundScale = 0.95f

    /** NYT `pulseHintOutline`'s live first second: dip to [PressScale] at [HintDipAt], swell to [HintSwellScale] at [HintSwellAt], rest by the end. */
    const val HintSwellScale = 1.1f
    const val HintDipAt = 0.15f
    const val HintSwellAt = 0.38f

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

    /** The refused path's shake: [ErrorShakeMs] of the error's total, the dissolve takes the rest. */
    val ErrorShakeMs: Int = Math.round(MotionTokens.ErrorDuration * MotionTokens.ErrorShakeShare)

    /** The shake's stops in units of [MotionTokens.ErrorShake], evenly spaced and Linear between them. */
    val ShakeStops = floatArrayOf(0f, -1f, 1f, -0.66f, 0.66f, -0.33f, 0.33f, 0f)

    /** A set-aside word's traced path holds neutral this long before it dissolves, by kind. */
    const val BonusHoldMs = 60
    const val ShortHoldMs = 200
    const val RepeatHoldMs = 260

    /** Reduced motion holds the neutral colour at least this long (a bonus word a little longer), then clears. */
    const val ReducedHoldMinMs = 300
    const val ReducedBonusHoldMs = 400

    /** The Hint button lights this long after its last dot has filled, then swells: 1 → .95 → 1.09 → 1, evenly spaced. */
    const val LightDelayMs = 80
    const val SwellMs = 500
    val SwellStops = floatArrayOf(1f, 0.95f, 1.09f, 1f)

    /** The count badge pops when its number changes: .5 → 1.25 → 1 over [MotionTokens.DurationMedium], eased out. */
    const val BadgePopFrom = 0.5f
    const val BadgePopPeak = 1.25f

    /** The "+1" pill's keyframes over [MotionTokens.BonusRiseDuration]: where in it, and its lift (a fraction of the rise), scale and alpha. */
    val BonusAt = floatArrayOf(0f, 0.2f, 0.65f, 1f)
    val BonusLift = floatArrayOf(-0.33f, 0.3f, 0.7f, 1f)
    val BonusScale = floatArrayOf(0.8f, 1f, 1f, 1f)
    val BonusAlpha = floatArrayOf(0f, 1f, 1f, 0f)

    /** Letter n of the spangram starts at n × [SweepStaggerMs], squeezed so the last starts within the cap. */
    fun sweepDelayMs(index: Int, letters: Int): Int {
        if (letters <= 1) return 0
        val stagger = minOf(SweepStaggerMs, SpangramSweep.enterDurationMillis / (letters - 1))
        return index * stagger
    }
}
