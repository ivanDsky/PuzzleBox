package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.ui.unit.Density
import com.ivandsky.puzzlebox.game.replay.MoveKind
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.assertEquals

class ReplayBarFillTest {
    @Test fun barLeftOfNeedleIsFullyPlayed() = assertEquals(10f, playedExtent(left = 0f, width = 10f, needleX = 50f), 0f)

    @Test fun barRightOfNeedleIsFullyUnplayed() = assertEquals(0f, playedExtent(left = 20f, width = 10f, needleX = 5f), 0f)

    @Test fun barUnderNeedleIsSplitAtTheNeedle() = assertEquals(4f, playedExtent(left = 20f, width = 10f, needleX = 24f), 0f)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReplayNeedleFillTest {
    private val inset = 4f
    private val width = 2000f

    @Test fun needleAtStartSitsOnTheKnobInset() = assertEquals(inset, needleX(width, inset, 0f), 0f)

    @Test fun needleAtEndStopsShortOfTheEdgeByTheInset() = assertEquals(width - inset, needleX(width, inset, 1f), 0f)

    private val shapes = Density(1f).barSeriesShapes(
        counts = Array(MoveKind.entries.size) { kind -> IntArray(ACTIVITY_BUCKETS) { if (kind == 0) 1 else 0 } },
        width = width - 2 * inset, axisY = 50f, room = 40f, up = true, inset = inset,
    )

    private fun extent(bar: BarShape, fraction: Float) = playedExtent(bar.left, bar.width, needleX(width, inset, fraction))

    @Test fun nothingIsPlayedAtFractionZero() = assertEquals(0f, extent(shapes.bars.first(), 0f), 0f)

    @Test fun everyBarIsPlayedAtFractionOne() {
        val last = shapes.bars.last()
        assertEquals(last.width, extent(last, 1f), 1e-3f)
    }

    @Test fun fillSplitsExactlyAtTheNeedleMidway() {
        val bar = shapes.bars[10]
        val fraction = (bar.left + bar.width / 2 - inset) / (width - 2 * inset)
        assertEquals(needleX(width, inset, fraction), bar.left + extent(bar, fraction), 1e-3f)
    }
}
