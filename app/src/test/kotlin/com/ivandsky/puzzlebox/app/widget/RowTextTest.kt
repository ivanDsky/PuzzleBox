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
class RowTextTest {
    private val resources: Resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources

    private fun text(status: TodayStatus, rank: WidgetRank = WidgetRank.Missing, bestMs: Long? = 48_000) =
        rowText(WidgetRing(GameId("zip"), "Zip", status, bestMs = bestMs, rank = rank, newBest = false), resources)

    private val solved = TodayStatus.Solved(timeMs = 72_000)

    @Test
    fun `solved reads its time, its place of the board and the best`() {
        assertEquals(
            RowText(status = "1:12", solved = true, rank = "#3 of 41", rankMuted = false, best = "Best 0:48"),
            text(solved, WidgetRank.Ranked(3, 41)),
        )
    }

    @Test
    fun `solved with no rank shows a dash, and signed out shows nothing at all`() {
        assertEquals(RowText("1:12", solved = true, rank = "–", rankMuted = true, best = "Best 0:48"), text(solved, WidgetRank.Missing))
        assertEquals(RowText("1:12", solved = true, rank = "", rankMuted = false, best = "Best 0:48"), text(solved, WidgetRank.SignedOut))
    }

    @Test
    fun `a game not solved has no rank, even one the board still knows`() {
        assertEquals("", text(TodayStatus.InProgress(elapsedMs = 192_000), WidgetRank.Ranked(3, 41)).rank)
        assertEquals("", text(TodayStatus.NotStarted, WidgetRank.Missing).rank)
        assertEquals("", text(TodayStatus.Offline, WidgetRank.Missing).rank)
    }

    @Test
    fun `the other statuses say where the game stands in plain words`() {
        assertEquals("In progress 3:12", text(TodayStatus.InProgress(elapsedMs = 192_000)).status)
        assertEquals("Not started", text(TodayStatus.NotStarted).status)
        assertEquals("Retry", text(TodayStatus.Offline).status)
    }

    @Test
    fun `best shows for every status, offline too, and is blank with no completion`() {
        assertEquals("Best 0:48", text(TodayStatus.Offline).best)
        assertEquals("Best 0:48", text(TodayStatus.NotStarted).best)
        assertEquals("", text(solved, bestMs = null).best)
    }

    @Test
    fun `a solve over an hour reads h mm ss`() {
        assertEquals("1:05:42", text(TodayStatus.Solved(timeMs = 3_942_000)).status)
    }
}
