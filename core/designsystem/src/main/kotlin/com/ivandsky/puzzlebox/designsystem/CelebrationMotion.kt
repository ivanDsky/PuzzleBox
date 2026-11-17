package com.ivandsky.puzzlebox.designsystem

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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

    /**
     * The solve moment on the board (M10-6, M12-10): from the winning move to the hand-off to Result, 1700 ms in all. It is
     * motion, not a still rest: the band sweeps, the board pops and the frame line laps it, and the motion ends at 1300 ms
     * (the frame closes). The closed board then holds still for the last 400 ms before Result. Its length holds under
     * reduced motion too, where only the accent outline shows.
     */
    const val SolveMomentMs = 1700L

    /** The board pop: as the band leaves, the whole board rises to this scale and releases on the Stamp spring. */
    const val BoardPopScale = 1.03f
    const val BoardPopStartMs = 440
    const val BoardPopRiseMs = 120

    /** The frame line: from this long after the solve it laps the board once, clockwise, at a steady pace. */
    const val FrameDrawStartMs = 500
    const val FrameDrawMs = 800

    /**
     * The comet at the head of the frame line: this share of the lap long and this wide, fading out over the last
     * [FrameCometFadeShare] of the lap.
     */
    const val FrameCometFraction = 0.07f
    const val FrameCometFadeShare = 0.08f
    val FrameCometWidth: Dp = 7.dp
}
