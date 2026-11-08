package com.ivandsky.puzzlebox.data.leaderboard

import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A7's window said once: the game's today and yesterday, nothing older, no day no window. */
class SubmitWindowTest {

    private val alpha = GameId("alpha")
    private val today = LocalDate.of(2026, 11, 8)

    @Test
    fun `the game's today and yesterday are in the window`() {
        val calendar = FakeGameCalendar(today)
        assertTrue(calendar.inSubmitWindow(alpha, today))
        assertTrue(calendar.inSubmitWindow(alpha, today.minusDays(1)))
    }

    @Test
    fun `an older day is out`() {
        val calendar = FakeGameCalendar(today)
        assertFalse(calendar.inSubmitWindow(alpha, today.minusDays(2)))
        assertFalse(calendar.inSubmitWindow(alpha, today.minusDays(30)))
    }

    @Test
    fun `a game with no day never opens the window`() {
        val noDay = object : GameCalendar {
            override fun today(game: GameId): LocalDate? = null
            override fun dayAt(game: GameId, epochMs: Long): LocalDate = error("unused")
            override fun observeToday(game: GameId): Flow<LocalDate> = error("unused")
        }
        assertFalse(noDay.inSubmitWindow(alpha, today))
    }
}
