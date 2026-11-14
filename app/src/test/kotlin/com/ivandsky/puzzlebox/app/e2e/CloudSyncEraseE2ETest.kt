package com.ivandsky.puzzlebox.app.e2e

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.data.cloud.CloudResult
import com.ivandsky.puzzlebox.data.cloud.CloudSpaceKinds
import com.ivandsky.puzzlebox.data.cloud.RestoreSummary
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.PublishedPuzzle
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.data.repository.PublishedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.model.SolveReplay
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Criterion 13, Delete my cloud data, over the real flag-on graph in [CloudSyncWorld] with both flags on. A syncs a row
 * of every kind and erases its cloud data: every kind the space holds is empty, A is signed out, and A's own rows stay on
 * A. A fresh phone B that signs in to the same account restores nothing and shows no welcome.
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w411dp-h891dp")
class CloudSyncEraseE2ETest {

    private val world = CloudSyncWorld(linkedIn = true)
    private val ada = Identity(uid = "uid-ada", displayName = "Ada", photoUrl = null)
    private val phoneA = world.device("phone-a")
    private val phoneB = world.device("phone-b")
    private val yesterday = E2E_TODAY.minusDays(1)

    @After
    fun closeWorld() = world.close()

    @Test
    fun `erasing the cloud data empties every kind, signs A out and keeps A's rows, and a fresh B restores nothing`() {
        var rowsBefore = emptyMap<String, Int>()
        phoneA.shell {
            phoneA.finish(yesterday, puzzleNumber = 590, replay = SolveReplay.of(byteArrayOf(1, 2, 3)))
            phoneA.makeDraft("draft-1", "Ada's draft")
            phoneA.saveBoard(yesterday)
            runBlocking {
                phoneA.graph.koin.get<PublishedPuzzleRepository>().upsert(published())
                phoneA.graph.koin.get<ReceivedPuzzleRepository>().upsert(received())
            }
            phoneA.signIn(ada)
            awaitCloud("every kind in A's space") {
                CloudSpaceKinds.ALL.all { world.space.docs(ada.uid, it).isNotEmpty() } && phoneA.uploaded(ada.uid) && phoneA.synced()
            }
            rowsBefore = phoneA.storedRows(ada.uid)
            assertTrue("A has a row of every local table: $rowsBefore", rowsBefore.values.all { it > 0 })

            val erased = CompletableFuture.supplyAsync { runBlocking { phoneA.cloud.eraseCloud() } }
            awaitLinkedIn { erased.isDone }

            assertEquals(CloudResult.Ok(Unit), erased.get())
            CloudSpaceKinds.ALL.forEach { kind -> assertEquals("$kind is empty in the space", emptyList<Any>(), world.space.docs(ada.uid, kind)) }
            assertTrue("the space is empty", world.space.isEmpty(ada.uid))
            assertNull("A is signed out", phoneA.graph.identity.currentIdentity)
            assertEquals("A's local rows are kept", rowsBefore, phoneA.storedRows(ada.uid))
        }

        // B is a fresh phone: it signs in and finds nothing to restore, and welcomes nobody.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        phoneB.shell {
            val welcomes = CopyOnWriteArrayList<RestoreSummary>()
            scope.launch { phoneB.cloud.restored.collect { welcomes += it } }
            val readsBefore = world.space.reads
            phoneB.signIn(ada)
            awaitCloud("B's first run") { phoneB.synced() && world.space.reads > readsBefore }
            letItSettle()

            assertEquals("B restores no finished game", emptyList<Any>(), phoneB.shownRecords())
            assertEquals("B restores no draft", emptyList<Any>(), phoneB.shownDrafts())
            assertEquals("B restores no board", emptySet<Any>(), phoneB.shownBoardDays(yesterday))
            assertEquals("B's restore summary never emits", emptyList<RestoreSummary>(), welcomes.toList())
            rule.onAllNodesWithText("Welcome back", substring = true).assertCountEquals(0)
            assertTrue("B uploaded nothing", world.space.isEmpty(ada.uid))
        }
        scope.cancel()
    }

    private fun published() = PublishedPuzzle(
        code = code("PKA001"),
        gameId = ZipDefinition.id,
        variant = VariantId("zip/6"),
        title = "Published",
        authorTimeMs = 42_000,
        authorHints = 0,
        createdAt = Instant.parse("2026-11-01T10:00:00Z"),
        unpublished = false,
        solvesCount = null,
        solvesCountAt = null,
    )

    private fun received() = ReceivedPuzzle(
        code = code("RCV001"),
        gameId = ZipDefinition.id,
        variant = VariantId("zip/6"),
        sharedFormatVersion = 1,
        puzzleBytes = "Received".toByteArray(),
        title = "Received",
        authorName = "Friend",
        note = "",
        authorTimeMs = 50_000,
        authorHints = 0,
        authorUid = "uid-friend",
        openedAt = Instant.parse("2026-11-01T10:00:00Z"),
        unpublished = false,
        postState = PostState.Pending,
        postAttempts = 0,
        difficulty = null,
    )

    private fun code(raw: String): ShareCode = checkNotNull(ShareCode.parse(raw))
}
