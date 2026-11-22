package com.ivandsky.puzzlebox.backend.firebase

import com.google.firebase.Timestamp
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc.Visibility
import com.ivandsky.puzzlebox.data.repository.SharedFetchResult
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.model.ShareCode
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The two community fields of `puzzles/{code}` (ADR-0020 amendment): a doc written before they existed reads as
 * link only, and a link-only doc is written exactly as before. Robolectric: the codec touches Firestore types.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedPuzzleDocCodecTest {

    @Test
    fun `a doc without the community fields decodes as link only with no difficulty`() {
        val old = stored(doc()) - SharedPuzzleDocCodec.FIELD_VISIBILITY - SharedPuzzleDocCodec.FIELD_DIFFICULTY

        val decoded = decoded(old)

        assertEquals(Visibility.LinkOnly, decoded.visibility)
        assertNull(decoded.difficulty)
        assertEquals(doc(), decoded)
    }

    @Test
    fun `a public doc with a difficulty round-trips`() {
        for (level in Difficulty.Level.entries) {
            val public = doc(Visibility.Public, level)

            assertEquals(public, decoded(stored(public)))
        }
    }

    @Test
    fun `a public doc without a difficulty round-trips`() {
        val public = doc(Visibility.Public)

        assertEquals(public, decoded(stored(public)))
    }

    @Test
    fun `a link-only doc with a difficulty round-trips`() {
        val linkOnly = doc(Visibility.LinkOnly, Difficulty.Level.Expert)

        assertEquals(linkOnly, decoded(stored(linkOnly)))
    }

    @Test
    fun `a link-only doc without a difficulty is written with the keys an old client writes`() {
        assertEquals(OLD_KEYS, SharedPuzzleDocCodec.encode(doc()).keys)
    }

    @Test
    fun `the community fields are written as the words the rules list`() {
        val fields = SharedPuzzleDocCodec.encode(doc(Visibility.Public, Difficulty.Level.Hard))

        assertEquals("public", fields[SharedPuzzleDocCodec.FIELD_VISIBILITY])
        assertEquals("hard", fields[SharedPuzzleDocCodec.FIELD_DIFFICULTY])
        assertEquals(
            OLD_KEYS + SharedPuzzleDocCodec.FIELD_VISIBILITY + SharedPuzzleDocCodec.FIELD_DIFFICULTY,
            fields.keys,
        )
        assertEquals(
            listOf("easy", "medium", "hard", "expert"),
            Difficulty.Level.entries.map {
                SharedPuzzleDocCodec.encode(doc(difficulty = it))[SharedPuzzleDocCodec.FIELD_DIFFICULTY]
            },
        )
    }

    @Test
    fun `an explicit link, an unknown or a mistyped visibility decodes as link only`() {
        for (value in listOf("link", "private", "PUBLIC", "", true, 1L, null)) {
            val fields = stored(doc(Visibility.Public)) + (SharedPuzzleDocCodec.FIELD_VISIBILITY to value)

            assertEquals("visibility $value", Visibility.LinkOnly, decoded(fields).visibility)
        }
    }

    @Test
    fun `an unknown or mistyped difficulty decodes as none and the doc still opens`() {
        for (value in listOf("extreme", "Hard", "", 2L, null)) {
            val fields = stored(doc(Visibility.Public, Difficulty.Level.Hard)) + (SharedPuzzleDocCodec.FIELD_DIFFICULTY to value)

            assertEquals("difficulty $value", doc(Visibility.Public), decoded(fields))
        }
    }

    @Test
    fun `the same content with the same community fields is the same`() {
        val public = doc(Visibility.Public, Difficulty.Level.Medium)

        assertTrue(SharedPuzzleDocCodec.sameContent(SharedPuzzleDocCodec.encode(public), stored(public)))
        assertTrue(SharedPuzzleDocCodec.sameContent(SharedPuzzleDocCodec.encode(doc()), stored(doc())))
    }

    @Test
    fun `a stored explicit link is the same content as a link-only retry`() {
        val storedLink = stored(doc()) + (SharedPuzzleDocCodec.FIELD_VISIBILITY to "link")

        assertTrue(SharedPuzzleDocCodec.sameContent(SharedPuzzleDocCodec.encode(doc()), storedLink))
    }

    @Test
    fun `a different visibility is different content, in both directions`() {
        val linkOnly = doc()
        val public = doc(Visibility.Public)

        assertFalse(SharedPuzzleDocCodec.sameContent(SharedPuzzleDocCodec.encode(public), stored(linkOnly)))
        assertFalse(SharedPuzzleDocCodec.sameContent(SharedPuzzleDocCodec.encode(linkOnly), stored(public)))
    }

    @Test
    fun `a different or missing difficulty is different content`() {
        val hard = doc(Visibility.Public, Difficulty.Level.Hard)
        val easy = doc(Visibility.Public, Difficulty.Level.Easy)
        val none = doc(Visibility.Public)

        assertFalse(SharedPuzzleDocCodec.sameContent(SharedPuzzleDocCodec.encode(hard), stored(easy)))
        assertFalse(SharedPuzzleDocCodec.sameContent(SharedPuzzleDocCodec.encode(hard), stored(none)))
        assertFalse(SharedPuzzleDocCodec.sameContent(SharedPuzzleDocCodec.encode(none), stored(hard)))
    }

    @Test
    fun `a doc without a solves field decodes with no count`() {
        val decoded = decoded(stored(doc()))

        assertNull(decoded.solves)
        assertEquals(doc(), decoded)
    }

    @Test
    fun `a stored solves count decodes as an int`() {
        for (value in listOf<Any>(0L, 1L, 42L, 7)) {
            val fields = stored(doc()) + (SharedPuzzleDocCodec.FIELD_SOLVES to value)

            assertEquals("solves $value", (value as Number).toInt(), decoded(fields).solves)
        }
    }

    @Test
    fun `a mistyped or negative solves decodes as no count and the doc still opens`() {
        for (value in listOf("3", true, -1L, null)) {
            val fields = stored(doc()) + (SharedPuzzleDocCodec.FIELD_SOLVES to value)

            assertEquals("solves $value", doc(), decoded(fields))
        }
    }

    @Test
    fun `encode never writes solves, so a new puzzle starts without the field`() {
        assertEquals(OLD_KEYS, SharedPuzzleDocCodec.encode(doc().copy(solves = 5)).keys)
    }

    @Test
    fun `a stored count does not change what a publish retry compares`() {
        val counted = stored(doc()) + (SharedPuzzleDocCodec.FIELD_SOLVES to 9L)

        assertTrue(SharedPuzzleDocCodec.sameContent(SharedPuzzleDocCodec.encode(doc()), counted))
    }

    @Test
    fun `docs that differ only in solves are not equal`() {
        assertFalse(doc() == doc().copy(solves = 1))
        assertFalse(doc().copy(solves = 1) == doc().copy(solves = 2))
        assertEquals(doc().copy(solves = 3).hashCode(), doc().copy(solves = 3).hashCode())
    }

    /** [doc] as the server holds it: the encoded fields with `createdAt` resolved. */
    private fun stored(doc: SharedPuzzleDoc): Map<String, Any?> =
        SharedPuzzleDocCodec.encode(doc) + (SharedPuzzleDocCodec.FIELD_CREATED_AT to Timestamp(Date(doc.createdAtMs)))

    private fun decoded(fields: Map<String, Any?>): SharedPuzzleDoc =
        (SharedPuzzleDocCodec.decode(CODE, fields) as SharedFetchResult.Found).doc

    private fun doc(visibility: Visibility = Visibility.LinkOnly, difficulty: Difficulty.Level? = null) = SharedPuzzleDoc(
        code = CODE,
        formatVersion = SharedPuzzleDoc.FORMAT_VERSION,
        gameId = "queens",
        variant = "classic",
        shareCodecVersion = 1,
        payload = byteArrayOf(1, 2, 3, 4),
        title = "Corner queens",
        authorName = "Ivan",
        note = "Start in the corner",
        authorUid = "author-uid",
        authorTimeMs = 61_000,
        authorHints = 0,
        createdAtMs = 1_762_000_000_000,
        unpublished = false,
        visibility = visibility,
        difficulty = difficulty,
    )

    private companion object {
        val CODE: ShareCode = checkNotNull(ShareCode.parse("K7Q2MX"))

        /** The 13 keys the rules require and every released client writes. */
        val OLD_KEYS = setOf(
            "formatVersion", "gameId", "variant", "shareCodecVersion", "payload", "title", "authorName", "note",
            "authorUid", "authorTimeMs", "authorHints", "createdAt", "unpublished",
        )
    }
}
