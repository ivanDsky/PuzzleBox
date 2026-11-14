package com.ivandsky.puzzlebox.gameui.replay

import org.junit.Test
import org.junit.Assert.assertEquals

class ReplayBarFillTest {
    @Test fun barLeftOfNeedleIsFullyPlayed() = assertEquals(10f, playedExtent(left = 0f, width = 10f, needleX = 50f), 0f)

    @Test fun barRightOfNeedleIsFullyUnplayed() = assertEquals(0f, playedExtent(left = 20f, width = 10f, needleX = 5f), 0f)

    @Test fun barUnderNeedleIsSplitAtTheNeedle() = assertEquals(4f, playedExtent(left = 20f, width = 10f, needleX = 24f), 0f)
}

class ReplayNeedleFillTest {
    private val inset = 4f
    private val width = 100f

    @Test fun needleAtStartSitsOnTheKnobInset() = assertEquals(inset, needleX(width, inset, 0f), 0f)

    @Test fun needleAtEndStopsShortOfTheEdgeByTheInset() = assertEquals(width - inset, needleX(width, inset, 1f), 0f)

    @Test fun firstBarIsPlayedUpToTheNeedleAtFractionZero() =
        assertEquals(inset, playedExtent(left = 0f, width = 10f, needleX = needleX(width, inset, 0f)), 0f)

    @Test fun lastBarIsNotFullyPlayedAtFractionOne() {
        val extent = playedExtent(left = 90f, width = 10f, needleX = needleX(width, inset, 1f))
        assertEquals(6f, extent, 0f)
    }
}
