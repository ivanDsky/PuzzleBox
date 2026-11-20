package com.ivandsky.puzzlebox.app.widget

import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.domain.StreakFreezes
import com.ivandsky.puzzlebox.domain.TodayEntry
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.model.GameId
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BuildWidgetStateTest {

    private val none = WidgetScoreboard(bestMs = emptyMap(), ranks = emptyMap(), newBest = emptySet(), nextRollover = null)

    private fun build(today: List<TodayEntry>, stats: GameStats) = BuildWidgetState()(today, stats, none)

    private fun entry(id: String, status: TodayStatus) = TodayEntry(GameId(id), id.replaceFirstChar { it.uppercase() }, status)
    private val notStarted = TodayStatus.NotStarted
    private val inProgress = TodayStatus.InProgress(elapsedMs = 1_000)
    private val solved = TodayStatus.Solved(timeMs = 2_000)

    private fun stats(current: Int = 0, best: Int = 0, freezes: StreakFreezes? = null) = GameStats(
        currentStreak = current,
        bestStreak = best,
        solved = 0,
        hintFree = 0,
        averageTimeMs = null,
        playingSince = null,
        best = null,
        rankOrder = RankOrder.TimeThenHints,
        freezes = freezes,
    )

    private fun freezes(held: Int) = StreakFreezes(held = held, frozenDays = emptySet(), daysToNext = null, lastSolve = null)

    @Test
    fun `rings keep Today's order and statuses, with no best, rank or new best when none was read`() {
        val today = listOf(entry("zip", solved), entry("queens", inProgress), entry("tango", TodayStatus.Offline))

        val rings = build(today, stats()).rings

        assertEquals(
            listOf(
                WidgetRing(GameId("zip"), "Zip", solved, bestMs = null, rank = WidgetRank.Missing, newBest = false),
                WidgetRing(GameId("queens"), "Queens", inProgress, bestMs = null, rank = WidgetRank.Missing, newBest = false),
                WidgetRing(GameId("tango"), "Tango", TodayStatus.Offline, bestMs = null, rank = WidgetRank.Missing, newBest = false),
            ),
            rings,
        )
    }

    @Test
    fun `each ring takes its own best, rank and new best, and the state the rollover`() {
        val rollover = Instant.parse("2026-11-21T08:00:00Z")
        val scoreboard = WidgetScoreboard(
            bestMs = mapOf(GameId("zip") to 41_000L),
            ranks = mapOf(GameId("zip") to WidgetRank.Ranked(3, 12), GameId("queens") to WidgetRank.SignedOut),
            newBest = setOf(GameId("zip")),
            nextRollover = rollover,
        )
        val today = listOf(entry("zip", solved), entry("queens", solved), entry("tango", notStarted))

        val state = BuildWidgetState()(today, stats(), scoreboard)

        assertEquals(
            listOf(
                WidgetRing(GameId("zip"), "Zip", solved, bestMs = 41_000L, rank = WidgetRank.Ranked(3, 12), newBest = true),
                WidgetRing(GameId("queens"), "Queens", solved, bestMs = null, rank = WidgetRank.SignedOut, newBest = false),
                WidgetRing(GameId("tango"), "Tango", notStarted, bestMs = null, rank = WidgetRank.Missing, newBest = false),
            ),
            state.rings,
        )
        assertEquals(rollover, state.nextRollover)
    }

    @Test
    fun `streak carries current and best and the held freezes while a streak runs`() {
        val streak = build(emptyList(), stats(current = 4, best = 9, freezes = freezes(held = 2))).streak

        assertEquals(WidgetStreak(days = 4, best = 9, freezesHeld = 2), streak)
    }

    @Test
    fun `freeze count is null at streak 0 even when freezes are held`() {
        assertNull(build(emptyList(), stats(current = 0, best = 9, freezes = freezes(held = 3))).streak.freezesHeld)
    }

    @Test
    fun `freeze count is null when stats have no freeze ledger`() {
        assertNull(build(emptyList(), stats(current = 4, freezes = null)).streak.freezesHeld)
    }

    @Test
    fun `up next prefers the first in-progress game over an earlier not-started one`() {
        val today = listOf(entry("zip", notStarted), entry("queens", inProgress), entry("tango", inProgress))

        assertEquals(WidgetUpNext.Play(GameId("queens"), "Queens"), build(today, stats()).upNext)
    }

    @Test
    fun `up next counts an offline game as unsolved`() {
        val today = listOf(entry("zip", solved), entry("queens", TodayStatus.Offline), entry("tango", notStarted))

        assertEquals(WidgetUpNext.Play(GameId("queens"), "Queens"), build(today, stats()).upNext)
    }

    @Test
    fun `up next is Done when every game is solved`() {
        val today = listOf(entry("zip", solved), entry("queens", solved))

        assertEquals(WidgetUpNext.Done, build(today, stats()).upNext)
    }

    @Test
    fun `up next is Unknown when Today's set is empty`() {
        assertEquals(WidgetUpNext.Unknown, build(emptyList(), stats()).upNext)
    }
}
