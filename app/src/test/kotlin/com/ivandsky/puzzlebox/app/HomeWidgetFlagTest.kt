package com.ivandsky.puzzlebox.app

import com.ivandsky.puzzlebox.play.flags.PlayFlag
import org.junit.Assert.assertFalse
import org.junit.Test

/** Pins that the home-screen widget ships dark; the flip ticket deletes this. */
class HomeWidgetFlagTest {

    @Test
    fun `HomeWidget ships off in release`() {
        assertFalse(PlayFlag.HomeWidget.releaseDefault)
    }
}
