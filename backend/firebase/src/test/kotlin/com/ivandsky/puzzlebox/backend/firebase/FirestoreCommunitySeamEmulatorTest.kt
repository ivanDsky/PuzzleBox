package com.ivandsky.puzzlebox.backend.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc.Visibility
import com.ivandsky.puzzlebox.data.repository.CommunityFilter
import com.ivandsky.puzzlebox.data.repository.CommunityListResult
import com.ivandsky.puzzlebox.data.repository.ReportResult
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The community list and report as the app runs them: the production repository over the production store and
 * SDK, against the Firestore emulator under `firestore.rules`. The contract test proves the repository over a
 * fake store; only here do the real query builder and the real report write meet the rules.
 * [FirestoreCommunityListEmulatorTest] proves the rules themselves with hand-built queries. The emulator does
 * not enforce composite indexes; the contract test checks those against firestore.indexes.json.
 * Skipped when the emulators are not up (see [FirestoreReplayDocStoreEmulatorTest]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirestoreCommunitySeamEmulatorTest {

    private val store = FirestoreSharedPuzzleStore()
    private var clockAheadMs = 0L
    private val repository = FirestoreSharedPuzzleRepository(
        store = store,
        currentUid = { FirebaseAuth.getInstance().currentUser?.uid },
        nowMs = { System.currentTimeMillis() + clockAheadMs },
    )

    @Before
    fun freshEmulator() = EmulatorBackend.startClean()

    @Test
    fun `every filter is a list the rules allow, signed out, newest first`() = EmulatorBackend.blocking {
        val author = EmulatorBackend.signInAsNewUser()
        publish(doc("K7Q2MA", author, Visibility.Public, Difficulty.Level.Easy))
        publish(doc("K7Q2MB", author, Visibility.Public, Difficulty.Level.Hard, gameId = "zip"))
        publish(doc("K7Q2MC", author, Visibility.Public, Difficulty.Level.Expert))
        publish(doc("K7Q2MD", author, Visibility.Public))
        publish(doc("K7Q2ME", author, difficulty = Difficulty.Level.Hard))
        publish(doc("K7Q2MF", author, Visibility.Public, Difficulty.Level.Hard))
        store.unpublish("K7Q2MF")
        EmulatorBackend.signOut()
        val hardChip = setOf(Difficulty.Level.Hard, Difficulty.Level.Expert)

        assertEquals(listOf("K7Q2MD", "K7Q2MC", "K7Q2MB", "K7Q2MA"), listed(CommunityFilter()))
        assertEquals(listOf("K7Q2MD", "K7Q2MC", "K7Q2MA"), listed(CommunityFilter(gameId = "queens")))
        assertEquals(listOf("K7Q2MC", "K7Q2MB"), listed(CommunityFilter(difficulties = hardChip)))
        assertEquals(listOf("K7Q2MC"), listed(CommunityFilter(gameId = "queens", difficulties = hardChip)))
    }

    @Test
    fun `a listed puzzle comes back as the doc its author published`() = EmulatorBackend.blocking {
        val author = EmulatorBackend.signInAsNewUser()
        val published = doc("K7Q2MA", author, Visibility.Public, Difficulty.Level.Medium)
        publish(published)

        val listed = (repository.listPublic(CommunityFilter()) as CommunityListResult.Listed).puzzles.single()

        assertEquals(published.copy(createdAtMs = listed.createdAtMs), listed)
    }

    @Test
    fun `a puzzle older than a day on the device clock is not listed`() = EmulatorBackend.blocking {
        val author = EmulatorBackend.signInAsNewUser()
        publish(doc("K7Q2MA", author, Visibility.Public))

        clockAheadMs = 23 * HOUR_MS
        assertEquals(listOf("K7Q2MA"), listed(CommunityFilter()))
        clockAheadMs = 25 * HOUR_MS
        assertEquals(emptyList<String>(), listed(CommunityFilter()))
    }

    @Test
    fun `a player reports once, and an author, a stranger to the code and a signed-out player cannot`() = EmulatorBackend.blocking {
        val author = EmulatorBackend.signInAsNewUser()
        publish(doc("K7Q2MA", author, Visibility.Public))
        assertEquals(ReportResult.AlreadyReported, repository.report(code("K7Q2MA")))

        EmulatorBackend.signInAsNewUser()
        assertEquals(ReportResult.Reported, repository.report(code("K7Q2MA")))
        assertEquals(ReportResult.AlreadyReported, repository.report(code("K7Q2MA")))
        assertEquals(ReportResult.AlreadyReported, repository.report(code("K7Q2MZ")))

        EmulatorBackend.signInAsNewUser()
        assertEquals(ReportResult.Reported, repository.report(code("K7Q2MA")))

        EmulatorBackend.signOut()
        assertEquals(ReportResult.SignedOut, repository.report(code("K7Q2MA")))
    }

    @Test
    fun `with the network off the list is Offline at once`() = EmulatorBackend.blocking {
        val author = EmulatorBackend.signInAsNewUser()
        publish(doc("K7Q2MA", author, Visibility.Public))
        // Read once online, so a cached copy exists for a wrong Source to fall back on.
        assertEquals(listOf("K7Q2MA"), listed(CommunityFilter()))

        FirebaseFirestore.getInstance().disableNetwork().await()
        try {
            assertEquals(CommunityListResult.Offline, repository.listPublic(CommunityFilter()))
        } finally {
            FirebaseFirestore.getInstance().enableNetwork().await()
        }
    }

    private suspend fun listed(filter: CommunityFilter): List<String> =
        when (val result = repository.listPublic(filter)) {
            is CommunityListResult.Listed -> result.puzzles.map { it.code.value }
            else -> throw AssertionError("expected a list, got $result")
        }

    /** Publishes [doc] as its author through the production transaction. */
    private suspend fun publish(doc: SharedPuzzleDoc) {
        val today = LocalDate.now(ZoneOffset.UTC).toString()
        assertEquals(StoreWrite.Created, store.publish(doc.code.value, SharedPuzzleDocCodec.encode(doc), doc.authorUid, today))
    }

    private fun code(value: String) = checkNotNull(ShareCode.parse(value))

    private fun doc(
        code: String,
        authorUid: String,
        visibility: Visibility = Visibility.LinkOnly,
        difficulty: Difficulty.Level? = null,
        gameId: String = "queens",
    ) = SharedPuzzleDoc(
        code = code(code),
        formatVersion = SharedPuzzleDoc.FORMAT_VERSION,
        gameId = gameId,
        variant = "classic",
        shareCodecVersion = 1,
        payload = byteArrayOf(1, 2, 3, 4),
        title = "Corner queens",
        authorName = "Ivan",
        note = "",
        authorUid = authorUid,
        authorTimeMs = 61_000,
        authorHints = 0,
        createdAtMs = 0,
        unpublished = false,
        visibility = visibility,
        difficulty = difficulty,
    )

    private companion object {
        const val HOUR_MS = 60L * 60 * 1000
    }
}
