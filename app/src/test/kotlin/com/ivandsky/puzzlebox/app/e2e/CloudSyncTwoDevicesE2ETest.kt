package com.ivandsky.puzzlebox.app.e2e

import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.e2e.CloudSyncWorld.Device
import com.ivandsky.puzzlebox.data.cloud.CloudSyncState
import com.ivandsky.puzzlebox.data.cloud.SyncedKind
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.linkedin.LinkedInDays
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.PublishedPuzzle
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DraftRepository
import com.ivandsky.puzzlebox.data.repository.PublishedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * One account on two phones (SC1, SC2, SC6, SC8, SC9; the JVM half), over the real flag-on graph in [CloudSyncWorld]:
 * two [Device]s with their own Room files and nothing shared but the one cloud space. The devices alternate, since
 * Koin is process-global: one runs, stops, and the other runs; a device that runs again is a relaunch.
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application: the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class)
class CloudSyncTwoDevicesE2ETest {

    private val world = CloudSyncWorld(linkedIn = true)
    private val ada = Identity(uid = "uid-ada", displayName = "Ada", photoUrl = null)
    private val phoneA = world.device("phone-a")
    private val phoneB = world.device("phone-b")

    @After
    fun closeWorld() = world.close()

    @Test
    fun `a year of history restores on a second phone with parity, within the read budget and one emission per screen`() {
        // Phone A, signed out: a year of finished games and saved boards, drafts, a published list and received puzzles.
        // Everything is ownerless, so the sign-in adopts it and the first run uploads it all at once.
        var shownOnA: Shown? = null
        phoneA.shell {
            repeat(DAYS) { i ->
                val day = E2E_TODAY.minusDays(i.toLong())
                phoneA.finish(day, puzzleNumber = 1_000 - i, timeMs = 30_000L + i * 7_919L % 50_000L)
                phoneA.saveBoard(day)
            }
            repeat(12) { phoneA.draft("draft-$it", "Draft $it", editedAt(it)) }
            publishedCodes.forEachIndexed { i, code -> phoneA.publish(code, "Published $i", editedAt(i)) }
            runBlocking { phoneA.graph.koin.get<PublishedPuzzleRepository>().markUnpublished(code(publishedCodes[1])) }
            receivedCodes.forEachIndexed { i, code -> phoneA.receive(code, "Received $i", editedAt(i)) }

            phoneA.signIn(ada)
            awaitCloud("A's year in the cloud") {
                cloudRecords() == DAYS && world.space.docs(ada.uid, "boards").size == DAYS && phoneA.uploaded(ada.uid) && phoneA.synced()
            }
            shownOnA = phoneA.shown()
        }
        val onA = checkNotNull(shownOnA)
        assertTrue("A's streak is the whole year", onA.stats.currentStreak >= DAYS)
        assertEquals(DAYS, onA.records.size)
        assertEquals(DAYS, onA.boards.size)
        assertEquals(12, onA.drafts.size)
        assertEquals(publishedCodes.size, onA.published.size)
        assertEquals(receivedCodes.size, onA.received.size)

        // Phone B: a fresh install that only signs in.
        var onB: Shown? = null
        phoneB.shell {
            val screens = Screens(phoneB)
            try {
                val readsBefore = world.space.reads
                val writesBefore = world.space.writes
                phoneB.signIn(ada)
                awaitCloud("B's first restore") { phoneB.synced() && phoneB.shownRecords().size == DAYS && phoneB.shownBoardDays(*year).size == DAYS }

                assertTrue("a first restore costs at most 1,600 reads, cost ${world.space.reads - readsBefore}", world.space.reads - readsBefore <= 1_600)
                assertEquals("a restore uploads nothing", writesBefore, world.space.writes)
                assertEquals("one emission of records", 1, screens.records.nonEmpty())
                assertEquals("one emission of drafts", 1, screens.drafts.nonEmpty())
                assertEquals("one emission of board days", 1, screens.boards.nonEmpty())
                assertEquals("one emission of the published list", 1, screens.published.nonEmpty())
                assertEquals("one emission of the received list", 1, screens.received.nonEmpty())
                onB = phoneB.shown()
            } finally {
                screens.close()
            }
        }
        val restored = checkNotNull(onB)
        assertEquals("History, bests, streak and playing since match", onA.stats, restored.stats)
        assertEquals("every game's stats match", onA.allStats, restored.allStats)
        assertEquals("playing since is the first day", E2E_TODAY.minusDays(DAYS - 1L), restored.stats.playingSince)
        assertEquals("records", onA.records, restored.records)
        assertEquals("drafts", onA.drafts, restored.drafts)
        assertEquals("saved boards", onA.boards, restored.boards)
        assertEquals("published list", onA.published, restored.published)
        assertEquals("received list", onA.received, restored.received)
    }

