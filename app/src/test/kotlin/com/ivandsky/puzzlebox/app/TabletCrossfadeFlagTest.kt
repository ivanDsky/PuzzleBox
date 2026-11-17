package com.ivandsky.puzzlebox.app

import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

// TabletCrossfade (backlog #1118, #1119) merges dark: every release build keeps today's tablet motion until the flip PR.
class TabletCrossfadeFlagTest {

    @Test
    fun `TabletCrossfade ships off in release`() {
        assertFalse(ReleaseFlag.TabletCrossfade.releaseDefault)
    }

    @Test
    fun `TabletCrossfade is labelled for the debug catalog`() {
        assertEquals("Tablet crossfades", ReleaseFlag.TabletCrossfade.label)
    }
}
