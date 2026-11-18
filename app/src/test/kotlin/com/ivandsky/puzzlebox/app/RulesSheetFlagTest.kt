package com.ivandsky.puzzlebox.app

import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import org.junit.Assert.assertFalse
import org.junit.Test

// RulesSheet (#1187) merges dark: every release build keeps How to play opening the tutorial until the flip PR sets it on.
class RulesSheetFlagTest {

    @Test
    fun `RulesSheet ships off in release`() {
        assertFalse(ReleaseFlag.RulesSheet.releaseDefault)
    }
}
