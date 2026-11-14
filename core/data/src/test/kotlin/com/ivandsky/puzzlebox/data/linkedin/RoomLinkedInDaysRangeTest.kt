package com.ivandsky.puzzlebox.data.linkedin

import androidx.room.Room
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.ivandsky.puzzlebox.database.linkedin.GhostEntity
import com.ivandsky.puzzlebox.database.linkedin.LinkedInDatabase
import com.ivandsky.puzzlebox.database.linkedin.SavedBoardEntity
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
import com.ivandsky.puzzlebox.testing.room.RoomQueryThread
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * One game's days read as one range, over real in-memory Room: every day of the range reports what that day's own
 * read reports. Room answers on its own threads, so a live test waits for the state it expects, never a position
 * in the stream.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomLinkedInDaysRangeTest {

    private val zip = GameId("zip")
    private val queens = GameId("queens")
    private val registry = TestGames.registry(listOf(zip, queens))
    private val today = LocalDate.of(2026, 11, 30)
    private val first = LocalDate.of(2026, 11, 1)
    private val month = (0L until 30).map { first.plusDays(it) }
    private val link = FakeLinkedInLink(LinkState.Connected("me"))
    private val source = FakeLinkedInAccountSource()
    private val completions = FakeCompletionRepository()
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
            .setQueryExecutor(RoomQueryThread.executor)
            .build()
        days = newDays(source)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun newDays(source: LinkedInAccountSource) = RoomLinkedInDays(
        link, source, completions, daily, FakeGameCalendar(today), registry, db, LinkedInCallBudget({ NOW }, limit = 100),
        testAvatars(createTempDirectory("avatars").toFile()), LinkedInStoreLock(), FakeOwnerScope(visible = "uid-1"),
    ) { NOW }

    private fun number(day: LocalDate): Int = 700 + ChronoUnit.DAYS.between(EPOCH, day).toInt()

    private fun november(dayOfMonth: Int): LocalDate = first.withDayOfMonth(dayOfMonth)

    /** A saved board and its ghosts, written the way a board read leaves them. */
    private suspend fun store(game: GameId, day: LocalDate, ownerUid: String?, vararg rows: Row, profileId: String = "me") {
        db.savedBoardDao().upsert(SavedBoardEntity(game.value, day.toString(), profileId, ownerUid, NOW, true, null))
        db.ghostDao().replace(
            game.value, day.toString(),
            rows.map { GhostEntity(game.value, day.toString(), it.memberRef, it.name, it.timeMs, hintFree = true, flawless = false, fetchedAtMs = NOW) },
        )
    }

    private suspend fun import(game: GameId, day: LocalDate) =
        completions.onCompleted(checkNotNull(LinkedInImport.toRecord(game, day, number(day), SOLVED, registry)))

    /**
     * A month holding every kind of day the per-day read tells apart: boards with and without the player's own
     * row, one read by another profile, one of another account, imports, a not-played day and a failed sync,
     * with boards of the days around the month and of another game beside them.
     */
    private suspend fun seedMonth() {
        // Rows tied on time and written against their member-ref order: a tie keeps the order the board is read in.
        store(zip, november(3), null, Row("rival", "Rival"), Row("me", "Me Myself"), Row("friend", "Friend"))
        store(zip, november(10), "uid-1", Row("friend", "Friend", timeMs = null))
        store(zip, november(12), "uid-1", Row("earlier", "Earlier Me"), Row("friend", "Friend"), profileId = "earlier")
        store(zip, november(14), "uid-2", Row("stranger", "Stranger"))
        store(zip, first.minusDays(1), null, Row("before", "Before"))
        store(zip, november(30).plusDays(1), null, Row("after", "After"))
        store(queens, november(3), null, Row("queen", "Queen"))
        import(zip, november(3))
        import(zip, november(5))
        import(queens, november(6))
        source.respondOwn("me", 6, number(november(20)), LinkedInRead.Ok(LinkedInOwnResult.NotPlayed))
        assertEquals(LinkedInSync.Done, days.sync(zip, november(20)))
        assertEquals(LinkedInSync.Offline, days.sync(zip, november(21)))
    }

    private suspend fun perDay(): Map<LocalDate, LinkedInDayData> = month.associateWith { days.observe(zip, it).first() }

    private suspend fun ReceiveTurbine<Map<LocalDate, LinkedInDayData>>.awaitWhere(
        settled: (Map<LocalDate, LinkedInDayData>) -> Boolean,
    ): Map<LocalDate, LinkedInDayData> {
        while (true) {
            val range = awaitItem()
            if (settled(range)) return range
        }
    }

    @Test
    fun `a seeded month reads every day as that day's own read does`() = runTest {
        seedMonth()

        val range = days.observeRange(zip, first, november(30)).first()

        assertEquals(perDay(), range)
        assertEquals(month, range.keys.toList())
        assertEquals(listOf("friend", "rival"), range.getValue(november(3)).ghosts.map { it.memberRef })
        assertEquals(LinkedInOwner("Me Myself", photoPath = null), range.getValue(november(10)).owner)
        assertEquals(LinkedInOwner("Earlier Me", photoPath = null), range.getValue(november(12)).owner)
        assertEquals(null, range.getValue(november(14)).boardFetchedAtMs)
        assertEquals(LinkedInOwnState.NotPlayed, range.getValue(november(20)).own)
        assertEquals(LinkedInSync.Offline, range.getValue(november(21)).lastSync)
    }

    @Test
    fun `a disconnected phone reads the month as the per-day reads do`() = runTest {
        seedMonth()
        link.disconnect()

        val range = days.observeRange(zip, first, november(30)).first()

        assertEquals(perDay(), range)
        assertEquals(listOf("friend", "rival"), range.getValue(november(3)).ghosts.map { it.memberRef })
        assertEquals(LinkedInOwnState.Unknown, range.getValue(november(20)).own)
    }

    @Test
    fun `a board saved for a day of the range shows on that day`() = runTest {
        days.observeRange(zip, first, november(30)).test {
            assertEquals(emptyList<LinkedInGhost>(), awaitItem().getValue(november(8)).ghosts)

            store(zip, november(8), null, Row("friend", "Friend"))

            val saved = awaitWhere { it.getValue(november(8)).ghosts.isNotEmpty() }
            assertEquals(listOf(november(8)), saved.filterValues { it.boardFetchedAtMs != null }.keys.toList())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a running sync shows on its own day only`() = runTest {
        val held = GatedLinkedInAccountSource(source, number(november(21)))
        val days = newDays(held)

        days.observeRange(zip, first, november(30)).test {
            assertEquals(emptySet<LocalDate>(), awaitItem().filterValues { it.inFlight }.keys)

            val sync = async { days.sync(zip, november(21)) }
            held.entered.await()
            assertEquals(setOf(november(21)), awaitWhere { range -> range.values.any { it.inFlight } }.filterValues { it.inFlight }.keys)

            held.gate.complete(Unit)
            assertEquals(LinkedInSync.Offline, sync.await())
            val ended = awaitWhere { range -> range.values.none { it.inFlight } && range.values.any { it.lastSync != null } }
            assertEquals(mapOf(november(21) to LinkedInSync.Offline), ended.mapValues { it.value.lastSync }.filterValues { it != null })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a range that ends before it starts has no days`() = runTest {
        assertEquals(emptyMap<LocalDate, LinkedInDayData>(), days.observeRange(zip, november(30), first).first())
    }

    private class Row(val memberRef: String, val name: String, val timeMs: Long? = 60_000L)

    /** Holds the own-result read of one puzzle open until [gate] completes; every other call passes straight through. */
    private class GatedLinkedInAccountSource(private val delegate: LinkedInAccountSource, private val heldNumber: Int) : LinkedInAccountSource by delegate {
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()

        override suspend fun ownResult(profileId: String, gameTypeId: Int, puzzleNumber: Int): LinkedInRead<LinkedInOwnResult> {
            val read = delegate.ownResult(profileId, gameTypeId, puzzleNumber)
            if (puzzleNumber == heldNumber) {
                entered.complete(Unit)
                gate.await()
            }
            return read
        }
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        val EPOCH: LocalDate = LocalDate.of(2026, 1, 1)
        val SOLVED = LinkedInOwnResult.Solved(83, hintFree = true)
    }
}
