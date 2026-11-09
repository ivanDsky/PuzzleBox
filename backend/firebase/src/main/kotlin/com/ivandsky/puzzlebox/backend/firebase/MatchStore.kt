package com.ivandsky.puzzlebox.backend.firebase

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import com.ivandsky.puzzlebox.data.match.DAILY_MATCH_CAP
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * The SDK seam for duels: raw `matches/{code}`, `matches/{code}/results/{legIndex}_{uid}` and
 * `matchQuota/{uid}` fields in and out, every map built or read by [MatchDocCodec]. Failures
 * surface as Firebase exceptions for the repository to map; a write the rules refuse is
 * `PERMISSION_DENIED`.
 */
internal interface MatchStore {

    /** True when `puzzles/{code}` exists on the server (puzzle and match codes share one namespace). */
    suspend fun codeTaken(code: String): Boolean

    /** True when `matches/{code}` exists on the server. */
    suspend fun matchExists(code: String): Boolean

    /**
     * One transaction, one attempt: reports an existing `matches/{code}` with its challenger, or
     * stops at the cap, or creates the match from [fields] and bumps the quota for [utcDay],
     * naming [code] as the bump's `last`.
     */
    suspend fun create(code: String, fields: Map<String, Any?>, uid: String, utcDay: String): MatchCreate

    /** Takes the open seat with [fields] unless [settledClaim] already answers; [now] is the deadline's clock. */
    suspend fun claim(code: String, fields: Map<String, Any?>, uid: String, now: Instant): SeatWrite

    /** Sets `cancelled` unless [settledCancel] already answers. */
    suspend fun cancel(code: String, uid: String): SeatWrite

    /** Sets `declined` unless [settledDecline] already answers. */
    suspend fun decline(code: String, uid: String): SeatWrite

    /** The match document as it changes, read with estimated server timestamps. */
    fun observe(code: String): Flow<MatchSnapshot>

    /** The documents whose `participants` contain [uid], by code. */
    fun observeMine(uid: String): Flow<Map<String, Map<String, Any?>>>

    /**
     * One batch: the result document [fields] and the `progress.{uid}` union of [legIndex]. It
     * completes when the server acknowledges, so offline it stays queued and never returns.
     */
    suspend fun post(code: String, fields: Map<String, Any?>, uid: String, legIndex: Int)

    /** The result's fields from the server, null when there is no such document. */
    suspend fun readResult(code: String, legIndex: Int, uid: String): Map<String, Any?>?
}

/** What a [MatchStore.create] transaction did. */
internal sealed interface MatchCreate {
    data object Created : MatchCreate
    data class Exists(val challengerUid: String) : MatchCreate
    data object CapReached : MatchCreate
}

/** What a claim, cancel or decline transaction ended with; [Done] is for the last two. */
internal enum class SeatWrite { Claimed, Taken, Cancelled, Closed, NotFound, Done }

/** One reading of a match document; a cache miss with no server answer yet is not an absence. */
internal sealed interface MatchSnapshot {
    data class Doc(val fields: Map<String, Any?>) : MatchSnapshot
    data object Absent : MatchSnapshot
    data object Unconfirmed : MatchSnapshot
}

/** The claim's answer when nothing needs writing, or null when the seat is to be taken. */
internal fun settledClaim(match: Map<String, Any?>?, uid: String, now: Instant): SeatWrite? = when {
    match == null -> SeatWrite.NotFound
    match[MatchDocCodec.FIELD_OPPONENT_UID] == uid -> SeatWrite.Claimed
    match[MatchDocCodec.FIELD_OPPONENT_UID] != null -> SeatWrite.Taken
    match[MatchDocCodec.FIELD_CANCELLED] == true || match[MatchDocCodec.FIELD_DECLINED] == true -> SeatWrite.Cancelled
    deadlineOf(match)?.let { !now.isBefore(it) } == true -> SeatWrite.Closed
    else -> null
}

/** Done when [uid] cancelled it already, so a retry after a lost ack succeeds; else null. */
internal fun settledCancel(match: Map<String, Any?>?, uid: String): SeatWrite? = when {
    match == null -> SeatWrite.NotFound
    match[MatchDocCodec.FIELD_CHALLENGER_UID] == uid && match[MatchDocCodec.FIELD_CANCELLED] == true -> SeatWrite.Done
    else -> null
}

/** Done when the invited [uid] declined it already; else null. */
internal fun settledDecline(match: Map<String, Any?>?, uid: String): SeatWrite? = when {
    match == null -> SeatWrite.NotFound
    match[MatchDocCodec.FIELD_INVITED_UID] == uid && match[MatchDocCodec.FIELD_DECLINED] == true -> SeatWrite.Done
    else -> null
}

private fun deadlineOf(match: Map<String, Any?>): Instant? {
    val createdAt = (match[MatchDocCodec.FIELD_CREATED_AT] as? Timestamp)?.toInstant() ?: return null
    val hours = (match[MatchDocCodec.FIELD_DEADLINE_HOURS] as? Number)?.toLong() ?: return null
    return createdAt.plusSeconds(hours * SECONDS_PER_HOUR)
}

private const val SECONDS_PER_HOUR = 3_600L

/**
 * The `matchQuota/{uid}` count after one more create on [utcDay], or null when that day's cap is
 * used up. The document has the shape of `authors/{uid}`, so [authorFields] writes it.
 */
internal fun nextMatchCount(quota: Map<String, Any?>?, utcDay: String): Long? {
    val sameDay = quota?.get(FIELD_AUTHOR_DAY) == utcDay
    val count = if (sameDay) (quota?.get(FIELD_AUTHOR_COUNT) as? Number)?.toLong() ?: 0L else 0L
    return if (count >= DAILY_MATCH_CAP) null else count + 1
}

/**
 * Runs a single-attempt transaction and, when another writer won the race for the document
 * (`ABORTED`, `FAILED_PRECONDITION` or `ALREADY_EXISTS`), runs it once more: the second read
 * sees the winner's write and answers Taken, Claimed or Exists. A lost race is never an outage.
 */
internal suspend fun <T> rerunOnLostRace(transaction: suspend () -> T): T = try {
    transaction()
} catch (e: FirebaseFirestoreException) {
    if (e.code in LOST_RACE) transaction() else throw e
}

private val LOST_RACE = setOf(
    FirebaseFirestoreException.Code.ABORTED,
    FirebaseFirestoreException.Code.FAILED_PRECONDITION,
    FirebaseFirestoreException.Code.ALREADY_EXISTS,
)
