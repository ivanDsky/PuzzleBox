package com.ivandsky.puzzlebox.data.linkedin

import androidx.room.Room
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.ivandsky.puzzlebox.database.linkedin.LinkedInDatabase
import com.ivandsky.puzzlebox.game.linkedin.LinkedInAccountSource
import com.ivandsky.puzzlebox.game.linkedin.LinkedInOwnResult
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead
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
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * What one account's day syncs leave behind never reaches another account, whatever the observer saw of
 * the link in between: the link here goes straight from one profile to the next, so no not-connected state
 * is there to clear anything.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomLinkedInDaysProfileTest {

    private val zip = GameId("zip")
    private val registry = TestGames.registry(listOf(zip))
    private val today = LocalDate.of(2026, 11, 5)
    private val day = today.minusDays(8)
    private var now = 1_800_000_000_000L
    private val link = FakeLinkedInLink(LinkState.Connected("a"))
    private val source = FakeLinkedInAccountSource()
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

    private fun newDays(source: LinkedInAccountSource = this.source) = RoomLinkedInDays(
        link,
        source,
        FakeCompletionRepository(),
        daily,
        FakeGameCalendar(today),
        registry,
        db,
        LinkedInCallBudget({ now }, limit = 100),
        testAvatars(createTempDirectory("avatars").toFile()),
        LinkedInStoreLock(),
        FakeOwnerScope(),
    ) { now }

    private fun number(day: LocalDate): Int = 700 + ChronoUnit.DAYS.between(LocalDate.of(2026, 1, 1), day).toInt()

    private suspend fun ReceiveTurbine<LinkedInDayData>.awaitWhere(settled: (LinkedInDayData) -> Boolean): LinkedInDayData {
        while (true) {
            val data = awaitItem()
            if (settled(data)) return data
        }
    }

    @Test
    fun `a day synced offline by one profile does not show as the next profile's last sync`() = runTest {
        val days = newDays()

        days.observe(zip, day).test {
            assertNull(awaitItem().lastSync)

            assertEquals(LinkedInSync.Offline, days.sync(zip, day))
            awaitWhere { it.lastSync == LinkedInSync.Offline && !it.inFlight }

            link.connect("b", now)
            val switched = awaitWhere { it.link == LinkState.Connected("b") }
            assertNull(switched.lastSync)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a profile's result comes back when the link returns to it`() = runTest {
        val days = newDays()
        source.respondOwn("a", 6, number(day), LinkedInRead.Ok(LinkedInOwnResult.Solved(83, hintFree = true)))

        days.observe(zip, day).test {
            assertNull(awaitItem().lastSync)
            assertEquals(LinkedInSync.Done, days.sync(zip, day))
            awaitWhere { it.lastSync == LinkedInSync.Done && !it.inFlight }

            link.connect("b", now)
            assertNull(awaitWhere { it.link == LinkState.Connected("b") }.lastSync)

            link.connect("a", now)
            assertEquals(LinkedInSync.Done, awaitWhere { it.link == LinkState.Connected("a") }.lastSync)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a sync running for one profile is not shared with the next profile`() = runTest {
        val held = HoldingLinkedInAccountSource(source, "a")
        val days = newDays(held)

        val first = async(start = CoroutineStart.UNDISPATCHED) { days.sync(zip, day) }
        held.entered.await()
        link.connect("b", now)

        // Started before the first sync ends, so a shared claim would hand it that sync's result.
        val second = async(start = CoroutineStart.UNDISPATCHED) { days.sync(zip, day) }
        held.gate.complete(Unit)
        first.await()

        assertEquals(LinkedInSync.Offline, second.await())
        assertTrue("own:b:6:${number(day)}" in source.calls)
    }

    /** Holds every own-result read made for [heldProfile] open until [gate] completes. */
    private class HoldingLinkedInAccountSource(
        private val delegate: LinkedInAccountSource,
        private val heldProfile: String,
    ) : LinkedInAccountSource by delegate {
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()

        override suspend fun ownResult(
            profileId: String,
            gameTypeId: Int,
            puzzleNumber: Int,
        ): LinkedInRead<LinkedInOwnResult> {
            val read = delegate.ownResult(profileId, gameTypeId, puzzleNumber)
            if (profileId == heldProfile) {
                entered.complete(Unit)
                gate.await()
            }
            return read
        }
    }
}
