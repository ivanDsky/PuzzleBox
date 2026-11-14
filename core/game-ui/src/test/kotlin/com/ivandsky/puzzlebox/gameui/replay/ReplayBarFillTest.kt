package com.ivandsky.puzzlebox.gameui.replay

import org.junit.Test
import org.junit.Assert.assertEquals

class ReplayBarFillTest {
    @Test fun barLeftOfNeedleIsFullyPlayed() = assertEquals(10f, playedExtent(left = 0f, width = 10f, needleX = 50f), 0f)

    @Test fun barRightOfNeedleIsFullyUnplayed() = assertEquals(0f, playedExtent(left = 20f, width = 10f, needleX = 5f), 0f)

    @Test fun barUnderNeedleIsSplitAtTheNeedle() = assertEquals(4f, playedExtent(left = 20f, width = 10f, needleX = 24f), 0f)
}
