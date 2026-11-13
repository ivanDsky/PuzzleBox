package com.ivandsky.puzzlebox.gameui.replay

import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.game.replay.Timeline
import org.junit.Assert.assertEquals
import org.junit.Test

/** core:game cannot see the design system, so the timeline's settle window is a copy of the live one: only this module sees both. */
class ReplaySettleWindowTest {

    @Test
    fun `the timeline mistake window is the live settle token`() {
        assertEquals(MotionTokens.SettleMillis, Timeline.MISTAKE_SETTLE_MS)
    }
}
