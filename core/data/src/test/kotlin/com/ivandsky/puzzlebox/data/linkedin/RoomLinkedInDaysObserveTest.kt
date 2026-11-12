package com.ivandsky.puzzlebox.data.linkedin

import androidx.room.Room
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.ivandsky.puzzlebox.database.linkedin.LinkedInDatabase
import com.ivandsky.puzzlebox.game.CompletionRecord
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
import kotlin.io.path.createTempDirectory
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
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
 * What one day reports to its observer, over real in-memory Room and scripted fakes. Room answers on
 * its own threads, so the order of the emissions between two settled states is not fixed: a test
 * waits for the state it expects and reads that emission, never a position in the stream.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomLinkedInDaysObserveTest {

    private val zip = GameId("zip")
    private val queens = GameId("queens")
    private val registry = TestGames.registry(listOf(zip, queens))
    private val today = LocalDate.of(2026, 11, 5)
    private var now = 1_800_000_000_000L
    private val link = FakeLinkedInLink(LinkState.Connected("me"))
    private val source = FakeLinkedInAccountSource()
    private val avatars = testAvatars(createTempDirectory("avatars").toFile())
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
    ) = RoomLinkedInDays(link, source, completions, daily, calendar, registry, db, budget, avatars, LinkedInStoreLock(), FakeOwnerScope()) { now }

    private fun number(day: LocalDate): Int = 700 + ChronoUnit.DAYS.between(EPOCH, day).toInt()

    private fun ago(days: Long): LocalDate = today.minusDays(days)

    private fun delta(day: LocalDate): Int = ChronoUnit.DAYS.between(day, today).toInt()

    private fun respondOwn(day: LocalDate, reply: LinkedInRead<LinkedInOwnResult> = LinkedInRead.Ok(SOLVED)) =
        source.respondOwn("me", 6, number(day), reply)

    private fun respondBoard(day: LocalDate, board: LinkedInBoard) =
        source.respondBoard("me", 6, number(today), delta(day), LinkedInRead.Ok(board))

    private fun import(game: GameId, day: LocalDate): CompletionRecord =
        checkNotNull(LinkedInImport.toRecord(game, day, number(day), SOLVED, registry))

    /** The first emission that has reached [settled]; what came before it is the unordered way there. */
    private suspend fun ReceiveTurbine<LinkedInDayData>.awaitWhere(
        settled: (LinkedInDayData) -> Boolean,
    ): LinkedInDayData {
        while (true) {
            val data = awaitItem()
            if (settled(data)) return data
        }
    }

    @Test
    fun `a connected day with nothing known reports Unknown, no ghosts and no board time`() = runTest {
        days.observe(zip, ago(3)).test {
            assertEquals(
                LinkedInDayData(
                    link = LinkState.Connected("me"),
                    delta = 3,
                    boardInWindow = true,
                    inFlight = false,
                    lastSync = null,
                    own = LinkedInOwnState.Unknown,
                    ghosts = emptyList(),
                    boardFetchedAtMs = null,
                ),
                awaitItem(),
            )
        }
    }

    @Test
    fun `an import for the day shows as Imported`() = runTest {
        val day = ago(3)
        completions.onCompleted(import(zip, ago(4)))
        completions.onCompleted(import(queens, day))

        days.observe(zip, day).test {
            assertEquals(LinkedInOwnState.Unknown, awaitItem().own)

            completions.onCompleted(import(zip, day))
            assertEquals(LinkedInOwnState.Imported(import(zip, day)), awaitItem().own)

            // A disconnect keeps the imports, and the day still shows the result as LinkedIn's.
            link.disconnect()
            val disconnected = awaitItem()
            assertEquals(LinkState.NotConnected, disconnected.link)
            assertEquals(LinkedInOwnState.Imported(import(zip, day)), disconnected.own)
        }
    }

    @Test
    fun `a not-played result shows as NotPlayed`() = runTest {
        val day = ago(8)
        respondOwn(day, LinkedInRead.Ok(LinkedInOwnResult.NotPlayed))

        days.observe(zip, day).test {
            assertEquals(LinkedInOwnState.Unknown, awaitItem().own)

            assertEquals(LinkedInSync.Done, days.sync(zip, day))
            awaitWhere { it.own == LinkedInOwnState.NotPlayed }

            // The not-played row is still in the log; a result that arrives later wins over it.
            completions.onCompleted(import(zip, day))
            assertEquals(LinkedInOwnState.Imported(import(zip, day)), awaitWhere { it.own != LinkedInOwnState.NotPlayed }.own)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ghosts appear after a board sync and stay on disconnect`() = runTest {
        val day = ago(2)
        val rows = listOf("friend", "me", "rival").map { LinkedInBoard.Row(it, it, 60, hintFree = true, flawless = false) }
        respondOwn(day)
        respondBoard(day, LinkedInBoard(rows))

        days.observe(zip, day).test {
            assertTrue(awaitItem().ghosts.isEmpty())

            assertEquals(LinkedInSync.Done, days.sync(zip, day))
            val synced = awaitWhere { it.ghosts.isNotEmpty() && it.boardFetchedAtMs != null }
            assertEquals(
                setOf("friend", "rival").map { LinkedInGhost(it, it, 60_000, hintFree = true, flawless = false) }.toSet(),
                synced.ghosts.toSet(),
            )
            assertEquals(now, synced.boardFetchedAtMs)
            assertTrue(synced.boardComplete)

            // The board was read and is the player's own record: a disconnect keeps it, saved by the same read.
            link.disconnect()
            val disconnected = awaitWhere { it.link == LinkState.NotConnected }
            assertEquals(synced.ghosts.toSet(), disconnected.ghosts.toSet())
            assertEquals(now, disconnected.boardFetchedAtMs)
            assertEquals(true, disconnected.boardInWindow)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the player's own board row is the owner, never a ghost, and stays on disconnect`() = runTest {
        val day = ago(2)
        val rows = listOf("friend", "me", "rival").map { LinkedInBoard.Row(it, if (it == "me") "Me Myself" else it, 60, hintFree = true, flawless = false) }
        respondOwn(day)
        respondBoard(day, LinkedInBoard(rows))

        days.observe(zip, day).test {
            assertNull(awaitItem().owner)

            assertEquals(LinkedInSync.Done, days.sync(zip, day))
            val synced = awaitWhere { it.owner != null }
            assertEquals(LinkedInOwner("Me Myself", photoPath = null), synced.owner)
            assertEquals(setOf("friend", "rival"), synced.ghosts.map { it.memberRef }.toSet())

            // Told apart by the board's profile, so the player is still the owner without a link.
            link.disconnect()
            val disconnected = awaitWhere { it.link == LinkState.NotConnected }
            assertEquals(LinkedInOwner("Me Myself", photoPath = null), disconnected.owner)
            assertEquals(setOf("friend", "rival"), disconnected.ghosts.map { it.memberRef }.toSet())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `inFlight is true while a sync runs`() = runTest {
        val day = ago(3)
        val held = GatedLinkedInAccountSource(source, number(day))
        val days = newDays(held)

        days.observe(zip, day).test {
            assertFalse(awaitItem().inFlight)

            val sync = async { days.sync(zip, day) }
            held.entered.await()
            assertTrue(awaitItem().inFlight)

            held.gate.complete(Unit)
            assertEquals(LinkedInSync.Offline, sync.await())
            assertEquals(LinkedInSync.Offline, awaitWhere { !it.inFlight }.lastSync)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `lastSync reports Done and Offline only`() = runTest {
        val day = ago(8)

        days.observe(zip, day).test {
            assertNull(awaitItem().lastSync)

            assertEquals(LinkedInSync.Offline, days.sync(zip, day))
            awaitWhere { it.lastSync == LinkedInSync.Offline && !it.inFlight }

            respondOwn(day)
            assertEquals(LinkedInSync.Done, days.sync(zip, day))
            awaitWhere { it.lastSync == LinkedInSync.Done && !it.inFlight }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a rejection is reported by the link and never as a last sync`() = runTest {
        val day = ago(8)
        respondOwn(day, LinkedInRead.Rejected(Kind.Challenge))

        days.observe(zip, day).test {
            assertNull(awaitItem().lastSync)

            assertEquals(LinkedInSync.Paused(Kind.Challenge), days.sync(zip, day))
            val paused = awaitWhere { data ->
                assertNull(data.lastSync)
                data.link is LinkState.Paused
            }
            assertEquals(LinkState.Paused("me", Kind.Challenge), paused.link)
            assertFalse(paused.inFlight)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a disconnect and reconnect forgets the last sync`() = runTest {
        val day = ago(3)

        days.observe(zip, day).test {
            assertEquals(LinkedInSync.Offline, days.sync(zip, day))
            awaitWhere { it.lastSync == LinkedInSync.Offline && !it.inFlight }

            link.disconnect()
            assertNull(awaitWhere { it.link == LinkState.NotConnected }.lastSync)

            link.connect("me", now)
            assertNull(awaitWhere { it.link == LinkState.Connected("me") }.lastSync)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `delta and boardInWindow follow the game's calendar`() = runTest {
        // Every other game is a month ahead, so only the viewed game's own today gives these numbers.
        calendar.setAll(today.plusDays(30))
        calendar.set(zip, today)

        suspend fun window(day: LocalDate) = days.observe(zip, day).first().let { it.delta to it.boardInWindow }

        assertEquals(0 to true, window(today))
        assertEquals(3 to true, window(ago(3)))
        assertEquals(7 to true, window(ago(7)))
        assertEquals(8 to false, window(ago(8)))
        assertEquals(-1 to false, window(today.plusDays(1)))
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
    }
}
