package com.ivandsky.puzzlebox.app.widget

import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.domain.ResultRank
import com.ivandsky.puzzlebox.domain.StreakFreezes
import com.ivandsky.puzzlebox.domain.TodayEntry
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric only for android.util.Log, which the reader writes its one failure line to.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
@OptIn(ExperimentalCoroutinesApi::class)
class WidgetStateReaderTest {

    private val today = listOf(
        TodayEntry(GameId("zip"), "Zip", TodayStatus.Solved(timeMs = 2_000)),
        TodayEntry(GameId("queens"), "Queens", TodayStatus.InProgress(elapsedMs = 1_000)),
        TodayEntry(GameId("tango"), "Tango", TodayStatus.NotStarted),
    )
    private val stats = GameStats(
        currentStreak = 4,
        bestStreak = 9,
        solved = 20,
        hintFree = 10,
        averageTimeMs = null,
        playingSince = null,
        best = null,
        rankOrder = RankOrder.TimeThenHints,
        freezes = StreakFreezes(held = 2, frozenDays = emptySet(), daysToNext = null, lastSolve = null),
    )

    private fun reader(
        todaySet: () -> Flow<List<TodayEntry>> = { flowOf(today) },
        stats: () -> Flow<GameStats> = { flowOf(this.stats) },
        scoreboard: ReadWidgetScoreboard = testScoreboard(),
        limitMs: Long = 8_000L,
    ) = WidgetStateReader(todaySet, stats, scoreboard, BuildWidgetState(), limitMs)

    @Test
    fun `the state is exactly what Today's set, the stats and the scoreboard give`() = runTest {
        val scoreboard = testScoreboard(
            best = { BestScore(timeMs = 1_500, hints = 0) },
            rank = { _, _ -> flowOf(ResultRank.Ranked(2, 9, topPercent = 23)) },
        )

        val expected = BuildWidgetState()(today, stats, scoreboard(today))

        assertEquals(expected, reader(scoreboard = scoreboard).read())
    }

    @Test
    fun `a slow rank costs the rank, not the state`() = runTest {
        val state = reader(scoreboard = testScoreboard(rank = { _, _ -> flow { awaitCancellation() } })).read()

        assertEquals(WidgetRank.Missing, state?.rings?.first()?.rank)
        assertEquals(today.map { it.game }, state?.rings?.map { it.game })
        assertEquals(4, state?.streak?.days)
        assertEquals(3_000L, currentTime)
    }

    @Test
    fun `a failing today read gives null`() = runTest {
        assertNull(reader(todaySet = { flow { error("db closed") } }).read())
    }

    @Test
    fun `a failing stats read gives null`() = runTest {
        assertNull(reader(stats = { flow { error("db closed") } }).read())
    }

    @Test
    fun `a hung read gives up at the limit, not after it`() = runTest {
        val state = reader(stats = { flow { awaitCancellation() } }, limitMs = 8_000L).read()

        assertNull(state)
        assertEquals(8_000L, currentTime)
    }

    @Test
    fun `cancelling the caller is not turned into a null state`() = runTest {
        var returned = false
        val job = launch {
            reader(stats = { flow { awaitCancellation() } }).read()
            returned = true
        }
        runCurrent()

        job.cancel()
        job.join()

        assertFalse(returned)
    }
}
