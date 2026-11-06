package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Test

/** The hub's sections ([planHub]): which game is the hero or which no-next panel, and where the rest go. */
class HubPlanTest {

    private val a = GameId("a")
    private val b = GameId("b")
    private val c = GameId("c")

    private fun row(game: GameId, status: TodayStatus?) = HubRow(game, game.value, RankOrder.TimeThenHints, status = status)

    @Test
    fun `a new game up next is the hero and the other new games are Also today`() {
        val plan = planHub(listOf(row(a, TodayStatus.NotStarted), row(b, TodayStatus.NotStarted), row(c, TodayStatus.NotStarted)), next = a)

        assertEquals(HubPlan(HubHero.Next(a), alsoToday = listOf(b, c), doneToday = emptyList()), plan)
    }

    @Test
    fun `an in-progress game up next leaves the new games before it in Also today, in registry order`() {
        val plan = planHub(listOf(row(a, TodayStatus.NotStarted), row(b, TodayStatus.InProgress(1_000L)), row(c, TodayStatus.NotStarted)), next = b)

        assertEquals(HubPlan(HubHero.Next(b), alsoToday = listOf(a, c), doneToday = emptyList()), plan)
    }

    @Test
    fun `a solved game is Done today`() {
        val plan = planHub(listOf(row(a, TodayStatus.Solved(30_000L)), row(b, TodayStatus.NotStarted), row(c, TodayStatus.Solved(9_000L))), next = b)

        assertEquals(HubPlan(HubHero.Next(b), alsoToday = emptyList(), doneToday = listOf(a, c)), plan)
    }

    @Test
    fun `an offline game stays in Also today so its tap retries`() {
        val plan = planHub(listOf(row(a, TodayStatus.Offline), row(b, TodayStatus.NotStarted)), next = b)

        assertEquals(HubPlan(HubHero.Next(b), alsoToday = listOf(a), doneToday = emptyList()), plan)
    }

    @Test
    fun `no next while a status is unknown is Loading, every game still to play`() {
        val plan = planHub(listOf(row(a, null), row(b, TodayStatus.Solved(30_000L)), row(c, null)), next = null)

        assertEquals(HubPlan(HubHero.Loading, alsoToday = listOf(a, c), doneToday = listOf(b)), plan)
    }

    @Test
    fun `every game solved is AllSolved with nothing left to play`() {
        val plan = planHub(listOf(row(a, TodayStatus.Solved(1_000L)), row(b, TodayStatus.Solved(2_000L))), next = null)

        assertEquals(HubPlan(HubHero.AllSolved, alsoToday = emptyList(), doneToday = listOf(a, b)), plan)
    }

    @Test
    fun `only offline games left is RestOffline, the offline games still to play`() {
        val plan = planHub(listOf(row(a, TodayStatus.Solved(1_000L)), row(b, TodayStatus.Offline), row(c, TodayStatus.Offline)), next = null)

        assertEquals(HubPlan(HubHero.RestOffline, alsoToday = listOf(b, c), doneToday = listOf(a)), plan)
    }
}
