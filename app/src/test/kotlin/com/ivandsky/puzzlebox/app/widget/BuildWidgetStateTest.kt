package com.ivandsky.puzzlebox.app.widget

import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.domain.StreakFreezes
import com.ivandsky.puzzlebox.domain.TodayEntry
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BuildWidgetStateTest {

    private val build = BuildWidgetState()

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
    fun `rings keep Today's order and statuses`() {
        val today = listOf(entry("zip", solved), entry("queens", inProgress), entry("tango", TodayStatus.Offline))

        val rings = build(today, stats()).rings

        assertEquals(
            listOf(
                WidgetRing(GameId("zip"), "Zip", solved),
                WidgetRing(GameId("queens"), "Queens", inProgress),
                WidgetRing(GameId("tango"), "Tango", TodayStatus.Offline),
            ),
            rings,
        )
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