    @Test
    fun `a resync with nothing changed reads at most once per kind and writes nothing`() {
        phoneA.shell {
            phoneA.finish(E2E_TODAY, puzzleNumber = 700)
            phoneA.draft("draft-1", "Only draft", editedAt(0))
            phoneA.saveBoard(E2E_TODAY)
            phoneA.publish(publishedCodes[0], "Published", editedAt(0))
            phoneA.receive(receivedCodes[0], "Received", editedAt(0))
            phoneA.signIn(ada)
            awaitCloud("A's rows in the cloud") { phoneA.uploaded(ada.uid) && phoneA.synced() && cloudRecords() == 1 && world.space.docs(ada.uid, "boards").size == 1 }
            assertNoChangeResync(phoneA)
        }
        phoneB.shell {
            phoneB.signIn(ada)
            awaitCloud("B's restore") { phoneB.synced() && phoneB.shownRecords().size == 1 && phoneB.shownBoardDays(E2E_TODAY).size == 1 }
            assertNoChangeResync(phoneB)
        }
    }

    @Test
    fun `a finish made offline on A reaches B after A reconnects`() {
        phoneA.shell {
            phoneA.signIn(ada)
            phoneA.finish(E2E_TODAY.minusDays(1), puzzleNumber = 699)
            awaitCloud("A's first finish uploaded") { phoneA.uploaded(ada.uid) && phoneA.synced() && cloudRecords() == 1 }
        }
        phoneB.shell {
            phoneB.signIn(ada)
            awaitCloud("B restored the first finish") { phoneB.synced() && phoneB.shownRecords().size == 1 }
        }

        var onA: Shown? = null
        phoneA.shell {
            awaitCloud("A's start-up run") { phoneA.synced() }
            world.gate.offline = true
            world.network.setOnline(false)
            phoneA.finish(E2E_TODAY, puzzleNumber = 700)
            awaitCloud("A offline") { phoneA.cloud.state.value is CloudSyncState.Offline }
            assertFalse("the finish waits on A", phoneA.uploaded(ada.uid))
            assertEquals("the cloud has only the first finish", 1, cloudRecords())

            world.gate.offline = false
            world.network.setOnline(true)
            awaitCloud("A's finish uploaded after reconnecting") { phoneA.uploaded(ada.uid) && phoneA.synced() && cloudRecords() == 2 }
            onA = phoneA.shown()
        }
        phoneB.shell {
            awaitCloud("B shows A's offline finish") { phoneB.shownRecords().size == 2 }
            awaitCloud("B's run finished") { phoneB.synced() }
            val onB = phoneB.shown()
            assertEquals("records", checkNotNull(onA).records, onB.records)
            assertEquals("streak, playing since and best match", onA!!.stats, onB.stats)
            assertEquals("the streak counts both days", 2, onB.stats.currentStreak)
        }
    }

    @Test
    fun `a put that died before its acknowledgement is uploaded again without a duplicate`() {
        phoneA.shell {
            phoneA.signIn(ada)
            awaitCloud("A's start-up run") { phoneA.synced() }
            val lost = world.gate.loseNextAnswer()
            phoneA.finish(E2E_TODAY.minusDays(1), puzzleNumber = 699)
            awaitCloud("the put reached the cloud and its answer was lost") { lost.isCompleted }
            assertEquals("the cloud has the finish", 1, cloudRecords())
            assertFalse("nothing was acknowledged", phoneA.uploaded(ada.uid))
        }

        // The relaunch: the row is still pending, so it goes up again, under the same id.
        val writes = world.space.writes
        phoneA.shell {
            awaitCloud("the finish goes up again") { phoneA.uploaded(ada.uid) && phoneA.synced() && world.space.writes > writes }
            assertEquals("one record in the cloud, not two", 1, cloudRecords())
            assertEquals("one record on A", 1, phoneA.shownRecords().size)
        }
        phoneB.shell {
            phoneB.signIn(ada)
            awaitCloud("B's restore") { phoneB.synced() && phoneB.shownRecords().isNotEmpty() }
            assertEquals("one record on B", 1, phoneB.shownRecords().size)
        }
    }

