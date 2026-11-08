package com.ivandsky.puzzlebox.designsystem

/**
 * Long celebration and feedback motion (Strands spec A33). [MotionTokens] keeps feedback
 * under ~160 ms; a found word's bounce and a hint's attention pulse are
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
}
