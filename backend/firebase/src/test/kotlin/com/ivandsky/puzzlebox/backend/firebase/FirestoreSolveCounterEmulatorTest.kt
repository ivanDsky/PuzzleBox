package com.ivandsky.puzzlebox.backend.firebase

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The per-puzzle solve counter (#2032) against the Firestore emulator under `firestore.rules`: `puzzles/{code}.solves`
 * moves only by +1, and only in the batch that creates the writer's own first entry on that puzzle's board. An old
 * client's entry without the bump still lands. Field and collection names are the rules' own words. Skipped when the
 * emulators are not up (see [FirestoreReplayDocStoreEmulatorTest]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirestoreSolveCounterEmulatorTest {

    private val puzzleStore = FirestoreSharedPuzzleStore()
    private val boardStore = FirestoreCustomBoardStore()

    @Before
    fun freshEmulator() = EmulatorBackend.startClean()

    @Test
    fun `a first entry with a plus one bump lands, from no field and from a count`() = EmulatorBackend.blocking {
        publishAsNewAuthor()

        val first = EmulatorBackend.signInAsNewUser()
        entryWithBump(first, solves = 1).commit().await()
        val second = EmulatorBackend.signInAsNewUser()
        entryWithBump(second, solves = 2).commit().await()

        assertEquals(2L, solves())
    }

    @Test
    fun `the production store bumps the count with its first entry and not on a repeat`() = EmulatorBackend.blocking {
        publishAsNewAuthor()
        val player = EmulatorBackend.signInAsNewUser()

        assertNull(boardStore.createIfAbsent(CODE, player, entryFields(player)))
        val repeat = boardStore.createIfAbsent(CODE, player, entryFields(player, timeMs = 9_000))

        assertEquals(42_000L, repeat?.get("timeMs"))
        assertEquals(1L, solves())
    }

    @Test
    fun `an old client's entry without a bump still lands and leaves no count`() = EmulatorBackend.blocking {
        publishAsNewAuthor()
        val player = EmulatorBackend.signInAsNewUser()

        entry(player).set(entryFields(player)).await()

        assertEquals(1, entries().size)
        assertNull(solves())
    }

    @Test
    fun `a bump of two or of minus one is refused`() = EmulatorBackend.blocking {
        publishAsNewAuthor()
        entryWithBump(EmulatorBackend.signInAsNewUser(), solves = 1).commit().await()

        val player = EmulatorBackend.signInAsNewUser()
        assertDenied("plus two") { entryWithBump(player, solves = 3).commit().await() }
        assertDenied("minus one") { entryWithBump(player, solves = 0).commit().await() }
        assertDenied("a string") { entryWithBump(player, solves = "2").commit().await() }
        assertEquals(1L, solves())
        assertEquals(1, entries().size)
    }

    @Test
    fun `a bump with no new entry is refused`() = EmulatorBackend.blocking {
        publishAsNewAuthor()
        val player = EmulatorBackend.signInAsNewUser()

        assertDenied("bump alone") { puzzle().update(SOLVES, 1).await() }
        assertDenied("increment alone") { puzzle().update(SOLVES, FieldValue.increment(1)).await() }
        entryWithBump(player, solves = 1).commit().await()
        assertDenied("bump again after the entry") { puzzle().update(SOLVES, 2).await() }
        assertDenied("bump again with a rewritten entry") { entryWithBump(player, solves = 2).commit().await() }
        assertEquals(1L, solves())
    }

    @Test
    fun `a bump that touches another field is refused`() = EmulatorBackend.blocking {
        publishAsNewAuthor()
        val player = EmulatorBackend.signInAsNewUser()

        assertDenied("with a title") {
            FirebaseFirestore.getInstance().batch()
                .set(entry(player), entryFields(player))
                .update(puzzle(), mapOf(SOLVES to 1, "title" to "Mine now"))
                .commit().await()
        }
        assertNull(solves())
    }

    @Test
    fun `the author cannot bump the count of their own puzzle`() = EmulatorBackend.blocking {
        val author = publishAsNewAuthor()

        assertDenied("author") { entryWithBump(author, solves = 1).commit().await() }
        assertNull(solves())
    }

    @Test
    fun `an unpublished puzzle takes no bump`() = EmulatorBackend.blocking {
        publishAsNewAuthor()
        puzzleStore.unpublish(CODE)
        val player = EmulatorBackend.signInAsNewUser()

        assertDenied("unpublished puzzle") { entryWithBump(player, solves = 1).commit().await() }
        assertNull(solves())
    }

    /** Publishes [CODE] as a fresh author through the production transaction; returns the author's uid. */
    private suspend fun publishAsNewAuthor(): String {
        val author = EmulatorBackend.signInAsNewUser()
        val today = LocalDate.now(ZoneOffset.UTC).toString()
        assertEquals(StoreWrite.Created, puzzleStore.publish(CODE, SharedPuzzleDocCodec.encode(doc(author)), author, today))
        return author
    }

    private fun puzzle() = FirebaseFirestore.getInstance().collection("puzzles").document(CODE)

    private fun entry(uid: String) = puzzle().collection("entries").document(uid)

    private suspend fun entries() = puzzle().collection("entries").get(Source.SERVER).await().documents

    private suspend fun solves(): Long? = puzzle().get(Source.SERVER).await().getLong(SOLVES)

    /** The player's entry and a `solves` write of [solves], in one batch. */
    private fun entryWithBump(uid: String, solves: Any) = FirebaseFirestore.getInstance().batch()
        .set(entry(uid), entryFields(uid))
        .update(puzzle(), SOLVES, solves)

    private fun entryFields(uid: String, timeMs: Long = 42_000): Map<String, Any?> = LeaderboardDocCodec.encode(
        LeaderboardEntry(uid, "Player", "custom/queens/$CODE", timeMs, hints = 0, completedAtMs = 1_700_000_000_000L),
    )

    private suspend fun assertDenied(what: String, call: suspend () -> Any?) {
        val refused = runCatching { call() }.exceptionOrNull()
        assertEquals(
            "$what: $refused",
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            (refused as? FirebaseFirestoreException)?.code,
        )
    }

    private fun doc(authorUid: String) = SharedPuzzleDoc(
        code = checkNotNull(ShareCode.parse(CODE)),
        formatVersion = SharedPuzzleDoc.FORMAT_VERSION,
        gameId = "queens",
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
    )

    private companion object {
        const val CODE = "K7Q2MX"
        const val SOLVES = "solves"
    }
}
