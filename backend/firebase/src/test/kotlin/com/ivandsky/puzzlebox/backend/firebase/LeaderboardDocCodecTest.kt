package com.ivandsky.puzzlebox.backend.firebase

import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The A19 envelope contract for board entries: encode/decode is symmetric, unknown
 * versions are dropped, unknown fields are tolerated, known fields are never
 * reinterpreted.
 */
class LeaderboardDocCodecTest {

    private val entry = LeaderboardEntry(
        uid = "u1",
        displayName = "Ann",
        puzzleRef = "zip/2026-10-31/592",
        timeMs = 95_000,
        hints = 2,
        completedAtMs = 1_700_000_000_000,
    )

    @Test
    fun `encode and decode round-trip`() {
        assertEquals(entry, LeaderboardDocCodec.decode(LeaderboardDocCodec.encode(entry)))
    }

    @Test
    fun `envelope self-describes format version 1`() {
        assertEquals(
            1,
            (LeaderboardDocCodec.encode(entry)[LeaderboardDocCodec.FIELD_FORMAT_VERSION] as Number).toInt(),
        )
        assertEquals(1, LeaderboardDocCodec.FORMAT_VERSION)
    }

    @Test
    fun `foreign format versions are dropped, never reinterpreted (A19)`() {
        val future = LeaderboardDocCodec.encode(entry) +
            mapOf(LeaderboardDocCodec.FIELD_FORMAT_VERSION to 2L)
        assertNull(LeaderboardDocCodec.decode(future))

        val ancient = LeaderboardDocCodec.encode(entry) +
            mapOf(LeaderboardDocCodec.FIELD_FORMAT_VERSION to 0L)
        assertNull(LeaderboardDocCodec.decode(ancient))
    }

    @Test
    fun `unknown extra fields are preserved on the doc and ignored on read`() {
        val enriched = LeaderboardDocCodec.encode(entry) +
            mapOf("someFutureField" to "x", "anotherOne" to 42L)

        assertEquals(entry, LeaderboardDocCodec.decode(enriched))
    }

    @Test
    fun `malformed docs are dropped`() {
        assertNull(LeaderboardDocCodec.decode(emptyMap()))
        assertNull(
            LeaderboardDocCodec.decode(
                LeaderboardDocCodec.encode(entry) - LeaderboardDocCodec.FIELD_UID,
            ),
        )
        assertNull(
            LeaderboardDocCodec.decode(
                LeaderboardDocCodec.encode(entry) - LeaderboardDocCodec.FIELD_TIME,
            ),
        )
        assertNull(
            LeaderboardDocCodec.decode(
                LeaderboardDocCodec.encode(entry).toMutableMap()
                    .apply { put(LeaderboardDocCodec.FIELD_TIME, 0L) },
            ),
        )
        assertNull(
            LeaderboardDocCodec.decode(
                LeaderboardDocCodec.encode(entry).toMutableMap()
                    .apply { put(LeaderboardDocCodec.FIELD_HINTS, -1L) },
            ),
        )
        assertNull(
            LeaderboardDocCodec.decode(
                LeaderboardDocCodec.encode(entry).toMutableMap()
                    .apply { put(LeaderboardDocCodec.FIELD_PUZZLE_REF, "zip/2026-10-31") },
            ),
        )
    }

    @Test
    fun `firestore integer widening decodes`() {
        // Firestore returns all integer fields as Long; a client-written Int must decode.
        val widened = LeaderboardDocCodec.encode(entry).toMutableMap()
            .apply { put(LeaderboardDocCodec.FIELD_HINTS, 2) }

        assertEquals(entry, LeaderboardDocCodec.decode(widened))
    }
}
