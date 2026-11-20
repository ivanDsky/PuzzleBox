package com.ivandsky.puzzlebox.app.widget

import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class ChipTextTest {
    private val resources: Resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources

    private fun ring(status: TodayStatus, rank: WidgetRank = WidgetRank.Missing) =
        WidgetRing(GameId("zip"), "Zip", status, bestMs = null, rank = rank, newBest = false)

    private fun text(status: TodayStatus, rank: WidgetRank = WidgetRank.Missing) = chipText(ring(status, rank), resources)

    private val solved = TodayStatus.Solved(timeMs = 72_000)

    @Test
    fun `solved with a rank reads the place over the time`() {
        assertEquals(ChipText("#3", "1:12"), text(solved, WidgetRank.Ranked(3, 41)))
    }

    @Test
    fun `solved with no rank reads its time alone, with no dash`() {
        assertEquals(ChipText("1:12", ""), text(solved, WidgetRank.Missing))
        assertEquals(ChipText("1:12", ""), text(solved, WidgetRank.SignedOut))
    }

    @Test
    fun `solved over an hour reads h mm ss`() {
        assertEquals(ChipText("1:05:42", ""), text(TodayStatus.Solved(timeMs = 3_942_000)))
    }

    @Test
    fun `in progress reads the elapsed time, not started New, offline Retry`() {
        assertEquals(ChipText("3:12", ""), text(TodayStatus.InProgress(elapsedMs = 192_000)))
        assertEquals(ChipText("New", ""), text(TodayStatus.NotStarted))
        assertEquals(ChipText("Retry", ""), text(TodayStatus.Offline))
    }

    @Test
    fun `a rank on a game that is not solved is not shown`() {
        assertEquals(ChipText("New", ""), text(TodayStatus.NotStarted, WidgetRank.Ranked(3, 41)))
    }

    @Test
    fun `the spoken label carries the status, the time and the rank when there is one`() {
        assertEquals("Zip, solved, 1:12, rank 3 of 41", chipDescription(solved, WidgetRank.Ranked(3, 41)))
        assertEquals("Zip, solved, 1:12", chipDescription(solved, WidgetRank.Missing))
        assertEquals("Zip, in progress, 3:12", chipDescription(TodayStatus.InProgress(elapsedMs = 192_000)))
        assertEquals("Zip, not started", chipDescription(TodayStatus.NotStarted))
        assertEquals("Zip, offline", chipDescription(TodayStatus.Offline))
    }

    private fun chipDescription(status: TodayStatus, rank: WidgetRank = WidgetRank.Missing) =
        ringDescription(ring(status, rank), resources)
}
