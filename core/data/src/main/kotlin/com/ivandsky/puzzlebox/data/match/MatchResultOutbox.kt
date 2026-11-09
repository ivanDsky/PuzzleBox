package com.ivandsky.puzzlebox.data.match

import com.ivandsky.puzzlebox.model.ShareCode
import kotlinx.coroutines.flow.Flow

/** Where a queued result stands; `MatchResultSync` decides when each is set, the outbox only stores it. */
enum class ResultPostState { Pending, Posted, Late, LocalOnly }

/** One queued result of [code] with its post [state] and the number of post [attempts] so far. */
data class OutboxResult(val code: ShareCode, val result: LegResult, val state: ResultPostState, val attempts: Int)

/**
 * The local queue of duel results waiting to be sent, and the player's own record of them. A
 * finished leg is written here first (offline-first); a row survives restarts.
 */
interface MatchResultOutbox {
    /** Insert-if-absent: one attempt per (code, legIndex, uid); a repeat changes nothing. New rows are Pending, 0 attempts. */
    suspend fun enqueue(code: ShareCode, result: LegResult)

    /** Rows of one match ordered by leg index. */
    fun observe(code: ShareCode): Flow<List<OutboxResult>>

    /** Pending rows of every match, oldest completion first. */
    fun observePending(): Flow<List<OutboxResult>>

    /** Every row of every match, newest completion first. */
    fun observeAll(): Flow<List<OutboxResult>>

    /** Sets state and attempts of an existing row; no-op when the row is missing. */
    suspend fun update(code: ShareCode, legIndex: Int, uid: String, state: ResultPostState, attempts: Int)
}
