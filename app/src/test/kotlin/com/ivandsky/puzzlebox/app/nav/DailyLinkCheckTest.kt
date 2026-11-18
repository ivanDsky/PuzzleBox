package com.ivandsky.puzzlebox.app.nav

import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.model.DailyRef
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which friend links may open: a daily one only for a shipped game and a day that has a puzzle. */
class DailyLinkCheckTest {

    private val games = GameRegistry(listOf(TestGames.plugin(TestGames.alpha)))
    private val today = LocalDate.parse("2026-11-18")
    private val calendar = FakeGameCalendar(today)

    private fun daily(game: GameId, day: String) = FriendLink.Daily(DailyRef(game, LocalDate.parse(day)))

    @Test
    fun `today and a past day of a shipped game are playable`() {
        assertTrue(daily(TestGames.alpha, "2026-11-18").isPlayable(games, calendar))
        assertTrue(daily(TestGames.alpha, "2026-11-17").isPlayable(games, calendar))
    }

    @Test
    fun `a day after the game's today has no puzzle yet`() {
        assertFalse(daily(TestGames.alpha, "2026-11-19").isPlayable(games, calendar))
    }

    @Test
    fun `a game this build does not ship is not playable`() {
        assertFalse(daily(GameId("nope"), "2026-11-18").isPlayable(games, calendar))
    }

    @Test
    fun `the day is judged in the game's own zone, not a shared today`() {
        calendar.set(TestGames.alpha, LocalDate.parse("2026-11-19"))

        assertTrue(daily(TestGames.alpha, "2026-11-19").isPlayable(games, calendar))
    }

    @Test
    fun `a code and a match always open`() {
        val code = checkNotNull(ShareCode.parse("K7Q2MX"))

        assertTrue(FriendLink.Code(code).isPlayable(games, calendar))
        assertTrue(FriendLink.Match(code).isPlayable(games, calendar))
        assertTrue(FriendLink.Invalid("zip/nope").isPlayable(games, calendar))
    }
}
