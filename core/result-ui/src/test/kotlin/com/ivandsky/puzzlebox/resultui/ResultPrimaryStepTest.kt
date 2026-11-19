package com.ivandsky.puzzlebox.resultui

import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** L3 and L4: the main button's words for each next thing, and when the Challenge row stands aside. */
class ResultPrimaryStepTest {

    private val maya = ResultPrimaryAction.ChallengeFriend("u1", "Maya")
    private val queens = GameId("queens")
    private val code = ShareCode.parse("ABC123")!!

    @Test
    fun `the existing cases keep their words`() {
        assertEquals("Next: Tango", ResultPrimaryAction.Next(GameId("tango"), "Tango").step()?.text)
        assertEquals("Done", ResultPrimaryAction.Done.step()?.text)
        assertNull("the countdown stands in for the button", ResultPrimaryAction.AllDone.step())
    }

    @Test
    fun `a past day's next names the game and the short date, and keeps the Next tag`() {
        val step = ResultPrimaryAction.NextDay(GameId("zip"), LocalDate.of(2026, 11, 13), "Zip").step(Locale.US)
        assertEquals("Next: Zip · Nov 13", step?.text)
        assertEquals("result.next", step?.tag)
    }

    @Test
    fun `beat a friend names the friend`() {
        assertEquals("Challenge Maya", maya.step()?.text)
    }

    @Test
    fun `a missed day names its weekday and game`() {
        // 2026-11-03 is a Tuesday.
        val missed = ResultPrimaryAction.MissedDay(queens, LocalDate.of(2026, 11, 3), "Queens")
        assertEquals("Play Tuesday's Queens", missed.step(Locale.US)?.text)
    }

    @Test
    fun `a friend's puzzle names the friend`() {
        assertEquals("Play Maya's puzzle", ResultPrimaryAction.FriendPuzzle(code, queens, "Maya").step()?.text)
    }

    @Test
    fun `a community puzzle names its author`() {
        assertEquals(
            "Try a new community puzzle by Sam",
            ResultPrimaryAction.CommunityPuzzle(code, queens, "Sam").step()?.text,
        )
    }

    @Test
    fun `every button has its own tag`() {
        val tags = listOf(
            ResultPrimaryAction.Next(queens, "Queens"), ResultPrimaryAction.Done, maya,
            ResultPrimaryAction.MissedDay(queens, LocalDate.of(2026, 11, 3), "Queens"),
            ResultPrimaryAction.FriendPuzzle(code, queens, "Maya"), ResultPrimaryAction.CommunityPuzzle(code, queens, "Sam"),
        ).map { it.step()?.tag }
        assertEquals(tags.size, tags.toSet().size)
    }

    @Test
    fun `the Challenge row shows under any other main button`() {
        assertEquals(maya, visibleChallenge(ResultPrimaryAction.Next(queens, "Queens"), maya))
        assertEquals(maya, visibleChallenge(ResultPrimaryAction.AllDone, maya))
        assertEquals(maya, visibleChallenge(null, maya))
    }

    @Test
    fun `the Challenge row is hidden when the main button already is the challenge`() {
        assertNull(visibleChallenge(maya, maya))
        assertNull(visibleChallenge(ResultPrimaryAction.ChallengeFriend("u2", "Noor"), maya))
    }

    @Test
    fun `no challenge, no row`() {
        assertNull(visibleChallenge(ResultPrimaryAction.Done, null))
    }

    @Test
    fun `the model yields the main button and the row as given`() {
        val missed = ResultPrimaryAction.MissedDay(queens, LocalDate.of(2026, 11, 3), "Queens")
        val model = resultHeroModel(heroSampleFacts(primary = missed).copy(challenge = maya), Locale.US)
        assertEquals(missed, model.primary)
        assertEquals(maya, model.challenge)
        assertNull(resultHeroModel(heroSampleFacts(primary = maya).copy(challenge = maya), Locale.US).challenge)
    }
}
