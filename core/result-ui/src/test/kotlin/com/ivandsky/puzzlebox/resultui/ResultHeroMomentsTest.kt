package com.ivandsky.puzzlebox.resultui

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.ivandsky.puzzlebox.domain.moments.Moment
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** L1: the hero model's moment rows, strongest first and at most three, and the haptic of the strongest. */
class ResultHeroMomentsTest {

    private val everything = listOf(
        Moment.Streak(7),
        Moment.FullSetDone(),
        Moment.PassedFriend("u1", "Maya"),
        Moment.TopThreeFriends(2),
        Moment.FirstSubX(60_000L),
        Moment.PersonalBest,
    )

    private fun model(moments: List<Moment>, practice: Boolean = false, playAgain: Boolean = false) =
        resultHeroModel(heroSampleFacts(practice = practice, playAgain = playAgain).copy(moments = moments), Locale.US)

    @Test
    fun `facts without moments give the model Result has today`() {
        val plain = resultHeroModel(heroSampleFacts(), Locale.US)
        assertEquals(emptyList<HeroMoment>(), plain.moments)
        assertNull(plain.challenge)
    }

    @Test
    fun `rows come strongest first whatever order the moments arrive in`() {
        val rows = model(everything).moments.map { it.moment }
        assertEquals(listOf(Moment.PersonalBest, Moment.FirstSubX(60_000L), Moment.TopThreeFriends(2)), rows)
    }

    @Test
    fun `at most three rows`() {
        assertEquals(3, model(everything).moments.size)
        assertEquals(2, model(listOf(Moment.FullSetDone(), Moment.Streak(3))).moments.size)
    }

    @Test
    fun `each moment reads in plain words`() {
        val texts = model(everything).moments
        assertEquals(listOf("New personal best", "First sub-1:00", "Top 3 among friends today"), texts.map { it.text })
        assertEquals("You passed Maya", heroMoments(listOf(Moment.PassedFriend("u1", "Maya"))).single().text)
        assertEquals("Full set done", heroMoments(listOf(Moment.FullSetDone())).single().text)
        val total = Moment.FullSetDone.DayTotal(timeMs = 312_000L, hints = 0)
        assertEquals("Full set done · 5:12 total", heroMoments(listOf(Moment.FullSetDone(total))).single().text)
        assertEquals("Full set done · 5:12 total, 3 hints", heroMoments(listOf(Moment.FullSetDone(total.copy(hints = 3)))).single().text)
        assertEquals("Full set done · 5:12 total, 1 hint", heroMoments(listOf(Moment.FullSetDone(total.copy(hints = 1)))).single().text)
        assertEquals("7-day streak", heroMoments(listOf(Moment.Streak(7))).single().text)
    }

    @Test
    fun `a practice puzzle and a play again show no moments`() {
        assertEquals(emptyList<HeroMoment>(), model(everything, practice = true).moments)
        assertEquals(emptyList<HeroMoment>(), model(everything, playAgain = true).moments)
    }

    @Test
    fun `a row's key stays while its moment stays so a late row is the only new one`() {
        val first = heroMoments(listOf(Moment.PersonalBest, Moment.Streak(3))).map { it.key }
        val later = heroMoments(listOf(Moment.PersonalBest, Moment.TopThreeFriends(1), Moment.Streak(3))).map { it.key }
        assertEquals(first.toSet(), later.toSet() - later[1])
    }

    @Test
    fun `the haptic is the reveal's strongest for a record, its confirm for a friends board and a soft tick after`() {
        assertEquals(HapticFeedbackType.LongPress, ResultMomentHaptics.typeFor(Moment.PersonalBest))
        assertEquals(HapticFeedbackType.LongPress, ResultMomentHaptics.typeFor(Moment.FirstSubX(60_000L)))
        assertEquals(HapticFeedbackType.Confirm, ResultMomentHaptics.typeFor(Moment.TopThreeFriends(3)))
        assertEquals(HapticFeedbackType.Confirm, ResultMomentHaptics.typeFor(Moment.PassedFriend("u1", "Maya")))
        assertEquals(HapticFeedbackType.TextHandleMove, ResultMomentHaptics.typeFor(Moment.FullSetDone()))
        assertEquals(HapticFeedbackType.TextHandleMove, ResultMomentHaptics.typeFor(Moment.Streak(7)))
    }
}
