package com.ivandsky.puzzlebox.gameui

import com.ivandsky.puzzlebox.domain.ResultDay
import com.ivandsky.puzzlebox.domain.StreakFreezes
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Result streak card's freeze sentences, and how the model carries the freezes to the card. */
class HeroFreezeCopyTest {

    private fun freezes(held: Int, daysToNext: Int?) = StreakFreezes(held, emptySet(), daysToNext, null)

    @Test
    fun `no freezes held names the days to earn the next`() {
        assertEquals("6 more days to earn a freeze", heroFreezeSub(freezes(held = 0, daysToNext = 6)))
    }

    @Test
    fun `some freezes held still name the days to earn the next`() {
        assertEquals("3 more days to earn a freeze", heroFreezeSub(freezes(held = 2, daysToNext = 3)))
    }

    @Test
    fun `one day to go reads in the singular`() {
        assertEquals("1 more day to earn a freeze", heroFreezeSub(freezes(held = 2, daysToNext = 1)))
    }

    @Test
    fun `the cap says the freezes are held`() {
        assertEquals("5 freezes held", heroFreezeSub(freezes(held = 5, daysToNext = null)))
    }

    @Test
    fun `without freezes the streak card keeps its plain sub line and dots`() {
        val streak = heroSampleModel(streak = 3).streak!!
        assertEquals(heroStreakSub(3), streak.sub)
        assertNull(streak.freezesHeld)
        assertTrue(streak.week.none { it.frozen })
    }

    @Test
    fun `freezes put the held count, the freeze sub line and the frozen days on the card`() {
        val facts = heroSampleFacts(streak = 3)
        val week = facts.insights!!.week
        val insights = facts.insights!!.copy(
            week = week.mapIndexed { i, day -> if (i == 2) ResultDay(day.date, solved = false, today = false, frozen = true) else day },
            freezes = freezes(held = 3, daysToNext = 2),
        )
        val streak = resultHeroModel(facts.copy(insights = insights), Locale.US).streak!!
        assertEquals(3, streak.freezesHeld)
        assertEquals("2 more days to earn a freeze", streak.sub)
        assertEquals(listOf(false, false, true, false, false, false, false), streak.week.map { it.frozen })
        assertFalse(streak.week[2].solved)
    }

    private val tuesday = LocalDate.of(2026, 11, 3)

    private fun moment(spent: List<LocalDate> = emptyList(), earned: Boolean = false) =
        StreakFreezes.Moment(day = tuesday.plusDays(1), spent = spent, earned = earned)

    @Test
    fun `one freeze spent names the day it held`() {
        assertEquals("Streak freeze used for Tue · 1 left", heroFreezeMoment(moment(spent = listOf(tuesday)), held = 1, Locale.US))
    }

    @Test
    fun `the day reads in the locale`() {
        assertEquals("Streak freeze used for mar. · 1 left", heroFreezeMoment(moment(spent = listOf(tuesday)), held = 1, Locale.FRANCE))
    }

    @Test
    fun `several freezes spent are counted, not named`() {
        val spent = listOf(tuesday, tuesday.plusDays(1))
        assertEquals("Used 2 freezes · 0 left", heroFreezeMoment(moment(spent = spent), held = 0, Locale.US))
    }

    @Test
    fun `an earned freeze says how many are held`() {
        assertEquals("Freeze earned · 3 held", heroFreezeMoment(moment(earned = true), held = 3, Locale.US))
    }

    @Test
    fun `a solve that spent and earned shows the used line`() {
        assertEquals("Streak freeze used for Tue · 2 left", heroFreezeMoment(moment(listOf(tuesday), earned = true), held = 2, Locale.US))
    }

    @Test
    fun `without a moment the streak card has no line`() {
        val facts = heroSampleFacts(streak = 3)
        val insights = facts.insights!!.copy(freezes = freezes(held = 3, daysToNext = 2))
        assertNull(resultHeroModel(facts.copy(insights = insights), Locale.US).streak!!.moment)
    }

    @Test
    fun `a moment puts its line on the streak card`() {
        val facts = heroSampleFacts(streak = 3)
        val lastSolve = moment(spent = listOf(tuesday))
        val insights = facts.insights!!.copy(freezes = StreakFreezes(1, emptySet(), 2, lastSolve))
        assertEquals("Streak freeze used for Tue · 1 left", resultHeroModel(facts.copy(insights = insights), Locale.US).streak!!.moment)
    }

    @Test
    fun `without freezes the streak card has no moment`() {
        assertNull(heroSampleModel(streak = 3).streak!!.moment)
    }
}
