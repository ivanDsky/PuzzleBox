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
import com.ivandsky.puzzlebox.model.ShareCode

/**
 * The field names and value shapes of a duel's Firestore documents (ADR-0026, I6):
 * `matches/{code}`, `matches/{code}/results/{legIndex}_{uid}`. The code is the document id,
 * so it is not a field. Pure maps in both directions; readers ignore unknown fields.
 * `matchQuota/{uid}` belongs to the store's quota transaction and is not shaped here.
 */
internal object MatchDocCodec {

    const val FORMAT_VERSION = Match.FORMAT_VERSION

    const val FIELD_FORMAT_VERSION = "formatVersion"
    const val FIELD_CREATED_AT = "createdAt"
    const val FIELD_DEADLINE_HOURS = "deadlineHours"
    const val FIELD_CHALLENGER_UID = "challengerUid"
    const val FIELD_CHALLENGER_NAME = "challengerName"
    const val FIELD_CHALLENGER_PHOTO = "challengerPhoto"
    const val FIELD_INVITED_UID = "invitedUid"
    const val FIELD_OPPONENT_UID = "opponentUid"
    const val FIELD_OPPONENT_NAME = "opponentName"
    const val FIELD_OPPONENT_PHOTO = "opponentPhoto"
    const val FIELD_PARTICIPANTS = "participants"
    const val FIELD_LEGS = "legs"
    const val FIELD_INCLUDE_FRIENDS = "includeFriends"
    const val FIELD_CANCELLED = "cancelled"
    const val FIELD_DECLINED = "declined"
    const val FIELD_REMATCH_OF = "rematchOf"
    const val FIELD_PROGRESS = "progress"
    const val FIELD_CLAIMED_AT = "claimedAt"

    /** Inside a `legs` entry. */
    const val FIELD_REF = "ref"
    const val FIELD_AUTHOR_UID = "authorUid"

    /** `results/{legIndex}_{uid}`. */
    const val FIELD_UID = "uid"
    const val FIELD_LEG_INDEX = "legIndex"
    const val FIELD_TIME = "timeMs"
    const val FIELD_HINTS = "hints"
    const val FIELD_COMPLETED_AT = "completedAtMs"
    const val FIELD_GAVE_UP = "gaveUp"

    fun encodeCreate(draft: MatchDraft): Map<String, Any?> = mapOf(
        FIELD_FORMAT_VERSION to FORMAT_VERSION,
        FIELD_CREATED_AT to FieldValue.serverTimestamp(),
        FIELD_DEADLINE_HOURS to draft.deadlineHours,
        FIELD_CHALLENGER_UID to draft.challenger.uid,
        FIELD_CHALLENGER_NAME to draft.challenger.name,
        FIELD_CHALLENGER_PHOTO to draft.challenger.photoUrl,
        FIELD_INVITED_UID to draft.invitedUid,
        FIELD_OPPONENT_UID to null,
        FIELD_OPPONENT_NAME to null,
        FIELD_OPPONENT_PHOTO to null,
        FIELD_PARTICIPANTS to listOfNotNull(draft.challenger.uid, draft.invitedUid),
        FIELD_LEGS to draft.legs.map { mapOf(FIELD_REF to it.ref.wire, FIELD_AUTHOR_UID to it.authorUid) },
        FIELD_INCLUDE_FRIENDS to draft.includeFriends,
        FIELD_CANCELLED to false,
        FIELD_DECLINED to false,
        FIELD_REMATCH_OF to draft.rematchOf?.value,
        FIELD_PROGRESS to emptyMap<String, List<Int>>(),
        FIELD_CLAIMED_AT to null,
    )

    /** Only the keys a claim may touch. */
    fun encodeClaim(me: Player): Map<String, Any?> = mapOf(
        FIELD_OPPONENT_UID to me.uid,
        FIELD_OPPONENT_NAME to me.name,
        FIELD_OPPONENT_PHOTO to me.photoUrl,
        FIELD_PARTICIPANTS to FieldValue.arrayUnion(me.uid),
        FIELD_CLAIMED_AT to FieldValue.serverTimestamp(),
    )

    fun encodeResult(result: LegResult): Map<String, Any?> = mapOf(
        FIELD_FORMAT_VERSION to FORMAT_VERSION,
        FIELD_UID to result.uid,
        FIELD_LEG_INDEX to result.legIndex,
        FIELD_TIME to result.timeMs,
        FIELD_HINTS to result.hints,
        FIELD_COMPLETED_AT to result.completedAtMs,
        FIELD_GAVE_UP to result.gaveUp,
    )

    fun resultId(legIndex: Int, uid: String): String = "${legIndex}_$uid"

