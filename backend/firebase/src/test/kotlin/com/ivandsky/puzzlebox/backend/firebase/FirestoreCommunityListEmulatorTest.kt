package com.ivandsky.puzzlebox.backend.firebase

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc.Visibility
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Date
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The community list contract (ADR-0020 amendment) against the Firestore emulator under `firestore.rules`: a real
 * query is allowed only when its own constraints keep it to public, still-published puzzles and it carries a limit
 * of at most 30. The rules matrix can only mock `request.query.limit`; whether a query's constraints satisfy the
 * rule is decided here. Puzzles are seeded through the production publish transaction, so the create rule is
 * proven on the new fields too. Field and collection names are the rules' own words, not the production constants.
 * Skipped when the emulators are not up (see [FirestoreReplayDocStoreEmulatorTest]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirestoreCommunityListEmulatorTest {

    private val store = FirestoreSharedPuzzleStore()

    @Before
    fun freshEmulator() = EmulatorBackend.startClean()

    @Test
    fun `a signed-out list of public puzzles returns them newest first and no link-only one`() = EmulatorBackend.blocking {
        val author = EmulatorBackend.signInAsNewUser()
        publish(doc("K7Q2MA", author, Visibility.Public, Difficulty.Level.Easy))
        publish(doc("K7Q2MB", author))
        publish(doc("K7Q2MC", author), extra = mapOf("visibility" to "link"))
        publish(doc("K7Q2MD", author, Visibility.Public))
        EmulatorBackend.signOut()

        assertEquals(listOf("K7Q2MD", "K7Q2MA"), codes(community().orderBy("createdAt", Query.Direction.DESCENDING).limit(30)))
    }

    @Test
    fun `the list filters by game, by difficulties and by age, alone and together`() = EmulatorBackend.blocking {
        val author = EmulatorBackend.signInAsNewUser()
        publish(doc("K7Q2MA", author, Visibility.Public, Difficulty.Level.Easy))
        publish(doc("K7Q2MB", author, Visibility.Public, Difficulty.Level.Hard, gameId = "zip"))
        publish(doc("K7Q2MC", author, Visibility.Public, Difficulty.Level.Hard))
        publish(doc("K7Q2MD", author, difficulty = Difficulty.Level.Hard))
        EmulatorBackend.signOut()
        val newest = { query: Query -> query.orderBy("createdAt", Query.Direction.DESCENDING).limit(30) }
        val lastDay = Timestamp(Date(System.currentTimeMillis() - DAY_MS))

        assertEquals(listOf("K7Q2MC", "K7Q2MA"), codes(newest(community().whereEqualTo("gameId", "queens"))))
        assertEquals(listOf("K7Q2MC", "K7Q2MB"), codes(newest(community().whereIn("difficulty", listOf("hard", "expert")))))
        assertEquals(
            listOf("K7Q2MC"),
            codes(newest(community().whereEqualTo("gameId", "queens").whereIn("difficulty", listOf("hard", "expert")))),
        )
        assertEquals(
            listOf("K7Q2MC", "K7Q2MB", "K7Q2MA"),
            codes(newest(community().whereGreaterThan("createdAt", lastDay))),
        )
        assertEquals(
            emptyList<String>(),
            codes(newest(community().whereGreaterThan("createdAt", Timestamp(Date(System.currentTimeMillis() + DAY_MS))))),
        )
    }

    @Test
    fun `an unpublished public puzzle drops out of the list`() = EmulatorBackend.blocking {
        val author = EmulatorBackend.signInAsNewUser()
        publish(doc("K7Q2MA", author, Visibility.Public))
        publish(doc("K7Q2MB", author, Visibility.Public))
        store.unpublish("K7Q2MA")

        assertEquals(listOf("K7Q2MB"), codes(community().limit(30)))
    }

    @Test
    fun `a list that could return a link-only or unpublished puzzle is refused whole`() = EmulatorBackend.blocking {
        val author = EmulatorBackend.signInAsNewUser()
        publish(doc("K7Q2MA", author, Visibility.Public))
        publish(doc("K7Q2MB", author))
        val published = puzzles().whereEqualTo("unpublished", false)

        for (reader in listOf("the author", "another player", "signed out")) {
            when (reader) {
                "another player" -> EmulatorBackend.signInAsNewUser()
                "signed out" -> EmulatorBackend.signOut()
            }
            assertDenied("$reader, every puzzle") { puzzles().limit(30).get(Source.SERVER).await() }
            assertDenied("$reader, no visibility") { published.limit(30).get(Source.SERVER).await() }
            assertDenied("$reader, link") { published.whereEqualTo("visibility", "link").limit(30).get(Source.SERVER).await() }
            assertDenied("$reader, public or link") {
                published.whereIn("visibility", listOf("public", "link")).limit(30).get(Source.SERVER).await()
            }
            assertDenied("$reader, an author's puzzles") {
                published.whereEqualTo("authorUid", author).limit(30).get(Source.SERVER).await()
            }
            assertDenied("$reader, public but maybe unpublished") {
                puzzles().whereEqualTo("visibility", "public").limit(30).get(Source.SERVER).await()
            }
            assertDenied("$reader, public and unpublished") {
                puzzles().whereEqualTo("visibility", "public").whereEqualTo("unpublished", true).limit(30).get(Source.SERVER).await()
            }
        }
    }

    @Test
    fun `a public list without a limit or with one above 30 is refused`() = EmulatorBackend.blocking {
        val author = EmulatorBackend.signInAsNewUser()
        publish(doc("K7Q2MA", author, Visibility.Public))

        assertDenied("no limit") { community().get(Source.SERVER).await() }
        assertDenied("limit 31") { community().limit(31).get(Source.SERVER).await() }
        assertEquals(listOf("K7Q2MA"), codes(community().limit(30)))
        assertEquals(listOf("K7Q2MA"), codes(community().limit(1)))
    }

    @Test
    fun `a link-only puzzle still opens by its code, signed out`() = EmulatorBackend.blocking {
        val author = EmulatorBackend.signInAsNewUser()
        publish(doc("K7Q2MA", author))
        publish(doc("K7Q2MB", author, Visibility.Public, Difficulty.Level.Expert))
        EmulatorBackend.signOut()

        assertEquals(setOf("visibility", "difficulty"), checkNotNull(store.get("K7Q2MB")).keys - checkNotNull(store.get("K7Q2MA")).keys)
        assertEquals("public", store.get("K7Q2MB")?.get("visibility"))
        assertEquals("expert", store.get("K7Q2MB")?.get("difficulty"))
    }

    @Test
    fun `a puzzle's visibility and difficulty cannot be changed after publish`() = EmulatorBackend.blocking {
        val author = EmulatorBackend.signInAsNewUser()
        publish(doc("K7Q2MA", author))
        publish(doc("K7Q2MB", author, Visibility.Public, Difficulty.Level.Easy))

        assertDenied("link only to public") { puzzles().document("K7Q2MA").update("visibility", "public").await() }
        assertDenied("public to link only") { puzzles().document("K7Q2MB").update("visibility", "link").await() }
        assertDenied("another difficulty") { puzzles().document("K7Q2MB").update("difficulty", "expert").await() }
        assertDenied("public while unpublishing") {
            puzzles().document("K7Q2MA").update(mapOf("unpublished" to true, "visibility" to "public")).await()
        }
    }

    @Test
    fun `a public puzzle with a word off either list is not created`() = EmulatorBackend.blocking {
        val author = EmulatorBackend.signInAsNewUser()

        assertDenied("visibility") { publish(doc("K7Q2MA", author), extra = mapOf("visibility" to "private")) }
        assertDenied("difficulty") { publish(doc("K7Q2MA", author, Visibility.Public), extra = mapOf("difficulty" to "extreme")) }
        assertEquals(null, store.get("K7Q2MA"))
    }

    @Test
    fun `a player reports a puzzle once, its author cannot, and no one reads a report`() = EmulatorBackend.blocking {
        val author = EmulatorBackend.signInAsNewUser()
        publish(doc("K7Q2MA", author, Visibility.Public))
        assertDenied("the author's own puzzle") { report("K7Q2MA", author).set(reportFields(author)).await() }

        val reporter = EmulatorBackend.signInAsNewUser()
        report("K7Q2MA", reporter).set(reportFields(reporter)).await()

        assertDenied("a second report") { report("K7Q2MA", reporter).set(reportFields(reporter)).await() }
        assertDenied("a report on a missing puzzle") { report("K7Q2MZ", reporter).set(reportFields(reporter)).await() }
        assertDenied("under another uid") { report("K7Q2MA", author).set(reportFields(reporter)).await() }
        assertDenied("with a reason") { report("K7Q2MA", reporter).set(reportFields(reporter) + ("reason" to "spam")).await() }
        assertDenied("the reporter's read") { report("K7Q2MA", reporter).get(Source.SERVER).await() }
        assertDenied("a list of reports") {
            puzzles().document("K7Q2MA").collection("reports").limit(1).get(Source.SERVER).await()
        }
    }

    private fun puzzles() = FirebaseFirestore.getInstance().collection("puzzles")

    /** The two constraints the list rule demands of every query. */
    private fun community(): Query = puzzles().whereEqualTo("visibility", "public").whereEqualTo("unpublished", false)

    private fun report(code: String, uid: String) = puzzles().document(code).collection("reports").document(uid)

    private fun reportFields(uid: String): Map<String, Any> =
        mapOf("formatVersion" to 1, "uid" to uid, "createdAt" to FieldValue.serverTimestamp())

    private suspend fun codes(query: Query): List<String> = query.get(Source.SERVER).await().documents.map { it.id }

    /** Publishes [doc] as its author through the production transaction; [extra] overrides what the codec wrote. */
    private suspend fun publish(doc: SharedPuzzleDoc, extra: Map<String, Any?> = emptyMap()) {
        val today = LocalDate.now(ZoneOffset.UTC).toString()
        assertEquals(StoreWrite.Created, store.publish(doc.code.value, SharedPuzzleDocCodec.encode(doc) + extra, doc.authorUid, today))
    }

    private suspend fun assertDenied(what: String, call: suspend () -> Any?) {
        val refused = runCatching { call() }.exceptionOrNull()
        assertEquals(
            "$what: $refused",
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            (refused as? FirebaseFirestoreException)?.code,
        )
    }

    private fun doc(
        code: String,
        authorUid: String,
        visibility: Visibility = Visibility.LinkOnly,
        difficulty: Difficulty.Level? = null,
        gameId: String = "queens",
    ) = SharedPuzzleDoc(
        code = checkNotNull(ShareCode.parse(code)),
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
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
