package com.ivandsky.puzzlebox.data.linkedin

import androidx.room.Room
import com.ivandsky.puzzlebox.database.linkedin.LinkedInDatabase
import com.ivandsky.puzzlebox.game.CompletionSource
import com.ivandsky.puzzlebox.game.linkedin.LinkedInAccountSource
import com.ivandsky.puzzlebox.game.linkedin.LinkedInBoard
import com.ivandsky.puzzlebox.game.linkedin.LinkedInOwnResult
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead.Rejected.Kind
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInAccountSource
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInLink
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The day sync over real in-memory Room and scripted fakes. Every reply a test needs is scripted:
 * an unscripted call answers Offline and stops the sync. `source.calls` is the proof of what was sent.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomLinkedInDaysSyncTest {

    private val zip = GameId("zip")
    private val today = LocalDate.of(2026, 11, 5)
    private var now = 1_800_000_000_000L
    private val link = FakeLinkedInLink(LinkState.Connected("me"))
    private val source = FakeLinkedInAccountSource()
    private val completions = FakeCompletionRepository()
    private val calendar = FakeGameCalendar(today)
    private val daily = FakeDailyRepository().apply {
        setPuzzleNumbers(zip) { number(it) }
        setLinkedInGameType(zip, 6)
    }
    private lateinit var db: LinkedInDatabase
    private lateinit var days: RoomLinkedInDays

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), LinkedInDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        days = newDays()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun newDays(
        source: LinkedInAccountSource = this.source,
        budget: LinkedInCallBudget = LinkedInCallBudget({ now }, limit = 100),
    ) = RoomLinkedInDays(link, source, completions, daily, calendar, TestGames.registry(listOf(zip)), db, budget) { now }

    private fun number(day: LocalDate): Int = 700 + ChronoUnit.DAYS.between(EPOCH, day).toInt()

    private fun ago(days: Long): LocalDate = today.minusDays(days)

    private fun delta(day: LocalDate, today: LocalDate): Int = ChronoUnit.DAYS.between(day, today).toInt()

    private fun ownCall(day: LocalDate) = "own:me:6:${number(day)}"

    private fun boardCall(day: LocalDate, today: LocalDate = this.today) =
        "board:me:6:${number(today)}:${delta(day, today)}"

    private fun respondOwn(day: LocalDate, reply: LinkedInRead<LinkedInOwnResult> = LinkedInRead.Ok(SOLVED)) =
        source.respondOwn("me", 6, number(day), reply)

    private fun respondBoard(
        day: LocalDate,
        reply: LinkedInRead<LinkedInBoard> = LinkedInRead.Ok(LinkedInBoard(emptyList())),
        today: LocalDate = this.today,
    ) = source.respondBoard("me", 6, number(today), delta(day, today), reply)

    private fun held(day: LocalDate) = GatedLinkedInAccountSource(source, number(day))

    private fun boardCalls() = source.calls.filter { it.startsWith("board:") }

    private suspend fun logged(day: LocalDate, item: LinkedInItem, profileId: String = "me"): String? =
        db.fetchLogDao().get(zip.value, day.toString(), item.name, profileId)?.outcome

    private suspend fun ghosts(day: LocalDate) = db.ghostDao().observe(zip.value, day.toString()).first()

    private suspend fun imports() = completions.observeImports().first()

    @Test
    fun `a rejected own result latches the pause for every later sync until resume`() = runTest {
        Kind.entries.forEachIndexed { index, kind ->
            val day = ago(index.toLong())
            respondOwn(day, LinkedInRead.Rejected(kind))
            val before = source.calls.size

            assertEquals(LinkedInSync.Paused(kind), days.sync(zip, day))
            assertEquals(before + 1, source.calls.size)
            assertEquals(LinkState.Paused("me", kind), link.state.first())

            assertEquals(LinkedInSync.Paused(kind), days.sync(zip, day))
            assertEquals(LinkedInSync.Paused(kind), days.sync(zip, day.minusDays(1)))
            // A new instance over the same link and database is the process restart.
            assertEquals(LinkedInSync.Paused(kind), newDays().sync(zip, day))
            assertEquals(before + 1, source.calls.size)

            link.resume()
            assertEquals(LinkedInSync.Paused(kind), days.sync(zip, day))
            assertEquals(before + 2, source.calls.size)
            link.resume()
        }
    }

    @Test
    fun `a rejected board after a successful own result pauses and keeps the import`() = runTest {
        val day = ago(3)
        respondOwn(day)
        respondBoard(day, LinkedInRead.Rejected(Kind.RateLimited))

        assertEquals(LinkedInSync.Paused(Kind.RateLimited), days.sync(zip, day))
        assertEquals(LinkState.Paused("me", Kind.RateLimited), link.state.first())
        assertEquals(1, imports().size)
        assertNull(logged(day, LinkedInItem.Board))

        assertEquals(LinkedInSync.Paused(Kind.RateLimited), days.sync(zip, day))
        assertEquals(listOf(ownCall(day), boardCall(day)), source.calls)
    }

    @Test
    fun `a signed-out own read pauses as an expired session without a further call`() = runTest {
        val day = ago(3)
        respondOwn(day, LinkedInRead.SignedOut)
        respondBoard(day)

        assertEquals(LinkedInSync.Paused(Kind.SessionExpired), days.sync(zip, day))
        assertEquals(LinkState.Paused("me", Kind.SessionExpired), link.state.first())
        assertEquals(LinkedInSync.Paused(Kind.SessionExpired), days.sync(zip, day))
        assertEquals(listOf(ownCall(day)), source.calls)
    }

    @Test
    fun `an unlinked install or a game without a LinkedIn type makes no call`() = runTest {
        respondOwn(today)
        respondBoard(today)

        assertEquals(LinkedInSync.NotLinkedInGame, days.sync(GameId("strands"), today))

        link.disconnect()
        assertEquals(LinkedInSync.NotLinked, days.sync(zip, today))
        // The next view after a disconnect, through a new instance too.
        assertEquals(LinkedInSync.NotLinked, newDays().sync(zip, today))
        assertTrue(source.calls.isEmpty())
    }

    @Test
    fun `a future day or a day without an edition makes no call, and a missing today number skips the board`() = runTest {
        val unnumbered = ago(2)
        daily.setPuzzleNumbers(zip) { day -> number(day).takeIf { day != unnumbered && day != today } }
        respondOwn(ago(1))

        assertEquals(LinkedInSync.NotLinkedInGame, days.sync(zip, today.plusDays(1)))
        assertEquals(LinkedInSync.NotLinkedInGame, days.sync(zip, unnumbered))
        assertTrue(source.calls.isEmpty())

        assertEquals(LinkedInSync.Done, days.sync(zip, ago(1)))
        assertEquals(listOf(ownCall(ago(1))), source.calls)
    }

    @Test
    fun `a repeat view inside the cache windows makes no call`() = runTest {
        val recent = ago(3)
        val old = ago(8)
        respondOwn(recent)
        respondBoard(recent)
        respondOwn(old)

        assertEquals(LinkedInSync.Done, days.sync(zip, recent))
        assertEquals(listOf(ownCall(recent), boardCall(recent)), source.calls)
        now += 30_000
        assertEquals(LinkedInSync.Done, days.sync(zip, recent))
        assertEquals(2, source.calls.size)

        // Past the board window only the own result is read.
        assertEquals(LinkedInSync.Done, days.sync(zip, old))
        assertEquals(LinkedInSync.Done, days.sync(zip, old))
        assertEquals(listOf(ownCall(recent), boardCall(recent), ownCall(old)), source.calls)
        assertEquals(LinkedInSync.Done, days.syncState.lastSync(DayKey(zip, old)).first())
    }

    @Test
    fun `a not-played own result is logged and asked again only after a day`() = runTest {
        val day = ago(8)
        respondOwn(day, LinkedInRead.Ok(LinkedInOwnResult.NotPlayed))

        assertEquals(LinkedInSync.Done, days.sync(zip, day))
        assertEquals(LinkedInOutcome.NotPlayed.name, logged(day, LinkedInItem.Own))
        assertTrue(imports().isEmpty())

        now += 23 * HOUR_MS
        assertEquals(LinkedInSync.Done, days.sync(zip, day))
        assertEquals(1, source.calls.size)

        now += HOUR_MS
        assertEquals(LinkedInSync.Done, days.sync(zip, day))
        assertEquals(listOf(ownCall(day), ownCall(day)), source.calls)
    }

    @Test
    fun `a solved result is imported once, also after the fetch log is wiped`() = runTest {
        val day = ago(3)
        respondOwn(day)
        respondBoard(day)

        assertEquals(LinkedInSync.Done, days.sync(zip, day))

        val record = imports().single()
        assertEquals(CompletionSource.LinkedIn, record.source)
        assertEquals(83_000L, record.time)
        assertEquals(0, record.hints)
        assertEquals(LinkedInOutcome.Solved.name, logged(day, LinkedInItem.Own))

        // What a disconnect then reconnect leaves behind: the import stays, the log is gone.
        db.fetchLogDao().clear()
        assertEquals(LinkedInSync.Done, days.sync(zip, day))
        assertEquals(2, source.calls.count { it == ownCall(day) })
        assertEquals(1, imports().size)
    }

    @Test
    fun `a board is stored as ghosts without the player's own row`() = runTest {
        val day = ago(3)
        val rows = listOf("friend", "me", "rival").map { LinkedInBoard.Row(it, it, 60, hintFree = true, flawless = false) }
        respondOwn(day)
        respondBoard(day, LinkedInRead.Ok(LinkedInBoard(rows)))

        assertEquals(LinkedInSync.Done, days.sync(zip, day))

        assertEquals(setOf("friend", "rival"), ghosts(day).map { it.memberRef }.toSet())
        assertEquals(now, ghosts(day).first().fetchedAtMs)
        assertEquals(LinkedInOutcome.Board.name, logged(day, LinkedInItem.Board))
    }

    @Test
    fun `a board read today is read once more as a past day`() = runTest {
        val day = today
        respondOwn(day)
        respondBoard(day)

        assertEquals(LinkedInSync.Done, days.sync(zip, day))
        assertEquals(listOf(boardCall(day)), boardCalls())
        assertEquals(LinkedInOutcome.BoardToday.name, logged(day, LinkedInItem.Board))

        val nextDay = today.plusDays(1)
        calendar.setAll(nextDay)
        now += 24 * HOUR_MS
        respondBoard(day, today = nextDay)
        assertEquals(LinkedInSync.Done, days.sync(zip, day))
        assertEquals(listOf(boardCall(day), boardCall(day, nextDay)), boardCalls())
        assertEquals(LinkedInOutcome.Board.name, logged(day, LinkedInItem.Board))

        calendar.setAll(today.plusDays(2))
        now += 24 * HOUR_MS
        assertEquals(LinkedInSync.Done, days.sync(zip, day))
        assertEquals(2, boardCalls().size)
    }

    @Test
    fun `an offline read is not logged and the next sync asks again`() = runTest {
        val day = ago(3)
        respondOwn(day, LinkedInRead.Offline)

        assertEquals(LinkedInSync.Offline, days.sync(zip, day))
        assertNull(logged(day, LinkedInItem.Own))
        assertEquals(LinkState.Connected("me"), link.state.first())
        assertEquals(LinkedInSync.Offline, days.syncState.lastSync(DayKey(zip, day)).first())

        assertEquals(LinkedInSync.Offline, days.sync(zip, day))
        assertEquals(listOf(ownCall(day), ownCall(day)), source.calls)
    }

    @Test
    fun `an item over the call budget is not sent and is fetched after the window`() = runTest {
        val day = ago(3)
        val days = newDays(budget = LinkedInCallBudget({ now }, limit = 1))
        respondOwn(day)
        respondBoard(day)

        assertEquals(LinkedInSync.OverBudget, days.sync(zip, day))
        assertEquals(listOf(ownCall(day)), source.calls)
        assertEquals(LinkState.Connected("me"), link.state.first())

        now += 60_000
        assertEquals(LinkedInSync.Done, days.sync(zip, day))
        assertEquals(listOf(ownCall(day), boardCall(day)), source.calls)
    }

    @Test
    fun `two concurrent syncs of one day make one own call and one board call`() = runTest {
        val day = ago(3)
        val key = DayKey(zip, day)
        val held = held(day)
        val days = newDays(held)
        respondOwn(day)
        respondBoard(day)

        val first = async { days.sync(zip, day) }
        held.entered.await()
        val second = async { days.sync(zip, day) }
        runCurrent()
        assertTrue(days.syncState.inFlight(key).first())
        held.gate.complete(Unit)

        assertEquals(LinkedInSync.Done, first.await())
        assertEquals(LinkedInSync.Done, second.await())
        assertEquals(listOf(ownCall(day), boardCall(day)), source.calls)
        assertFalse(days.syncState.inFlight(key).first())
        assertEquals(LinkedInSync.Done, days.syncState.lastSync(key).first())
    }

    @Test
    fun `a sync of another day is not blocked by a held one`() = runTest {
        val day = ago(3)
        val other = ago(4)
        val held = held(day)
        val days = newDays(held)
        respondOwn(day)
        respondBoard(day)
        respondOwn(other)
        respondBoard(other)

        val first = async { days.sync(zip, day) }
        held.entered.await()

        assertEquals(LinkedInSync.Done, days.sync(zip, other))
        assertTrue(first.isActive)
        held.gate.complete(Unit)
        assertEquals(LinkedInSync.Done, first.await())
    }

    @Test
    fun `a waiter finishes by itself when the first caller is cancelled`() = runTest {
        val day = ago(3)
        val held = held(day)
        val days = newDays(held)
        respondOwn(day)
        respondBoard(day)

        val first = async { days.sync(zip, day) }
        held.entered.await()
        val second = async { days.sync(zip, day) }
        runCurrent()
        first.cancelAndJoin()
        held.gate.complete(Unit)

        assertEquals(LinkedInSync.Done, second.await())
        assertEquals(listOf(ownCall(day), ownCall(day), boardCall(day)), source.calls)
        assertEquals(LinkedInOutcome.Solved.name, logged(day, LinkedInItem.Own))
    }

    @Test
    fun `a failed sync fails its waiter too, without a second call`() = runTest {
        val day = ago(3)
        val held = held(day)
        val days = newDays(held)
        respondOwn(day)
        completions.fail = true

        supervisorScope {
            val first = async { days.sync(zip, day) }
            held.entered.await()
            val second = async { days.sync(zip, day) }
            runCurrent()
            held.gate.complete(Unit)

            assertTrue(runCatching { first.await() }.exceptionOrNull() is IllegalStateException)
            assertTrue(runCatching { second.await() }.exceptionOrNull() is IllegalStateException)
        }
        assertEquals(listOf(ownCall(day)), source.calls)
        assertFalse(days.syncState.inFlight(DayKey(zip, day)).first())
    }

    @Test
    fun `a disconnect during an in-flight sync writes nothing`() =
        linkChangeWritesNothing(LinkedInSync.NotLinked) { link.disconnect() }

    @Test
    fun `a reconnect as another member during an in-flight sync writes nothing`() =
        linkChangeWritesNothing(LinkedInSync.NotLinked) { link.connect("other", now) }

    @Test
    fun `a pause from another day during an in-flight sync writes nothing`() =
        linkChangeWritesNothing(LinkedInSync.Paused(Kind.Challenge)) { link.pause(Kind.Challenge) }

    private fun linkChangeWritesNothing(expected: LinkedInSync, change: suspend () -> Unit) = runTest {
        val day = ago(3)
        val held = held(day)
        val days = newDays(held)
        respondOwn(day)
        respondBoard(day)

        val result = async { days.sync(zip, day) }
        held.entered.await()
        change()
        held.gate.complete(Unit)

        assertEquals(expected, result.await())
        assertEquals(listOf(ownCall(day)), source.calls)
        assertTrue(imports().isEmpty())
        assertNull(logged(day, LinkedInItem.Own))
        assertNull(logged(day, LinkedInItem.Own, profileId = "other"))
        assertTrue(ghosts(day).isEmpty())
        assertNull(days.syncState.lastSync(DayKey(zip, day)).first())
        assertFalse(days.syncState.inFlight(DayKey(zip, day)).first())
    }

    /** Holds the own-result read of one puzzle open until [gate] completes; every other call passes straight through. */
    private class GatedLinkedInAccountSource(
        private val delegate: LinkedInAccountSource,
        private val heldNumber: Int,
    ) : LinkedInAccountSource by delegate {
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()

        override suspend fun ownResult(
            profileId: String,
            gameTypeId: Int,
            puzzleNumber: Int,
        ): LinkedInRead<LinkedInOwnResult> {
            val read = delegate.ownResult(profileId, gameTypeId, puzzleNumber)
            if (puzzleNumber == heldNumber) {
                entered.complete(Unit)
                gate.await()
            }
            return read
        }
    }

    private companion object {
        val EPOCH: LocalDate = LocalDate.of(2026, 1, 1)
        val SOLVED = LinkedInOwnResult.Solved(83, hintFree = true)
        const val HOUR_MS = 3_600_000L
    }
}
