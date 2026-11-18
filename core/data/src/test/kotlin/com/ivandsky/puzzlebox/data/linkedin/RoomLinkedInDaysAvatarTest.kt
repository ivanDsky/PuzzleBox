package com.ivandsky.puzzlebox.data.linkedin

import androidx.room.Room
import com.ivandsky.puzzlebox.testing.room.RoomQueryThread
import app.cash.turbine.test
import com.ivandsky.puzzlebox.database.linkedin.FetchLogDao
import com.ivandsky.puzzlebox.database.linkedin.FetchLogEntity
import com.ivandsky.puzzlebox.database.linkedin.LinkedInDatabase
import com.ivandsky.puzzlebox.game.linkedin.LinkedInAccountSource
import com.ivandsky.puzzlebox.game.linkedin.LinkedInBoard
import com.ivandsky.puzzlebox.game.linkedin.LinkedInOwnResult
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPhotoSource
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead.Rejected.Kind
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.cloud.FakeOwnerScope
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInAccountSource
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInLink
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInPhotoSource
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
 * A board read brings the connections' photos with it, once each, and a photo that cannot be fetched
 * costs the sync nothing: no pause, no budget, no failure for the day.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomLinkedInDaysAvatarTest {

    private val zip = GameId("zip")
    private val today = LocalDate.of(2026, 11, 5)
    private var now = 1_800_000_000_000L
    private val link = FakeLinkedInLink(LinkState.Connected("me"))
    private val source = FakeLinkedInAccountSource()
    private val photos = FakeLinkedInPhotoSource()
    private val dir = File(createTempDirectory("avatars").toFile(), "linkedin_avatars")
    private val avatars = testAvatars(dir, photos)
    private val daily = FakeDailyRepository().apply {
        setPuzzleNumbers(zip) { number(it) }
        setLinkedInGameType(zip, 6)
    }
    private lateinit var db: LinkedInDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), LinkedInDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(RoomQueryThread.executor)
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun newDays(
        source: LinkedInAccountSource = this.source,
        budget: LinkedInCallBudget = LinkedInCallBudget({ now }, limit = 100),
        avatars: LinkedInAvatars = this.avatars,
    ) = RoomLinkedInDays(
        link, source, FakeCompletionRepository(), daily, FakeGameCalendar(today),
        TestGames.registry(listOf(zip)), db, budget, avatars, LinkedInStoreLock(), testPostLog(), FakeOwnerScope(),
    ) { now }

    private fun number(day: LocalDate): Int = 700 + ChronoUnit.DAYS.between(EPOCH, day).toInt()

    private fun ago(days: Long): LocalDate = today.minusDays(days)

    private fun respond(day: LocalDate, board: LinkedInRead<LinkedInBoard>) {
        source.respondOwn("me", 6, number(day), LinkedInRead.Ok(LinkedInOwnResult.NotPlayed))
        source.respondBoard("me", 6, number(today), ChronoUnit.DAYS.between(day, today).toInt(), board)
    }

    private fun row(ref: String, photo: String? = null) =
        LinkedInBoard.Row(ref, ref, 60, hintFree = true, flawless = false, pictureUrl = photo)

    private fun url(ref: String) = "https://media.licdn.com/p/$ref"

    private fun scriptPhotos(vararg refs: String) = refs.forEach { photos.respond(url(it), byteArrayOf(1, 2, 3)) }

    private suspend fun cachedFor(vararg refs: String) = refs.associateWith { avatars.pathOf(it, avatars.present.first()) }

    @Test
    fun `a board read downloads the photo of each member that has one`() = runTest {
        scriptPhotos("a", "b")
        respond(ago(3), LinkedInRead.Ok(LinkedInBoard(listOf(row("a", url("a")), row("b", url("b")), row("c")))))

        assertEquals(LinkedInSync.Done, newDays().sync(zip, ago(3)))

        assertEquals(listOf(url("a"), url("b")), photos.calls.sorted())
        val cached = cachedFor("a", "b", "c")
        assertTrue(File(checkNotNull(cached["a"])).readBytes().contentEquals(byteArrayOf(1, 2, 3)))
        assertTrue(File(checkNotNull(cached["b"])).exists())
        assertNull(cached["c"])
    }

    @Test
    fun `a later board asks only for the photos still missing`() = runTest {
        scriptPhotos("a", "d")
        val days = newDays()
        respond(ago(3), LinkedInRead.Ok(LinkedInBoard(listOf(row("a", url("a"))))))
        respond(ago(2), LinkedInRead.Ok(LinkedInBoard(listOf(row("a", url("a")), row("d", url("d"))))))

        days.sync(zip, ago(3))
        days.sync(zip, ago(2))

        assertEquals(listOf(url("a"), url("d")), photos.calls)
    }

    @Test
    fun `the player's own photo is downloaded like a connection's`() = runTest {
        scriptPhotos("me", "a")
        respond(ago(3), LinkedInRead.Ok(LinkedInBoard(listOf(row("me", url("me")), row("a", url("a"))))))

        newDays().sync(zip, ago(3))

        assertEquals(listOf(url("a"), url("me")), photos.calls.sorted())
    }

    @Test
    fun `a photo that fails leaves the sync done, the link connected and the budget untouched`() = runTest {
        // The photo is not scripted, so the source answers null as a dead network would.
        respond(ago(3), LinkedInRead.Ok(LinkedInBoard(listOf(row("a", url("a"))))))
        val budget = LinkedInCallBudget({ now }, limit = 3)

        assertEquals(LinkedInSync.Done, newDays(budget = budget).sync(zip, ago(3)))

        assertEquals(listOf(url("a")), photos.calls)
        assertEquals(LinkState.Connected("me"), link.state.first())
        assertNull(cachedFor("a")["a"])
        // The sync took two calls (own result, board) and the photo none: exactly one is left.
        assertTrue(budget.tryAcquire())
        assertFalse(budget.tryAcquire())
    }

    @Test
    fun `a failed photo is asked for again by the next board that names the member`() = runTest {
        val days = newDays()
        respond(ago(3), LinkedInRead.Ok(LinkedInBoard(listOf(row("a", url("a"))))))
        respond(ago(2), LinkedInRead.Ok(LinkedInBoard(listOf(row("a", url("a"))))))
        days.sync(zip, ago(3))

        scriptPhotos("a")
        days.sync(zip, ago(2))

        assertEquals(listOf(url("a"), url("a")), photos.calls)
        assertTrue(cachedFor("a")["a"] != null)
    }

    @Test
    fun `nothing downloads when the board read is offline or rejected`() = runTest {
        scriptPhotos("a")
        respond(ago(3), LinkedInRead.Offline)
        respond(ago(2), LinkedInRead.Rejected(Kind.Challenge))

        newDays().sync(zip, ago(3))
        newDays().sync(zip, ago(2))

        assertTrue(photos.calls.isEmpty())
    }

    @Test
    fun `nothing downloads when the link changes while the board is being read`() = runTest {
        scriptPhotos("a")
        respond(ago(3), LinkedInRead.Ok(LinkedInBoard(listOf(row("a", url("a"))))))
        val held = GatedBoardLinkedInAccountSource(source)
        val days = newDays(held)

        val result = async { days.sync(zip, ago(3)) }
        held.entered.await()
        link.disconnect()
        held.gate.complete(Unit)

        assertEquals(LinkedInSync.NotLinked, result.await())
        assertTrue(photos.calls.isEmpty())
        assertTrue(avatars.present.first().isEmpty())
    }

    @Test
    fun `the sync returns once the board is stored and its photos still land after it is cancelled`() = runTest {
        respond(ago(3), LinkedInRead.Ok(LinkedInBoard(listOf(row("a", url("a"))))))
        val onTheWire = CompletableDeferred<Unit>()
        val arrive = CompletableDeferred<Unit>()
        val slow = testAvatars(
            File(dir.parentFile, "slow_avatars"),
            object : LinkedInPhotoSource {
                override suspend fun fetch(url: String): ByteArray {
                    onTheWire.complete(Unit)
                    arrive.await()
                    return byteArrayOf(1, 2, 3)
                }
            },
        )

        val sync = async { newDays(avatars = slow).sync(zip, ago(3)) }
        onTheWire.await()
        // The board is stored on Room's own thread, so the wait runs on the real clock, not the test's.
        withContext(Dispatchers.Default) { withTimeout(10_000) { sync.await() } }
        assertTrue("a day is not held back by its photos", sync.isCompleted)
        sync.cancel()
        arrive.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, slow.present.first().size)
    }

    @Test
    fun `a day that is observed shows a photo as soon as it lands`() = runTest {
        respond(ago(3), LinkedInRead.Ok(LinkedInBoard(listOf(row("a", url("a"))))))
        val days = newDays()
        days.sync(zip, ago(3))

        days.observe(zip, ago(3)).test {
            var data = awaitItem()
            while (data.ghosts.isEmpty()) data = awaitItem()
            assertNull(data.ghosts.single().photoPath)

            scriptPhotos("a")
            avatars.download(listOf(row("a", url("a"))))

            while (data.ghosts.single().photoPath == null) data = awaitItem()
            assertEquals(avatars.pathOf("a", avatars.present.first()), data.ghosts.single().photoPath)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the owner of a day shows the player's cached photo, and no ghost is the player`() = runTest {
        scriptPhotos("me", "a")
        respond(ago(3), LinkedInRead.Ok(LinkedInBoard(listOf(row("me", url("me")), row("a", url("a"))))))
        val days = newDays()
        days.sync(zip, ago(3))

        days.observe(zip, ago(3)).test {
            var data = awaitItem()
            while (data.owner?.photoPath == null) data = awaitItem()
            assertEquals(LinkedInOwner("me", avatars.pathOf("me", avatars.present.first())), data.owner)
            assertEquals(listOf("a"), data.ghosts.map { it.memberRef })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a sync cancelled right after its board is stored still brings the photos, and the observed rows show them`() = runTest {
        scriptPhotos("a", "b")
        respond(ago(3), LinkedInRead.Ok(LinkedInBoard(listOf(row("a", url("a")), row("b", url("b"))))))
        val logging = CompletableDeferred<Unit>()
        val held = object : FetchLogDao by db.fetchLogDao() {
            override suspend fun put(entry: FetchLogEntity) {
                if (entry.item == LinkedInItem.Board.name) {
                    logging.complete(Unit)
                    awaitCancellation()
                }
                db.fetchLogDao().put(entry)
            }
        }
        val sync = LinkedInDaySync(
            link, source, FakeCompletionRepository(), daily, LinkedInDayWindow(FakeGameCalendar(today)), TestGames.registry(listOf(zip)),
            held, LinkedInGhostWriter(db, FakeOwnerScope()), LinkedInCallBudget({ now }, limit = 100), avatars,
            LinkedInStoreLock(), testPostLog(), LinkedInSyncState(),
        ) { now }

        val running = async { sync.sync(zip, ago(3), "me") }
        logging.await()
        running.cancel()
        advanceUntilIdle()

        newDays().observe(zip, ago(3)).test {
            var data = awaitItem()
            while (data.ghosts.size < 2 || data.ghosts.any { it.photoPath == null }) data = awaitItem()
            assertEquals(setOf(avatars.pathOf("a", avatars.present.first()), avatars.pathOf("b", avatars.present.first())), data.ghosts.map { it.photoPath }.toSet())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a photo that fails on the first pass is asked for once more while the day is shown, and shows in the same visit`() = runTest {
        val source = FailsFirst(url("a")).apply {
            respond(url("a"), byteArrayOf(1))
            respond(url("b"), byteArrayOf(2))
        }
        val due = CompletableDeferred<Unit>()
        val days = newDays(avatars = reaskStore(source) { due.await() })
        respond(ago(3), LinkedInRead.Ok(LinkedInBoard(listOf(row("a", url("a")), row("b", url("b"))))))

        days.observe(zip, ago(3)).test {
            awaitItem()
            days.sync(zip, ago(3))
            advanceUntilIdle()
            // The first pass is over: a failed, b landed, and the re-ask is not due yet.
            assertEquals(listOf(url("a"), url("b")), source.calls.sorted())
            due.complete(Unit)
            var data = awaitItem()
            while (data.ghosts.size < 2 || data.ghosts.any { it.photoPath == null }) data = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(listOf(url("a"), url("a"), url("b")), source.calls.sorted())
    }

    @Test
    fun `a day left before the re-ask is due asks no more and holds no address`() = runTest {
        val source = FailsFirst(url("a")).apply {
            respond(url("a"), byteArrayOf(1))
            respond(url("b"), byteArrayOf(2))
        }
        val due = CompletableDeferred<Unit>()
        val store = reaskStore(source) { due.await() }
        respond(ago(3), LinkedInRead.Ok(LinkedInBoard(listOf(row("a", url("a")), row("b", url("b"))))))

        val days = newDays(avatars = store)
        days.observe(zip, ago(3)).test {
            awaitItem()
            days.sync(zip, ago(3))
            cancelAndIgnoreRemainingEvents()
        }
        due.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(url("a"), url("b")), source.calls.sorted())
        assertEquals(0, store.heldAddresses())
    }

    @Test
    fun `a board read of a day nobody is watching holds no address`() = runTest {
        val store = reaskStore(photos)
        respond(ago(3), LinkedInRead.Ok(LinkedInBoard(listOf(row("a", url("a")), row("b", url("b"))))))

        newDays(avatars = store).sync(zip, ago(3))
        advanceUntilIdle()

        assertEquals(listOf(url("a"), url("b")), photos.calls.sorted())
        assertEquals(0, store.heldAddresses())
    }

    @Test
    fun `a visit asks once, however many collectors show the day and boards are read during it`() = runTest {
        val due = CompletableDeferred<Unit>()
        val store = reaskStore(photos) { due.await() }
        val rows = listOf(row("a", url("a")))
        val started = store.generation()
        // Not backgroundScope: advanceUntilIdle does not run work that only background tasks wait on.
        val collectors = List(2) {
            launch(start = CoroutineStart.UNDISPATCHED) { store.reaskWhileShown(zip, ago(3)) }
        }

        store.downloadInBackground(rows, started, zip, ago(3))
        advanceUntilIdle()
        assertEquals(1, photos.calls.size)

        due.complete(Unit)
        advanceUntilIdle()
        // One ask for two collectors, and its address is dropped.
        assertEquals(2, photos.calls.size)
        assertEquals(0, store.heldAddresses())

        // A later board read of the same visit downloads as usual and arms no further ask.
        store.downloadInBackground(rows, started, zip, ago(3))
        advanceUntilIdle()
        assertEquals(3, photos.calls.size)
        assertEquals(0, store.heldAddresses())
        collectors.forEach { it.cancel() }
    }

    /** The avatar store with its own directory, whose re-ask waits for [reaskWait]: never, by default. */
    private fun reaskStore(source: LinkedInPhotoSource, reaskWait: suspend () -> Unit = { awaitCancellation() }) =
        testAvatars(File(dir.parentFile, "reask_avatars"), source, reaskWait)

    /** A fake source whose first fetch of [failing] answers nothing, as a dead cold connection would. */
    private class FailsFirst(private val failing: String) : LinkedInPhotoSource {
        private val delegate = FakeLinkedInPhotoSource()
        private var failed = false
        val calls: List<String> get() = delegate.calls

        fun respond(url: String, photo: ByteArray) = delegate.respond(url, photo)

        override suspend fun fetch(url: String): ByteArray? {
            val answer = delegate.fetch(url)
            if (url != failing || failed) return answer
            failed = true
            return null
        }
    }

    /** Holds the board read open until [gate] completes; the own-result read passes straight through. */
    private class GatedBoardLinkedInAccountSource(
        private val delegate: LinkedInAccountSource,
    ) : LinkedInAccountSource by delegate {
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()

        override suspend fun board(
            profileId: String,
            gameTypeId: Int,
            todayNumber: Int,
            delta: Int,
        ): LinkedInRead<LinkedInBoard> {
            val read = delegate.board(profileId, gameTypeId, todayNumber, delta)
            entered.complete(Unit)
            gate.await()
            return read
        }
    }

    private companion object {
        val EPOCH: LocalDate = LocalDate.of(2026, 1, 1)
    }
}