    /** The `update()` pair that records [legIndex] as done by [uid]. */
    fun progressUpdate(uid: String, legIndex: Int): Pair<String, Any> =
        "$FIELD_PROGRESS.$uid" to FieldValue.arrayUnion(legIndex)

    /**
     * [MatchFetch.UnsupportedVersion] for a foreign `formatVersion`; [MatchFetch.NotFound] for
     * anything malformed, including a missing `createdAt` (the store reads with estimated
     * server timestamps, so a pending write still carries one).
     */
    fun decode(code: ShareCode, data: Map<String, Any?>): MatchFetch {
        val version = (data[FIELD_FORMAT_VERSION] as? Number)?.toInt() ?: return MatchFetch.NotFound
        if (version != FORMAT_VERSION) return MatchFetch.UnsupportedVersion(version)
        val match = decodeMatch(code, version, data) ?: return MatchFetch.NotFound
        return MatchFetch.Found(match)
    }

    fun decodeResult(data: Map<String, Any?>): LegResult? {
        val version = (data[FIELD_FORMAT_VERSION] as? Number)?.toInt() ?: return null
        if (version != FORMAT_VERSION) return null
        return LegResult(
            uid = data[FIELD_UID] as? String ?: return null,
            legIndex = (data[FIELD_LEG_INDEX] as? Number)?.toInt() ?: return null,
            timeMs = (data[FIELD_TIME] as? Number)?.toLong() ?: return null,
            hints = (data[FIELD_HINTS] as? Number)?.toInt() ?: return null,
            completedAtMs = (data[FIELD_COMPLETED_AT] as? Number)?.toLong() ?: return null,
            gaveUp = data[FIELD_GAVE_UP] as? Boolean ?: return null,
        )
    }

    private fun decodeMatch(code: ShareCode, version: Int, data: Map<String, Any?>): Match? {
        val createdAt = (data[FIELD_CREATED_AT] as? Timestamp)?.toInstant() ?: return null
        val challenger = Player(
            uid = data[FIELD_CHALLENGER_UID] as? String ?: return null,
            name = data[FIELD_CHALLENGER_NAME] as? String ?: return null,
            photoUrl = data[FIELD_CHALLENGER_PHOTO] as? String,
        )
        val rematchOf = when (val raw = data[FIELD_REMATCH_OF]) {
            null -> null
            is String -> ShareCode.parse(raw) ?: return null
            else -> return null
        }
        return Match(
            code = code,
            formatVersion = version,
            createdAt = createdAt,
            deadlineHours = (data[FIELD_DEADLINE_HOURS] as? Number)?.toInt() ?: return null,
            challenger = challenger,
            invitedUid = data[FIELD_INVITED_UID] as? String,
            opponent = decodeOpponent(data),
            legs = decodeLegs(data[FIELD_LEGS]) ?: return null,
            includeFriends = data[FIELD_INCLUDE_FRIENDS] as? Boolean ?: return null,
            cancelled = data[FIELD_CANCELLED] as? Boolean ?: return null,
            declined = data[FIELD_DECLINED] as? Boolean ?: return null,
            rematchOf = rematchOf,
            progress = decodeProgress(data[FIELD_PROGRESS]),
        )
    }

    /** No `opponentUid` means the seat is open. A taken seat with a bad name stays taken, named "" for the UI to fill in. */
    private fun decodeOpponent(data: Map<String, Any?>): Player? {
        val uid = data[FIELD_OPPONENT_UID] as? String ?: return null
        val name = data[FIELD_OPPONENT_NAME] as? String ?: ""
        return Player(uid, name, data[FIELD_OPPONENT_PHOTO] as? String)
    }

    private fun decodeLegs(raw: Any?): List<MatchLeg>? {
        val entries = raw as? List<*> ?: return null
        if (entries.isEmpty()) return null
        return entries.map { entry ->
            val leg = entry as? Map<*, *> ?: return null
            if (!leg.containsKey(FIELD_AUTHOR_UID)) return null
            val authorUid = when (val author = leg[FIELD_AUTHOR_UID]) {
                null -> null
                is String -> author
                else -> return null
            }
            MatchLeg(LegRef.parse(leg[FIELD_REF] as? String ?: return null) ?: return null, authorUid)
        }
    }

    /** `Map<String, List<Long>>` from Firestore; non-numeric entries are dropped. */
    private fun decodeProgress(raw: Any?): Map<String, Set<Int>> {
        val byUid = raw as? Map<*, *> ?: return emptyMap()
        return buildMap {
            for ((uid, legs) in byUid) {
                if (uid !is String || legs !is List<*>) continue
                put(uid, legs.mapNotNull { (it as? Number)?.toInt() }.toSet())
            }
        }
    }
}
