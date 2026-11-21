package com.ivandsky.puzzlebox.gameui.trainer

/**
 * A recorded demo of one technique variant (spec §2.7): one real step of that variant on a drill puzzle, cut into
 * frames the intro plays in order. Every frame is a complete board state, so the host can show any frame at any time.
 * Frame 0 is the position before the step; the following frames show the evidence, then each square of the step
 * landing in order; the last frame is the position after the step. Opaque to the Train feature, which draws a frame
 * through [TrainerGame.Demo].
 */
interface IntroDemo {
    /** Index of the variant in [TrainerGame.variants] this demo shows. */
    val variant: Int

    /** One plain caption per frame; the frame count is its size (at least 2). */
    val captions: List<String>
}
