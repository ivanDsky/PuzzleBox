package com.ivandsky.puzzlebox.designsystem

import kotlin.math.sqrt

/**
 * Long celebration and feedback motion (Strands spec A33). [MotionTokens] keeps feedback
 * under ~160 ms; a found word's bounce, a hint's attention pulse and a meter fill are
 * deliberate moments that run longer, so they get their own names instead of stretching
 * those. Game-agnostic: a game composes its staggers from these.
 */
object CelebrationMotion {
    /** A found word's letters settle (scale bounce): NYT's 1 s Pulser bounce. */
    const val SettleMs = 1000

    /** Cap on a spangram's edge-to-edge sweep, however long the word. */
    const val SweepMaxMs = 900

    /**
     * One pass of the hint outline's attention pulse. NYT loops a 4 s pulse forever; this
     * runs once, so the ring draws the eye and then holds still.
     */
    const val HintPulseMs = 1000

    /** The hint meter filling a third. */
    val MeterFillMs: Int = MotionTokens.DurationMedium

    /** NYT's react-flip-toolkit `noWobble` spring stiffness. */
    const val SpringStiffness = 200f

    /**
     * `noWobble` is specified as stiffness 200 and damping 26 at unit mass; Compose's spring
     * takes a damping ratio instead: ratio = damping / (2 * sqrt(stiffness * mass)), so
     * 26 / (2 * sqrt(200)) ≈ 0.919, just under critical damping.
     */
    val SpringDampingRatio: Float = (NoWobbleDamping / (2.0 * sqrt(SpringStiffness.toDouble()))).toFloat()

    private const val NoWobbleDamping = 26.0
}