    @Test
    fun `a delete on A reaches B after the undo window and an edit on both keeps the later one`() {
        val kept = "kept"
        val gone = "gone"
        val both = "both"
        phoneA.shell {
            phoneA.signIn(ada)
            phoneA.draft(kept, "Kept", editedAt(1))
            phoneA.draft(gone, "Deleted on A", editedAt(2))
            phoneA.draft(both, "Both start", editedAt(3))
            phoneA.receive(receivedCodes[0], "Opened, kept", editedAt(1))
            phoneA.receive(receivedCodes[1], "Opened, deleted on A", editedAt(2))
            awaitCloud("A's rows in the cloud") { phoneA.uploaded(ada.uid) && phoneA.synced() && world.space.docs(ada.uid, "drafts").size == 3 && world.space.docs(ada.uid, "received").size == 2 }
        }
        phoneB.shell {
            phoneB.signIn(ada)
            awaitCloud("B restored everything") { phoneB.synced() && phoneB.shownDrafts().size == 3 && phoneB.shownReceived().size == 2 }
        }

        // A deletes a draft and a received puzzle, and edits the third draft.
        phoneA.shell {
            awaitCloud("A's start-up run") { phoneA.synced() }
            runBlocking {
                phoneA.graph.koin.get<DraftRepository>().delete(gone)
                phoneA.graph.koin.get<ReceivedPuzzleRepository>().delete(code(receivedCodes[1]))
            }
            phoneA.draft(both, "Edited on A", editedAt(10))
            assertEquals("the delete shows on A at once", setOf(kept, both), phoneA.shownDrafts().map { it.id }.toSet())
            // The draft's tombstone waits out the undo window; the received one goes at once.
            awaitCloud("A's deletes in the cloud") {
                phoneA.uploaded(ada.uid) && phoneA.synced() && isDeletedInCloud("drafts", gone) && isDeletedInCloud("received", receivedCodes[1])
            }
        }

        // B edits the same draft later than A did, while it cannot reach the cloud; then it comes back.
        world.gate.offline = true
        phoneB.shell {
            awaitCloud("B's start-up run found no cloud") { phoneB.cloud.state.value is CloudSyncState.Offline }
            phoneB.draft(both, "Edited on B", editedAt(20))
            assertEquals("B still has everything A deleted", 3, phoneB.shownDrafts().size)
            world.gate.offline = false
            runBlocking { phoneB.cloud.syncNow() }
            awaitCloud("A's deletes reach B") {
                phoneB.synced() && phoneB.shownDrafts().map { it.id }.toSet() == setOf(kept, both) && phoneB.shownReceived().map { it.code.value } == listOf(receivedCodes[0])
            }
            assertEquals("B keeps its later edit over A's", "Edited on B", phoneB.shownDrafts().single { it.id == both }.title)
            awaitCloud("B's edit uploaded") { phoneB.uploaded(ada.uid) }
        }

        // A relaunches: B's later edit wins on A too.
        phoneA.shell {
            awaitCloud("A pulls B's edit") { phoneA.synced() && phoneA.shownDrafts().singleOrNull { it.id == both }?.title == "Edited on B" }
            assertEquals(setOf(kept, both), phoneA.shownDrafts().map { it.id }.toSet())
            assertEquals(listOf(receivedCodes[0]), phoneA.shownReceived().map { it.code.value })
        }
    }

    // -- the scenario's vocabulary ---------------------------------------------------------------------------------

    /** What a phone shows: every list the restore has to bring back, in a form that compares across phones. */
    private data class Shown(
        val stats: GameStats,
        val allStats: GameStats,
        val records: Set<CompletionRecord>,
        val drafts: Set<List<Any>>,
        val boards: Set<LocalDate>,
        val published: Set<List<Any>>,
        val received: Set<List<Any>>,
    )

    private fun Device.shown(): Shown = runBlocking {
        val stats = graph.koin.get<ObserveGameStats>()
        Shown(
            stats = stats(ZipDefinition.id).first(),
            allStats = stats().first(),
            records = shownRecords().toSet(),
            drafts = shownDrafts().mapTo(HashSet()) { listOf(it.id, it.title, it.updatedAt, it.blob.toList()) },
            boards = shownBoardDays(*year),
            published = graph.koin.get<PublishedPuzzleRepository>().observeAll().first()
                .mapTo(HashSet()) { listOf(it.code.value, it.gameId, it.title, it.createdAt, it.unpublished) },
            received = shownReceived().mapTo(HashSet()) {
                listOf(it.code.value, it.gameId, it.title, it.authorName, it.puzzleBytes.toList(), it.openedAt, it.postState)
            },
        )
    }

