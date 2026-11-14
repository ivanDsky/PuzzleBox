package com.ivandsky.puzzlebox.app.e2e

import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.PublishedPuzzle
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DraftRepository
import com.ivandsky.puzzlebox.data.repository.PublishedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.model.SolveReplay
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R-12, the first restore of a year of history WITH replays stays inside the read budget. A plays a year of Zip, each
 * with its replay, plus drafts and lists; B signs in fresh. The restore costs at most 1,600 reads (completions are
 * bucketed up to eight docs a month, replays are one doc each), writes nothing, brings every replay back, and each
 * screen shows its data in one emission. No boards are seeded: with them the year would pass the account's 500 writes
 * a day and the upload would pause part way, and the board reads are already pinned by the year test in
 * [CloudSyncTwoDevicesE2ETest], which has no replays.
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class)
class CloudSyncReplayBudgetE2ETest {

    private val world = CloudSyncWorld()
    private val ada = Identity(uid = "uid-ada", displayName = "Ada", photoUrl = null)
    private val phoneA = world.device("phone-a")
    private val phoneB = world.device("phone-b")

    @After
    fun closeWorld() = world.close()

    @Test
    fun `a year of history with replays restores on a fresh phone within 1,600 reads and one emission per screen`() {
        phoneA.shell {
            repeat(DAYS) { i ->
                val day = E2E_TODAY.minusDays(i.toLong())
                phoneA.finish(day, puzzleNumber = 1_000 - i, timeMs = 30_000L + i * 7_919L % 50_000L, replay = replay(i))
            }
            repeat(12) { draft("draft-$it", editedAt(it)) }
            published.forEachIndexed { i, raw -> publish(raw, editedAt(i)) }
            received.forEachIndexed { i, raw -> receive(raw, editedAt(i)) }

            phoneA.signIn(ada)
            var last = ""
            try {
                awaitCloud("A's year and its replays in the cloud") {
                    val records = cloudRecords()
                    val replays = world.space.docs(ada.uid, "replays").size
                    last = "records=$records replays=$replays uploaded=${phoneA.uploaded(ada.uid)} synced=${phoneA.synced()} state=${phoneA.cloud.state.value}"
                    records == DAYS && replays == DAYS && phoneA.uploaded(ada.uid) && phoneA.synced()
                }
            } catch (e: AssertionError) {
                throw AssertionError("${e.message}: $last", e)
            }
        }

        phoneB.shell {
            val screens = Screens(phoneB)
            try {
                val readsBefore = world.space.reads
                val writesBefore = world.space.writes
                phoneB.signIn(ada)
                awaitCloud("B's first restore") {
                    phoneB.synced() && phoneB.shownRecords().size == DAYS &&
                        phoneB.shownReplays() == DAYS
                }

                val reads = world.space.reads - readsBefore
                println("R-12 replay restore: $reads reads for $DAYS finished games and $DAYS replays")
                assertTrue("a first restore with replays costs at most 1,600 reads, cost $reads", reads <= 1_600)
                assertEquals("a restore uploads nothing", writesBefore, world.space.writes)
                assertEquals("one emission of records", 1, screens.records.nonEmpty())
                assertEquals("one emission of drafts", 1, screens.drafts.nonEmpty())
                assertEquals("one emission of the published list", 1, screens.published.nonEmpty())
                assertEquals("one emission of the received list", 1, screens.received.nonEmpty())
            } finally {
                screens.close()
            }
        }
    }

    /** A replay of [i]: any sealed bytes within the cap, distinct per game. */
    private fun replay(i: Int): SolveReplay = checkNotNull(SolveReplay.of(ByteArray(REPLAY_BYTES) { (it + i).toByte() }))

    /** How many of the year's finished games hold a replay on this phone. */
    private fun CloudSyncWorld.Device.shownReplays(): Int = runBlocking {
        val completions = graph.koin.get<CompletionRepository>()
        shownRecords().count { completions.recordReplay(it.puzzleRef) != null }
    }

    /** How many finished games the cloud holds for A's account, over every month doc. */
    private fun cloudRecords(): Int = world.space.docs(ada.uid, "completions").sumOf { (it.fields["records"] as? Map<*, *>)?.size ?: 0 }

    /** The screens' flows of one phone, counted: how many times each showed something other than an empty list. */
    private class Screens(phone: CloudSyncWorld.Device) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val records = Counter(phone.graph.koin.get<CompletionRepository>().observeRecords())
        val drafts = Counter(phone.graph.koin.get<DraftRepository>().observeAll())
        val published = Counter(phone.graph.koin.get<PublishedPuzzleRepository>().observeAll())
        val received = Counter(phone.graph.koin.get<ReceivedPuzzleRepository>().observeAll())

        inner class Counter(source: Flow<Collection<Any?>>) {
            private val shown = CopyOnWriteArrayList<Collection<Any?>>().also { seen -> scope.launch { source.collect { seen += it } } }

            fun nonEmpty(): Int = shown.count { it.isNotEmpty() }
        }

        fun close() = scope.cancel()
    }

    private fun draft(id: String, editedAt: Instant) {
        val draft = Draft(
            id = id,
            gameId = ZipDefinition.id,
            variant = VariantId("zip/6"),
            draftFormatVersion = 1,
            blob = id.toByteArray(),
            title = id,
            updatedAt = editedAt,
            testSolve = null,
            reservedCode = null,
            imported = false,
            authorName = "",
            note = "",
        )
        runBlocking { phoneA.graph.koin.get<DraftRepository>().upsert(draft) }
    }

    private fun publish(raw: String, createdAt: Instant) {
        val puzzle = PublishedPuzzle(
            code = code(raw),
            gameId = ZipDefinition.id,
            variant = VariantId("zip/6"),
            title = raw,
            authorTimeMs = 42_000,
            authorHints = 0,
            createdAt = createdAt,
            unpublished = false,
            solvesCount = null,
            solvesCountAt = null,
        )
        runBlocking { phoneA.graph.koin.get<PublishedPuzzleRepository>().upsert(puzzle) }
    }

    private fun receive(raw: String, openedAt: Instant) {
        val puzzle = ReceivedPuzzle(
            code = code(raw),
            gameId = ZipDefinition.id,
            variant = VariantId("zip/6"),
            sharedFormatVersion = 1,
            puzzleBytes = raw.toByteArray(),
            title = raw,
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
        runBlocking { phoneA.graph.koin.get<ReceivedPuzzleRepository>().upsert(puzzle) }
    }

    private fun code(raw: String): ShareCode = checkNotNull(ShareCode.parse(raw))

    /** A fixed edit time in the past, [n] minutes apart. */
    private fun editedAt(n: Int): Instant = BASE.plusSeconds(n * 60L)

    private companion object {
        const val DAYS = 365
        const val REPLAY_BYTES = 600
        val BASE: Instant = Instant.now().minusSeconds(24 * 3_600L).let { Instant.ofEpochMilli(it.toEpochMilli()) }
        val published = listOf("PKA001", "PKA002", "PKA003", "PKA004")
        val received = listOf("RCV001", "RCV002", "RCV003")
    }
}
