package com.ivandsky.puzzlebox.backend.firebase

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.LegResult
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.match.MatchDraft
import com.ivandsky.puzzlebox.data.match.MatchFetch
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchDocCodecTest {

    private val code = ShareCode.parse("K7Q2MX")!!
    private val ann = Player("u-ann", "Ann", "https://img/ann.png")
    private val bob = Player("u-bob", "Bob", null)
    private val serverTime = Instant.parse("2026-11-01T10:15:30Z")
    private val daily = MatchLeg(LegRef.Daily(GameId("zip"), LocalDate.parse("2026-11-01"), 224), null)
    private val custom = MatchLeg(
        LegRef.Custom(GameId("zip"), ShareCode.parse("ABC234")!!),
        authorUid = "u-friend",
    )

    private fun draft(
        legs: List<MatchLeg> = listOf(daily),
        invitedUid: String? = null,
        rematchOf: ShareCode? = null,
    ) = MatchDraft(code, 24, ann, invitedUid, legs, includeFriends = true, rematchOf = rematchOf)

    /** What the server does to a create: the sentinel becomes a stored Timestamp. */
    private fun serverStamped(data: Map<String, Any?>): Map<String, Any?> =
        data + (MatchDocCodec.FIELD_CREATED_AT to Timestamp(serverTime.epochSecond, serverTime.nano))

    private fun expectedMatch(draft: MatchDraft) = Match(
        code = draft.code,
        formatVersion = Match.FORMAT_VERSION,
        createdAt = serverTime,
        deadlineHours = draft.deadlineHours,
        challenger = draft.challenger,
        invitedUid = draft.invitedUid,
        opponent = null,
        legs = draft.legs,
        includeFriends = draft.includeFriends,
        cancelled = false,
        declined = false,
        rematchOf = draft.rematchOf,
        progress = emptyMap(),
    )

    private fun created(draft: MatchDraft) =
        MatchDocCodec.decode(draft.code, serverStamped(MatchDocCodec.encodeCreate(draft)))

    private fun stampedDoc() = serverStamped(MatchDocCodec.encodeCreate(draft()))

    @Test
    fun `a created match decodes back to the same match`() {
        val drafts = listOf(
            draft(),
            draft(legs = listOf(daily, custom)),
            draft(invitedUid = bob.uid),
            draft(rematchOf = ShareCode.parse("ZZ9999")!!),
        )
        for (d in drafts) {
            assertEquals(MatchFetch.Found(expectedMatch(d)), created(d))
        }
    }

    @Test
    fun `create stamps a server timestamp and lists the invited player as participant`() {
        val plain = MatchDocCodec.encodeCreate(draft())
        val invited = MatchDocCodec.encodeCreate(draft(invitedUid = bob.uid))
        assertTrue(plain[MatchDocCodec.FIELD_CREATED_AT] is FieldValue)
        assertEquals(listOf(ann.uid), plain[MatchDocCodec.FIELD_PARTICIPANTS])
        assertEquals(listOf(ann.uid, bob.uid), invited[MatchDocCodec.FIELD_PARTICIPANTS])
    }

    @Test
    fun `claim fields add the opponent and the participant but touch nothing else`() {
        val claim = MatchDocCodec.encodeClaim(bob)
        assertEquals(
            setOf(
                MatchDocCodec.FIELD_OPPONENT_UID,
                MatchDocCodec.FIELD_OPPONENT_NAME,
                MatchDocCodec.FIELD_OPPONENT_PHOTO,
                MatchDocCodec.FIELD_PARTICIPANTS,
                MatchDocCodec.FIELD_CLAIMED_AT,
            ),
            claim.keys,
        )
        assertEquals(bob.uid, claim[MatchDocCodec.FIELD_OPPONENT_UID])
        assertEquals(bob.name, claim[MatchDocCodec.FIELD_OPPONENT_NAME])
        assertNull(claim[MatchDocCodec.FIELD_OPPONENT_PHOTO])
    }

    @Test
    fun `a claimed match decodes with its opponent and progress`() {
        val claimed = stampedDoc() + mapOf(
            MatchDocCodec.FIELD_OPPONENT_UID to bob.uid,
            MatchDocCodec.FIELD_OPPONENT_NAME to bob.name,
            MatchDocCodec.FIELD_OPPONENT_PHOTO to null,
            MatchDocCodec.FIELD_PROGRESS to mapOf(ann.uid to listOf(0L, 1L), bob.uid to listOf(0L, "x")),
        )
        val match = (MatchDocCodec.decode(code, claimed) as MatchFetch.Found).match
        assertEquals(bob, match.opponent)
        assertEquals(mapOf(ann.uid to setOf(0, 1), bob.uid to setOf(0)), match.progress)
    }

    @Test
    fun `a taken seat with no usable name still decodes as claimed`() {
        for (name in listOf(null, 7L)) {
            val data = stampedDoc() + mapOf(
                MatchDocCodec.FIELD_OPPONENT_UID to bob.uid,
                MatchDocCodec.FIELD_OPPONENT_NAME to name,
            )
            val match = (MatchDocCodec.decode(code, data) as MatchFetch.Found).match
            assertEquals(bob.uid, match.opponent?.uid)
        }
        val noKey = stampedDoc() + (MatchDocCodec.FIELD_OPPONENT_UID to bob.uid) - MatchDocCodec.FIELD_OPPONENT_NAME
        assertEquals(bob.uid, ((MatchDocCodec.decode(code, noKey) as MatchFetch.Found).match.opponent)?.uid)
    }

    @Test
    fun `a result round-trips`() {
        val results = listOf(
            LegResult("u-ann", 1, 83_000, 2, 1_700_000_000_000, gaveUp = false),
            LegResult("u-bob", 0, 0, 0, 1_700_000_100_000, gaveUp = true),
        )
        for (r in results) {
            assertEquals(r, MatchDocCodec.decodeResult(MatchDocCodec.encodeResult(r)))
        }
        assertEquals("1_u-ann", MatchDocCodec.resultId(1, "u-ann"))
        val (path, value) = MatchDocCodec.progressUpdate("u-ann", 1)
        assertEquals("progress.u-ann", path)
        assertTrue(value is FieldValue)
    }

    @Test
    fun `an unknown formatVersion is reported as unsupported`() {
        val future = stampedDoc() + (MatchDocCodec.FIELD_FORMAT_VERSION to 2L)
        assertEquals(MatchFetch.UnsupportedVersion(2), MatchDocCodec.decode(code, future))

        val result = MatchDocCodec.encodeResult(LegResult("u-ann", 0, 5, 0, 9, false))
        assertNull(MatchDocCodec.decodeResult(result + (MatchDocCodec.FIELD_FORMAT_VERSION to 2)))
    }

    @Test
    fun `malformed data is not found`() {
        fun legs(vararg leg: Map<String, Any?>) = MatchDocCodec.FIELD_LEGS to leg.toList()
        val good = mapOf(MatchDocCodec.FIELD_REF to daily.ref.wire, MatchDocCodec.FIELD_AUTHOR_UID to null)
        val broken = listOf(
            stampedDoc() - MatchDocCodec.FIELD_CREATED_AT,
            stampedDoc() + (MatchDocCodec.FIELD_CREATED_AT to "yesterday"),
            stampedDoc() + legs(good + (MatchDocCodec.FIELD_REF to "zip/not-a-date/3")),
            stampedDoc() + legs(good + (MatchDocCodec.FIELD_REF to "zip/2026-11-01")),
            stampedDoc() + legs(good + (MatchDocCodec.FIELD_REF to "zip/2026-11-01/x")),
            stampedDoc() + (MatchDocCodec.FIELD_DEADLINE_HOURS to "24"),
            stampedDoc() + legs(good - MatchDocCodec.FIELD_AUTHOR_UID),
            stampedDoc() + (MatchDocCodec.FIELD_CANCELLED to "no"),
            stampedDoc() + (MatchDocCodec.FIELD_DECLINED to 0),
            stampedDoc() + (MatchDocCodec.FIELD_INCLUDE_FRIENDS to null),
            stampedDoc() + (MatchDocCodec.FIELD_REMATCH_OF to "not a code!"),
            stampedDoc() + (MatchDocCodec.FIELD_LEGS to emptyList<Any>()),
        )
        for (data in broken) {
            assertEquals(MatchFetch.NotFound, MatchDocCodec.decode(code, data))
        }
        assertNull(MatchDocCodec.decodeResult(mapOf("uid" to "u")))
    }

    @Test
    fun `unknown extra fields are ignored`() {
        assertEquals(
            MatchFetch.Found(expectedMatch(draft())),
            MatchDocCodec.decode(code, stampedDoc() + ("somethingNew" to listOf(1, 2))),
        )
        val result = LegResult("u-ann", 1, 83_000, 2, 1_700_000_000_000, gaveUp = false)
        assertEquals(result, MatchDocCodec.decodeResult(MatchDocCodec.encodeResult(result) + ("x" to 1)))
    }
}
