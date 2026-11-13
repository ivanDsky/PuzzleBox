package com.ivandsky.puzzlebox.data.replay

import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef

/**
 * The signed-in player's published daily replay and other players' (A20), one per board. Reads are gated by the owner's
 * visibility tier in the backend rules, not here; callers gate [publish] by the player's own tier.
 */
interface DailyReplayRepository {

    /** Stores [blob] as the signed-in player's replay on [board]; false when it is empty, over [MAX_BYTES], signed out, or the write fails. */
    suspend fun publish(board: LeaderboardRef, blob: ByteArray): Boolean

    /** [uid]'s replay on [board]; null when absent, denied by the owner's tier, offline or failed. */
    suspend fun fetch(board: LeaderboardRef, uid: String): ByteArray?

    /** Removes the signed-in player's replay on [board]; a no-op when signed out or absent. */
    suspend fun delete(board: LeaderboardRef)

    companion object {
        /** Same cap as a solve replay (A12); the backend rules enforce it too. */
        const val MAX_BYTES: Int = 12_288
    }
}
