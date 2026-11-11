package com.ivandsky.puzzlebox.data.linkedin

import androidx.room.Room
import com.ivandsky.puzzlebox.data.calendar.CadenceGameCalendar
import com.ivandsky.puzzlebox.database.linkedin.LinkedInDatabase
import com.ivandsky.puzzlebox.game.Cadence
import com.ivandsky.puzzlebox.game.GameDefinition
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.linkedin.LinkedInBoard
import com.ivandsky.puzzlebox.game.linkedin.LinkedInOwnResult
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInAccountSource
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInLink
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import java.time.LocalDate
import kotlin.io.path.createTempDirectory
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The board window against a real Pacific-time calendar: which days get a board call, and with which
 * delta, at noon, across PT midnight and across the 25-hour fall-back day. Each row runs on a fresh
 * sync and database, so no cached answer from one row hides a call in the next.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomLinkedInDaysDeltaTest {

    private val pacific = ZoneId.of("America/Los_Angeles")
    private val zip = GameId("zip")
    private val queens = GameId("queens")
    private val registry = GameRegistry(listOf(ptPlugin(zip)))

    @Test
    fun `the board is read for today and the seven days before it, not the eighth`() = runTest {
        val noon = pt(2026, 11, 5, 12, 0)

        assertBoard(noon, LocalDate.of(2026, 11, 5), delta = 0)
        assertBoard(noon, LocalDate.of(2026, 10, 29), delta = 7)
        assertNoBoard(noon, LocalDate.of(2026, 10, 28))
    }

    @Test
    fun `the window moves at Pacific midnight`() = runTest {
        val day = LocalDate.of(2026, 10, 29)

        assertBoard(pt(2026, 11, 5, 23, 59, 59), day, delta = 7)
        assertNoBoard(pt(2026, 11, 6, 0, 0), day)
    }

    @Test
    fun `the 25-hour fall-back day counts as one day`() = runTest {
        assertBoard(pt(2026, 11, 1, 23, 30), LocalDate.of(2026, 10, 25), delta = 7)
        assertNoBoard(pt(2026, 11, 2, 0, 30), LocalDate.of(2026, 10, 25))
        assertBoard(pt(2026, 11, 2, 0, 30), LocalDate.of(2026, 10, 26), delta = 7)
    }

    @Test
    fun `a game the calendar does not know has no today and makes no call`() = runTest {
        val row = Row(pt(2026, 11, 5, 12, 0))

        assertEquals(LinkedInSync.NotLinkedInGame, row.sync(queens, LocalDate.of(2026, 11, 5)))
        assertTrue(row.source.calls.isEmpty())
        row.close()
    }

    private suspend fun assertBoard(now: ZonedDateTime, day: LocalDate, delta: Int) {
        val today = now.toLocalDate()
        assertEquals(
            listOf("own:me:6:${number(day)}", "board:me:6:${number(today)}:$delta"),
            callsOf(now, day),
        )
    }

    private suspend fun assertNoBoard(now: ZonedDateTime, day: LocalDate) {
        assertEquals(listOf("own:me:6:${number(day)}"), callsOf(now, day))
    }

    private suspend fun callsOf(now: ZonedDateTime, day: LocalDate): List<String> {
        val row = Row(now)
        try {
            assertEquals(LinkedInSync.Done, row.sync(zip, day))
            return row.source.calls
        } finally {
            row.close()
        }
    }

    private fun pt(year: Int, month: Int, dayOfMonth: Int, hour: Int, minute: Int, second: Int = 0): ZonedDateTime =
        ZonedDateTime.of(year, month, dayOfMonth, hour, minute, second, 0, pacific)

    private fun number(day: LocalDate): Int = 700 + ChronoUnit.DAYS.between(EPOCH, day).toInt()

    /** A [TestGames] plugin whose cadence runs in Pacific time, as the LinkedIn games do. */
    private fun ptPlugin(game: GameId): GamePlugin<*, *, *> {
        val base = TestGames.plugin(game)
        val definition = object : GameDefinition by base.definition {
            override val cadence: Cadence = Cadence.Daily(pacific)
        }
        return GamePlugin(
            definition = definition,
            rules = base.rules,
            session = base.session,
            score = base.score,
            stateOf = base.stateOf,
            initialSession = base.initialSession,
            refOf = base.refOf,
            replaySession = base.replaySession,
            rendererModel = base.rendererModel,
        )
    }

    /**
     * One table row: a fresh sync and database with the wall clock fixed at [now]. The board is scripted
     * at every delta, so a wrong delta shows up in the recorded call rather than as an Offline result.
     */
    private inner class Row(now: ZonedDateTime) {
        private val nowMs = now.toInstant().toEpochMilli()
        private val today = now.toLocalDate()
        private val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), LinkedInDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        private val daily = FakeDailyRepository().apply {
            setPuzzleNumbers(zip) { number(it) }
            setLinkedInGameType(zip, 6)
            setPuzzleNumbers(queens) { number(it) }
            setLinkedInGameType(queens, 3)
        }
        val source = FakeLinkedInAccountSource().apply {
            (0..10).forEach { delta ->
                respondOwn("me", 6, number(today.minusDays(delta.toLong())), LinkedInRead.Ok(LinkedInOwnResult.NotPlayed))
                respondBoard("me", 6, number(today), delta, LinkedInRead.Ok(LinkedInBoard(emptyList())))
            }
        }
        private val days = RoomLinkedInDays(
            avatars = testAvatars(createTempDirectory("avatars").toFile()),
            link = FakeLinkedInLink(LinkState.Connected("me")),
            source = source,
            completions = FakeCompletionRepository(),
            daily = daily,
            calendar = CadenceGameCalendar(registry) { nowMs },
            registry = registry,
            db = db,
            budget = LinkedInCallBudget({ nowMs }),
            clock = { nowMs },
        )

        suspend fun sync(game: GameId, day: LocalDate): LinkedInSync = days.sync(game, day)

        fun close() = db.close()
    }

    private companion object {
        val EPOCH: LocalDate = LocalDate.of(2026, 1, 1)
    }
}
