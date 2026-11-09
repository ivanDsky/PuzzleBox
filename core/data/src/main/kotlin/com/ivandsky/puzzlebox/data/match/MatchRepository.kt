package com.ivandsky.puzzlebox.data.match

import com.ivandsky.puzzlebox.model.ShareCode
import kotlinx.coroutines.flow.Flow

sealed interface CreateResult {
    data class Created(val code: ShareCode) : CreateResult
    data object CodeTaken : CreateResult
    data object DailyCapReached : CreateResult
    data object Offline : CreateResult
    data object SignedOut : CreateResult
    data class Rejected(val message: String?) : CreateResult
}

sealed interface ClaimResult {
    data object Claimed : ClaimResult
    data object Taken : ClaimResult
    data object Cancelled : ClaimResult
    data object Closed : ClaimResult
    data object Offline : ClaimResult
    data object SignedOut : ClaimResult
    data class Rejected(val message: String?) : ClaimResult
}

sealed interface WriteResult {
    data object Done : WriteResult
    data object Offline : WriteResult
    data class Rejected(val message: String?) : WriteResult
}

sealed interface MatchFetch {
    data class Found(val match: Match) : MatchFetch
    data object NotFound : MatchFetch
    data object Offline : MatchFetch
    data class UnsupportedVersion(val found: Int) : MatchFetch
}

sealed interface PostResult {
    data object Posted : PostResult
    data object Duplicate : PostResult
    data object Late : PostResult
    data class Rejected(val message: String?) : PostResult
    data class Unavailable(val message: String?) : PostResult
}

sealed interface ResultRead {
    data class Found(val result: LegResult) : ResultRead
    data object Sealed : ResultRead
    data object Missing : ResultRead
    data object Offline : ResultRead
}

/**
 * The one remote port of a duel. Never throws (except `CancellationException`); every failure is a result value.
 * `Late` = rules rejection when device time >= `closesAt - 1 min` or `completedAtMs > deadline`.
 */
interface MatchRepository {
    /** Null = offline. */
    suspend fun puzzleCodeTaken(code: ShareCode): Boolean?

    /** Create-if-absent plus quota bump; the caller's own existing document is `Created`. */
    suspend fun create(draft: MatchDraft): CreateResult
    suspend fun claim(code: ShareCode, me: Player): ClaimResult
    suspend fun cancel(code: ShareCode): WriteResult
    suspend fun decline(code: ShareCode): WriteResult
    fun observe(code: ShareCode): Flow<MatchFetch>

    /** Matches whose participants contain me; null = signed out. */
    fun observeMine(): Flow<List<Match>?>

    /** Result document plus progress arrayUnion, one batch. */
    suspend fun post(code: ShareCode, result: LegResult): PostResult
    suspend fun readResult(code: ShareCode, legIndex: Int, uid: String): ResultRead
}

/** Mirrors the `matchQuota` rule bound. */
const val DAILY_MATCH_CAP = 20
