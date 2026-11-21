package com.ivandsky.puzzlebox.app.widget

import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.domain.ResultRank
import com.ivandsky.puzzlebox.domain.TodayEntry
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.Instant
import java.time.LocalDate
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

// Robolectric only for android.util.Log, which a read that could not be had writes its line to.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ReadWidgetScoreboardTest {

    private val zip = GameId("zip")
    private val queens = GameId("queens")
    private val tango = GameId("tango")
    private val day = LocalDate.of(2026, 11, 20)
    private val rollover = Instant.parse("2026-11-21T08:00:00Z")

    private data class Ref(override val gameId: GameId, override val key: PuzzleKey) : PuzzleRef

    private fun solved(id: GameId) = TodayEntry(id, id.value, TodayStatus.Solved(timeMs = 50_000))
    private fun open(id: GameId) = TodayEntry(id, id.value, TodayStatus.NotStarted)
    private fun ranked(position: Int, count: Int) = ResultRank.Ranked(position, count, topPercent = 1)
    private fun record(game: GameId, daysAgo: Long, time: Long) =
        CompletionRecord(Ref(game, PuzzleKey.Daily(day.minusDays(daysAgo), null)), time, hints = 0, stats = RecordStats(1, "{}"))

    @Test
    fun `a solved game with a rank is Ranked`() = runTest {
        val board = testScoreboard(rank = { _, _ -> flowOf(ranked(position = 3, count = 12)) })

        assertEquals(mapOf(zip to WidgetRank.Ranked(3, 12)), board(listOf(solved(zip), open(queens))).ranks)
    }

    @Test
    fun `signed out is SignedOut`() = runTest {
        val board = testScoreboard(rank = { _, _ -> flowOf(ResultRank.SignedOut) })

        assertEquals(mapOf(zip to WidgetRank.SignedOut), board(listOf(solved(zip))).ranks)
    }

    @Test
    fun `the loading Hidden is waited past to the first real rank`() = runTest {
        val board = testScoreboard(rank = { _, _ -> flowOf(ResultRank.Hidden, ResultRank.Hidden, ranked(2, 5)) })

        assertEquals(mapOf(zip to WidgetRank.Ranked(2, 5)), board(listOf(solved(zip))).ranks)
    }

    @Test
    fun `a rank that stays Hidden is Missing after the limit`() = runTest {
        val board = testScoreboard(rank = { _, _ -> flow { emit(ResultRank.Hidden); awaitCancellation() } })

        assertEquals(mapOf(zip to WidgetRank.Missing), board(listOf(solved(zip))).ranks)
        assertEquals(3_000L, currentTime)
    }

    @Test
    fun `a slow rank is Missing and costs only its own 3 seconds, the rest is intact`() = runTest {
        val board = testScoreboard(
            best = { BestScore(timeMs = 40_000, hints = 0) },
            rank = { game, _ -> if (game == zip) flow { awaitCancellation() } else flowOf(ranked(1, 4)) },
        )

        val read = board(listOf(solved(zip), solved(queens)))

        assertEquals(mapOf(zip to WidgetRank.Missing, queens to WidgetRank.Ranked(1, 4)), read.ranks)
        assertEquals(mapOf(zip to 40_000L, queens to 40_000L), read.bestMs)
        assertEquals(rollover, read.nextRollover)
        assertEquals(3_000L, currentTime)
    }

    @Test
    fun `a failing rank is Missing`() = runTest {
        val board = testScoreboard(rank = { _, _ -> flow { error("offline") } })

        assertEquals(mapOf(zip to WidgetRank.Missing), board(listOf(solved(zip))).ranks)
    }

    @Test
    fun `a game not solved has no rank and its board is not asked`() = runTest {
        var asked = emptyList<GameId>()
        val board = testScoreboard(rank = { game, _ -> asked = asked + game; flowOf(ranked(1, 1)) })

        val read = board(listOf(open(zip), solved(queens)))

        assertEquals(listOf(queens), asked)
        assertNull(read.ranks[zip])
    }

    @Test
    fun `best is present for a game with a completion and absent for one without`() = runTest {
        val board = testScoreboard(best = { if (it == zip) BestScore(timeMs = 41_000, hints = 1) else null })

        assertEquals(mapOf(zip to 41_000L), board(listOf(solved(zip), open(queens))).bestMs)
    }

    @Test
    fun `a failing best read leaves that game without a best and the rest intact`() = runTest {
        val board = testScoreboard(best = { if (it == zip) error("db closed") else BestScore(30_000, 0) })

        val read = board(listOf(open(zip), open(queens)))

        assertEquals(mapOf(queens to 30_000L), read.bestMs)
        assertEquals(rollover, read.nextRollover)
    }

    @Test
    fun `new best on a beat, none on a first solve or a slower solve`() = runTest {
        val board = testScoreboard(
            records = {
                listOf(
                    record(zip, daysAgo = 0, time = 40_000), record(zip, daysAgo = 1, time = 50_000), // beat
                    record(queens, daysAgo = 0, time = 40_000), // first ever
                    record(tango, daysAgo = 0, time = 70_000), record(tango, daysAgo = 2, time = 50_000), // slower
                )
            },
        )

        assertEquals(setOf(zip), board(listOf(solved(zip), solved(queens), solved(tango))).newBest)
    }

    @Test
    fun `new best on a tie`() = runTest {
        val board = testScoreboard(records = { listOf(record(zip, daysAgo = 0, time = 50_000), record(zip, daysAgo = 3, time = 50_000)) })

        assertEquals(setOf(zip), board(listOf(solved(zip))).newBest)
    }

    @Test
    fun `a failing records read gives no new best and keeps the rest`() = runTest {
        val board = testScoreboard(records = { error("db closed") }, rank = { _, _ -> flowOf(ranked(1, 2)) })

        val read = board(listOf(solved(zip)))

        assertEquals(emptySet<GameId>(), read.newBest)
        assertEquals(mapOf(zip to WidgetRank.Ranked(1, 2)), read.ranks)
    }

    @Test
    fun `the next rollover is carried, and null when it cannot be had`() = runTest {
        assertEquals(rollover, testScoreboard()(listOf(open(zip))).nextRollover)
        assertNull(testScoreboard(nextRollover = { error("no zone") })(listOf(open(zip))).nextRollover)
    }

    @Test
    fun `cancelling the caller is not swallowed by a rank read`() = runTest {
        var returned = false
        val job = launch {
            testScoreboard(rank = { _, _ -> flow { awaitCancellation() } })(listOf(solved(zip)))
            returned = true
        }
        runCurrent()

        job.cancel()
        job.join()

        assertFalse(returned)
    }
}
