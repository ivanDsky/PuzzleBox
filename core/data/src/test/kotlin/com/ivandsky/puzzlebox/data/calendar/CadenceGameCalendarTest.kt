package com.ivandsky.puzzlebox.data.calendar

import app.cash.turbine.test
import com.ivandsky.puzzlebox.game.Cadence
import com.ivandsky.puzzlebox.game.GameDefinition
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.game.TestGames
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Each game's "today" follows its own cadence zone, and `observeToday` rolls over at that
 * zone's midnight (A9, SC8). The wall clock is the test scheduler, so midnight is virtual.
 */
class CadenceGameCalendarTest {

    private val la = ZoneId.of("America/Los_Angeles")
    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val zip = GameId("zip")
    private val queens = GameId("queens")
    private val registry = GameRegistry(listOf(plugin(zip, la), plugin(queens, tokyo)))

    /** 2026-11-04 23:30 in Los Angeles = 2026-11-05 16:30 in Tokyo. */
    private val startMs = ZonedDateTime.of(2026, 11, 4, 23, 30, 0, 0, la).toInstant().toEpochMilli()

    /** A [TestGames] plugin whose cadence runs in [zone]. */
    private fun plugin(game: GameId, zone: ZoneId): GamePlugin<*, *, *> {
        val base = TestGames.plugin(game)
        val definition = object : GameDefinition by base.definition {
            override val cadence: Cadence = Cadence.Daily(zone)
        }
        return GamePlugin(
            definition = definition,
            rules = base.rules,
            session = base.session,
            score = base.score,
            stateOf = base.stateOf,
            initialSession = base.initialSession,
            refOf = base.refOf,
            playAgainSession = base.playAgainSession,
            rendererModel = base.rendererModel,
        )
    }

    private fun TestScope.calendar() = CadenceGameCalendar(registry) { startMs + testScheduler.currentTime }

    @Test
    fun `today follows each game's cadence zone`() = runTest {
        val calendar = calendar()

        assertEquals(LocalDate.of(2026, 11, 4), calendar.today(zip))
        assertEquals(LocalDate.of(2026, 11, 5), calendar.today(queens))
    }

    @Test
    fun `unregistered game has no today`() = runTest {
        assertNull(calendar().today(GameId("strands")))
    }

    @Test
    fun `dayAt reads an instant in each game's cadence zone`() = runTest {
        val calendar = calendar()

        assertEquals(LocalDate.of(2026, 11, 4), calendar.dayAt(zip, startMs))
        assertEquals(LocalDate.of(2026, 11, 5), calendar.dayAt(queens, startMs))
        assertNull(calendar.dayAt(GameId("strands"), startMs))
    }

    @Test
    fun `observeToday emits now then the new day after the zone midnight`() = runTest {
        calendar().observeToday(zip).test {
            assertEquals(LocalDate.of(2026, 11, 4), awaitItem())
            testScheduler.advanceTimeBy(29 * 60_000L)
            expectNoEvents()
            testScheduler.advanceTimeBy(61_000L)
            assertEquals(LocalDate.of(2026, 11, 5), awaitItem())
            testScheduler.advanceTimeBy(24 * 60 * 60_000L)
            assertEquals(LocalDate.of(2026, 11, 6), awaitItem())
        }
    }

    @Test
    fun `observeToday waits for its own zone's midnight`() = runTest {
        calendar().observeToday(queens).test {
            assertEquals(LocalDate.of(2026, 11, 5), awaitItem())
            testScheduler.advanceTimeBy(60 * 60_000L) // LA rolls over; Tokyo is still 17:30.
            expectNoEvents()
            testScheduler.advanceTimeBy(7 * 60 * 60_000L) // Tokyo 00:30 next day.
            assertEquals(LocalDate.of(2026, 11, 6), awaitItem())
        }
    }
}
