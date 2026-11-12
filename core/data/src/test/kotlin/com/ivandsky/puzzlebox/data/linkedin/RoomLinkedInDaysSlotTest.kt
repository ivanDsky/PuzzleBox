package com.ivandsky.puzzlebox.data.linkedin

import androidx.room.Room
import com.ivandsky.puzzlebox.database.linkedin.FetchLogEntity
import com.ivandsky.puzzlebox.database.linkedin.LinkedInDatabase
import com.ivandsky.puzzlebox.game.linkedin.LinkedInAccountSource
import com.ivandsky.puzzlebox.game.linkedin.LinkedInBoard
import com.ivandsky.puzzlebox.game.linkedin.LinkedInOwnResult
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead.Rejected.Kind
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.cloud.FakeOwnerScope
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInAccountSource
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInLink
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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
 * A shown day waits for a free call slot instead of giving up. The budget's clock is the test
 * scheduler's, so the wait is virtual time, and every call is stamped with the virtual time it was sent.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomLinkedInDaysSlotTest {

    private val zip = GameId("zip")
    private val today = LocalDate.of(2026, 11, 5)
    private val link = FakeLinkedInLink(LinkState.Connected("me"))
    private val source = FakeLinkedInAccountSource()
    private val completions = FakeCompletionRepository()
    private val daily = FakeDailyRepository().apply {
        setPuzzleNumbers(zip) { number(it) }
        setLinkedInGameType(zip, 6)
    }
    private lateinit var db: LinkedInDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), LinkedInDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun number(day: LocalDate): Int = 700 + ChronoUnit.DAYS.between(EPOCH, day).toInt()

    private fun ago(days: Long): LocalDate = today.minusDays(days)

    private fun respondAll(day: LocalDate) {
        source.respondOwn("me", 6, number(day), LinkedInRead.Ok(LinkedInOwnResult.Solved(83, hintFree = true)))
        source.respondBoard("me", 6, number(today), ChronoUnit.DAYS.between(day, today).toInt(), LinkedInRead.Ok(LinkedInBoard(emptyList())))
    }

    /** The own result of [day] is already read, so the sync's only call is its board. */
    private suspend fun ownAlreadyRead(day: LocalDate) {
        db.fetchLogDao().put(
            FetchLogEntity(zip.value, day.toString(), LinkedInItem.Own.name, "me", FETCHED_AT_MS, LinkedInOutcome.Solved.name),
        )
    }

    /** A sync over a budget of [limit] calls a minute whose slots are all spent at virtual time 0. */
    private fun TestScope.spentBudget(limit: Int, stamped: StampedLinkedInAccountSource): Pair<RoomLinkedInDays, Spent> {
        val spent = Spent()
        val budget = LinkedInCallBudget(
            nowMs = {
                if (spent.all) spent.waiting.complete(Unit)
                FETCHED_AT_MS + testScheduler.currentTime
            },
            limit = limit,
        )
        repeat(limit) { check(budget.tryAcquire()) }
        spent.all = true
        val days = RoomLinkedInDays(
            link, stamped, completions, daily, FakeGameCalendar(today), TestGames.registry(listOf(zip)), db, budget,
            testAvatars(createTempDirectory("avatars").toFile()), LinkedInStoreLock(), FakeOwnerScope(),
        ) { FETCHED_AT_MS + testScheduler.currentTime }
        return days to spent
    }

    @Test
    fun `with the cap used up a shown day is read once a slot frees`() = runTest {
        val day = ago(3)
        respondAll(day)
        val stamped = StampedLinkedInAccountSource(source) { testScheduler.currentTime }
        val (days, _) = spentBudget(limit = 2, stamped)

        assertEquals(LinkedInSync.Done, days.sync(zip, day))

        // Both slots were spent at 0, so both free together at 60 s.
        assertEquals(listOf("own@60000", "board@60000"), stamped.sent)
        assertEquals(LinkedInOutcome.Board.name, db.fetchLogDao().get(zip.value, day.toString(), LinkedInItem.Board.name, "me")?.outcome)
        assertEquals(LinkedInSync.Done, days.syncState.lastSync(DayKey(zip, day)).first())
    }

    @Test
    fun `the day card is loading while it waits for a slot`() = runTest {
        val day = ago(3)
        respondAll(day)
        val stamped = StampedLinkedInAccountSource(source) { testScheduler.currentTime }
        val (days, spent) = spentBudget(limit = 1, stamped)

        val sync = async { days.sync(zip, day) }
        spent.waiting.await()
        advanceTimeBy(59_999)

        assertTrue(days.syncState.inFlight(DayKey(zip, day)).first())
        assertEquals(emptyList<String>(), stamped.sent)
        sync.await()
    }

    @Test
    fun `a day left while waiting makes no call`() = runTest {
        val day = ago(3)
        respondAll(day)
        ownAlreadyRead(day)
        val stamped = StampedLinkedInAccountSource(source) { testScheduler.currentTime }
        val (days, spent) = spentBudget(limit = 1, stamped)

        val sync = async { days.sync(zip, day) }
        spent.waiting.await()
        advanceTimeBy(30_000)
        sync.cancelAndJoin()
        advanceTimeBy(10 * 60_000)
        runCurrent()

        assertEquals(emptyList<String>(), stamped.sent)
        assertFalse(days.syncState.inFlight(DayKey(zip, day)).first())
        assertNull(db.fetchLogDao().get(zip.value, day.toString(), LinkedInItem.Board.name, "me"))
    }

    @Test
    fun `a pause while waiting stops the call`() = runTest {
        val day = ago(3)
        respondAll(day)
        ownAlreadyRead(day)
        val stamped = StampedLinkedInAccountSource(source) { testScheduler.currentTime }
        val (days, spent) = spentBudget(limit = 1, stamped)

        val sync = async { days.sync(zip, day) }
        spent.waiting.await()
        // Another day's call was rejected meanwhile: nothing more is sent, however long this one waited.
        link.pause(Kind.Challenge)

        assertEquals(LinkedInSync.Paused(Kind.Challenge), sync.await())
        assertEquals(emptyList<String>(), stamped.sent)
    }

    @Test
    fun `calls of several days waiting at once never exceed the cap`() = runTest {
        val stamped = StampedLinkedInAccountSource(source) { testScheduler.currentTime }
        val (days, _) = spentBudget(limit = 2, stamped)
        val shown = (1L..4L).map { ago(it) }
        shown.forEach { respondAll(it) }

        shown.map { async { days.sync(zip, it) } }.forEach { it.await() }

        val times = stamped.sent.map { it.substringAfterLast('@').toLong() }
        assertEquals(8, times.size)
        times.forEach { start ->
            assertTrue("calls in [$start, ${start + 60_000})", times.count { it in start until start + 60_000 } <= 2)
        }
    }

    /** Signals through [waiting] once every slot is spent and the sync first asks for one. */
    private class Spent {
        @Volatile
        var all = false
        val waiting = CompletableDeferred<Unit>()
    }

    /** Stamps each call that reaches the source with the virtual time it was sent, as `"own:…@<ms>"` or `"board:…@<ms>"`. */
    private class StampedLinkedInAccountSource(
        private val delegate: LinkedInAccountSource,
        private val nowMs: () -> Long,
    ) : LinkedInAccountSource by delegate {
        private val recorded = CopyOnWriteArrayList<String>()
        val sent: List<String> get() = recorded.toList()

        override suspend fun ownResult(profileId: String, gameTypeId: Int, puzzleNumber: Int): LinkedInRead<LinkedInOwnResult> {
            recorded += "own@${nowMs()}"
            return delegate.ownResult(profileId, gameTypeId, puzzleNumber)
        }

        override suspend fun board(profileId: String, gameTypeId: Int, todayNumber: Int, delta: Int): LinkedInRead<LinkedInBoard> {
            recorded += "board@${nowMs()}"
            return delegate.board(profileId, gameTypeId, todayNumber, delta)
        }
    }

    private companion object {
        val EPOCH: LocalDate = LocalDate.of(2026, 1, 1)
        const val FETCHED_AT_MS = 1_800_000_000_000L
    }
}