    /** The screens' flows of one phone, counted: how many times each showed something other than an empty list. */
    private class Screens(phone: Device) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val records = Counter(phone.graph.koin.get<CompletionRepository>().observeRecords())
        val drafts = Counter(phone.graph.koin.get<DraftRepository>().observeAll())
        // The saved-board line of a day card: the board-days list left with the archive dot (#783).
        val boards = Counter(phone.graph.koin.get<LinkedInDays>().observe(ZipDefinition.id, E2E_TODAY).map { listOfNotNull(it.boardFetchedAtMs) }.distinctUntilChanged())
        val published = Counter(phone.graph.koin.get<PublishedPuzzleRepository>().observeAll())
        val received = Counter(phone.graph.koin.get<ReceivedPuzzleRepository>().observeAll())

        inner class Counter(source: Flow<Collection<Any?>>) {
            private val shown = CopyOnWriteArrayList<Collection<Any?>>().also { seen -> scope.launch { source.collect { seen += it } } }

            fun nonEmpty(): Int = shown.count { it.isNotEmpty() }
        }

        fun close() = scope.cancel()
    }

    private fun Device.shownReceived(): List<ReceivedPuzzle> = runBlocking { graph.koin.get<ReceivedPuzzleRepository>().observeAll().first() }

    private fun Device.draft(id: String, title: String, editedAt: Instant) {
        val draft = Draft(
            id = id,
            gameId = ZipDefinition.id,
            variant = VariantId("zip/6"),
            draftFormatVersion = 1,
            blob = title.toByteArray(),
            title = title,
            updatedAt = editedAt,
            testSolve = null,
            reservedCode = null,
            imported = false,
            authorName = "",
            note = "",
        )
        runBlocking { graph.koin.get<DraftRepository>().upsert(draft) }
    }

    private fun Device.publish(code: String, title: String, createdAt: Instant) {
        val puzzle = PublishedPuzzle(
            code = code(code),
            gameId = ZipDefinition.id,
            variant = VariantId("zip/6"),
            title = title,
            authorTimeMs = 42_000,
            authorHints = 0,
            createdAt = createdAt,
            unpublished = false,
            solvesCount = null,
            solvesCountAt = null,
        )
        runBlocking { graph.koin.get<PublishedPuzzleRepository>().upsert(puzzle) }
    }

    private fun Device.receive(code: String, title: String, openedAt: Instant) {
        val puzzle = ReceivedPuzzle(
            code = code(code),
            gameId = ZipDefinition.id,
            variant = VariantId("zip/6"),
            sharedFormatVersion = 1,
            puzzleBytes = title.toByteArray(),
            title = title,
            authorName = "Friend",
            note = "",
            authorTimeMs = 50_000,
            authorHints = 0,
            authorUid = "uid-friend",
            openedAt = openedAt,
            unpublished = false,
            postState = PostState.Pending,
            postAttempts = 0,
            difficulty = null,
        )
        runBlocking { graph.koin.get<ReceivedPuzzleRepository>().upsert(puzzle) }
    }

    /** A sync with nothing to send or fetch: at most one read per kind and not one write. */
    private fun assertNoChangeResync(phone: Device) {
        val kinds = phone.graph.koin.getAll<SyncedKind>().size
        assertTrue("the five kinds this test fills are registered, found $kinds", kinds >= 5)
        runBlocking { phone.cloud.syncNow() }
        val reads = world.space.reads
        val writes = world.space.writes
        runBlocking { phone.cloud.syncNow() }
        assertTrue("a no-change resync reads at most once per kind, read ${world.space.reads - reads}", world.space.reads - reads <= kinds)
        assertEquals("a no-change resync writes nothing", writes, world.space.writes)
        assertTrue(phone.synced())
    }

    /** How many finished games the cloud holds for A's account, over every month doc. */
    private fun cloudRecords(): Int = world.space.docs(ada.uid, "completions").sumOf { (it.fields["records"] as? Map<*, *>)?.size ?: 0 }

    private fun isDeletedInCloud(kind: String, id: String): Boolean = world.space.docs(ada.uid, kind).any { it.id == id && it.fields["deleted"] == true }

    private fun code(raw: String): ShareCode = checkNotNull(ShareCode.parse(raw))

    /** A fixed edit time, [n] minutes before a minute ago: in the past, so a delete made now is later than every edit. */
    private fun editedAt(n: Int): Instant = BASE.plusSeconds(n * 60L)

    private companion object {
        const val DAYS = 365
        val year = Array(DAYS) { E2E_TODAY.minusDays(it.toLong()) }
        val BASE: Instant = Instant.now().minusSeconds(24 * 3_600L).let { Instant.ofEpochMilli(it.toEpochMilli()) }
        val publishedCodes = listOf("PKA001", "PKA002", "PKA003", "PKA004")
        val receivedCodes = listOf("RCV001", "RCV002", "RCV003")
    }
}
