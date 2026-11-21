package com.ivandsky.puzzlebox.train.practice

/**
 * Whether a screen reader is on right now. The board end asks once when a board is done: with one on, the player
 * moves to the next board with a tap instead of after a timed hold, so nothing opens while it is still reading.
 */
internal fun interface ScreenReaderCheck {
    fun on(): Boolean
}
